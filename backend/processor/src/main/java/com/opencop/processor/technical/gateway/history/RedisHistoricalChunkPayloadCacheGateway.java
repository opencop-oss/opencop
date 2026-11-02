// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.history;

import com.opencop.processor.ports.technical.gateway.history.HistoricalChunkPayloadCacheGateway;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisHistoricalChunkPayloadCacheGateway implements HistoricalChunkPayloadCacheGateway {

  private static final long REDIS_KEY_NOT_PRESENT = -2L;
  private static final long REDIS_KEY_NO_EXPIRY = -1L;

  private final RedisTemplate<String, byte[]> redisTemplate;

  public RedisHistoricalChunkPayloadCacheGateway(RedisTemplate<String, byte[]> redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  @Override
  public Map<String, Optional<Duration>> remainingPayloadTtl(Set<String> payloadKeys) {
    Map<String, Optional<Duration>> result = new LinkedHashMap<>();
    for (String payloadKey : payloadKeys) {
      Long ttlMillis = redisTemplate.getExpire(payloadKey, TimeUnit.MILLISECONDS);
      if (ttlMillis == null || ttlMillis == REDIS_KEY_NOT_PRESENT) {
        continue;
      }
      if (ttlMillis == REDIS_KEY_NO_EXPIRY) {
        result.put(payloadKey, Optional.empty());
      } else {
        result.put(payloadKey, Optional.of(Duration.ofMillis(ttlMillis)));
      }
    }
    return result;
  }

  @Override
  public void put(String payloadKey, byte[] payloadBytes, Duration ttl) {
    redisTemplate.opsForValue().set(payloadKey, payloadBytes, ttl);
  }

  @Override
  public Optional<byte[]> find(String payloadKey) {
    return Optional.ofNullable(redisTemplate.opsForValue().get(payloadKey));
  }

  @Override
  public Map<String, Optional<byte[]>> findAll(List<String> payloadKeys) {
    Map<String, Optional<byte[]>> result = new LinkedHashMap<>();
    for (String payloadKey : payloadKeys) {
      result.put(payloadKey, find(payloadKey));
    }
    return result;
  }
}
