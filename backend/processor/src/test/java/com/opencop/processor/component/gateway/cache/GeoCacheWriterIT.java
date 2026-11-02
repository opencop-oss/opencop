// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.component.gateway.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.opencop.core.sdk.component.redis.LocalRedisSupport;
import com.opencop.core.sdk.component.redis.RedisKeyspaceSnapshot;
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
import com.opencop.processor.technical.gateway.cache.GeoCacheWriter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.geojson.Feature;
import org.geojson.Point;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.scripting.support.ResourceScriptSource;

class GeoCacheWriterIT {

  private static final UUID SOURCE_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655449010");
  private static final UUID SOURCE_B = UUID.fromString("550e8400-e29b-41d4-a716-446655449011");
  private static final String OBJECT_ID = "it-object-id";

  private LettuceConnectionFactory connectionFactory;
  private RedisTemplate<String, byte[]> byteTemplate;
  private StringRedisTemplate stringTemplate;
  private Serializer<Feature, byte[]> serializer;
  private GeoCacheWriter writer;

  @BeforeEach
  void setUp() {
    connectionFactory =
        new LettuceConnectionFactory(LocalRedisSupport.host(), LocalRedisSupport.port());
    connectionFactory.afterPropertiesSet();

    byteTemplate = new RedisTemplate<>();
    byteTemplate.setKeySerializer(RedisSerializer.string());
    byteTemplate.setValueSerializer(RedisSerializer.byteArray());
    byteTemplate.setHashKeySerializer(RedisSerializer.string());
    byteTemplate.setHashValueSerializer(RedisSerializer.byteArray());
    byteTemplate.setConnectionFactory(connectionFactory);
    byteTemplate.afterPropertiesSet();

    stringTemplate = new StringRedisTemplate(connectionFactory);
    stringTemplate.afterPropertiesSet();

    byteTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();

    DefaultRedisScript<Long> geoUpsertScript = new DefaultRedisScript<>();
    geoUpsertScript.setResultType(Long.class);
    geoUpsertScript.setScriptSource(
        new ResourceScriptSource(new ClassPathResource("redis/geo_upsert.lua")));

    DefaultRedisScript<Long> geoEvictScript = new DefaultRedisScript<>();
    geoEvictScript.setResultType(Long.class);
    geoEvictScript.setScriptSource(
        new ResourceScriptSource(new ClassPathResource("redis/geo_evict.lua")));

    serializer = mock();
    CachePublisher<CacheEventsNotification> publisher = mock();

    writer =
        new GeoCacheWriter(
            byteTemplate,
            EventGeoCacheKeyLayout.STANDALONE,
            new RedisEventGeoSourceDirectory(
                EventGeoCacheKeyLayout.STANDALONE, stringTemplate, new SimpleMeterRegistry()),
            geoUpsertScript,
            geoEvictScript,
            serializer,
            publisher);
  }

  @AfterEach
  void tearDown() {
    if (byteTemplate != null && byteTemplate.getConnectionFactory() != null) {
      byteTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }
    if (connectionFactory != null) {
      connectionFactory.destroy();
    }
  }

  @Test
  void evictObject_whenEventExistsAndIsNotNewer_removesEventKeyAndSortedSetMembers() {
    String eventKey = EventGeoCacheKeys.event(SOURCE_ID, OBJECT_ID);
    String prefix = EventGeoCacheKeys.geoSourcePrefix(SOURCE_ID);
    String updatedAtKey = EventGeoCacheKeys.updatedAtFromPrefix(prefix);
    String staleAtKey = EventGeoCacheKeys.staleAtFromPrefix(prefix);
    long eventTime = 1_000_000L;

    byteTemplate.opsForValue().set(eventKey, "payload".getBytes(StandardCharsets.UTF_8));
    stringTemplate.opsForZSet().add(updatedAtKey, eventKey, eventTime);
    stringTemplate.opsForZSet().add(staleAtKey, eventKey, eventTime + 60_000);

    writer.evictObject(SOURCE_ID, OBJECT_ID, eventTime);

    assertThat(byteTemplate.hasKey(eventKey)).isFalse();
    assertThat(stringTemplate.opsForZSet().score(updatedAtKey, eventKey)).isNull();
    assertThat(stringTemplate.opsForZSet().score(staleAtKey, eventKey)).isNull();
  }

  @Test
  void evictObject_whenNewerReplacementExists_leavesEventKeyAndSortedSetMemberIntact() {
    String eventKey = EventGeoCacheKeys.event(SOURCE_ID, OBJECT_ID);
    String prefix = EventGeoCacheKeys.geoSourcePrefix(SOURCE_ID);
    String updatedAtKey = EventGeoCacheKeys.updatedAtFromPrefix(prefix);
    long newerTime = 2_000_000L;

    byteTemplate.opsForValue().set(eventKey, "payload".getBytes(StandardCharsets.UTF_8));
    stringTemplate.opsForZSet().add(updatedAtKey, eventKey, newerTime);

    writer.evictObject(SOURCE_ID, OBJECT_ID, newerTime - 1);

    assertThat(byteTemplate.hasKey(eventKey)).isTrue();
    assertThat(stringTemplate.opsForZSet().score(updatedAtKey, eventKey)).isEqualTo(newerTime);
  }

  @Test
  void evictObject_whenNotInRedis_completesWithoutError() {
    String eventKey = EventGeoCacheKeys.event(SOURCE_ID, OBJECT_ID);

    writer.evictObject(SOURCE_ID, OBJECT_ID, 999_999L);

    assertThat(byteTemplate.hasKey(eventKey)).isFalse();
  }

