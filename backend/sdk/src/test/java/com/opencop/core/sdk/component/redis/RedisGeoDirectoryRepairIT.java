// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.component.redis;

import static org.assertj.core.api.Assertions.*;

import com.opencop.core.sdk.technical.gateway.cache.EventGeoCacheKeyLayout;
import com.opencop.core.sdk.technical.gateway.cache.redis.RedisEventGeoSourceDirectory;
import com.opencop.core.sdk.technical.gateway.cache.redis.RedisGeoDirectoryRepair;
import io.lettuce.core.cluster.api.async.RedisAdvancedClusterAsyncCommands;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnection;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

class RedisGeoDirectoryRepairIT {
  @Test
  @SuppressWarnings("unchecked")
  void repair_realCluster_recoversPartialWorkAndRejectsObservedTopologyChange() throws Exception {
    try (var fixture = LocalRedisClusterSupport.start();
        var context = RedisClusterCandidateContext.start(fixture)) {
      var layout = EventGeoCacheKeyLayout.CLUSTER;
      var directory = context.bean(RedisEventGeoSourceDirectory.class);
      var redis = context.stringTemplate();
      var sources =
          fixture.sourceIdsByPrimaryPort(s -> layout.sourceKeys(s).registryKey()).values();
      for (var source : sources)
        redis.opsForValue().set(layout.sourceKeys(source).registryKey(), "unused");
      redis.opsForValue().set("opencop:event:geo:{*}:registry", "malformed");
      var prefix = "opencop:event:geo:{bad".getBytes(StandardCharsets.UTF_8);
      var suffix = "}:registry".getBytes(StandardCharsets.UTF_8);
      var invalidUtf8Marker =
          ByteBuffer.allocate(prefix.length + 1 + suffix.length)
              .put(prefix)
              .put((byte) 0xff)
              .put(suffix)
              .array();
      var factory = context.bean(LettuceConnectionFactory.class);
      try (var spring = (LettuceConnection) factory.getConnection()) {
        var nativeCommands =
            (RedisAdvancedClusterAsyncCommands<byte[], byte[]>) spring.getNativeConnection();
        nativeCommands.set(invalidUtf8Marker, "unused".getBytes(StandardCharsets.UTF_8)).get();
      }
      for (int i = 0; i < 100; i++) redis.opsForValue().set("repair-noise:" + i, "noise");
      var repair = new RedisGeoDirectoryRepair(factory);
      var partial = new RedisGeoDirectoryRepair.Progress();
      assertThatThrownBy(
              () ->
                  repair.repair(
                      1,
                      1,
                      () -> {
                        if (partial.restored() > 0)
                          throw new InterruptedException("injected interruption");
                      },
                      partial))
          .isInstanceOf(InterruptedException.class);
      assertThat(partial.restored()).isPositive();
      assertThat(directory.discoverSources()).isNotEmpty();
      var progress = new RedisGeoDirectoryRepair.Progress();
      var pauses = new AtomicInteger();
      assertThat(repair.repair(1, 2, pauses::incrementAndGet, progress))
          .isEqualTo(RedisGeoDirectoryRepair.Outcome.COMPLETE);
      assertThat(pauses.get()).isEqualTo(progress.scans() - 1);
      assertThat(progress.malformed()).isGreaterThanOrEqualTo(2);
      assertThat(directory.discoverSources().stream().map(k -> k.sourceId()))
          .containsExactlyInAnyOrderElementsOf(sources)
          .doesNotContain("bad\uFFFD");
      var repeat = new RedisGeoDirectoryRepair.Progress();
      repair.repair(1000, 2, () -> {}, repeat);
      assertThat(repeat.restored()).isZero();
      // Model a change in the driver's observed topology, not actual Redis resharding/failover.
      try (var spring = (LettuceConnection) factory.getConnection()) {
        var nativeCommands =
            (RedisAdvancedClusterAsyncCommands<byte[], byte[]>) spring.getNativeConnection();
        var partitions = nativeCommands.getStatefulConnection().getPartitions();
        var node = partitions.iterator().next();
        var slots = new java.util.ArrayList<>(node.getSlots());
        var changed = new AtomicInteger();
        try {
          assertThat(
                  repair.repair(
                      1,
                      2,
                      () -> {
                        if (changed.getAndIncrement() == 0)
                          node.setSlots(slots.subList(1, slots.size()));
                      },
                      new RedisGeoDirectoryRepair.Progress()))
              .isEqualTo(RedisGeoDirectoryRepair.Outcome.TOPOLOGY_CHANGED);
        } finally {
          node.setSlots(slots);
        }
      }
      try (var connection = factory.getConnection()) {
        assertThat(connection.ping()).isEqualTo("PONG");
      }
    }
  }
}
