// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.component.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class LocalRedisSupportTest {

  @AfterEach
  void clearRedisProperties() {
    System.clearProperty("opencop.test.redis.host");
    System.clearProperty("opencop.test.redis.port");
  }

  @Test
  void usesConfiguredRedisEndpointWithoutStartingContainer() {
    System.setProperty("opencop.test.redis.host", "redis");
    System.setProperty("opencop.test.redis.port", "6379");

    assertThat(LocalRedisSupport.host()).isEqualTo("redis");
    assertThat(LocalRedisSupport.port()).isEqualTo(6379);
  }

  @Test
  void rejectsInvalidConfiguredRedisPort() {
    System.setProperty("opencop.test.redis.host", "redis");
    System.setProperty("opencop.test.redis.port", "not-a-port");

    assertThatThrownBy(LocalRedisSupport::port)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("not an integer");
  }

  @Test
  void rejectsPartialConfiguredRedisEndpoint() {
    System.setProperty("opencop.test.redis.host", "redis");

    assertThatThrownBy(LocalRedisSupport::port)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Configure both");
  }
}
