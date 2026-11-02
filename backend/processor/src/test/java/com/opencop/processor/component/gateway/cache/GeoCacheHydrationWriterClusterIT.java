// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.component.gateway.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.sdk.component.redis.LocalRedisClusterSupport;
import com.opencop.core.sdk.component.redis.RedisClusterCandidateContext;
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
import com.opencop.processor.ports.technical.gateway.cache.GeoCacheGateway;
import com.opencop.processor.ports.technical.gateway.db.GeoCacheHydrationGateway.HydrationSnapshotStats;
import com.opencop.processor.technical.gateway.cache.GeoCacheWriter;
import com.opencop.processor.technical.gateway.db.GeoCacheHydrationRepository;
import com.opencop.processor.technical.gateway.lock.geo.RedisGeoCacheHydrationLock;
import io.lettuce.core.cluster.SlotHash;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.geojson.Feature;
import org.geojson.Point;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

class GeoCacheHydrationWriterClusterIT {

  private static final String OBJECT_ID = "cluster-hydrated-object";
  private static final byte[] PAYLOAD = "cluster-hydrated-payload".getBytes(StandardCharsets.UTF_8);

  @Test
  void hydration_whenClustered_holdsSelectedLockAndWritesSourceLocalState() {
    try (var fixture = LocalRedisClusterSupport.start();
        var cluster = RedisClusterCandidateContext.start(fixture)) {
      var layout = cluster.bean(EventGeoCacheKeyLayout.class);
      var directory = cluster.bean(RedisEventGeoSourceDirectory.class);
      var rawIdsByPort =
          fixture.sourceIdsByPrimaryPort(
              candidate -> layout.sourceKeys(hydrationSourceId(candidate)).hydrationLockKey());
      var nonSeed =
          rawIdsByPort.entrySet().stream()
              .filter(entry -> entry.getKey() != fixture.seedPort())
              .findFirst()
              .orElseThrow();
      var sourceId = UUID.fromString(hydrationSourceId(nonSeed.getValue()));
      var sourceKeys = layout.sourceKeys(sourceId);
      fixture.assertKeyOwnedByNonSeedPrimary(sourceKeys.hydrationLockKey());
      long now = System.currentTimeMillis();
      long eventTime = now - 1_000;
      long staleAt = now + 300_000;
      var feature = feature(sourceId, eventTime, staleAt);
      var job =
          GeoCacheHydrationJobSubmission.forSource(
              JobIssuer.system("geo-hydration-cluster-it"),
              sourceId,
              GeoCacheHydrationJob.Trigger.MANUAL);
      var repository = mock(GeoCacheHydrationRepository.class);
      when(repository.hydrateSnapshot(eq(sourceId), eq(1_000), any()))
          .thenAnswer(
              invocation -> {
                assertThat(
                        cluster.stringTemplate().opsForValue().get(sourceKeys.hydrationLockKey()))
                    .isEqualTo(job.jobId().toString());
                assertThat(
                        cluster
                            .stringTemplate()
                            .getExpire(sourceKeys.hydrationLockKey(), TimeUnit.MILLISECONDS))
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
      var writer =
          new GeoCacheWriter(
              cluster.byteTemplate(),
              layout,
              directory,
              script("redis/geo_upsert.lua"),
              script("redis/geo_evict.lua"),
              serializer,
              publisher);
      var handler =
          new GeoCacheHydrationHandler(
              repository, writer, new RedisGeoCacheHydrationLock(cluster.stringTemplate(), layout));

      assertThat(
              List.of(
                      sourceKeys.hydrationLockKey(),
                      sourceKeys.eventKey(OBJECT_ID),
                      sourceKeys.updatedAtKey(),
                      sourceKeys.staleAtKey(),
                      sourceKeys.registryKey())
                  .stream()
                  .map(SlotHash::getSlot))
          .containsOnly(SlotHash.getSlot(sourceKeys.registryKey()));

      handler.hydrate(job);

      assertThat(cluster.byteTemplate().opsForValue().get(sourceKeys.eventKey(OBJECT_ID)))
          .containsExactly(PAYLOAD);
      assertThat(
              cluster
                  .stringTemplate()
                  .opsForZSet()
                  .score(sourceKeys.updatedAtKey(), sourceKeys.eventKey(OBJECT_ID)))
          .isEqualTo((double) eventTime);
      assertThat(
              cluster
                  .stringTemplate()
                  .opsForZSet()
                  .score(sourceKeys.staleAtKey(), sourceKeys.eventKey(OBJECT_ID)))
          .isEqualTo((double) staleAt);
      assertThat(cluster.stringTemplate().opsForValue().get(sourceKeys.registryKey()))
          .isEqualTo(sourceKeys.sourcePrefix());
      assertThat(cluster.stringTemplate().getExpire(sourceKeys.registryKey())).isEqualTo(-1L);
      assertThat(directory.discoverSources()).containsExactly(sourceKeys);
      assertThat(cluster.stringTemplate().hasKey(sourceKeys.hydrationLockKey())).isFalse();
      assertThat(cluster.stringTemplate().hasKey(EventGeoCacheKeys.SOURCE_INDEX)).isFalse();
      verify(publisher).publish(new CacheEventsNotification(sourceId, List.of(OBJECT_ID)));
    }
  }

  @Test
  void hydration_whenExpiredOwnerIsReplaced_finallyPreservesSuccessorValueAndTtl() {
    try (var fixture = LocalRedisClusterSupport.start();
        var cluster = RedisClusterCandidateContext.start(fixture)) {
      var layout = cluster.bean(EventGeoCacheKeyLayout.class);
      var rawIdsByPort =
          fixture.sourceIdsByPrimaryPort(
              candidate -> layout.sourceKeys(hydrationSourceId(candidate)).hydrationLockKey());
      var nonSeed =
          rawIdsByPort.entrySet().stream()
              .filter(entry -> entry.getKey() != fixture.seedPort())
              .findFirst()
              .orElseThrow();
      var sourceId = UUID.fromString(hydrationSourceId(nonSeed.getValue()));
      var sourceKeys = layout.sourceKeys(sourceId);
      fixture.assertKeyOwnedByNonSeedPrimary(sourceKeys.hydrationLockKey());
      var job =
          GeoCacheHydrationJobSubmission.forSource(
              JobIssuer.system("geo-hydration-successor-owner-it"),
              sourceId,
              GeoCacheHydrationJob.Trigger.MANUAL);
      var successorOwner = "successor-owner";
      var lockGateway = new RedisGeoCacheHydrationLock(cluster.stringTemplate(), layout);
      var repository = mock(GeoCacheHydrationRepository.class);
      when(repository.hydrateSnapshot(eq(sourceId), eq(1_000), any()))
          .thenAnswer(
              invocation -> {
                assertThat(
                        cluster.stringTemplate().opsForValue().get(sourceKeys.hydrationLockKey()))
                    .isEqualTo(job.jobId().toString());
                assertThat(cluster.stringTemplate().delete(sourceKeys.hydrationLockKey())).isTrue();
                assertThat(lockGateway.tryAcquire(sourceId, successorOwner, Duration.ofMinutes(30)))
                    .isTrue();
                return new HydrationSnapshotStats(0, 0, null);
              });
      var handler =
          new GeoCacheHydrationHandler(repository, mock(GeoCacheGateway.class), lockGateway);

      handler.hydrate(job);

      assertThat(cluster.stringTemplate().opsForValue().get(sourceKeys.hydrationLockKey()))
          .isEqualTo(successorOwner);
      assertThat(
              cluster
                  .stringTemplate()
                  .getExpire(sourceKeys.hydrationLockKey(), TimeUnit.MILLISECONDS))
          .isBetween(1L, TimeUnit.MINUTES.toMillis(30));
    }
  }

  private static DefaultRedisScript<Long> script(String path) {
    var script = new DefaultRedisScript<Long>();
    script.setResultType(Long.class);
    script.setScriptSource(new ResourceScriptSource(new ClassPathResource(path)));
    return script;
  }

  private static Feature feature(UUID sourceId, long eventTime, long staleAt) {
    var properties =
        new OpenCopProperties.Builder()
            .id(UUID.nameUUIDFromBytes(OBJECT_ID.getBytes(StandardCharsets.UTF_8)))
            .objectId(OBJECT_ID)
            .kind("track")
            .source(sourceId)
            .displayName(OBJECT_ID)
            .timestampz(new Timestampz(eventTime, eventTime - 100, staleAt))
            .element(new PointElement.Builder().type(ElementTypes.POINT).build())
            .build();
    return OpenCopPropertiesParser.writeToFeature(properties, new Point(-75.1652, 39.9526));
  }

  private static String hydrationSourceId(String candidate) {
    return UUID.nameUUIDFromBytes(("hydration:" + candidate).getBytes(StandardCharsets.UTF_8))
        .toString();
  }
}
