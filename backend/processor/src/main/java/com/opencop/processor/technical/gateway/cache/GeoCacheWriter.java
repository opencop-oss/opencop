// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.cache;

import com.opencop.core.sdk.model.cache.CacheEventsNotification;
import com.opencop.core.sdk.ports.gateway.cache.CachePublisher;
import com.opencop.core.sdk.technical.gateway.cache.EventGeoCacheKeyLayout;
import com.opencop.core.sdk.technical.gateway.cache.EventGeoCacheKeyLayout.SourceKeys;
import com.opencop.core.sdk.technical.gateway.cache.redis.RedisEventGeoSourceDirectory;
import com.opencop.core.sdk.technical.model.opencop.OpenCopProperties;
import com.opencop.core.sdk.technical.model.opencop.parser.OpenCopPropertiesParser;
import com.opencop.core.sdk.technical.model.serializer.Serializer;
import com.opencop.processor.ports.technical.gateway.cache.GeoCacheGateway;
import io.lettuce.core.RedisNoScriptException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.geojson.Feature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisPipelineException;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.script.RedisScript;

public class GeoCacheWriter implements GeoCacheGateway {

  private static final Logger log = LoggerFactory.getLogger(GeoCacheWriter.class);
  private static final int PIPELINE_CHUNK_SIZE = 1_000;

  private final RedisTemplate<String, byte[]> redisTemplate;
  private final EventGeoCacheKeyLayout keyLayout;
  private final RedisEventGeoSourceDirectory sourceDirectory;
  private final RedisScript<Long> geoUpsertScript;
  private final RedisScript<Long> geoEvictScript;
  private final Serializer<Feature, byte[]> serializer;
  private final CachePublisher<CacheEventsNotification> publisher;

  private final Object scriptLoadLock = new Object();
  private volatile String cachedScriptSha;

  public GeoCacheWriter(
      RedisTemplate<String, byte[]> redisTemplate,
      EventGeoCacheKeyLayout keyLayout,
      RedisEventGeoSourceDirectory sourceDirectory,
      RedisScript<Long> geoUpsertScript,
      RedisScript<Long> geoEvictScript,
      Serializer<Feature, byte[]> serializer,
      CachePublisher<CacheEventsNotification> publisher) {
    this.redisTemplate = redisTemplate;
    this.keyLayout = keyLayout;
    this.sourceDirectory = sourceDirectory;
    this.geoUpsertScript = geoUpsertScript;
    this.geoEvictScript = geoEvictScript;
    this.serializer = serializer;
    this.publisher = publisher;
  }

  @Override
  public void write(List<Feature> inputs, boolean publishUpdates) {
    if (inputs == null || inputs.isEmpty()) {
      return;
    }

    Instant startedAt = Instant.now();
    int chunkCount = Math.ceilDiv(inputs.size(), PIPELINE_CHUNK_SIZE);
    int sourceCount = distinctSourceCount(inputs);

    log.debug(
        "Starting geo cache write for {} feature(s) across {} source(s) in {} chunk(s), publishUpdates={}",
        inputs.size(),
        sourceCount,
        chunkCount,
        publishUpdates);

    final long nowMs = System.currentTimeMillis();
    final long staleKeepMs = Duration.ofMinutes(1).toMillis();
    // Resolve script text once; SHA is resolved per attempt so that a reload mid-loop
    // picks up the fresh SHA for subsequent chunks.
    final String scriptText = geoUpsertScript.getScriptAsString();

    try {
      for (int offset = 0; offset < inputs.size(); offset += PIPELINE_CHUNK_SIZE) {
        int end = Math.min(offset + PIPELINE_CHUNK_SIZE, inputs.size());
        List<Feature> chunk = inputs.subList(offset, end);
        List<PreparedFeature> preparedFeatures = prepareChunk(chunk, nowMs);
        if (preparedFeatures.isEmpty()) {
          continue;
        }

        sourceDirectory.registerPreparedSources(
            preparedFeatures.stream().map(PreparedFeature::sourceKeys).toList());

        // If Redis lost the cached script (restart, SCRIPT FLUSH, failover), the pipeline
        // will fail with NOSCRIPT. We catch that, re-register the script, and retry the
        // chunk once. The upsert script is idempotent so replaying a chunk is safe.
        // TODO: a cleaner long-term approach is Spring's DefaultScriptExecutor which handles
        //  EVALSHA→EVAL fallback automatically, but it doesn't compose with executePipelined().
        try {
          executePipelineChunk(
              preparedFeatures, ensureScriptLoaded(scriptText), nowMs, staleKeepMs);
        } catch (RedisPipelineException e) {
          if (!hasOnlyNoScriptFailures(e)) {
            throw e;
          }
          log.warn(
              "NOSCRIPT for geo cache chunk offset={}, size={}; reloading script and retrying",
              offset,
              chunk.size(),
              e);
          cachedScriptSha = null;
          sourceDirectory.registerPreparedSources(
              preparedFeatures.stream().map(PreparedFeature::sourceKeys).toList());
          executePipelineChunk(
              preparedFeatures, ensureScriptLoaded(scriptText), nowMs, staleKeepMs);
          log.info(
              "NOSCRIPT recovery succeeded for geo cache chunk offset={}, size={}",
              offset,
              chunk.size());
        }
      }

      if (publishUpdates) {
        sendUpdate(inputs);
      }

      log.debug(
          "Completed geo cache write for {} feature(s) across {} source(s) in {} chunk(s), publishUpdates={}, durationMs={}",
          inputs.size(),
          sourceCount,
          chunkCount,
          publishUpdates,
          Duration.between(startedAt, Instant.now()).toMillis());
    } catch (Exception e) {
      log.error(
          "Geo cache write failed for {} feature(s) across {} source(s) in {} chunk(s), publishUpdates={}, durationMs={}",
          inputs.size(),
          sourceCount,
          chunkCount,
          publishUpdates,
          Duration.between(startedAt, Instant.now()).toMillis(),
          e);
      throw e;
    }
  }

