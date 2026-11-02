// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.component.gateway.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.sdk.component.redis.LocalRedisClusterSupport;
import com.opencop.core.sdk.component.redis.RedisClusterCandidateContext;
import com.opencop.core.sdk.model.cache.CacheEventsNotification;
import com.opencop.core.sdk.ports.gateway.cache.CachePublisher;
import com.opencop.core.sdk.technical.gateway.cache.EventGeoCacheKeyLayout;
import com.opencop.core.sdk.technical.gateway.cache.EventGeoCacheKeyLayout.SourceKeys;
import com.opencop.core.sdk.technical.gateway.cache.EventGeoCacheKeys;
import com.opencop.core.sdk.technical.gateway.cache.redis.RedisEventGeoSourceDirectory;
import com.opencop.core.sdk.technical.model.opencop.OpenCopProperties;
import com.opencop.core.sdk.technical.model.opencop.Timestampz;
import com.opencop.core.sdk.technical.model.opencop.elements.ElementTypes;
import com.opencop.core.sdk.technical.model.opencop.elements.PointElement;
import com.opencop.core.sdk.technical.model.opencop.parser.OpenCopPropertiesParser;
import com.opencop.core.sdk.technical.model.serializer.Serializer;
import com.opencop.processor.technical.gateway.cache.GeoCacheWriter;
import io.lettuce.core.cluster.SlotHash;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.geojson.Feature;
import org.geojson.Point;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

class GeoCacheWriterClusterIT {

  @Test
  void write_whenOneChunkSpansAllPrimaries_usesSourceLocalKeysAndPersistentMarkers() {
    try (var fixture = LocalRedisClusterSupport.start();
        var cluster = RedisClusterCandidateContext.start(fixture)) {
      var layout = cluster.bean(EventGeoCacheKeyLayout.class);
      var directory = cluster.bean(RedisEventGeoSourceDirectory.class);
      var sourceIdsByPort = sourceIdsByPort(fixture, layout, "all-primary");
      var serializer = serializer();
      var publisher = publisher();
      var upsert = script("redis/geo_upsert.lua");
      var writer = writer(cluster, layout, directory, upsert, serializer, publisher);
      var now = System.currentTimeMillis();
      var features =
          sourceIdsByPort.entrySet().stream()
              .map(
                  entry ->
                      feature(
                          UUID.fromString(entry.getValue()),
                          "all-primary-" + entry.getKey(),
                          now - entry.getKey(),
                          now + 300_000))
              .toList();
      for (var feature : features) {
        when(serializer.serialize(feature)).thenReturn(bytes(feature.getId()));
      }

      writer.write(features, true);

      assertThat(layout).isEqualTo(EventGeoCacheKeyLayout.CLUSTER);
      for (var feature : features) {
        var properties = OpenCopPropertiesParser.readFromFeature(feature);
        var sourceKeys = layout.sourceKeys(properties.getSource());
        assertSourceKeysShareSlot(sourceKeys, properties.getObjectId());
        assertThat(
                cluster
                    .byteTemplate()
                    .opsForValue()
                    .get(sourceKeys.eventKey(properties.getObjectId())))
            .containsExactly(bytes(feature.getId()));
        assertThat(cluster.stringTemplate().opsForValue().get(sourceKeys.registryKey()))
            .isEqualTo(sourceKeys.sourcePrefix());
        assertThat(cluster.stringTemplate().getExpire(sourceKeys.registryKey())).isEqualTo(-1L);
        assertThat(
                cluster
                    .stringTemplate()
                    .opsForZSet()
                    .score(
                        sourceKeys.updatedAtKey(), sourceKeys.eventKey(properties.getObjectId())))
            .isEqualTo((double) properties.getTimestampz().getTime());
        verify(serializer).serialize(feature);
      }
      assertThat(directory.discoverSources())
          .extracting(SourceKeys::sourceId)
          .containsExactlyInAnyOrderElementsOf(sourceIdsByPort.values());
      assertThat(cluster.stringTemplate().hasKey(EventGeoCacheKeys.SOURCE_INDEX)).isFalse();
      verify(publisher, times(features.size())).publish(any(CacheEventsNotification.class));

      var selected = features.getFirst();
      var selectedProperties = OpenCopPropertiesParser.readFromFeature(selected);
      var selectedKeys = layout.sourceKeys(selectedProperties.getSource());
      var older =
          feature(
              selectedProperties.getSource(),
              selectedProperties.getObjectId(),
              selectedProperties.getTimestampz().getTime() - 1_000,
              selectedProperties.getTimestampz().getStale());
      when(serializer.serialize(older)).thenReturn(bytes("older"));
      writer.write(List.of(older), false);
      assertThat(
              cluster
                  .byteTemplate()
                  .opsForValue()
                  .get(selectedKeys.eventKey(selectedProperties.getObjectId())))
          .containsExactly(bytes(selected.getId()));

      writer.evictObject(
          selectedProperties.getSource(),
          selectedProperties.getObjectId(),
          selectedProperties.getTimestampz().getTime() - 1);
      assertThat(
              cluster
                  .byteTemplate()
                  .hasKey(selectedKeys.eventKey(selectedProperties.getObjectId())))
          .isTrue();
      writer.evictObject(
          selectedProperties.getSource(),
          selectedProperties.getObjectId(),
          selectedProperties.getTimestampz().getTime());
      assertThat(
              cluster
                  .byteTemplate()
                  .hasKey(selectedKeys.eventKey(selectedProperties.getObjectId())))
          .isFalse();

      writer.clearSource(selectedProperties.getSource());
      assertThat(cluster.stringTemplate().hasKey(selectedKeys.registryKey())).isFalse();
      for (var other : features.stream().skip(1).toList()) {
        var otherSource = OpenCopPropertiesParser.readFromFeature(other).getSource();
        var otherKeys = layout.sourceKeys(otherSource);
        assertThat(cluster.byteTemplate().hasKey(otherKeys.eventKey(other.getId()))).isTrue();
        assertThat(cluster.stringTemplate().hasKey(otherKeys.registryKey())).isTrue();
      }

      writer.write(List.of(older), false);
      assertThat(cluster.stringTemplate().opsForValue().get(selectedKeys.registryKey()))
          .isEqualTo(selectedKeys.sourcePrefix());
      assertThat(
              cluster
                  .stringTemplate()
                  .opsForZSet()
                  .score(
                      selectedKeys.updatedAtKey(),
                      selectedKeys.eventKey(selectedProperties.getObjectId())))
          .isEqualTo(
              (double) OpenCopPropertiesParser.readFromFeature(older).getTimestampz().getTime());
    }
  }

