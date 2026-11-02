// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.history;

import com.opencop.processor.business.model.history.chunk.HistoricalChunkCacheStatus;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkPlan;
import com.opencop.processor.ports.technical.gateway.history.HistoricalChunkStateGateway;
import com.opencop.processor.technical.gateway.history.model.chunk.HistoricalChunkState;
import com.opencop.processor.technical.gateway.history.model.chunk.HistoricalChunkStateStatus;
import com.opencop.processor.technical.spring.config.HistoricalExecutionProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisHistoricalChunkStateGateway implements HistoricalChunkStateGateway {

  private final StringRedisTemplate redisTemplate;
  private final HashOperations<String, Object, Object> hashOperations;
  private final HistoricalExecutionProperties properties;
  private final Clock clock;

  public RedisHistoricalChunkStateGateway(
      StringRedisTemplate redisTemplate, HistoricalExecutionProperties properties, Clock clock) {
    this.redisTemplate = redisTemplate;
    this.hashOperations = redisTemplate.opsForHash();
    this.properties = properties;
    this.clock = clock;
  }

  @Override
  public void initialize(HistoricalChunkPlan plan) {
    Instant now = Instant.now(clock);
    for (var entry : plan.entries()) {
      HistoricalChunkStateStatus initialStatus =
          entry.cacheStatus() == null || entry.cacheStatus() == HistoricalChunkCacheStatus.MISSING
              ? HistoricalChunkStateStatus.PLANNED
              : HistoricalChunkStateStatus.CACHED;
      HistoricalChunkState state =
          HistoricalChunkState.initialize(
              plan.jobId(),
              entry.chunkIndex(),
              entry.chunkKey(),
              entry.payloadKey(),
              entry.chunkStateKey(),
              1,
              initialStatus,
              entry.from(),
              entry.to(),
              now,
              properties.getJobStateTtl());
      String key = entry.chunkStateKey();
      hashOperations.putAll(key, state.toRedisHash());
      redisTemplate.expire(key, properties.getJobStateTtl());
    }
  }

  @Override
  public Optional<HistoricalChunkState> findByChunkStateKey(String chunkStateKey) {
    Map<Object, Object> entries = hashOperations.entries(chunkStateKey);
    if (entries == null || entries.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(HistoricalChunkState.fromRedisHash(entries));
  }

  @Override
  public void transition(String chunkStateKey, HistoricalChunkStateStatus status) {
    findByChunkStateKey(chunkStateKey)
        .ifPresent(
            state ->
                persist(
                    chunkStateKey,
                    state.update(status, Instant.now(clock), properties.getJobStateTtl())));
  }

  @Override
  public void updatePartCount(String chunkStateKey, int partCount) {
    findByChunkStateKey(chunkStateKey)
        .ifPresent(
            state ->
                persist(
                    chunkStateKey,
                    state.updatePartCount(
                        partCount, Instant.now(clock), properties.getJobStateTtl())));
  }

  private void persist(String key, HistoricalChunkState state) {
    hashOperations.putAll(key, state.toRedisHash());
    redisTemplate.expire(key, properties.getJobStateTtl());
  }
}
