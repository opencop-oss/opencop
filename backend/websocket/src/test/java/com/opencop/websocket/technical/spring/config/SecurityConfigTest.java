// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.technical.spring.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.junit.jupiter.api.Test;

class SecurityConfigTest {

  @Test
  void heartbeatExpirySchedulerReclaimsCancelledTasks() {
    var scheduler = new WebSocketSchedulers().wsHeartbeatExpiryScheduler();
    scheduler.initialize();

    try {
      var future =
          Objects.requireNonNull(
              scheduler.schedule(() -> {}, Instant.now().plus(Duration.ofHours(1))));
      assertThat(scheduler.getScheduledThreadPoolExecutor().getQueue()).hasSize(1);

      future.cancel(false);

      assertThat(scheduler.getScheduledThreadPoolExecutor().getQueue()).isEmpty();
    } finally {
      scheduler.shutdown();
    }
  }
}