  private void executePipelineChunk(
      List<PreparedFeature> preparedFeatures, String scriptSha, long nowMs, long staleKeepMs) {
    redisTemplate.executePipelined(
        (RedisCallback<Object>)
            connection -> {
              for (PreparedFeature prepared : preparedFeatures) {
                var sourceKeys = prepared.sourceKeys();
                connection
                    .scriptingCommands()
                    .evalSha(
                        scriptSha,
                        ReturnType.INTEGER,
                        4,
                        b(prepared.eventKey()),
                        b(sourceKeys.updatedAtKey()),
                        b(sourceKeys.staleAtKey()),
                        b(sourceKeys.registryKey()),
                        prepared.payload(),
                        b(Long.toString(prepared.ttlMs())),
                        b(Long.toString(prepared.eventTime())),
                        b(Long.toString(prepared.staleAt())),
                        b(Long.toString(nowMs)),
                        b(sourceKeys.sourcePrefix()),
                        b(Long.toString(staleKeepMs)),
                        b(sourceKeys.registryKind().wireValue()));
              }
              return null;
            });
  }

  private List<PreparedFeature> prepareChunk(List<Feature> chunk, long nowMs) {
    List<PreparedFeature> preparedFeatures = new ArrayList<>(chunk.size());
    for (Feature feature : chunk) {
      OpenCopProperties opencop = OpenCopPropertiesParser.readFromFeature(feature);
      if (opencop == null) {
        continue;
      }

      byte[] payload;
      try {
        payload = serializer.serialize(feature);
      } catch (RuntimeException _) {
        // Preserve best-effort serialization: skip this payload without failing the chunk.
        // Successful chunks still publish the original notification IDs.
        continue;
      }

      var sourceKeys = keyLayout.sourceKeys(opencop.getSource());
      var eventTime = opencop.getTimestampz().getTime();
      var staleAt = opencop.getTimestampz().getStale();
      preparedFeatures.add(
          new PreparedFeature(
              sourceKeys,
              sourceKeys.eventKey(opencop.getObjectId()),
              payload,
              staleAt - nowMs,
              eventTime,
              staleAt));
    }
    return preparedFeatures;
  }

  /**
   * Check whether a pipeline exception was caused by Redis no longer having the Lua script cached
   * (NOSCRIPT). This happens after Redis restarts, SCRIPT FLUSH, or Azure Cache failovers.
   *
   * <p>Spring may store the NOSCRIPT error either in the exception cause chain or in the
   * per-command pipeline results, so we check both.
   */
  private static boolean hasOnlyNoScriptFailures(RedisPipelineException exception) {
    var cause = exception.getCause();
    var foundNoScript = containsCause(cause, RedisNoScriptException.class);
    if (cause != null && !foundNoScript) {
      return false;
    }

    var results = exception.getPipelineResult();
    for (var result : results) {
      if (!(result instanceof Throwable failure)) {
        continue;
      }
      if (!containsCause(failure, RedisNoScriptException.class)) {
        return false;
      }
      foundNoScript = true;
    }
    return foundNoScript;
  }

  private static boolean containsCause(Throwable t, Class<? extends Throwable> type) {
    Throwable current = t;
    while (current != null) {
      if (type.isInstance(current)) {
        return true;
      }
      current = current.getCause();
    }
    return false;
  }

  @Override
  public void evictObject(UUID sourceId, String objectId, long ifNotNewerThanMs) {
    var sourceKeys = keyLayout.sourceKeys(sourceId);
    String eventKey = sourceKeys.eventKey(objectId);

    Long result =
        redisTemplate.execute(
            geoEvictScript,
            List.of(sourceKeys.updatedAtKey(), sourceKeys.staleAtKey(), eventKey),
            b(Long.toString(ifNotNewerThanMs)));

    if (result == null || result == 0L) {
      log.debug(
          "Ghost eviction skipped — event not in Redis: source={}, objectId={}",
          sourceId,
          objectId);
    } else if (result == -1L) {
      log.info(
          "Ghost eviction skipped — replacement found in Redis (threshold={}): source={}, objectId={}",
          ifNotNewerThanMs,
          sourceId,
          objectId);
    } else {
      log.info("Evicted ghost from Redis: source={}, objectId={}", sourceId, objectId);
    }
  }