  @Test
  void write_afterOneNonSeedPrimaryLosesScript_recoversIdempotentlyWithoutDuplicateWork() {
    try (var fixture = LocalRedisClusterSupport.start();
        var cluster = RedisClusterCandidateContext.start(fixture)) {
      var layout = cluster.bean(EventGeoCacheKeyLayout.class);
      var directory = cluster.bean(RedisEventGeoSourceDirectory.class);
      var sourceIdsByPort = sourceIdsByPort(fixture, layout, "noscript-recovery");
      var serializer = serializer();
      var publisher = publisher();
      var upsert = script("redis/geo_upsert.lua");
      var writer = writer(cluster, layout, directory, upsert, serializer, publisher);
      var now = System.currentTimeMillis();
      var primingFeatures = featuresForPorts(sourceIdsByPort, "prime", now - 2_000, now + 300_000);
      var recoveryFeatures =
          featuresForPorts(sourceIdsByPort, "recovery", now - 1_000, now + 300_000);
      for (var feature : primingFeatures) {
        when(serializer.serialize(feature)).thenReturn(bytes(feature.getId()));
      }
      for (var feature : recoveryFeatures) {
        when(serializer.serialize(feature)).thenReturn(bytes(feature.getId()));
      }

      writer.write(primingFeatures, false);

      var scriptSha = sha1(upsert.getScriptAsString());
      for (var sourceId : sourceIdsByPort.values()) {
        assertThat(
                fixture.scriptExistsOnPrimaryOwning(
                    layout.sourceKeys(sourceId).registryKey(), scriptSha))
            .isTrue();
      }
      var nonSeedEntry =
          sourceIdsByPort.entrySet().stream()
              .filter(entry -> entry.getKey() != fixture.seedPort())
              .findFirst()
              .orElseThrow();
      var flushedMarkerKey = layout.sourceKeys(nonSeedEntry.getValue()).registryKey();
      fixture.flushScriptsOnPrimaryOwning(flushedMarkerKey);
      assertThat(fixture.scriptExistsOnPrimaryOwning(flushedMarkerKey, scriptSha)).isFalse();
      for (var entry : sourceIdsByPort.entrySet()) {
        if (!entry.getKey().equals(nonSeedEntry.getKey())) {
          assertThat(
                  fixture.scriptExistsOnPrimaryOwning(
                      layout.sourceKeys(entry.getValue()).registryKey(), scriptSha))
              .isTrue();
        }
      }

      writer.write(recoveryFeatures, true);

      for (var feature : recoveryFeatures) {
        var properties = OpenCopPropertiesParser.readFromFeature(feature);
        var sourceKeys = layout.sourceKeys(properties.getSource());
        assertThat(
                cluster
                    .byteTemplate()
                    .opsForValue()
                    .get(sourceKeys.eventKey(properties.getObjectId())))
            .containsExactly(bytes(feature.getId()));
        assertThat(cluster.stringTemplate().opsForZSet().size(sourceKeys.updatedAtKey()))
            .isEqualTo(2L);
        assertThat(fixture.scriptExistsOnPrimaryOwning(sourceKeys.registryKey(), scriptSha))
            .isTrue();
        verify(serializer).serialize(feature);
      }
      verify(publisher, times(recoveryFeatures.size())).publish(any(CacheEventsNotification.class));
    }
  }

