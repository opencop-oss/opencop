// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.usecase.event;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.sdk.model.cache.CacheEventsNotification;
import com.opencop.core.sdk.ports.gateway.cache.CachePublisher;
import com.opencop.core.sdk.technical.gateway.cache.EventGeoCacheKeyLayout;
import com.opencop.core.sdk.technical.gateway.cache.redis.RedisEventGeoSourceDirectory;
import com.opencop.core.sdk.technical.model.opencop.OpenCopProperties;
import com.opencop.core.sdk.technical.model.opencop.Timestampz;
import com.opencop.core.sdk.technical.model.serializer.Serializer;
import com.opencop.processor.technical.gateway.cache.GeoCacheWriter;
import io.lettuce.core.RedisNoScriptException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.geojson.Feature;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisPipelineException;
import org.springframework.data.redis.connection.RedisScriptingCommands;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

class RealTimeGeoFeatureHandlerTest {

  @Test
  void process_loadsScriptOnce_andReusesShaAcrossInvocations() {
    @SuppressWarnings("unchecked")
    RedisTemplate<String, byte[]> redisTemplate = mock(RedisTemplate.class);
    @SuppressWarnings("unchecked")
    RedisScript<Long> geoUpsertScript = mock(RedisScript.class);
    @SuppressWarnings("unchecked")
    RedisScript<Long> geoEvictScript = mock(RedisScript.class);
    @SuppressWarnings("unchecked")
    Serializer<Feature, byte[]> serializer = mock(Serializer.class);
    @SuppressWarnings("unchecked")
    CachePublisher<CacheEventsNotification> publisher = mock(CachePublisher.class);

    RedisConnection connection = mock(RedisConnection.class);
    RedisScriptingCommands scriptingCommands = mock(RedisScriptingCommands.class);
    when(connection.scriptingCommands()).thenReturn(scriptingCommands);

    when(redisTemplate.execute(any(RedisCallback.class)))
        .thenAnswer(
            invocation -> {
              RedisCallback<?> callback = invocation.getArgument(0);
              return callback.doInRedis(connection);
            });

    when(redisTemplate.executePipelined(any(RedisCallback.class)))
        .thenAnswer(
            invocation -> {
              RedisCallback<?> callback = invocation.getArgument(0);
              callback.doInRedis(connection);
              return List.of();
            });

    when(serializer.serialize(any(Feature.class))).thenReturn("payload".getBytes());
    when(geoUpsertScript.getScriptAsString()).thenReturn("return 1");
    when(scriptingCommands.scriptLoad(any(byte[].class))).thenReturn("sha-1");

    RealTimeGeoFeatureHandler handler =
        new RealTimeGeoFeatureHandler(
            new GeoCacheWriter(
                redisTemplate,
                EventGeoCacheKeyLayout.STANDALONE,
                mock(RedisEventGeoSourceDirectory.class),
                geoUpsertScript,
                geoEvictScript,
                serializer,
                publisher));

    UUID sourceId = UUID.randomUUID();
    List<Feature> features = List.of(feature("evt-1", sourceId), feature("evt-2", sourceId));

    handler.process(features);
    handler.process(features);

    // Script gets loaded once per handler instance, then sha is reused.
    verify(scriptingCommands, times(1)).scriptLoad(eq("return 1".getBytes(StandardCharsets.UTF_8)));

    verify(redisTemplate, times(2)).executePipelined(any(RedisCallback.class));
    verify(scriptingCommands, times(features.size() * 2))
        .evalSha(eq("sha-1"), eq(ReturnType.INTEGER), eq(4), any(byte[][].class));

    verify(serializer, times(features.size() * 2)).serialize(any(Feature.class));
    verify(publisher, times(2)).publish(any(CacheEventsNotification.class));
  }

