// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.technical.gateway.cache;

import static org.assertj.core.api.Assertions.assertThat;

import com.opencop.core.sdk.component.redis.LocalRedisClusterSupport;
import com.opencop.core.sdk.component.redis.RedisClusterCandidateContext;
import com.opencop.core.sdk.ports.gateway.cache.CacheService;
import com.opencop.core.sdk.technical.gateway.cache.EventGeoCacheKeyLayout;
import com.opencop.core.sdk.technical.gateway.cache.redis.RedisEventGeoSourceDirectory;
import com.opencop.core.sdk.technical.gateway.cache.redis.RedisService;
import com.opencop.core.sdk.technical.model.serializer.Serializer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.geojson.Feature;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

class JanitorClusterIT {

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

  @Test
  void run_discoversEveryPrimaryPreservesLiveSourcesAndRetiresEmptyMarkerOnNextPass() {
    try (var fixture = LocalRedisClusterSupport.start();
        var candidate = RedisClusterCandidateContext.start(fixture)) {
      var layout = candidate.bean(EventGeoCacheKeyLayout.class);
      var directory = candidate.bean(RedisEventGeoSourceDirectory.class);
      var stringTemplate = candidate.stringTemplate();
      var byteTemplate = candidate.byteTemplate();
      var cache = cache(byteTemplate, stringTemplate);
      var sourceIdsByPrimary =
          fixture.sourceIdsByPrimaryPort(sourceId -> layout.sourceKeys(sourceId).registryKey());
      assertThat(sourceIdsByPrimary).hasSize(3);

      var sourceKeys = sourceIdsByPrimary.values().stream().map(layout::sourceKeys).toList();
      var staleSource = sourceKeys.get(0);
      var liveSource = sourceKeys.get(1);
      var untouchedSource = sourceKeys.get(2);
      var stalePayload = staleSource.eventKey("expired");
      var livePayload = liveSource.eventKey("live");
      var untouchedPayload = untouchedSource.eventKey("untouched");
      try {
        directory.registerPreparedSources(sourceKeys);
        for (var keys : sourceKeys) {
          stringTemplate.opsForValue().set(keys.registryKey(), keys.sourcePrefix());
        }
        byteTemplate.opsForValue().set(stalePayload, new byte[] {1});
        stringTemplate.opsForZSet().add(staleSource.updatedAtKey(), stalePayload, 1.0d);
        stringTemplate
            .opsForZSet()
            .add(staleSource.staleAtKey(), stalePayload, System.currentTimeMillis() - 1_000L);

        byteTemplate.opsForValue().set(livePayload, new byte[] {2, 3});
        stringTemplate.opsForZSet().add(liveSource.updatedAtKey(), livePayload, 2.0d);
        stringTemplate
            .opsForZSet()
            .add(liveSource.staleAtKey(), livePayload, System.currentTimeMillis() + 60_000L);

        byteTemplate.opsForValue().set(untouchedPayload, new byte[] {4, 5});
        stringTemplate.opsForZSet().add(untouchedSource.updatedAtKey(), untouchedPayload, 3.0d);
        stringTemplate
            .opsForZSet()
            .add(
                untouchedSource.staleAtKey(),
                untouchedPayload,
                System.currentTimeMillis() + 60_000L);

        assertThat(directory.discoverSources()).containsExactlyInAnyOrderElementsOf(sourceKeys);

        new Janitor(cache, directory, 100, new SimpleMeterRegistry()).run();

        assertThat(byteTemplate.hasKey(stalePayload)).isFalse();
        assertThat(byteTemplate.opsForValue().get(livePayload)).containsExactly(2, 3);
        assertThat(byteTemplate.opsForValue().get(untouchedPayload)).containsExactly(4, 5);
        assertThat(stringTemplate.hasKey(liveSource.registryKey())).isTrue();
        assertThat(stringTemplate.hasKey(untouchedSource.registryKey())).isTrue();
        assertThat(stringTemplate.hasKey(staleSource.registryKey())).isTrue();

        new Janitor(cache, directory, 100, new SimpleMeterRegistry()).run();

        assertThat(stringTemplate.hasKey(staleSource.registryKey())).isFalse();
        assertThat(stringTemplate.hasKey(liveSource.registryKey())).isTrue();
        assertThat(stringTemplate.hasKey(untouchedSource.registryKey())).isTrue();
      } finally {
        for (var keys : sourceKeys) {
          byteTemplate.delete(
              List.of(keys.eventKey("expired"), keys.eventKey("live"), keys.eventKey("untouched")));
          stringTemplate.delete(
              List.of(keys.registryKey(), keys.updatedAtKey(), keys.staleAtKey()));
        }
      }
    }
  }

  private static CacheService<Feature> cache(
      RedisTemplate<String, byte[]> byteTemplate, StringRedisTemplate stringTemplate) {
    return new RedisService<>(NOOP_SERIALIZER, byteTemplate, stringTemplate);
  }
}
