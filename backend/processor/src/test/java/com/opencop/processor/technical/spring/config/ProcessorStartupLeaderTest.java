// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.spring.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class ProcessorStartupLeaderTest {

  @Test
  void disabledElectionAssumesLeaderWithoutRedisCall() {
    var redis = mock(StringRedisTemplate.class);

    var leader = new ProcessorStartupLeader(redis, ProcessorStartupLeader.DEFAULT_LOCK_KEY, false);

    assertThat(leader.isLeader()).isTrue();
    verify(redis, never()).opsForValue();
  }

  @Test
  void enabledElectionUsesSetIfAbsentResult() {
    var redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    ValueOperations<String, String> values = mock(ValueOperations.class);
    when(redis.opsForValue()).thenReturn(values);
    when(values.setIfAbsent(
            eq(ProcessorStartupLeader.DEFAULT_LOCK_KEY),
            any(String.class),
            eq(Duration.ofMinutes(5))))
        .thenReturn(false);

    var leader = new ProcessorStartupLeader(redis, ProcessorStartupLeader.DEFAULT_LOCK_KEY, true);

    assertThat(leader.isLeader()).isFalse();
  }

  @Test
  void redisFailureFailsOpen() {
    var redis = mock(StringRedisTemplate.class);
    when(redis.opsForValue()).thenThrow(new IllegalStateException("redis unavailable"));

    var leader = new ProcessorStartupLeader(redis, ProcessorStartupLeader.DEFAULT_LOCK_KEY, true);

    assertThat(leader.isLeader()).isTrue();
  }
}
