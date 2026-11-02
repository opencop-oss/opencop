// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.component.gateway.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.sdk.component.redis.LocalRedisSupport;
import com.opencop.core.sdk.component.redis.RedisKeyspaceSnapshot;
import com.opencop.core.sdk.model.cache.CacheEventsNotification;
import com.opencop.core.sdk.model.cache.GeoCacheHydrationJob;
import com.opencop.core.sdk.model.job.GeoCacheHydrationJobSubmission;
import com.opencop.core.sdk.model.job.JobIssuer;
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
import com.opencop.processor.business.usecase.event.GeoCacheHydrationHandler;
import com.opencop.processor.ports.technical.gateway.db.GeoCacheHydrationGateway.HydrationSnapshotStats;
import com.opencop.processor.technical.gateway.cache.GeoCacheWriter;
import com.opencop.processor.technical.gateway.db.GeoCacheHydrationRepository;
import com.opencop.processor.technical.gateway.lock.geo.RedisGeoCacheHydrationLock;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.geojson.Feature;
import org.geojson.Point;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.DataType;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.scripting.support.ResourceScriptSource;

class GeoCacheHydrationWriterIT {

  private static final UUID SOURCE_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655449120");
  private static final String OBJECT_ID = "hydrated-object";
  private static final byte[] PAYLOAD = "hydrated-payload".getBytes(StandardCharsets.UTF_8);

  private LettuceConnectionFactory connectionFactory;
  private RedisTemplate<String, byte[]> byteTemplate;
  private StringRedisTemplate stringTemplate;

  @BeforeEach
  void setUp() {
    connectionFactory =
        new LettuceConnectionFactory(LocalRedisSupport.host(), LocalRedisSupport.port());
    connectionFactory.afterPropertiesSet();
    byteTemplate = byteTemplate(connectionFactory);
    stringTemplate = new StringRedisTemplate(connectionFactory);
    stringTemplate.afterPropertiesSet();
    connectionFactory.getConnection().serverCommands().flushAll();
  }

  @AfterEach
  void tearDown() {
    if (connectionFactory != null) {
      connectionFactory.getConnection().serverCommands().flushAll();
      connectionFactory.destroy();
    }
  }

