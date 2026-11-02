// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.technical.gateway.cache;

import static org.assertj.core.api.Assertions.assertThat;

import com.opencop.core.sdk.component.redis.LocalRedisSupport;
import com.opencop.core.sdk.ports.gateway.cache.CacheService;
import com.opencop.core.sdk.technical.gateway.cache.EventGeoCacheKeyLayout;
import com.opencop.core.sdk.technical.gateway.cache.EventGeoCacheKeyLayout.SourceKeys;
import com.opencop.core.sdk.technical.gateway.cache.redis.RedisEventGeoSourceDirectory;
import com.opencop.core.sdk.technical.gateway.cache.redis.RedisService;
import com.opencop.core.sdk.technical.model.serializer.Serializer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.ArrayList;
import java.util.List;
import org.geojson.Feature;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;

class JanitorIT {

  private static final LettuceConnectionFactory CONNECTION_FACTORY =
      new LettuceConnectionFactory(LocalRedisSupport.host(), LocalRedisSupport.port());
  private static final StringRedisTemplate STRING_REDIS_TEMPLATE = new StringRedisTemplate();
  private static final RedisTemplate<String, byte[]> REDIS_TEMPLATE = new RedisTemplate<>();
  private static final Serializer<Feature, byte[]> NOOP_SERIALIZER =
      new Serializer<>() {
        @Override
        public byte[] serialize(Feature value) {
          return new byte[0];
        }

        @Override
        public Feature deserialize(byte[] serialized) {
          return null;
        }
      };

  private static CacheService<Feature> cache;
  private static RedisEventGeoSourceDirectory directory;
  private final List<SourceKeys> sourcesForCleanup = new ArrayList<>();

  @BeforeAll
  static void setUpRedis() {
    CONNECTION_FACTORY.afterPropertiesSet();
    STRING_REDIS_TEMPLATE.setConnectionFactory(CONNECTION_FACTORY);
    STRING_REDIS_TEMPLATE.afterPropertiesSet();
    REDIS_TEMPLATE.setKeySerializer(RedisSerializer.string());
    REDIS_TEMPLATE.setValueSerializer(RedisSerializer.byteArray());
    REDIS_TEMPLATE.setHashKeySerializer(RedisSerializer.string());
    REDIS_TEMPLATE.setHashValueSerializer(RedisSerializer.byteArray());
    REDIS_TEMPLATE.setConnectionFactory(CONNECTION_FACTORY);
    REDIS_TEMPLATE.afterPropertiesSet();
    cache = new RedisService<>(NOOP_SERIALIZER, REDIS_TEMPLATE, STRING_REDIS_TEMPLATE);
    directory =
        new RedisEventGeoSourceDirectory(
            EventGeoCacheKeyLayout.STANDALONE, STRING_REDIS_TEMPLATE, new SimpleMeterRegistry());
  }

  @AfterAll
  static void tearDownRedis() {
    CONNECTION_FACTORY.destroy();
  }

  @AfterEach
  void cleanOwnedSources() {
    for (var sourceKeys : sourcesForCleanup) {
      var ownedKeys = STRING_REDIS_TEMPLATE.keys(sourceKeys.sourcePrefix() + "*");
      if (ownedKeys != null && !ownedKeys.isEmpty()) {
        STRING_REDIS_TEMPLATE.delete(ownedKeys);
      }
      STRING_REDIS_TEMPLATE
          .opsForZSet()
          .remove(sourceKeys.registryKey(), sourceKeys.sourcePrefix());
    }
  }

  @Test
  void run_removesStalePayloadAndRetiresSourceOnNextEligiblePass() {
    var sourceKeys = source("standalone-retire");
    var payload = sourceKeys.eventKey("expired");
    register(sourceKeys);
    REDIS_TEMPLATE.opsForValue().set(payload, new byte[] {1, 2, 3});
    STRING_REDIS_TEMPLATE.opsForZSet().add(sourceKeys.updatedAtKey(), payload, 1.0d);
    STRING_REDIS_TEMPLATE
        .opsForZSet()
        .add(sourceKeys.staleAtKey(), payload, System.currentTimeMillis() - 1_000L);

    new Janitor(cache, directory, 100, new SimpleMeterRegistry()).run();

    assertThat(REDIS_TEMPLATE.hasKey(payload)).isFalse();
    assertThat(STRING_REDIS_TEMPLATE.opsForZSet().range(sourceKeys.updatedAtKey(), 0, -1))
        .isEmpty();
    assertThat(STRING_REDIS_TEMPLATE.opsForZSet().range(sourceKeys.staleAtKey(), 0, -1)).isEmpty();
    assertThat(
            STRING_REDIS_TEMPLATE
                .opsForZSet()
                .score(sourceKeys.registryKey(), sourceKeys.sourcePrefix()))
        .isNotNull();

    new Janitor(cache, directory, 100, new SimpleMeterRegistry()).run();

    assertThat(
            STRING_REDIS_TEMPLATE
                .opsForZSet()
                .score(sourceKeys.registryKey(), sourceKeys.sourcePrefix()))
        .isNull();
  }