  @Test
  void clearSource_whenUnindexedGracePayloadIsOnNonSeedPrimary_findsAndDeletesIt() {
    try (var fixture = LocalRedisClusterSupport.start();
        var cluster = RedisClusterCandidateContext.start(fixture)) {
      var layout = cluster.bean(EventGeoCacheKeyLayout.class);
      var directory = cluster.bean(RedisEventGeoSourceDirectory.class);
      var sourceIdsByPort = sourceIdsByPort(fixture, layout, "stale-orphan");
      var nonSeedSourceId =
          UUID.fromString(
              sourceIdsByPort.entrySet().stream()
                  .filter(entry -> entry.getKey() != fixture.seedPort())
                  .findFirst()
                  .orElseThrow()
                  .getValue());
      var otherSourceId = UUID.fromString(sourceIdsByPort.get(fixture.seedPort()));
      var staleSourceKeys = layout.sourceKeys(nonSeedSourceId);
      var otherSourceKeys = layout.sourceKeys(otherSourceId);
      fixture.assertKeyOwnedByNonSeedPrimary(staleSourceKeys.eventKey("orphan"));
      var serializer = serializer();
      var writer =
          writer(
              cluster, layout, directory, script("redis/geo_upsert.lua"), serializer, publisher());
      var now = System.currentTimeMillis();
      var orphan = feature(nonSeedSourceId, "orphan", now - 2_000, now - 1_000);
      var other = feature(otherSourceId, "other", now - 1_000, now + 300_000);
      when(serializer.serialize(orphan)).thenReturn(bytes("orphan"));
      when(serializer.serialize(other)).thenReturn(bytes("other"));
      writer.write(List.of(orphan, other), false);

      assertThat(cluster.byteTemplate().hasKey(staleSourceKeys.eventKey("orphan"))).isTrue();
      assertThat(
              cluster
                  .stringTemplate()
                  .getExpire(staleSourceKeys.eventKey("orphan"), TimeUnit.MILLISECONDS))
          .isBetween(1L, 60_000L);
      assertThat(cluster.stringTemplate().opsForZSet().size(staleSourceKeys.updatedAtKey()))
          .isZero();
      assertThat(cluster.stringTemplate().opsForZSet().size(staleSourceKeys.staleAtKey())).isZero();
      assertThat(cluster.stringTemplate().hasKey(staleSourceKeys.registryKey())).isTrue();

      writer.clearSource(nonSeedSourceId);

      assertThat(cluster.byteTemplate().hasKey(staleSourceKeys.eventKey("orphan"))).isFalse();
      assertThat(cluster.stringTemplate().hasKey(staleSourceKeys.registryKey())).isFalse();
      assertThat(cluster.byteTemplate().hasKey(otherSourceKeys.eventKey("other"))).isTrue();
      assertThat(cluster.stringTemplate().hasKey(otherSourceKeys.registryKey())).isTrue();
    }
  }