  @Override
  public void clearSource(UUID sourceId) {
    var startedAt = Instant.now();
    var sourceKeys = keyLayout.sourceKeys(sourceId);

    log.info("Clearing geo cache for source {}", sourceId);
    String eventKeyPrefix = sourceKeys.eventPrefix() + ":";

    Set<byte[]> updatedAtMembers =
        redisTemplate.opsForZSet().range(sourceKeys.updatedAtKey(), 0, -1);
    Set<byte[]> staleAtMembers = redisTemplate.opsForZSet().range(sourceKeys.staleAtKey(), 0, -1);

    List<String> keysToDelete = new ArrayList<>();
    List<String> scannedKeys = scanKeys(sourceKeys.eventScanPattern());
    if (scannedKeys != null) {
      keysToDelete.addAll(scannedKeys);
    }
    addSourceScopedEventKeys(keysToDelete, updatedAtMembers, eventKeyPrefix);
    addSourceScopedEventKeys(keysToDelete, staleAtMembers, eventKeyPrefix);
    keysToDelete.add(sourceKeys.updatedAtKey());
    keysToDelete.add(sourceKeys.staleAtKey());

    redisTemplate.delete(keysToDelete);
    sourceDirectory.unregisterIfEmpty(sourceKeys);
    log.info(
        "Cleared geo cache for source {}: deletedKeys={}, durationMs={}",
        sourceId,
        keysToDelete.size(),
        Duration.between(startedAt, Instant.now()).toMillis());
  }

  private static void addSourceScopedEventKeys(
      List<String> keysToDelete, Set<byte[]> candidateKeys, String eventKeyPrefix) {
    if (candidateKeys == null) {
      return;
    }
    candidateKeys.stream()
        .map(bytes -> new String(bytes, StandardCharsets.UTF_8))
        .filter(key -> key.startsWith(eventKeyPrefix))
        .forEach(keysToDelete::add);
  }

  private List<String> scanKeys(String pattern) {
    return redisTemplate.execute(
        (RedisCallback<List<String>>)
            connection -> {
              List<String> keys = new ArrayList<>();
              try (var cursor =
                  connection
                      .keyCommands()
                      .scan(ScanOptions.scanOptions().match(pattern).count(1000).build())) {
                while (cursor.hasNext()) {
                  keys.add(new String(cursor.next(), StandardCharsets.UTF_8));
                }
              }
              return keys;
            });
  }

  private String ensureScriptLoaded(String script) {
    String cached = cachedScriptSha;
    if (cached != null) {
      return cached;
    }

    synchronized (scriptLoadLock) {
      if (cachedScriptSha != null) {
        return cachedScriptSha;
      }

      byte[] scriptBytes = b(script);
      String loadedSha =
          redisTemplate.execute(
              (RedisCallback<String>)
                  connection -> connection.scriptingCommands().scriptLoad(scriptBytes));

      cachedScriptSha = loadedSha != null ? loadedSha : sha1Hex(scriptBytes);
      return cachedScriptSha;
    }
  }

  private static String sha1Hex(byte[] content) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-1");
      return HexFormat.of().formatHex(digest.digest(content));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-1 algorithm not available", e);
    }
  }

  private static int distinctSourceCount(List<Feature> features) {
    return (int)
        features.stream()
            .map(OpenCopPropertiesParser::readFromFeature)
            .filter(opencop -> opencop != null && opencop.getSource() != null)
            .map(OpenCopProperties::getSource)
            .distinct()
            .count();
  }

  private static byte[] b(String s) {
    return s.getBytes(StandardCharsets.UTF_8);
  }

  private void sendUpdate(List<Feature> features) {
    Map<UUID, List<String>> notifyMap = new HashMap<>();
    for (var feature : features) {
      OpenCopProperties opencop = OpenCopPropertiesParser.readFromFeature(feature);
      if (opencop == null) {
        log.warn("Failed to retrieve opencop properties from Feature");
        continue;
      }
      notifyMap
          .computeIfAbsent(opencop.getSource(), ignored -> new ArrayList<>())
          .add(opencop.getObjectId());
    }
    for (Map.Entry<UUID, List<String>> entry : notifyMap.entrySet()) {
      publisher.publish(new CacheEventsNotification(entry.getKey(), entry.getValue()));
    }
  }

  private record PreparedFeature(
      SourceKeys sourceKeys,
      String eventKey,
      byte[] payload,
      long ttlMs,
      long eventTime,
      long staleAt) {}
}
