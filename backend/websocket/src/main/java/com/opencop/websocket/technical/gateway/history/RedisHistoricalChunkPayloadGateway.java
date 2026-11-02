// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.technical.gateway.history;

import com.opencop.websocket.ports.gateway.history.HistoricalChunkPayloadGateway;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.redis.core.RedisTemplate;

public class RedisHistoricalChunkPayloadGateway implements HistoricalChunkPayloadGateway {

  private final RedisTemplate<String, byte[]> redisTemplate;

  public RedisHistoricalChunkPayloadGateway(RedisTemplate<String, byte[]> redisTemplate) {
    this.redisTemplate = redisTemplate;
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