  @Test
  void write_whenLateLuaError_leavesSurvivingStateDiscoverableAndPublishesNothing() {
    try (var fixture = LocalRedisClusterSupport.start();
        var cluster = RedisClusterCandidateContext.start(fixture)) {
      var layout = cluster.bean(EventGeoCacheKeyLayout.class);
      var directory = cluster.bean(RedisEventGeoSourceDirectory.class);
      var sourceId =
          UUID.fromString(
              sourceIdsByPort(fixture, layout, "late-error").values().stream()
                  .findFirst()
                  .orElseThrow());
      var sourceKeys = layout.sourceKeys(sourceId);
      var serializer = serializer();
      var publisher = publisher();
      var writer =
          writer(cluster, layout, directory, script("redis/geo_upsert.lua"), serializer, publisher);
      long now = System.currentTimeMillis();
      var feature = feature(sourceId, "late-error", now - 1_000, now + 300_000);
      when(serializer.serialize(feature)).thenReturn(bytes("late-error"));
      cluster.stringTemplate().opsForValue().set(sourceKeys.staleAtKey(), "wrong-type");

      var failure = catchThrowable(() -> writer.write(List.of(feature), true));

      assertThat(failure).isNotNull();
      assertThat(cluster.stringTemplate().opsForValue().get(sourceKeys.registryKey()))
          .isEqualTo(sourceKeys.sourcePrefix());
      assertThat(
              cluster
                  .stringTemplate()
                  .opsForZSet()
                  .score(sourceKeys.updatedAtKey(), sourceKeys.eventKey("late-error")))
          .isEqualTo((double) (now - 1_000));
      assertThat(cluster.byteTemplate().opsForValue().get(sourceKeys.eventKey("late-error")))
          .containsExactly(bytes("late-error"));
      assertThat(directory.discoverSources()).containsExactly(sourceKeys);
      verify(publisher, never()).publish(any());
    }
  }

  @Test
  void write_whenFeatureIsMalformedOrCannotSerialize_createsNoSourceState() {
    try (var fixture = LocalRedisClusterSupport.start();
        var cluster = RedisClusterCandidateContext.start(fixture)) {
      var layout = cluster.bean(EventGeoCacheKeyLayout.class);
      var directory = cluster.bean(RedisEventGeoSourceDirectory.class);
      var sourceId =
          UUID.fromString(
              sourceIdsByPort(fixture, layout, "serializer-rejection").values().stream()
                  .findFirst()
                  .orElseThrow());
      var sourceKeys = layout.sourceKeys(sourceId);
      var serializer = serializer();
      var rejected = feature(sourceId, "rejected", 1L, 2L);
      when(serializer.serialize(rejected)).thenThrow(new IllegalArgumentException("bad payload"));
      var writer =
          writer(
              cluster, layout, directory, script("redis/geo_upsert.lua"), serializer, publisher());

      writer.write(List.of(new Feature(), rejected), false);

      assertThat(
              cluster
                  .stringTemplate()
                  .countExistingKeys(
                      List.of(
                          sourceKeys.eventKey("rejected"),
                          sourceKeys.updatedAtKey(),
                          sourceKeys.staleAtKey(),
                          sourceKeys.registryKey())))
          .isZero();
      assertThat(directory.discoverSources()).isEmpty();
    }
  }

  @Test
  void upsertScript_whenRegistryKindIsInvalid_failsBeforeAnyMutation() {
    try (var fixture = LocalRedisClusterSupport.start();
        var cluster = RedisClusterCandidateContext.start(fixture)) {
      var layout = cluster.bean(EventGeoCacheKeyLayout.class);
      var sourceId =
          UUID.fromString(
              sourceIdsByPort(fixture, layout, "invalid-registry").values().stream()
                  .findFirst()
                  .orElseThrow());
      var keys = layout.sourceKeys(sourceId);

      assertThatThrownBy(
              () ->
                  cluster
                      .stringTemplate()
                      .execute(
                          script("redis/geo_upsert.lua"),
                          List.of(
                              keys.eventKey("invalid-kind"),
                              keys.updatedAtKey(),
                              keys.staleAtKey(),
                              keys.registryKey()),
                          "payload",
                          "60000",
                          "1",
                          "60001",
                          "1",
                          keys.sourcePrefix(),
                          "60000",
                          "INVALID"))
          .hasRootCauseMessage("ERR invalid live-geo registry kind");
      assertThat(
              cluster
                  .stringTemplate()
                  .countExistingKeys(
                      List.of(
                          keys.eventKey("invalid-kind"),
                          keys.updatedAtKey(),
                          keys.staleAtKey(),
                          keys.registryKey())))
          .isZero();
    }
  }