  @Test
  void run_preservesRegisteredSourceWithFutureStaleMembership() {
    var sourceKeys = source("standalone-live");
    var payload = sourceKeys.eventKey("live");
    register(sourceKeys);
    REDIS_TEMPLATE.opsForValue().set(payload, new byte[] {4, 5, 6});
    STRING_REDIS_TEMPLATE.opsForZSet().add(sourceKeys.updatedAtKey(), payload, 2.0d);
    STRING_REDIS_TEMPLATE
        .opsForZSet()
        .add(sourceKeys.staleAtKey(), payload, System.currentTimeMillis() + 60_000L);

    new Janitor(cache, directory, 100, new SimpleMeterRegistry()).run();

    assertThat(REDIS_TEMPLATE.opsForValue().get(payload)).containsExactly(4, 5, 6);
    assertThat(STRING_REDIS_TEMPLATE.opsForZSet().score(sourceKeys.updatedAtKey(), payload))
        .isEqualTo(2.0d);
    assertThat(STRING_REDIS_TEMPLATE.opsForZSet().score(sourceKeys.staleAtKey(), payload))
        .isNotNull();
    assertThat(
            STRING_REDIS_TEMPLATE
                .opsForZSet()
                .score(sourceKeys.registryKey(), sourceKeys.sourcePrefix()))
        .isNotNull();
  }

  @Test
  void run_keepsAnotherRegisteredSourceByteAndScoreStateIntact() {
    var staleSource = source("standalone-stale");
    var otherSource = source("standalone-other");
    var stalePayload = staleSource.eventKey("expired");
    var otherPayload = otherSource.eventKey("survivor");
    register(staleSource);
    register(otherSource);
    REDIS_TEMPLATE.opsForValue().set(stalePayload, new byte[] {7});
    REDIS_TEMPLATE.opsForValue().set(otherPayload, new byte[] {8, 9});
    STRING_REDIS_TEMPLATE.opsForZSet().add(staleSource.updatedAtKey(), stalePayload, 3.0d);
    STRING_REDIS_TEMPLATE
        .opsForZSet()
        .add(staleSource.staleAtKey(), stalePayload, System.currentTimeMillis() - 1_000L);
    STRING_REDIS_TEMPLATE.opsForZSet().add(otherSource.updatedAtKey(), otherPayload, 4.0d);
    STRING_REDIS_TEMPLATE
        .opsForZSet()
        .add(otherSource.staleAtKey(), otherPayload, System.currentTimeMillis() + 60_000L);
    var otherUpdatedScore =
        STRING_REDIS_TEMPLATE.opsForZSet().score(otherSource.updatedAtKey(), otherPayload);
    var otherStaleScore =
        STRING_REDIS_TEMPLATE.opsForZSet().score(otherSource.staleAtKey(), otherPayload);

    new Janitor(cache, directory, 100, new SimpleMeterRegistry()).run();

    assertThat(REDIS_TEMPLATE.hasKey(stalePayload)).isFalse();
    assertThat(REDIS_TEMPLATE.opsForValue().get(otherPayload)).containsExactly(8, 9);
    assertThat(STRING_REDIS_TEMPLATE.opsForZSet().score(otherSource.updatedAtKey(), otherPayload))
        .isEqualTo(otherUpdatedScore);
    assertThat(STRING_REDIS_TEMPLATE.opsForZSet().score(otherSource.staleAtKey(), otherPayload))
        .isEqualTo(otherStaleScore);
    assertThat(
            STRING_REDIS_TEMPLATE
                .opsForZSet()
                .score(otherSource.registryKey(), otherSource.sourcePrefix()))
        .isNotNull();
  }

  private SourceKeys source(String sourceId) {
    var sourceKeys = EventGeoCacheKeyLayout.STANDALONE.sourceKeys(sourceId);
    sourcesForCleanup.add(sourceKeys);
    return sourceKeys;
  }

  private static void register(SourceKeys sourceKeys) {
    STRING_REDIS_TEMPLATE
        .opsForZSet()
        .add(sourceKeys.registryKey(), sourceKeys.sourcePrefix(), 1.0d);
  }
}
