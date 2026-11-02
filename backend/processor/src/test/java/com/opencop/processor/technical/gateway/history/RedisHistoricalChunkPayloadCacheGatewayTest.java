// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.history;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RedisHistoricalChunkPayloadCacheGatewayTest {

  @Test
  void remainingPayloadTtl_reportsTtlPerKey() {
    var redisTemplate = new RecordingRedisTemplate();
    redisTemplate.ttlMillis.put("payload-fresh", 600_000L);
    redisTemplate.ttlMillis.put("payload-no-expiry", -1L);
    redisTemplate.ttlMillis.put("payload-missing", -2L);

    var gateway = new RedisHistoricalChunkPayloadCacheGateway(redisTemplate);

    var result =
        gateway.remainingPayloadTtl(
            Set.of("payload-fresh", "payload-no-expiry", "payload-missing"));

    assertEquals(Optional.of(Duration.ofMillis(600_000L)), result.get("payload-fresh"));
    assertEquals(Optional.empty(), result.get("payload-no-expiry"));
    assertFalse(result.containsKey("payload-missing"));
  }

  @Test
  void putAndFind_storePayloadBytesWithConfiguredTtl() {
    var redisTemplate = new RecordingRedisTemplate();
    var gateway = new RedisHistoricalChunkPayloadCacheGateway(redisTemplate);
    byte[] payload = new byte[] {1, 2, 3};

    gateway.put("payload-a", payload, Duration.ofMinutes(5));
    Optional<byte[]> stored = gateway.find("payload-a");

    assertTrue(stored.isPresent());
    assertArrayEquals(payload, stored.orElseThrow());
    assertEquals(Duration.ofMinutes(5), redisTemplate.expirations.get("payload-a"));
  }

  private static final class RecordingRedisTemplate extends RedisTemplate<String, byte[]> {

    private final Map<String, Long> ttlMillis = new HashMap<>();
    private final Map<String, byte[]> values = new HashMap<>();
    private final Map<String, Duration> expirations = new HashMap<>();

    @Override
    public Long getExpire(String key, TimeUnit timeUnit) {
      Long stored = ttlMillis.get(key);
      if (stored == null) {
        return -2L;
      }
      if (timeUnit == TimeUnit.MILLISECONDS) {
        return stored;
      }
      throw new UnsupportedOperationException("only millis are supported in the stub");
    }

    @Override
    @SuppressWarnings("unchecked")
    public ValueOperations<String, byte[]> opsForValue() {
      return (ValueOperations<String, byte[]>)
          java.lang.reflect.Proxy.newProxyInstance(
              getClass().getClassLoader(),
              new Class<?>[] {ValueOperations.class},
              (proxy, method, args) -> {
                return switch (method.getName()) {
                  case "set" -> {
                    values.put((String) args[0], (byte[]) args[1]);
                    expirations.put((String) args[0], (Duration) args[2]);
                    yield null;
                  }
                  case "get" -> values.get((String) args[0]);
                  default -> throw new UnsupportedOperationException(method.getName());
                };
              });
    }
  }
}
