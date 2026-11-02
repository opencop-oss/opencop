// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.technical.gateway.cache;

import com.opencop.core.sdk.ports.gateway.cache.CacheException;
import com.opencop.core.sdk.ports.gateway.cache.CacheService;
import com.opencop.core.sdk.technical.gateway.cache.EventGeoCacheKeyLayout;
import com.opencop.core.sdk.technical.gateway.cache.redis.RedisEventGeoSourceDirectory;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import org.geojson.Feature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;

public class Janitor {

  private static final Logger log = LoggerFactory.getLogger(Janitor.class);

  private final CacheService<Feature> redis;
  private final RedisEventGeoSourceDirectory sourceDirectory;
  private final int pageSize;
  private volatile double sourceDirectorySize = Double.NaN;

  public Janitor(
      final CacheService<Feature> redis,
      final RedisEventGeoSourceDirectory sourceDirectory,
      final int pageSize,
      final MeterRegistry meterRegistry) {
    if (pageSize <= 0) {
      throw new IllegalArgumentException("pageSize must be positive");
    }
    this.redis = Objects.requireNonNull(redis, "redis");
    this.sourceDirectory = Objects.requireNonNull(sourceDirectory, "sourceDirectory");
    this.pageSize = pageSize;
    Gauge.builder(
            "opencop_redis_source_directory_size", this, janitor -> janitor.sourceDirectorySize)
        .description(
            "Registered Redis source namespaces observed before the latest Janitor cleanup")
        .register(meterRegistry);
  }

  @Scheduled(fixedDelayString = "${janitor.interval.ms:30000}")
  public void run() {
    Instant start = Instant.now();
    log.debug("Purging Redis of stale events at: {}", start);
    purge(start.toEpochMilli());
    log.debug(
        "Finished purge at: {} | Duration: {}ms",
        Instant.now(),
        Duration.between(start, Instant.now()).toMillis());
  }

  private void purge(long now) {
    try {
      // A failed or null directory read must not keep publishing an older count as a new sample.
      sourceDirectorySize = Double.NaN;
      var sources = sourceDirectory.discoverSources(count -> sourceDirectorySize = count);
      if (sources == null || sources.isEmpty()) {
        return;
      }
      for (var sourceKeys : sources) {
        boolean firstPass = true;
        while (true) {
          Set<String> staleEvents =
              redis.getEventKeysByScore(sourceKeys.staleAtKey(), -1, now, 0, pageSize);
          if (staleEvents == null) {
            sourceDirectory.unregisterIfEmpty(sourceKeys);
            break;
          }
          if (firstPass && staleEvents.isEmpty()) {
            Set<String> notStaleEvents =
                redis.getEventKeysByScore(
                    sourceKeys.staleAtKey(), now, Instant.MAX.getEpochSecond(), 0, 1);
            // If it's empty, just delete it, no need to keep it
            if (notStaleEvents == null || notStaleEvents.isEmpty()) {
              sourceDirectory.unregisterIfEmpty(sourceKeys);
            }
            break;
          }
          firstPass = false;

          this.handleStaleEvents(staleEvents, sourceKeys);

          if (staleEvents.size() < pageSize) {
            break;
          }
        }
      }
    } catch (CacheException e) {
      log.error("Failed to purge stale geo events: {}", e.getCode());
    } catch (DataAccessException e) {
      log.error(
          "Failed to purge stale geo events (Redis access: {})", e.getClass().getSimpleName());
    }
  }

  private void handleStaleEvents(
      Set<String> staleEvents, EventGeoCacheKeyLayout.SourceKeys sourceKeys) throws CacheException {
    String eventPrefix = sourceKeys.eventPrefix() + ":";
    for (String stale : staleEvents) {
      if (stale != null && stale.startsWith(eventPrefix)) {
        redis.deleteIfExists(stale);
      }
      redis.removeFromSet(sourceKeys.updatedAtKey(), stale);
      redis.removeFromSet(sourceKeys.staleAtKey(), stale);
    }
  }
}