  @Test
  void process_splitsLargeInputIntoPipelineChunks() {
    @SuppressWarnings("unchecked")
    RedisTemplate<String, byte[]> redisTemplate = mock(RedisTemplate.class);
    @SuppressWarnings("unchecked")
    RedisScript<Long> geoUpsertScript = mock(RedisScript.class);
    @SuppressWarnings("unchecked")
    RedisScript<Long> geoEvictScript = mock(RedisScript.class);
    @SuppressWarnings("unchecked")
    Serializer<Feature, byte[]> serializer = mock(Serializer.class);
    @SuppressWarnings("unchecked")
    CachePublisher<CacheEventsNotification> publisher = mock(CachePublisher.class);

    RedisConnection connection = mock(RedisConnection.class);
    RedisScriptingCommands scriptingCommands = mock(RedisScriptingCommands.class);
    when(connection.scriptingCommands()).thenReturn(scriptingCommands);

    when(redisTemplate.execute(any(RedisCallback.class)))
        .thenAnswer(
            invocation -> {
              RedisCallback<?> callback = invocation.getArgument(0);
              return callback.doInRedis(connection);
            });

    when(redisTemplate.executePipelined(any(RedisCallback.class)))
        .thenAnswer(
            invocation -> {
              RedisCallback<?> callback = invocation.getArgument(0);
              callback.doInRedis(connection);
              return List.of();
            });

    when(serializer.serialize(any(Feature.class))).thenReturn("payload".getBytes());
    when(geoUpsertScript.getScriptAsString()).thenReturn("return 1");
    when(scriptingCommands.scriptLoad(any(byte[].class))).thenReturn("sha-1");

    RealTimeGeoFeatureHandler handler =
        new RealTimeGeoFeatureHandler(
            new GeoCacheWriter(
                redisTemplate,
                EventGeoCacheKeyLayout.STANDALONE,
                mock(RedisEventGeoSourceDirectory.class),
                geoUpsertScript,
                geoEvictScript,
                serializer,
                publisher));

    UUID sourceId = UUID.randomUUID();
    List<Feature> features = new ArrayList<>();
    for (int i = 0; i < 1001; i++) {
      features.add(feature("evt-" + i, sourceId));
    }

    handler.process(features);

    // 1001 items with chunk size 1000 -> 2 pipeline executions.
    verify(redisTemplate, times(2)).executePipelined(any(RedisCallback.class));
    verify(scriptingCommands, times(features.size()))
        .evalSha(eq("sha-1"), eq(ReturnType.INTEGER), eq(4), any(byte[][].class));
  }

