// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.cache;

import static org.assertj.core.api.Assertions.assertThat;

import com.opencop.core.sdk.component.redis.LocalRedisClusterSupport;
import com.opencop.core.sdk.component.redis.RedisClusterCandidateContext;
import com.opencop.core.sdk.technical.gateway.cache.EventGeoCacheKeyLayout;
import com.opencop.core.sdk.technical.gateway.cache.EventGeoCacheKeyLayout.SourceKeys;
import com.opencop.core.sdk.technical.gateway.cache.redis.RedisEventGeoSourceDirectory;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

class RedisEventGeoCacheEvictionGatewayClusterIT {

  @Test
  void clusterEviction_clearsOneNonSeedSourceAndLeavesOtherSourcesByteAndScoreIntact() {
    try (var fixture = LocalRedisClusterSupport.start();
        var candidate = RedisClusterCandidateContext.start(fixture)) {
      var layout = candidate.bean(EventGeoCacheKeyLayout.class);
      var directory = candidate.bean(RedisEventGeoSourceDirectory.class);
      var redis = candidate.stringTemplate();
      var byteRedis = candidate.byteTemplate();
      var sourceIdsByPrimaryPort =
          fixture.sourceIdsByPrimaryPort(sourceId -> layout.sourceKeys(sourceId).registryKey());

      assertThat(sourceIdsByPrimaryPort.keySet())
          .containsExactly(fixture.seedPort(), fixture.seedPort() + 1, fixture.seedPort() + 2);
      assertThat(sourceIdsByPrimaryPort.values()).doesNotHaveDuplicates();
      var sources = sourceIdsByPrimaryPort.values().stream().map(layout::sourceKeys).toList();
      var target = sources.get(1);
      var untouched = sources.get(2);
      fixture.assertKeyOwnedByNonSeedPrimary(target.registryKey());

      try {
        directory.registerPreparedSources(sources);
        for (var source : sources) {
          seedSource(redis, source, source.sourceId());
        }
        var foreignEventKey = untouched.eventKey("foreign-member");
        redis.opsForValue().set(foreignEventKey, "untouched-foreign-payload");
        redis.opsForZSet().add(target.updatedAtKey(), foreignEventKey, 91.0);
        redis.opsForZSet().add(target.staleAtKey(), foreignEventKey, 92.0);

        var beforePayloads = payloads(byteRedis, untouched);
        var beforeUpdatedScores = scores(byteRedis, untouched.updatedAtKey());
        var beforeStaleScores = scores(byteRedis, untouched.staleAtKey());

        new RedisEventGeoCacheEvictionGateway(redis, layout, directory)
            .evictGeoSource(UUID.fromString(target.sourceId()));

        assertThat(redis.hasKey(target.registryKey())).isFalse();
        assertThat(redis.hasKey(target.eventKey("event-1"))).isFalse();
        assertThat(redis.hasKey(target.eventKey("orphan"))).isFalse();
        assertThat(redis.hasKey(target.updatedAtKey())).isFalse();
        assertThat(redis.hasKey(target.staleAtKey())).isFalse();
        assertThat(redis.hasKey(foreignEventKey)).isTrue();
        assertThat(directory.discoverSources()).containsExactlyInAnyOrderElementsOf(sources);

        assertPayloadsEqual(byteRedis, beforePayloads);
        assertThat(scores(byteRedis, untouched.updatedAtKey())).isEqualTo(beforeUpdatedScores);
        assertThat(scores(byteRedis, untouched.staleAtKey())).isEqualTo(beforeStaleScores);
      } finally {
        for (var source : sources) {
          deleteSource(redis, source);
        }
      }
    }
  }

  @Test
  void clusterDirectory_unregisterIfEmpty_preservesRegistrationWhenAnIndexIsLive() {
    try (var fixture = LocalRedisClusterSupport.start();
        var candidate = RedisClusterCandidateContext.start(fixture)) {
      var layout = candidate.bean(EventGeoCacheKeyLayout.class);
      var directory = candidate.bean(RedisEventGeoSourceDirectory.class);
      var redis = candidate.stringTemplate();
      var sourceIdsByPrimaryPort =
          fixture.sourceIdsByPrimaryPort(sourceId -> layout.sourceKeys(sourceId).registryKey());
      var source =
          layout.sourceKeys(
              sourceIdsByPrimaryPort.values().stream().skip(1).findFirst().orElseThrow());

      try {
        redis.opsForValue().set(source.registryKey(), source.sourcePrefix());
        redis.opsForZSet().add(source.updatedAtKey(), source.eventKey("still-live"), 1.0);

        assertThat(directory.unregisterIfEmpty(source)).isFalse();
        assertThat(redis.hasKey(source.registryKey())).isTrue();
      } finally {
        deleteSource(redis, source);
      }
    }
  }

  private static void seedSource(StringRedisTemplate redis, SourceKeys source, String valuePrefix) {
    redis.opsForValue().set(source.registryKey(), source.sourcePrefix());
    redis.opsForValue().set(source.eventKey("event-1"), valuePrefix + "-event-1");
    redis.opsForValue().set(source.eventKey("orphan"), valuePrefix + "-orphan");
    redis.opsForZSet().add(source.updatedAtKey(), source.eventKey("event-1"), 11.0);
    redis.opsForZSet().add(source.staleAtKey(), source.eventKey("event-1"), 21.0);
  }

  private static void deleteSource(StringRedisTemplate redis, SourceKeys source) {
    redis.delete(
        List.of(
            source.registryKey(),
            source.eventKey("event-1"),
            source.eventKey("orphan"),
            source.eventKey("foreign-member"),
            source.eventKey("still-live"),
            source.updatedAtKey(),
            source.staleAtKey()));
  }

  private static Map<String, byte[]> payloads(
      RedisTemplate<String, byte[]> redis, SourceKeys source) {
    var payloads = new LinkedHashMap<String, byte[]>();
    for (var objectId : List.of("event-1", "orphan", "foreign-member")) {
      var value = redis.opsForValue().get(source.eventKey(objectId));
      if (value != null) {
        payloads.put(source.eventKey(objectId), value);
      }
    }
    return payloads;
  }

  private static Map<String, Double> scores(RedisTemplate<String, byte[]> redis, String key) {
    var scores = new LinkedHashMap<String, Double>();
    var tuples = redis.opsForZSet().rangeWithScores(key, 0, -1);
    if (tuples != null) {
      for (var tuple : tuples) {
        if (tuple.getValue() != null && tuple.getScore() != null) {
          scores.put(new String(tuple.getValue(), StandardCharsets.UTF_8), tuple.getScore());
        }
      }
    }
    return scores;
  }

  private static void assertPayloadsEqual(
      RedisTemplate<String, byte[]> redis, Map<String, byte[]> expectedPayloads) {
    for (var entry : expectedPayloads.entrySet()) {
      assertThat(redis.opsForValue().get(entry.getKey())).containsExactly(entry.getValue());
    }
  }
}
