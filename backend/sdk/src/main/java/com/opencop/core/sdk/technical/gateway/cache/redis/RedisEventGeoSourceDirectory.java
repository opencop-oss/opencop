// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.cache.redis;

import com.opencop.core.sdk.technical.gateway.cache.EventGeoCacheKeyLayout;
import com.opencop.core.sdk.technical.gateway.cache.EventGeoCacheKeys;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.IntConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

/**
 * Normal source discovery reads a compact global directory. Cluster members are retained even when
 * empty; source-local markers are only used by the separate recovery sweep.
 */
public final class RedisEventGeoSourceDirectory {

  private static final Logger log = LoggerFactory.getLogger(RedisEventGeoSourceDirectory.class);
  private static final String UNREGISTER_IF_EMPTY_SCRIPT_PATH = "redis/geo_unregister_if_empty.lua";
  private static final String REMOVALS_METRIC = "opencop_redis_source_directory_removals_total";
  public static final String CLUSTER_SOURCE_SET = "opencop:event:geo:sources";

  private final EventGeoCacheKeyLayout layout;
  private final StringRedisTemplate redisTemplate;
  private final RedisScript<Long> unregisterIfEmptyScript;
  private final Counter removals;

  public RedisEventGeoSourceDirectory(
      EventGeoCacheKeyLayout layout,
      StringRedisTemplate redisTemplate,
      MeterRegistry meterRegistry) {
    this(layout, redisTemplate, meterRegistry, unregisterIfEmptyScript());
  }

  RedisEventGeoSourceDirectory(
      EventGeoCacheKeyLayout layout,
      StringRedisTemplate redisTemplate,
      MeterRegistry meterRegistry,
      RedisScript<Long> unregisterIfEmptyScript) {
    this.layout = Objects.requireNonNull(layout, "layout");
    this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate");
    this.unregisterIfEmptyScript =
        Objects.requireNonNull(unregisterIfEmptyScript, "unregisterIfEmptyScript");
    this.removals =
        Counter.builder(REMOVALS_METRIC)
            .description("Confirmed standalone source-directory member removals")
            .register(Objects.requireNonNull(meterRegistry, "meterRegistry"));
  }

  /** The layout every returned aggregate belongs to. */
  public EventGeoCacheKeyLayout layout() {
    return layout;
  }

  /**
   * Returns every currently registered source, or throws if the directory could not be read
   * completely.
   */
  public List<EventGeoCacheKeyLayout.SourceKeys> discoverSources() {
    return discoverSources(_ -> {});
  }

  /**
   * Reports the raw directory size synchronously after a non-null read, before parsing or cleanup.
   * A failed or null read does not notify the observer; malformed entries still contribute to the
   * observed size. This reuses the discovery read and performs no additional Redis operation.
   */
  public List<EventGeoCacheKeyLayout.SourceKeys> discoverSources(IntConsumer sourceCountObserver) {
    Objects.requireNonNull(sourceCountObserver, "sourceCountObserver");
    return switch (layout) {
      case STANDALONE -> discoverStandaloneSources(sourceCountObserver);
      case CLUSTER -> discoverClusterSources(sourceCountObserver);
    };
  }

  /**
   * Retires one source's registration, with a layout-dependent emptiness guarantee.
   *
   * <p>{@link EventGeoCacheKeyLayout#CLUSTER} is atomically conditional: the Lua script re-checks
   * both live indexes in the source's own slot and refuses to delete the marker if either is
   * non-empty, so a writer that repopulated the source cannot be erased by a stale cleaner.
   *
   * <p>{@link EventGeoCacheKeyLayout#STANDALONE} is an unconditional {@code ZREM} of the exact
   * source member, preserving the legacy standalone cleanup semantics. In standalone the emptiness
   * decision belongs to the caller, exactly as the existing WebSocket Janitor already makes it
   * before removing a source. Do not assume this call re-checks anything in standalone mode.
   *
   * @return whether this call removed the source's registration
   */
  public boolean unregisterIfEmpty(EventGeoCacheKeyLayout.SourceKeys sourceKeys) {
    Objects.requireNonNull(sourceKeys, "sourceKeys");
    if (sourceKeys.layout() != layout) {
      throw new IllegalArgumentException(
          "Directory selected "
              + layout
              + " but received "
              + sourceKeys.layout()
              + " source keys for "
              + sourceKeys.sourceId());
    }
    return switch (layout) {
      case STANDALONE -> {
        var removed =
            redisTemplate
                .opsForZSet()
                .remove(EventGeoCacheKeys.SOURCE_INDEX, sourceKeys.sourcePrefix());
        yield recordStandaloneRemovals(removed);
      }
      case CLUSTER -> {
        var deleted =
            redisTemplate.execute(
                unregisterIfEmptyScript,
                List.of(
                    sourceKeys.registryKey(), sourceKeys.updatedAtKey(), sourceKeys.staleAtKey()));
        yield deleted != null && deleted > 0L;
      }
    };
  }