  @Test
  void process_recoversFromNoScriptByReloadingAndRetrying() {
    @SuppressWarnings("unchecked")
    RedisTemplate<String, byte[]> redisTemplate = mock(RedisTemplate.class);
    @SuppressWarnings("unchecked")
    RedisScript<Long> geoUpsertScript = mock(RedisScript.class);
    @SuppressWarnings("unchecked")
    RedisScript<Long> geoEvictScript = mock(RedisScript.class);
    @SuppressWarnings("unchecked")
    Serializer<Feature, byte[]> serializer = mock(Serializer.class);
    @SuppressWarnings("unchecked")
    CachePublisher<CacheEventsNotification> publisher = mock(CachePublisher.class);

    RedisConnection connection = mock(RedisConnection.class);
    RedisScriptingCommands scriptingCommands = mock(RedisScriptingCommands.class);
    when(connection.scriptingCommands()).thenReturn(scriptingCommands);

    when(redisTemplate.execute(any(RedisCallback.class)))
        .thenAnswer(
            invocation -> {
              RedisCallback<?> callback = invocation.getArgument(0);
              return callback.doInRedis(connection);
            });

    // First executePipelined call throws NOSCRIPT (simulates Redis restart);
    // second call succeeds.
    AtomicBoolean failedOnce = new AtomicBoolean(false);
    when(redisTemplate.executePipelined(any(RedisCallback.class)))
        .thenAnswer(
            invocation -> {
              RedisCallback<?> callback = invocation.getArgument(0);
              callback.doInRedis(connection);
              if (failedOnce.compareAndSet(false, true)) {
                throw new RedisPipelineException(
                    new RedisNoScriptException("NOSCRIPT No matching script. Please use EVAL."));
              }
              return List.of();
            });

    when(serializer.serialize(any(Feature.class))).thenReturn("payload".getBytes());
    when(geoUpsertScript.getScriptAsString()).thenReturn("return 1");
    // First load returns "sha-1"; second load (after NOSCRIPT reset) returns "sha-2".
    when(scriptingCommands.scriptLoad(any(byte[].class))).thenReturn("sha-1", "sha-2");

    RealTimeGeoFeatureHandler handler =
        new RealTimeGeoFeatureHandler(
            new GeoCacheWriter(
                redisTemplate,
                EventGeoCacheKeyLayout.STANDALONE,
                mock(RedisEventGeoSourceDirectory.class),
                geoUpsertScript,
                geoEvictScript,
                serializer,
                publisher));

    UUID sourceId = UUID.randomUUID();
    List<Feature> features = List.of(feature("evt-1", sourceId));

    handler.process(features);

    // Script was loaded twice: initial load + reload after NOSCRIPT.
    verify(scriptingCommands, times(2)).scriptLoad(eq("return 1".getBytes(StandardCharsets.UTF_8)));
    // Pipeline was executed twice: failed attempt + successful retry.
    verify(redisTemplate, times(2)).executePipelined(any(RedisCallback.class));
    // Both attempts called evalSha (first with stale sha, second with fresh sha).
    verify(scriptingCommands, times(1))
        .evalSha(eq("sha-1"), eq(ReturnType.INTEGER), eq(4), any(byte[][].class));
    verify(scriptingCommands, times(1))
        .evalSha(eq("sha-2"), eq(ReturnType.INTEGER), eq(4), any(byte[][].class));
    verify(serializer).serialize(features.getFirst());
    verify(publisher).publish(any(CacheEventsNotification.class));
  }

  @Test
  void
      process_whenNoScriptRetryAlsoFails_propagatesAfterOneRetryWithoutReserializingOrPublishing() {
    @SuppressWarnings("unchecked")
    RedisTemplate<String, byte[]> redisTemplate = mock(RedisTemplate.class);
    @SuppressWarnings("unchecked")
    RedisScript<Long> geoUpsertScript = mock(RedisScript.class);
    @SuppressWarnings("unchecked")
    RedisScript<Long> geoEvictScript = mock(RedisScript.class);
    @SuppressWarnings("unchecked")
    Serializer<Feature, byte[]> serializer = mock(Serializer.class);
    @SuppressWarnings("unchecked")
    CachePublisher<CacheEventsNotification> publisher = mock(CachePublisher.class);
    RedisConnection connection = mock(RedisConnection.class);
    RedisScriptingCommands scriptingCommands = mock(RedisScriptingCommands.class);
    when(connection.scriptingCommands()).thenReturn(scriptingCommands);
    when(redisTemplate.execute(any(RedisCallback.class)))
        .thenAnswer(
            invocation -> {
              RedisCallback<?> callback = invocation.getArgument(0);
              return callback.doInRedis(connection);
            });
    when(redisTemplate.executePipelined(any(RedisCallback.class)))
        .thenAnswer(
            invocation -> {
              RedisCallback<?> callback = invocation.getArgument(0);
              callback.doInRedis(connection);
              throw new RedisPipelineException(
                  new RedisNoScriptException("NOSCRIPT No matching script. Please use EVAL."));
            });
    when(serializer.serialize(any(Feature.class))).thenReturn("payload".getBytes());
    when(geoUpsertScript.getScriptAsString()).thenReturn("return 1");
    when(scriptingCommands.scriptLoad(any(byte[].class))).thenReturn("sha-1", "sha-2");
    var handler =
        new RealTimeGeoFeatureHandler(
            new GeoCacheWriter(
                redisTemplate,
                EventGeoCacheKeyLayout.STANDALONE,
                mock(RedisEventGeoSourceDirectory.class),
                geoUpsertScript,
                geoEvictScript,
                serializer,
                publisher));
    var feature = feature("evt-1", UUID.randomUUID());

    assertThatThrownBy(() -> handler.process(List.of(feature)))
        .isInstanceOf(RedisPipelineException.class)
        .hasRootCauseInstanceOf(RedisNoScriptException.class);

    verify(redisTemplate, times(2)).executePipelined(any(RedisCallback.class));
    verify(scriptingCommands, times(2)).scriptLoad(any(byte[].class));
    verify(serializer).serialize(feature);
    verify(publisher, never()).publish(any());
  }

