// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.component.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.opencop.core.sdk.technical.gateway.cache.EventGeoCacheKeyLayout;
import com.opencop.core.sdk.technical.gateway.cache.redis.RedisEventGeoSourceDirectory;
import java.util.List;
import org.junit.jupiter.api.Test;

class RedisEventGeoSourceDirectoryIT {

  @Test
  void clusterDirectory_retainsMembersAfterEmptyMarkersRetire() {
    try (var fixture = LocalRedisClusterSupport.start();
        var candidate = RedisClusterCandidateContext.start(fixture)) {
      var layout = candidate.bean(EventGeoCacheKeyLayout.class);
      var directory = candidate.bean(RedisEventGeoSourceDirectory.class);
      assertThat(layout).isEqualTo(EventGeoCacheKeyLayout.CLUSTER);
      assertThat(directory.layout()).isEqualTo(EventGeoCacheKeyLayout.CLUSTER);

      var sourceIdsByPrimaryPort =
          fixture.sourceIdsByPrimaryPort(sourceId -> layout.sourceKeys(sourceId).registryKey());
      assertThat(sourceIdsByPrimaryPort).hasSize(3);
      assertThat(sourceIdsByPrimaryPort.keySet()).containsOnlyOnce(fixture.seedPort());
      assertThat(sourceIdsByPrimaryPort.values()).doesNotHaveDuplicates();
      assertThatExceptionOfType(UnsupportedOperationException.class)
          .isThrownBy(() -> sourceIdsByPrimaryPort.put(0, "not-immutable"));

      var redis = candidate.stringTemplate();
      var sourceKeys = sourceIdsByPrimaryPort.values().stream().map(layout::sourceKeys).toList();
      try {
        directory.registerPreparedSources(sourceKeys);
        for (var keys : sourceKeys) {
          redis.opsForValue().set(keys.registryKey(), keys.sourcePrefix());
          assertThat(redis.opsForValue().get(keys.registryKey())).isEqualTo(keys.sourcePrefix());
          assertThat(redis.getExpire(keys.registryKey())).isEqualTo(-1L);
        }

        assertThat(directory.discoverSources())
            .containsExactlyInAnyOrderElementsOf(sourceKeys)
            .hasSize(3);

        var live = sourceKeys.getFirst();
        redis.opsForZSet().add(live.updatedAtKey(), live.eventKey("object-1"), 1.0d);
        assertThat(directory.unregisterIfEmpty(live)).isFalse();
        assertThat(redis.hasKey(live.registryKey())).isTrue();
        assertThat(directory.discoverSources()).containsExactlyInAnyOrderElementsOf(sourceKeys);

        redis.delete(live.updatedAtKey());
        assertThat(directory.unregisterIfEmpty(live)).isTrue();
        assertThat(redis.hasKey(live.registryKey())).isFalse();
        assertThat(directory.unregisterIfEmpty(live)).isFalse();

        assertThat(directory.discoverSources()).containsExactlyInAnyOrderElementsOf(sourceKeys);
      } finally {
        for (var keys : sourceKeys) {
          redis.delete(List.of(keys.registryKey(), keys.updatedAtKey(), keys.staleAtKey()));
        }
      }
    }
  }
}