  private List<EventGeoCacheKeyLayout.SourceKeys> discoverStandaloneSources(
      IntConsumer sourceCountObserver) {
    var members = redisTemplate.opsForZSet().range(EventGeoCacheKeys.SOURCE_INDEX, 0, -1);
    if (members != null) {
      sourceCountObserver.accept(members.size());
    }
    if (members == null || members.isEmpty()) {
      return List.of();
    }
    var sources = new LinkedHashSet<EventGeoCacheKeyLayout.SourceKeys>();
    var malformed = new ArrayList<String>();
    for (var member : members) {
      try {
        sources.add(layout.sourceKeysFromDirectoryEntry(member));
      } catch (IllegalArgumentException exception) {
        malformed.add(member);
      }
    }
    if (!malformed.isEmpty()) {
      log.warn(
          "Removing {} malformed member(s) from {}",
          malformed.size(),
          EventGeoCacheKeys.SOURCE_INDEX);
      var removed =
          redisTemplate.opsForZSet().remove(EventGeoCacheKeys.SOURCE_INDEX, malformed.toArray());
      recordStandaloneRemovals(removed);
    }
    return List.copyOf(sources);
  }

  /** Register one prepared chunk before any event script; standalone registration stays in Lua. */
  public void registerPreparedSources(Collection<EventGeoCacheKeyLayout.SourceKeys> sources) {
    if (layout == EventGeoCacheKeyLayout.STANDALONE || sources.isEmpty()) return;
    var ids = new LinkedHashSet<String>();
    for (var source : sources) {
      if (source.layout() != layout) throw new IllegalArgumentException("Foreign source layout");
      ids.add(source.sourceId());
    }
    Objects.requireNonNull(
        redisTemplate.opsForSet().add(CLUSTER_SOURCE_SET, ids.toArray(String[]::new)),
        "Registration must execute outside a pipeline or transaction");
  }

  private List<EventGeoCacheKeyLayout.SourceKeys> discoverClusterSources(
      IntConsumer sourceCountObserver) {
    var members = redisTemplate.opsForSet().members(CLUSTER_SOURCE_SET);
    if (members != null) {
      sourceCountObserver.accept(members.size());
    }
    if (members == null || members.isEmpty()) return List.of();
    var sources = new ArrayList<EventGeoCacheKeyLayout.SourceKeys>();
    var malformed = 0;
    for (var member : members) {
      try {
        sources.add(layout.sourceKeys(member));
      } catch (IllegalArgumentException exception) {
        malformed++;
      }
    }
    if (malformed > 0) log.warn("Ignoring {} malformed geo directory members", malformed);
    return List.copyOf(sources);
  }

  private boolean recordStandaloneRemovals(Long removed) {
    if (removed == null || removed <= 0L) return false;
    // Count Redis-confirmed removals, including malformed members and re-retired source IDs.
    // Cluster marker deletion leaves its directory member retained, so it must not count here.
    removals.increment(removed);
    return true;
  }

  private static RedisScript<Long> unregisterIfEmptyScript() {
    var script = new DefaultRedisScript<Long>();
    script.setResultType(Long.class);
    script.setScriptSource(
        new ResourceScriptSource(new ClassPathResource(UNREGISTER_IF_EMPTY_SCRIPT_PATH)));
    return script;
  }
}
