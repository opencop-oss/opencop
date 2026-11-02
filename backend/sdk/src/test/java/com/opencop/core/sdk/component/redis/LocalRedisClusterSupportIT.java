// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.component.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.images.builder.Transferable;

class LocalRedisClusterSupportIT {

  @Test
  void start_readinessCommandTimesOut_removesOwnedContainerAndAllowsFreshFixture() {
    var containerId = new AtomicReference<String>();
    var readinessStarted = new AtomicLong();

    assertThatThrownBy(
            () ->
                LocalRedisClusterSupport.start(
                    Duration.ofSeconds(1),
                    container -> {
                      containerId.set(container.getContainerId());
                      // Cluster setup has completed. Stall only the subsequent readiness exec;
                      // the outer one-second budget must override the normal command timeout.
                      container.copyFileToContainer(
                          Transferable.of("#!/bin/sh\nexec sleep 60\n", 07_55),
                          "/usr/local/bin/redis-cli");
                      readinessStarted.set(System.nanoTime());
                    }))
        .isInstanceOf(LocalRedisClusterSupport.ExecTimeoutException.class)
        .hasMessageContaining("redis-cli -p 17100 cluster info");

    assertThat(readinessStarted.get()).isPositive();
    assertThat(Duration.ofNanos(System.nanoTime() - readinessStarted.get()))
        .isLessThan(Duration.ofSeconds(20));
    assertThat(containerId.get()).isNotBlank();
    var client = DockerClientFactory.instance().client();
    assertThat(client.listContainersCmd().withShowAll(true).exec())
        .noneMatch(container -> container.getId().equals(containerId.get()));

    // Reuse verifies release of both the interprocess ownership lock and fixed host ports.
    try (var fixture = LocalRedisClusterSupport.start();
        var context = RedisClusterCandidateContext.start(fixture)) {
      try (var connection = context.bean(LettuceConnectionFactory.class).getConnection()) {
        assertThat(connection.ping()).isEqualTo("PONG");
      }
    }
  }
}