  @Test
  void process_whenLaterChunkFails_doesNotPublishAnyUpdate() {
    @SuppressWarnings("unchecked")
    RedisTemplate<String, byte[]> redisTemplate = mock(RedisTemplate.class);
    @SuppressWarnings("unchecked")
    RedisScript<Long> geoUpsertScript = mock(RedisScript.class);
    @SuppressWarnings("unchecked")
    RedisScript<Long> geoEvictScript = mock(RedisScript.class);
    @SuppressWarnings("unchecked")
    Serializer<Feature, byte[]> serializer = mock(Serializer.class);
    @SuppressWarnings("unchecked")
    CachePublisher<CacheEventsNotification> publisher = mock(CachePublisher.class);
    RedisConnection connection = mock(RedisConnection.class);
    RedisScriptingCommands scriptingCommands = mock(RedisScriptingCommands.class);
    when(connection.scriptingCommands()).thenReturn(scriptingCommands);
    when(redisTemplate.execute(any(RedisCallback.class)))
        .thenAnswer(
            invocation -> {
              RedisCallback<?> callback = invocation.getArgument(0);
              return callback.doInRedis(connection);
            });
    var pipelineAttempt = new AtomicInteger();
    when(redisTemplate.executePipelined(any(RedisCallback.class)))
        .thenAnswer(
            invocation -> {
              RedisCallback<?> callback = invocation.getArgument(0);
              callback.doInRedis(connection);
              if (pipelineAttempt.incrementAndGet() == 2) {
                throw new RedisPipelineException(new IllegalStateException("WRONGTYPE"));
              }
              return List.of();
            });
    when(serializer.serialize(any(Feature.class))).thenReturn("payload".getBytes());
    when(geoUpsertScript.getScriptAsString()).thenReturn("return 1");
    when(scriptingCommands.scriptLoad(any(byte[].class))).thenReturn("sha-1");
    var handler =
        new RealTimeGeoFeatureHandler(
            new GeoCacheWriter(
                redisTemplate,
                EventGeoCacheKeyLayout.STANDALONE,
                mock(RedisEventGeoSourceDirectory.class),
                geoUpsertScript,
                geoEvictScript,
                serializer,
                publisher));
    var sourceId = UUID.randomUUID();
    var features = new ArrayList<Feature>();
    for (int index = 0; index < 1_001; index++) {
      features.add(feature("evt-" + index, sourceId));
    }

    assertThatThrownBy(() -> handler.process(features))
        .isInstanceOf(RedisPipelineException.class)
        .hasRootCauseMessage("WRONGTYPE");

    verify(redisTemplate, times(2)).executePipelined(any(RedisCallback.class));
    verify(serializer, times(features.size())).serialize(any(Feature.class));
    verify(publisher, never()).publish(any());
  }

  private static Feature feature(String objectId, UUID sourceId) {
    OpenCopProperties props = new OpenCopProperties();
    props.setObjectId(objectId);
    props.setSource(sourceId);

    long now = System.currentTimeMillis();
    props.setTimestampz(new Timestampz(now, now, now + 60_000));

    Feature feature = new Feature();
    feature.setProperty("opencop", props);
    return feature;
  }
}