  @Test
  void clearSource_whenIndexContainsForeignMember_doesNotDeleteForeignPayload() {
    try (var fixture = LocalRedisClusterSupport.start();
        var cluster = RedisClusterCandidateContext.start(fixture)) {
      var layout = cluster.bean(EventGeoCacheKeyLayout.class);
      var directory = cluster.bean(RedisEventGeoSourceDirectory.class);
      var sourceIds =
          sourceIdsByPort(fixture, layout, "foreign-index-member").values().stream()
              .limit(2)
              .toList();
      var localSourceId = UUID.fromString(sourceIds.get(0));
      var foreignSourceId = UUID.fromString(sourceIds.get(1));
      var localKeys = layout.sourceKeys(localSourceId);
      var foreignKeys = layout.sourceKeys(foreignSourceId);
      var local = feature(localSourceId, "local", 1_000L, System.currentTimeMillis() + 300_000);
      var foreign =
          feature(foreignSourceId, "foreign", 2_000L, System.currentTimeMillis() + 300_000);
      var serializer = serializer();
      when(serializer.serialize(local)).thenReturn(bytes("local"));
      when(serializer.serialize(foreign)).thenReturn(bytes("foreign"));
      var writer =
          writer(
              cluster, layout, directory, script("redis/geo_upsert.lua"), serializer, publisher());
      writer.write(List.of(local, foreign), false);
      cluster
          .stringTemplate()
          .opsForZSet()
          .add(localKeys.updatedAtKey(), foreignKeys.eventKey("foreign"), 2_000L);

      writer.clearSource(localSourceId);

      assertThat(cluster.byteTemplate().hasKey(localKeys.eventKey("local"))).isFalse();
      assertThat(cluster.stringTemplate().hasKey(localKeys.registryKey())).isFalse();
      assertThat(cluster.byteTemplate().opsForValue().get(foreignKeys.eventKey("foreign")))
          .containsExactly(bytes("foreign"));
      assertThat(cluster.stringTemplate().hasKey(foreignKeys.registryKey())).isTrue();
    }
  }

  @Test
  void clearSource_whenWriterRepopulatesBeforeConditionalUnregister_preservesMarker()
      throws Exception {
    try (var fixture = LocalRedisClusterSupport.start();
        var cluster = RedisClusterCandidateContext.start(fixture)) {
      var layout = cluster.bean(EventGeoCacheKeyLayout.class);
      var realDirectory = cluster.bean(RedisEventGeoSourceDirectory.class);
      var directory = spy(realDirectory);
      var sourceId =
          UUID.fromString(
              sourceIdsByPort(fixture, layout, "clear-write-race").values().stream()
                  .findFirst()
                  .orElseThrow());
      var sourceKeys = layout.sourceKeys(sourceId);
      var beforeUnregister = new CountDownLatch(1);
      var allowUnregister = new CountDownLatch(1);
      doAnswer(
              invocation -> {
                beforeUnregister.countDown();
                assertThat(allowUnregister.await(10, TimeUnit.SECONDS)).isTrue();
                return invocation.callRealMethod();
              })
          .when(directory)
          .unregisterIfEmpty(any(SourceKeys.class));
      var serializer = serializer();
      var writer =
          writer(
              cluster, layout, directory, script("redis/geo_upsert.lua"), serializer, publisher());
      long now = System.currentTimeMillis();
      var initial = feature(sourceId, "race", now - 2_000, now + 300_000);
      var replacement = feature(sourceId, "race", now - 1_000, now + 300_000);
      when(serializer.serialize(initial)).thenReturn(bytes("initial"));
      when(serializer.serialize(replacement)).thenReturn(bytes("replacement"));
      writer.write(List.of(initial), false);

      var clear = CompletableFuture.runAsync(() -> writer.clearSource(sourceId));
      assertThat(beforeUnregister.await(10, TimeUnit.SECONDS)).isTrue();
      assertThat(cluster.byteTemplate().hasKey(sourceKeys.eventKey("race"))).isFalse();
      assertThat(cluster.stringTemplate().hasKey(sourceKeys.updatedAtKey())).isFalse();
      assertThat(cluster.stringTemplate().hasKey(sourceKeys.staleAtKey())).isFalse();
      try {
        writer.write(List.of(replacement), false);
      } finally {
        allowUnregister.countDown();
      }
      clear.get(10, TimeUnit.SECONDS);

      assertThat(cluster.byteTemplate().opsForValue().get(sourceKeys.eventKey("race")))
          .containsExactly(bytes("replacement"));
      assertThat(
              cluster
                  .stringTemplate()
                  .opsForZSet()
                  .score(sourceKeys.updatedAtKey(), sourceKeys.eventKey("race")))
          .isEqualTo((double) (now - 1_000));
      assertThat(cluster.stringTemplate().opsForValue().get(sourceKeys.registryKey()))
          .isEqualTo(sourceKeys.sourcePrefix());
    }
  }

