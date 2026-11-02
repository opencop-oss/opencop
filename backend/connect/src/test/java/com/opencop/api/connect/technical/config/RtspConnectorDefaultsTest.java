// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.technical.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class RtspConnectorDefaultsTest {

  @Test
  void defaultsMatchCurrentRtspRuntimePolicy() {
    var defaults = RtspConnectorDefaults.defaults();

    assertThat(defaults.connectTimeout()).isEqualTo(Duration.ofSeconds(30));
    assertThat(defaults.readTimeout()).isEqualTo(Duration.ofSeconds(60));
    assertThat(defaults.preferTcp()).isTrue();
    assertThat(defaults.connectTimeoutMicros()).isEqualTo(30_000_000);
    assertThat(defaults.readTimeoutMicros()).isEqualTo(60_000_000);
  }

  @Test
  void rejectsNonPositiveTimeouts() {
    assertThatThrownBy(() -> new RtspConnectorDefaults(Duration.ZERO, Duration.ofSeconds(60), true))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("connectTimeout");

    assertThatThrownBy(() -> new RtspConnectorDefaults(Duration.ofSeconds(30), Duration.ZERO, true))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("readTimeout");
  }
}
