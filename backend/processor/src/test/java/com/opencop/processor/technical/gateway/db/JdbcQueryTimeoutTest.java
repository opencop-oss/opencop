// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class JdbcQueryTimeoutTest {

  @Test
  void secondsRoundsPositiveFractionalDurationsUp() {
    assertThat(JdbcQueryTimeout.seconds(Duration.ofMillis(500))).isEqualTo(1);
    assertThat(JdbcQueryTimeout.seconds(Duration.ofMillis(1_500))).isEqualTo(2);
    assertThat(JdbcQueryTimeout.seconds(Duration.ofSeconds(5))).isEqualTo(5);
  }

  @Test
  void secondsRejectsDurationsThatRoundPastJdbcRange() {
    assertThat(JdbcQueryTimeout.fitsSeconds(Duration.ofSeconds(Integer.MAX_VALUE))).isTrue();
    assertThat(JdbcQueryTimeout.fitsSeconds(Duration.ofSeconds(Integer.MAX_VALUE, 1))).isFalse();
    assertThatThrownBy(() -> JdbcQueryTimeout.seconds(Duration.ofSeconds(Integer.MAX_VALUE, 1)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("fit int");
  }

  @Test
  void secondsRejectsNonPositiveDurations() {
    assertThatThrownBy(() -> JdbcQueryTimeout.seconds(Duration.ZERO))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("greater than zero");
    assertThatThrownBy(() -> JdbcQueryTimeout.seconds(Duration.ofMillis(-1)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("greater than zero");
  }
}