  private static Map<Integer, String> sourceIdsByPort(
      LocalRedisClusterSupport.Fixture fixture,
      EventGeoCacheKeyLayout layout,
      String testNamespace) {
    var candidates =
        fixture.sourceIdsByPrimaryPort(
            sourceId ->
                layout.sourceKeys(namespacedSourceId(testNamespace, sourceId)).registryKey());
    var namespaced = new LinkedHashMap<Integer, String>();
    candidates.forEach(
        (port, sourceId) -> namespaced.put(port, namespacedSourceId(testNamespace, sourceId)));
    return Collections.unmodifiableMap(namespaced);
  }

  private static String namespacedSourceId(String testNamespace, String candidateSourceId) {
    return UUID.nameUUIDFromBytes(
            (testNamespace + ":" + candidateSourceId).getBytes(StandardCharsets.UTF_8))
        .toString();
  }

  private static List<Feature> featuresForPorts(
      Map<Integer, String> sourceIdsByPort, String prefix, long eventTime, long staleAt) {
    return sourceIdsByPort.entrySet().stream()
        .map(
            entry ->
                feature(
                    UUID.fromString(entry.getValue()),
                    prefix + "-" + entry.getKey(),
                    eventTime,
                    staleAt))
        .toList();
  }

  private static void assertSourceKeysShareSlot(SourceKeys keys, String objectId) {
    assertThat(
            List.of(
                    keys.eventKey(objectId),
                    keys.updatedAtKey(),
                    keys.staleAtKey(),
                    keys.hydrationLockKey(),
                    keys.registryKey())
                .stream()
                .map(SlotHash::getSlot))
        .containsOnly(SlotHash.getSlot(keys.registryKey()));
  }

  private static GeoCacheWriter writer(
      RedisClusterCandidateContext cluster,
      EventGeoCacheKeyLayout layout,
      RedisEventGeoSourceDirectory directory,
      DefaultRedisScript<Long> upsert,
      Serializer<Feature, byte[]> serializer,
      CachePublisher<CacheEventsNotification> publisher) {
    return new GeoCacheWriter(
        cluster.byteTemplate(),
        layout,
        directory,
        upsert,
        script("redis/geo_evict.lua"),
        serializer,
        publisher);
  }

  private static DefaultRedisScript<Long> script(String path) {
    var script = new DefaultRedisScript<Long>();
    script.setResultType(Long.class);
    script.setScriptSource(new ResourceScriptSource(new ClassPathResource(path)));
    return script;
  }

  @SuppressWarnings("unchecked")
  private static Serializer<Feature, byte[]> serializer() {
    return mock(Serializer.class);
  }

  @SuppressWarnings("unchecked")
  private static CachePublisher<CacheEventsNotification> publisher() {
    return mock(CachePublisher.class);
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
    var feature = OpenCopPropertiesParser.writeToFeature(properties, new Point(-75.1652, 39.9526));
    feature.setId(objectId);
    return feature;
  }

  private static String sha1(String script) {
    try {
      var digest = MessageDigest.getInstance("SHA-1");
      return HexFormat.of().formatHex(digest.digest(bytes(script)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-1 algorithm not available", exception);
    }
  }

  private static byte[] bytes(String value) {
    return value.getBytes(StandardCharsets.UTF_8);
  }
}
