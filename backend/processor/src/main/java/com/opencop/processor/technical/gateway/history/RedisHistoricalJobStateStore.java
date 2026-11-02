// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.history;

import com.opencop.core.sdk.technical.gateway.history.HistoricalJobControlKeyLayout;
import com.opencop.processor.technical.gateway.history.model.job.HistoricalJobState;
import com.opencop.processor.technical.spring.config.HistoricalExecutionProperties;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;

final class RedisHistoricalJobStateStore {

  private final StringRedisTemplate redisTemplate;
  private final HistoricalJobControlKeyLayout keyLayout;
  private final HistoricalExecutionProperties properties;

  /**
   * Creates a standalone adapter for direct test construction. Production callers use the
   * constructor accepting the topology-selected key layout.
   */
  RedisHistoricalJobStateStore(
      StringRedisTemplate redisTemplate, HistoricalExecutionProperties properties) {
    this(redisTemplate, HistoricalJobControlKeyLayout.STANDALONE, properties);
  }

  RedisHistoricalJobStateStore(
      StringRedisTemplate redisTemplate,
      HistoricalJobControlKeyLayout keyLayout,
      HistoricalExecutionProperties properties) {
    this.redisTemplate = redisTemplate;
    this.keyLayout = Objects.requireNonNull(keyLayout, "keyLayout");
    this.properties = properties;
  }

  void persistWithTtl(HistoricalJobState state) {
    String key = keyLayout.jobState(state.jobId());
    redisTemplate.opsForHash().putAll(key, state.toRedisHash());
    redisTemplate.expire(key, properties.getJobStateTtl());
  }

  Optional<HistoricalJobState> findByJobId(UUID jobId) {
    Map<Object, Object> entries = redisTemplate.opsForHash().entries(keyLayout.jobState(jobId));
    if (entries == null || entries.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(HistoricalJobState.fromRedisHash(entries));
  }
}
