// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.sdk.model.cache.CacheEventsNotification;
import com.opencop.core.sdk.ports.gateway.cache.CachePublisher;
import com.opencop.core.sdk.technical.gateway.cache.EventGeoCacheKeyLayout;
import com.opencop.core.sdk.technical.gateway.cache.EventGeoCacheKeys;
import com.opencop.core.sdk.technical.gateway.cache.redis.RedisEventGeoSourceDirectory;
import com.opencop.core.sdk.technical.model.opencop.OpenCopProperties;
import com.opencop.core.sdk.technical.model.opencop.Timestampz;
import com.opencop.core.sdk.technical.model.opencop.elements.ElementTypes;
import com.opencop.core.sdk.technical.model.opencop.elements.PointElement;
import com.opencop.core.sdk.technical.model.opencop.parser.OpenCopPropertiesParser;
import com.opencop.core.sdk.technical.model.serializer.Serializer;
import io.lettuce.core.RedisNoScriptException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.geojson.Feature;
import org.geojson.Point;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisPipelineException;
import org.springframework.data.redis.connection.RedisScriptingCommands;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.RedisScript;

class GeoCacheWriterTest {

  private static final UUID SOURCE_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655449001");
  private static final String OBJECT_ID = "test-object-id";
  private static final long EVENT_TIME_MS = 1_000_000L;

