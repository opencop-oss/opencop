// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.history;

import com.opencop.processor.business.model.history.chunk.HistoricalChunkPlan;
import com.opencop.processor.ports.technical.gateway.history.HistoricalPlanGateway;
import com.opencop.processor.technical.spring.config.HistoricalExecutionProperties;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class RedisHistoricalPlanGateway implements HistoricalPlanGateway {

  private static final String KEY_PREFIX = "opencop:history:plan:";

  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;
  private final HistoricalExecutionProperties properties;

  public RedisHistoricalPlanGateway(
      StringRedisTemplate redisTemplate,
      ObjectMapper objectMapper,
      HistoricalExecutionProperties properties) {
    this.redisTemplate = redisTemplate;
    this.objectMapper = objectMapper;
    this.properties = properties;
  }

  @Override
  public void save(HistoricalChunkPlan plan) {
    try {
      String key = redisKey(plan.jobId());
      redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(plan));
      redisTemplate.expire(key, properties.getJobStateTtl());
    } catch (Exception e) {
      throw new IllegalStateException("Failed to persist historical chunk plan", e);
    }
  }

  @Override
  public Optional<HistoricalChunkPlan> findByJobId(UUID jobId) {
    try {
      String payload = redisTemplate.opsForValue().get(redisKey(jobId));
      if (payload == null || payload.isBlank()) {
        return Optional.empty();
      }
      return Optional.of(objectMapper.readValue(payload, HistoricalChunkPlan.class));
    } catch (Exception e) {
      throw new IllegalStateException("Failed to read historical chunk plan", e);
    }
  }

  static String redisKey(UUID jobId) {
    return KEY_PREFIX + jobId;
  }
}