  @Test
  void standaloneLayout_preservesExactLegacyMutationSnapshotAndSelectiveClear() {
    long now = System.currentTimeMillis();
    long staleAt = now + 300_000;
    var sourceAInitial = feature(SOURCE_ID, OBJECT_ID, now - 2_000, staleAt);
    var sourceAOlder = feature(SOURCE_ID, OBJECT_ID, now - 3_000, staleAt);
    var sourceAReplacement = feature(SOURCE_ID, OBJECT_ID, now - 1_000, staleAt);
    var sourceB = feature(SOURCE_B, "source-b-object", now - 500, staleAt);
    when(serializer.serialize(sourceAInitial)).thenReturn(bytes("source-a-initial"));
    when(serializer.serialize(sourceAOlder)).thenReturn(bytes("source-a-older"));
    when(serializer.serialize(sourceAReplacement)).thenReturn(bytes("source-a-replacement"));
    when(serializer.serialize(sourceB)).thenReturn(bytes("source-b"));

    long beforeInitialWrite = System.currentTimeMillis();
    writer.write(List.of(sourceAInitial, sourceB), false);
    long afterInitialWrite = System.currentTimeMillis();

    var sourceAKeys = EventGeoCacheKeyLayout.STANDALONE.sourceKeys(SOURCE_ID);
    var sourceBKeys = EventGeoCacheKeyLayout.STANDALONE.sourceKeys(SOURCE_B);
    var sourceAEvent = sourceAKeys.eventKey(OBJECT_ID);
    var sourceBEvent = sourceBKeys.eventKey("source-b-object");
    var initial = RedisKeyspaceSnapshot.capture(byteTemplate);
    assertThat(initial.entries().keySet())
        .containsExactlyInAnyOrderElementsOf(
            Set.of(
                sourceAEvent,
                sourceAKeys.updatedAtKey(),
                sourceAKeys.staleAtKey(),
                sourceBEvent,
                sourceBKeys.updatedAtKey(),
                sourceBKeys.staleAtKey(),
                EventGeoCacheKeys.SOURCE_INDEX));
    assertThat(initial.entries().get(sourceAEvent).value())
        .containsExactly(bytes("source-a-initial"));
    assertThat(initial.entries().get(sourceBEvent).value()).containsExactly(bytes("source-b"));
    assertThat(initial.entries().get(sourceAEvent).ttlMillis()).isBetween(250_000L, 300_000L);
    assertThat(initial.entries().get(sourceBEvent).ttlMillis()).isBetween(250_000L, 300_000L);
    assertThat(initial.entries().get(sourceAKeys.updatedAtKey()).sortedSetMembers())
        .containsExactlyEntriesOf(java.util.Map.of(sourceAEvent, (double) (now - 2_000)));
    assertThat(initial.entries().get(sourceAKeys.staleAtKey()).sortedSetMembers())
        .containsExactlyEntriesOf(java.util.Map.of(sourceAEvent, (double) staleAt));
    assertThat(initial.entries().get(sourceBKeys.updatedAtKey()).sortedSetMembers())
        .containsExactlyEntriesOf(java.util.Map.of(sourceBEvent, (double) (now - 500)));
    assertThat(initial.entries().get(sourceBKeys.staleAtKey()).sortedSetMembers())
        .containsExactlyEntriesOf(java.util.Map.of(sourceBEvent, (double) staleAt));
    assertThat(initial.entries().get(EventGeoCacheKeys.SOURCE_INDEX).sortedSetMembers())
        .containsOnlyKeys(sourceAKeys.sourcePrefix(), sourceBKeys.sourcePrefix());
    assertThat(initial.entries().get(EventGeoCacheKeys.SOURCE_INDEX).sortedSetMembers().values())
        .allSatisfy(
            score ->
                assertThat(score)
                    .isBetween((double) beforeInitialWrite, (double) afterInitialWrite));
    assertThat(initial.entries().values())
        .filteredOn(
            entry -> entry.type() == org.springframework.data.redis.connection.DataType.ZSET)
        .allSatisfy(entry -> assertThat(entry.ttlMillis()).isEqualTo(-1L));
    assertThat(initial.entries().keySet())
        .doesNotContain(
            EventGeoCacheKeyLayout.CLUSTER.sourceKeys(SOURCE_ID).registryKey(),
            EventGeoCacheKeyLayout.CLUSTER.sourceKeys(SOURCE_B).registryKey());

    writer.write(List.of(sourceAOlder), false);
    assertThat(byteTemplate.opsForValue().get(sourceAEvent))
        .containsExactly(bytes("source-a-initial"));
    assertThat(stringTemplate.opsForZSet().score(sourceAKeys.updatedAtKey(), sourceAEvent))
        .isEqualTo((double) (now - 2_000));

    writer.evictObject(SOURCE_ID, OBJECT_ID, now - 2_001);
    assertThat(byteTemplate.hasKey(sourceAEvent)).isTrue();
    writer.evictObject(SOURCE_ID, OBJECT_ID, now - 2_000);
    assertThat(byteTemplate.hasKey(sourceAEvent)).isFalse();

    writer.write(List.of(sourceAReplacement), false);
    writer.clearSource(SOURCE_ID);

    var afterClear = RedisKeyspaceSnapshot.capture(byteTemplate);
    assertThat(afterClear.entries().keySet())
        .containsExactlyInAnyOrder(
            sourceBEvent,
            sourceBKeys.updatedAtKey(),
            sourceBKeys.staleAtKey(),
            EventGeoCacheKeys.SOURCE_INDEX);
    assertThat(afterClear.entries().get(sourceBEvent).value()).containsExactly(bytes("source-b"));
    assertThat(afterClear.entries().get(EventGeoCacheKeys.SOURCE_INDEX).sortedSetMembers())
        .containsOnlyKeys(sourceBKeys.sourcePrefix());
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
}