  @Test
  @SuppressWarnings("unchecked")
  void evictObject_callsEvictScriptWithCorrectKeysAndThreshold() {
    RedisTemplate<String, byte[]> redisTemplate = mock();
    RedisScript<Long> geoUpsertScript = mock();
    RedisScript<Long> geoEvictScript = mock();
    Serializer<Feature, byte[]> serializer = mock();
    CachePublisher<CacheEventsNotification> publisher = mock();
    String eventKey = EventGeoCacheKeys.event(SOURCE_ID, OBJECT_ID);
    String prefix = EventGeoCacheKeys.geoSourcePrefix(SOURCE_ID);
    String updatedAtKey = EventGeoCacheKeys.updatedAtFromPrefix(prefix);
    String staleAtKey = EventGeoCacheKeys.staleAtFromPrefix(prefix);
    when(redisTemplate.execute(eq(geoEvictScript), anyList(), any(byte[].class))).thenReturn(1L);
    var writer =
        new GeoCacheWriter(
            redisTemplate,
            EventGeoCacheKeyLayout.STANDALONE,
            mock(RedisEventGeoSourceDirectory.class),
            geoUpsertScript,
            geoEvictScript,
            serializer,
            publisher);

    writer.evictObject(SOURCE_ID, OBJECT_ID, EVENT_TIME_MS);

    ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);
    ArgumentCaptor<byte[]> thresholdCaptor = ArgumentCaptor.forClass(byte[].class);
    verify(redisTemplate)
        .execute(eq(geoEvictScript), keysCaptor.capture(), thresholdCaptor.capture());
    assertThat(keysCaptor.getValue()).containsExactly(updatedAtKey, staleAtKey, eventKey);
    assertArrayEquals(
        Long.toString(EVENT_TIME_MS).getBytes(StandardCharsets.UTF_8), thresholdCaptor.getValue());
  }

  @Test
  void evictObject_whenReplacementArrived_doesNotBulkDelete() {
    RedisTemplate<String, byte[]> redisTemplate = mock();
    RedisScript<Long> geoUpsertScript = mock();
    RedisScript<Long> geoEvictScript = mock();
    Serializer<Feature, byte[]> serializer = mock();
    CachePublisher<CacheEventsNotification> publisher = mock();
    when(redisTemplate.execute(eq(geoEvictScript), anyList(), any(byte[].class))).thenReturn(-1L);
    var writer =
        new GeoCacheWriter(
            redisTemplate,
            EventGeoCacheKeyLayout.STANDALONE,
            mock(RedisEventGeoSourceDirectory.class),
            geoUpsertScript,
            geoEvictScript,
            serializer,
            publisher);

    writer.evictObject(SOURCE_ID, OBJECT_ID, EVENT_TIME_MS);

    verify(redisTemplate, never()).delete(anyCollection());
  }

  @Test
  void evictObject_whenNotInRedis_doesNotBulkDelete() {
    RedisTemplate<String, byte[]> redisTemplate = mock();
    RedisScript<Long> geoUpsertScript = mock();
    RedisScript<Long> geoEvictScript = mock();
    Serializer<Feature, byte[]> serializer = mock();
    CachePublisher<CacheEventsNotification> publisher = mock();
    when(redisTemplate.execute(eq(geoEvictScript), anyList(), any(byte[].class))).thenReturn(0L);
    var writer =
        new GeoCacheWriter(
            redisTemplate,
            EventGeoCacheKeyLayout.STANDALONE,
            mock(RedisEventGeoSourceDirectory.class),
            geoUpsertScript,
            geoEvictScript,
            serializer,
            publisher);

    writer.evictObject(SOURCE_ID, OBJECT_ID, EVENT_TIME_MS);

    verify(redisTemplate, never()).delete(anyCollection());
  }

  @Test
  void evictObject_doesNotBulkDelete() {
    RedisTemplate<String, byte[]> redisTemplate = mock();
    RedisScript<Long> geoUpsertScript = mock();
    RedisScript<Long> geoEvictScript = mock();
    Serializer<Feature, byte[]> serializer = mock();
    CachePublisher<CacheEventsNotification> publisher = mock();
    when(redisTemplate.execute(eq(geoEvictScript), anyList(), any(byte[].class))).thenReturn(1L);
    var writer =
        new GeoCacheWriter(
            redisTemplate,
            EventGeoCacheKeyLayout.STANDALONE,
            mock(RedisEventGeoSourceDirectory.class),
            geoUpsertScript,
            geoEvictScript,
            serializer,
            publisher);

    writer.evictObject(SOURCE_ID, OBJECT_ID, EVENT_TIME_MS);

    verify(redisTemplate, never()).delete(anyCollection());
  }

  @Test
  @SuppressWarnings("unchecked")
  void clearSource_removesSourceIndexUsingByteArrayMember() {
    RedisTemplate<String, byte[]> redisTemplate = mock();
    ZSetOperations<String, byte[]> zSetOperations = mock();
    RedisScript<Long> geoUpsertScript = mock();
    RedisScript<Long> geoEvictScript = mock();
    Serializer<Feature, byte[]> serializer = mock();
    CachePublisher<CacheEventsNotification> publisher = mock();
    when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
    when(redisTemplate.execute(any(RedisCallback.class))).thenReturn(List.of());
    String prefix = EventGeoCacheKeys.geoSourcePrefix(SOURCE_ID);
    String updatedAtKey = EventGeoCacheKeys.updatedAtFromPrefix(prefix);
    String staleAtKey = EventGeoCacheKeys.staleAtFromPrefix(prefix);
    when(zSetOperations.range(updatedAtKey, 0, -1)).thenReturn(Set.of());
    when(zSetOperations.range(staleAtKey, 0, -1)).thenReturn(Set.of());
    var sourceDirectory = mock(RedisEventGeoSourceDirectory.class);
    var writer =
        new GeoCacheWriter(
            redisTemplate,
            EventGeoCacheKeyLayout.STANDALONE,
            sourceDirectory,
            geoUpsertScript,
            geoEvictScript,
            serializer,
            publisher);

    writer.clearSource(SOURCE_ID);

    verify(redisTemplate).delete(List.of(updatedAtKey, staleAtKey));
    verify(sourceDirectory)
        .unregisterIfEmpty(EventGeoCacheKeyLayout.STANDALONE.sourceKeys(SOURCE_ID));
  }

  @Test
  @SuppressWarnings("unchecked")
  void clearSource_filtersIndexedMembersToSourceScopedEventKeys() {
    RedisTemplate<String, byte[]> redisTemplate = mock();
    ZSetOperations<String, byte[]> zSetOperations = mock();
    RedisScript<Long> geoUpsertScript = mock();
    RedisScript<Long> geoEvictScript = mock();
    Serializer<Feature, byte[]> serializer = mock();
    CachePublisher<CacheEventsNotification> publisher = mock();
    when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
    when(redisTemplate.execute(any(RedisCallback.class))).thenReturn(List.of());
    String prefix = EventGeoCacheKeys.geoSourcePrefix(SOURCE_ID);
    String updatedAtKey = EventGeoCacheKeys.updatedAtFromPrefix(prefix);
    String staleAtKey = EventGeoCacheKeys.staleAtFromPrefix(prefix);
    String sourceEventKey = EventGeoCacheKeys.event(SOURCE_ID, "source-object");
    String otherSourceEventKey =
        EventGeoCacheKeys.event(UUID.fromString("550e8400-e29b-41d4-a716-446655449002"), "other");
    when(zSetOperations.range(updatedAtKey, 0, -1))
        .thenReturn(
            Set.of(
                sourceEventKey.getBytes(StandardCharsets.UTF_8),
                otherSourceEventKey.getBytes(StandardCharsets.UTF_8)));
    when(zSetOperations.range(staleAtKey, 0, -1)).thenReturn(Set.of());
    var writer =
        new GeoCacheWriter(
            redisTemplate,
            EventGeoCacheKeyLayout.STANDALONE,
            mock(RedisEventGeoSourceDirectory.class),
            geoUpsertScript,
            geoEvictScript,
            serializer,
            publisher);

    writer.clearSource(SOURCE_ID);

    ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);
    verify(redisTemplate).delete(keysCaptor.capture());
    assertThat(keysCaptor.getValue())
        .containsExactly(sourceEventKey, updatedAtKey, staleAtKey)
        .doesNotContain(otherSourceEventKey);
  }

  @Test
  @SuppressWarnings("unchecked")
  void clearSource_whenClustered_deletesSelectedLegacyKeysBeforeConditionalUnregister() {
    RedisTemplate<String, byte[]> redisTemplate = mock();
    ZSetOperations<String, byte[]> zSetOperations = mock();
    RedisScript<Long> geoUpsertScript = mock();
    RedisScript<Long> geoEvictScript = mock();
    Serializer<Feature, byte[]> serializer = mock();
    CachePublisher<CacheEventsNotification> publisher = mock();
    var sourceDirectory = mock(RedisEventGeoSourceDirectory.class);
    var sourceKeys = EventGeoCacheKeyLayout.CLUSTER.sourceKeys(SOURCE_ID);
    var scannedEventKey = sourceKeys.eventKey("scanned");
    var updatedAtEventKey = sourceKeys.eventKey("updated-indexed");
    var staleAtEventKey = sourceKeys.eventKey("stale-indexed");
    var foreignMember =
        EventGeoCacheKeyLayout.CLUSTER
            .sourceKeys(UUID.fromString("550e8400-e29b-41d4-a716-446655449002"))
            .eventKey("foreign")
            .getBytes(StandardCharsets.UTF_8);
    var updatedAtMembers = new LinkedHashSet<byte[]>();
    updatedAtMembers.add(updatedAtEventKey.getBytes(StandardCharsets.UTF_8));
    updatedAtMembers.add(foreignMember);
    when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
    when(zSetOperations.range(sourceKeys.updatedAtKey(), 0, -1)).thenReturn(updatedAtMembers);
    when(zSetOperations.range(sourceKeys.staleAtKey(), 0, -1))
        .thenReturn(Set.of(staleAtEventKey.getBytes(StandardCharsets.UTF_8)));
    when(redisTemplate.execute(any(RedisCallback.class))).thenReturn(List.of(scannedEventKey));
    var writer =
        new GeoCacheWriter(
            redisTemplate,
            EventGeoCacheKeyLayout.CLUSTER,
            sourceDirectory,
            geoUpsertScript,
            geoEvictScript,
            serializer,
            publisher);

    writer.clearSource(SOURCE_ID);

    var order = inOrder(redisTemplate, sourceDirectory);
    ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);
    order.verify(redisTemplate).delete(keysCaptor.capture());
    order.verify(sourceDirectory).unregisterIfEmpty(sourceKeys);
    assertThat(keysCaptor.getValue())
        .containsExactly(
            scannedEventKey,
            updatedAtEventKey,
            staleAtEventKey,
            sourceKeys.updatedAtKey(),
            sourceKeys.staleAtKey())
        .doesNotContain(new String(foreignMember, StandardCharsets.UTF_8));
  }

  @Test
  @SuppressWarnings("unchecked")
  void write_preparesWholeChunkBeforeMutationAndUsesExactSelectedScriptContract() {
    RedisTemplate<String, byte[]> redisTemplate = mock();
    RedisScript<Long> geoUpsertScript = mock();
    RedisScript<Long> geoEvictScript = mock();
    Serializer<Feature, byte[]> serializer = mock();
    CachePublisher<CacheEventsNotification> publisher = mock();
    RedisConnection connection = mock();
    RedisScriptingCommands scriptingCommands = mock();
    when(connection.scriptingCommands()).thenReturn(scriptingCommands);
    when(redisTemplate.execute(any(RedisCallback.class)))
        .thenAnswer(
            invocation -> invocation.<RedisCallback<?>>getArgument(0).doInRedis(connection));
    when(redisTemplate.executePipelined(any(RedisCallback.class)))
        .thenAnswer(
            invocation -> {
              invocation.<RedisCallback<?>>getArgument(0).doInRedis(connection);
              return List.of();
            });
    when(geoUpsertScript.getScriptAsString()).thenReturn("return 1");
    when(scriptingCommands.scriptLoad(any(byte[].class))).thenReturn("sha-1");
    long now = System.currentTimeMillis();
    var first = feature(SOURCE_ID, "prepared-1", now - 2_000, now + 60_000);
    var second = feature(SOURCE_ID, "prepared-2", now - 1_000, now + 70_000);
    when(serializer.serialize(first)).thenReturn(bytes("payload-1"));
    when(serializer.serialize(second)).thenReturn(bytes("payload-2"));
    var directory = mock(RedisEventGeoSourceDirectory.class);
    var writer =
        new GeoCacheWriter(
            redisTemplate,
            EventGeoCacheKeyLayout.CLUSTER,
            directory,
            geoUpsertScript,
            geoEvictScript,
            serializer,
            publisher);

    writer.write(List.of(first, second), false);

    var order = inOrder(serializer, directory, redisTemplate);
    order.verify(serializer).serialize(first);
    order.verify(serializer).serialize(second);
    order.verify(directory).registerPreparedSources(any());
    order.verify(redisTemplate).execute(any(RedisCallback.class));
    order.verify(redisTemplate).executePipelined(any(RedisCallback.class));
    ArgumentCaptor<byte[][]> keysAndArgs = ArgumentCaptor.forClass(byte[][].class);
    verify(scriptingCommands, times(2))
        .evalSha(eq("sha-1"), eq(ReturnType.INTEGER), eq(4), keysAndArgs.capture());
    assertScriptInvocation(keysAndArgs.getAllValues().get(0), first, bytes("payload-1"));
    assertScriptInvocation(keysAndArgs.getAllValues().get(1), second, bytes("payload-2"));
    verify(serializer).serialize(first);
    verify(serializer).serialize(second);
    verify(publisher, never()).publish(any());
  }

  @Test
  @SuppressWarnings("unchecked")
  void write_whenInputIsNullEmptyOrMalformed_doesNotMutateOrPublish() {
    RedisTemplate<String, byte[]> redisTemplate = mock();
    RedisScript<Long> geoUpsertScript = mock();
    RedisScript<Long> geoEvictScript = mock();
    Serializer<Feature, byte[]> serializer = mock();
    CachePublisher<CacheEventsNotification> publisher = mock();
    var writer =
        new GeoCacheWriter(
            redisTemplate,
            EventGeoCacheKeyLayout.CLUSTER,
            mock(RedisEventGeoSourceDirectory.class),
            geoUpsertScript,
            geoEvictScript,
            serializer,
            publisher);

    writer.write(null, true);
    writer.write(List.of(), true);
    writer.write(List.of(new Feature()), true);

    verify(redisTemplate, never()).execute(any(RedisCallback.class));
    verify(redisTemplate, never()).executePipelined(any(RedisCallback.class));
    verify(serializer, never()).serialize(any());
    verify(publisher, never()).publish(any());
  }

  @Test
  @SuppressWarnings("unchecked")
  void write_whenPipelineFailsForNonNoScriptReason_propagatesWithoutRetryOrPublication() {
    RedisTemplate<String, byte[]> redisTemplate = mock();
    RedisScript<Long> geoUpsertScript = mock();
    RedisScript<Long> geoEvictScript = mock();
    Serializer<Feature, byte[]> serializer = mock();
    CachePublisher<CacheEventsNotification> publisher = mock();
    RedisConnection connection = mock();
    RedisScriptingCommands scriptingCommands = mock();
    when(connection.scriptingCommands()).thenReturn(scriptingCommands);
    when(redisTemplate.execute(any(RedisCallback.class)))
        .thenAnswer(
            invocation -> invocation.<RedisCallback<?>>getArgument(0).doInRedis(connection));
    when(redisTemplate.executePipelined(any(RedisCallback.class)))
        .thenAnswer(
            invocation -> {
              invocation.<RedisCallback<?>>getArgument(0).doInRedis(connection);
              throw new RedisPipelineException(new IllegalStateException("WRONGTYPE"));
            });
    when(geoUpsertScript.getScriptAsString()).thenReturn("return 1");
    when(scriptingCommands.scriptLoad(any(byte[].class))).thenReturn("sha-1");
    var feature = feature(SOURCE_ID, "wrong-type", 1_000L, 61_000L);
    when(serializer.serialize(feature)).thenReturn(bytes("payload"));
    var writer =
        new GeoCacheWriter(
            redisTemplate,
            EventGeoCacheKeyLayout.CLUSTER,
            mock(RedisEventGeoSourceDirectory.class),
            geoUpsertScript,
            geoEvictScript,
            serializer,
            publisher);

    assertThatThrownBy(() -> writer.write(List.of(feature), true))
        .isInstanceOf(RedisPipelineException.class)
        .hasRootCauseMessage("WRONGTYPE");

    verify(redisTemplate).executePipelined(any(RedisCallback.class));
    verify(scriptingCommands).scriptLoad(any(byte[].class));
    verify(serializer).serialize(feature);
    verify(publisher, never()).publish(any());
  }

  @Test
  void write_whenNoScriptPrecedesAnotherPipelineFailure_doesNotRetryOrPublish() {
    var noScript = new RedisNoScriptException("NOSCRIPT missing");
    var wrongType = new IllegalStateException("WRONGTYPE");
    var failure = new RedisPipelineException(noScript, List.<Object>of(noScript, wrongType));

    assertMixedPipelineFailureIsNotRetried(failure);
  }

  @Test
  void write_whenAnotherPipelineFailurePrecedesNoScript_doesNotRetryOrPublish() {
    var wrongType = new IllegalStateException("WRONGTYPE");
    var noScript = new RedisNoScriptException("NOSCRIPT missing");
    var failure = new RedisPipelineException(wrongType, List.<Object>of(wrongType, noScript));

    assertMixedPipelineFailureIsNotRetried(failure);
  }

  @Test
  @SuppressWarnings("unchecked")
  void write_whenOneParseableFeatureCannotSerialize_publishesOriginalNotificationAfterSuccess() {
    RedisTemplate<String, byte[]> redisTemplate = mock();
    RedisScript<Long> geoUpsertScript = mock();
    RedisScript<Long> geoEvictScript = mock();
    Serializer<Feature, byte[]> serializer = mock();
    CachePublisher<CacheEventsNotification> publisher = mock();
    RedisConnection connection = mock();
    RedisScriptingCommands scriptingCommands = mock();
    when(connection.scriptingCommands()).thenReturn(scriptingCommands);
    when(redisTemplate.execute(any(RedisCallback.class)))
        .thenAnswer(
            invocation -> invocation.<RedisCallback<?>>getArgument(0).doInRedis(connection));
    when(redisTemplate.executePipelined(any(RedisCallback.class)))
        .thenAnswer(
            invocation -> {
              invocation.<RedisCallback<?>>getArgument(0).doInRedis(connection);
              return List.of();
            });
    when(geoUpsertScript.getScriptAsString()).thenReturn("return 1");
    when(scriptingCommands.scriptLoad(any(byte[].class))).thenReturn("sha-1");
    var accepted = feature(SOURCE_ID, "accepted", 1_000L, 61_000L);
    var rejected = feature(SOURCE_ID, "rejected", 2_000L, 62_000L);
    when(serializer.serialize(accepted)).thenReturn(bytes("accepted"));
    when(serializer.serialize(rejected)).thenThrow(new IllegalArgumentException("bad payload"));
    var writer =
        new GeoCacheWriter(
            redisTemplate,
            EventGeoCacheKeyLayout.CLUSTER,
            mock(RedisEventGeoSourceDirectory.class),
            geoUpsertScript,
            geoEvictScript,
            serializer,
            publisher);

    writer.write(List.of(accepted, rejected), true);

    verify(scriptingCommands)
        .evalSha(eq("sha-1"), eq(ReturnType.INTEGER), eq(4), any(byte[][].class));
    ArgumentCaptor<CacheEventsNotification> notification =
        ArgumentCaptor.forClass(CacheEventsNotification.class);
    verify(publisher).publish(notification.capture());
    assertThat(notification.getValue())
        .isEqualTo(new CacheEventsNotification(SOURCE_ID, List.of("accepted", "rejected")));
  }

  private static void assertScriptInvocation(
      byte[][] keysAndArgs, Feature feature, byte[] expectedPayload) {
    var properties = OpenCopPropertiesParser.readFromFeature(feature);
    var keys = EventGeoCacheKeyLayout.CLUSTER.sourceKeys(properties.getSource());
    assertThat(keysAndArgs.length).isEqualTo(12);
    assertThat(asString(keysAndArgs[0])).isEqualTo(keys.eventKey(properties.getObjectId()));
    assertThat(asString(keysAndArgs[1])).isEqualTo(keys.updatedAtKey());
    assertThat(asString(keysAndArgs[2])).isEqualTo(keys.staleAtKey());
    assertThat(asString(keysAndArgs[3])).isEqualTo(keys.registryKey());
    assertThat(keysAndArgs[4]).containsExactly(expectedPayload);
    long nowMs = Long.parseLong(asString(keysAndArgs[8]));
    assertThat(asString(keysAndArgs[5]))
        .isEqualTo(Long.toString(properties.getTimestampz().getStale() - nowMs));
    assertThat(asString(keysAndArgs[6]))
        .isEqualTo(Long.toString(properties.getTimestampz().getTime()));
    assertThat(asString(keysAndArgs[7]))
        .isEqualTo(Long.toString(properties.getTimestampz().getStale()));
    assertThat(asString(keysAndArgs[9])).isEqualTo(keys.sourcePrefix());
    assertThat(asString(keysAndArgs[10])).isEqualTo("60000");
    assertThat(asString(keysAndArgs[11])).isEqualTo("PER_SOURCE_MARKER");
  }

  @SuppressWarnings("unchecked")
  private void assertMixedPipelineFailureIsNotRetried(RedisPipelineException pipelineFailure) {
    RedisTemplate<String, byte[]> redisTemplate = mock();
    RedisScript<Long> geoUpsertScript = mock();
    RedisScript<Long> geoEvictScript = mock();
    Serializer<Feature, byte[]> serializer = mock();
    CachePublisher<CacheEventsNotification> publisher = mock();
    RedisConnection connection = mock();
    RedisScriptingCommands scriptingCommands = mock();
    when(connection.scriptingCommands()).thenReturn(scriptingCommands);
    when(redisTemplate.execute(any(RedisCallback.class)))
        .thenAnswer(
            invocation -> invocation.<RedisCallback<?>>getArgument(0).doInRedis(connection));
    when(redisTemplate.executePipelined(any(RedisCallback.class)))
        .thenAnswer(
            invocation -> {
              invocation.<RedisCallback<?>>getArgument(0).doInRedis(connection);
              throw pipelineFailure;
            });
    when(geoUpsertScript.getScriptAsString()).thenReturn("return 1");
    when(scriptingCommands.scriptLoad(any(byte[].class))).thenReturn("sha-1", "sha-2");
    var feature = feature(SOURCE_ID, "mixed-pipeline", 1_000L, 61_000L);
    when(serializer.serialize(feature)).thenReturn(bytes("payload"));
    var writer =
        new GeoCacheWriter(
            redisTemplate,
            EventGeoCacheKeyLayout.CLUSTER,
            mock(RedisEventGeoSourceDirectory.class),
            geoUpsertScript,
            geoEvictScript,
            serializer,
            publisher);

    assertThatThrownBy(() -> writer.write(List.of(feature), true)).isSameAs(pipelineFailure);

    verify(redisTemplate).executePipelined(any(RedisCallback.class));
    verify(scriptingCommands).scriptLoad(any(byte[].class));
    verify(scriptingCommands)
        .evalSha(eq("sha-1"), eq(ReturnType.INTEGER), eq(4), any(byte[][].class));
    verify(serializer).serialize(feature);
    verify(publisher, never()).publish(any());
  }

  private static Feature feature(UUID sourceId, String objectId, long eventTime, long staleAt) {
    var properties =
        new OpenCopProperties.Builder()
            .id(UUID.nameUUIDFromBytes(objectId.getBytes(StandardCharsets.UTF_8)))
            .objectId(objectId)
            .kind("track")
            .source(sourceId)
            .displayName(objectId)
            .timestampz(new Timestampz(eventTime, eventTime - 100, staleAt))
            .element(new PointElement.Builder().type(ElementTypes.POINT).build())
            .build();
    return OpenCopPropertiesParser.writeToFeature(properties, new Point(-75.1652, 39.9526));
  }

  private static byte[] bytes(String value) {
    return value.getBytes(StandardCharsets.UTF_8);
  }

  private static String asString(byte[] value) {
    return new String(value, StandardCharsets.UTF_8);
  }
}