  @Test
  void hydrationHandler_feedsRealWriterAndProducesExactStandaloneLayout() {
    long now = System.currentTimeMillis();
    long eventTime = now - 1_000;
    long staleAt = now + 300_000;
    var feature = feature(eventTime, staleAt);
    var job =
        GeoCacheHydrationJobSubmission.forSource(
            JobIssuer.system("geo-hydration-writer-it"),
            SOURCE_ID,
            GeoCacheHydrationJob.Trigger.MANUAL);
    var sourceKeys = EventGeoCacheKeyLayout.STANDALONE.sourceKeys(SOURCE_ID);
    var repository = mock(GeoCacheHydrationRepository.class);
    when(repository.hydrateSnapshot(eq(SOURCE_ID), eq(1000), any()))
        .thenAnswer(
            invocation -> {
              assertThat(stringTemplate.opsForValue().get(sourceKeys.hydrationLockKey()))
                  .isEqualTo(job.jobId().toString());
              assertThat(
                      stringTemplate.getExpire(
                          sourceKeys.hydrationLockKey(), TimeUnit.MILLISECONDS))
                  .isBetween(1L, TimeUnit.MINUTES.toMillis(30));
              @SuppressWarnings("unchecked")
              Consumer<List<Feature>> consumer = invocation.getArgument(2, Consumer.class);
              consumer.accept(List.of(feature));
              return new HydrationSnapshotStats(1, 1, OBJECT_ID);
            });

    @SuppressWarnings("unchecked")
    Serializer<Feature, byte[]> serializer = mock(Serializer.class);
    when(serializer.serialize(feature)).thenReturn(PAYLOAD);
    @SuppressWarnings("unchecked")
    CachePublisher<CacheEventsNotification> publisher = mock(CachePublisher.class);
    var writer = writer(serializer, publisher);
    var handler =
        new GeoCacheHydrationHandler(
            repository,
            writer,
            new RedisGeoCacheHydrationLock(stringTemplate, EventGeoCacheKeyLayout.STANDALONE));

    long beforeHydrate = System.currentTimeMillis();
    handler.hydrate(job);
    long afterHydrate = System.currentTimeMillis();

    var eventKey = sourceKeys.eventKey(OBJECT_ID);
    var snapshot = RedisKeyspaceSnapshot.capture(byteTemplate);
    assertThat(snapshot.entries().keySet())
        .containsExactlyInAnyOrderElementsOf(
            Set.of(
                eventKey,
                sourceKeys.updatedAtKey(),
                sourceKeys.staleAtKey(),
                EventGeoCacheKeys.SOURCE_INDEX));
    assertThat(snapshot.entries().get(eventKey).value()).containsExactly(PAYLOAD);
    assertThat(snapshot.entries().get(eventKey).ttlMillis()).isBetween(250_000L, 300_000L);
    assertThat(snapshot.entries().get(sourceKeys.updatedAtKey()).sortedSetMembers())
        .containsExactlyEntriesOf(java.util.Map.of(eventKey, (double) eventTime));
    assertThat(snapshot.entries().get(sourceKeys.staleAtKey()).sortedSetMembers())
        .containsExactlyEntriesOf(java.util.Map.of(eventKey, (double) staleAt));
    assertThat(snapshot.entries().get(EventGeoCacheKeys.SOURCE_INDEX).sortedSetMembers())
        .containsOnlyKeys(sourceKeys.sourcePrefix());
    assertThat(
            snapshot
                .entries()
                .get(EventGeoCacheKeys.SOURCE_INDEX)
                .sortedSetMembers()
                .get(sourceKeys.sourcePrefix()))
        .isBetween((double) beforeHydrate, (double) afterHydrate);
    assertThat(snapshot.entries().values())
        .filteredOn(entry -> entry.type() == DataType.ZSET)
        .allSatisfy(entry -> assertThat(entry.ttlMillis()).isEqualTo(-1L));
    assertThat(snapshot.entries()).doesNotContainKey(sourceKeys.hydrationLockKey());
    verify(publisher).publish(new CacheEventsNotification(SOURCE_ID, List.of(OBJECT_ID)));
  }

  private GeoCacheWriter writer(
      Serializer<Feature, byte[]> serializer, CachePublisher<CacheEventsNotification> publisher) {
    return new GeoCacheWriter(
        byteTemplate,
        EventGeoCacheKeyLayout.STANDALONE,
        new RedisEventGeoSourceDirectory(
            EventGeoCacheKeyLayout.STANDALONE, stringTemplate, new SimpleMeterRegistry()),
        script("redis/geo_upsert.lua"),
        script("redis/geo_evict.lua"),
        serializer,
        publisher);
  }

  private static RedisTemplate<String, byte[]> byteTemplate(
      LettuceConnectionFactory connectionFactory) {
    var template = new RedisTemplate<String, byte[]>();
    template.setKeySerializer(RedisSerializer.string());
    template.setValueSerializer(RedisSerializer.byteArray());
    template.setHashKeySerializer(RedisSerializer.string());
    template.setHashValueSerializer(RedisSerializer.byteArray());
    template.setConnectionFactory(connectionFactory);
    template.afterPropertiesSet();
    return template;
  }

  private static DefaultRedisScript<Long> script(String path) {
    var script = new DefaultRedisScript<Long>();
    script.setResultType(Long.class);
    script.setScriptSource(new ResourceScriptSource(new ClassPathResource(path)));
    return script;
  }

  private static Feature feature(long eventTime, long staleAt) {
    var properties =
        new OpenCopProperties.Builder()
            .id(UUID.nameUUIDFromBytes(OBJECT_ID.getBytes(StandardCharsets.UTF_8)))
            .objectId(OBJECT_ID)
            .kind("track")
            .source(SOURCE_ID)
            .displayName(OBJECT_ID)
            .timestampz(new Timestampz(eventTime, eventTime - 100, staleAt))
            .element(new PointElement.Builder().type(ElementTypes.POINT).build())
            .build();
    return OpenCopPropertiesParser.writeToFeature(properties, new Point(-75.1652, 39.9526));
  }
}
