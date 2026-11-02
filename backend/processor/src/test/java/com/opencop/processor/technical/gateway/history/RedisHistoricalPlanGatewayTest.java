// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.history;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.opencop.core.sdk.model.SubjectType;
import com.opencop.core.sdk.model.history.HistoricalResolution;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkCacheStatus;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkPlan;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkPlanEntry;
import com.opencop.processor.technical.spring.config.HistoricalExecutionProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

class RedisHistoricalPlanGatewayTest {

  private final HistoricalExecutionProperties properties = new HistoricalExecutionProperties();
  private RecordingStringRedisTemplate redisTemplate;
  private RedisHistoricalPlanGateway repository;

  @BeforeEach
  void setUp() {
    redisTemplate = new RecordingStringRedisTemplate();
    properties.setJobStateTtl(Duration.ofHours(24));
    repository = new RedisHistoricalPlanGateway(redisTemplate, new ObjectMapper(), properties);
  }

  @Test
  void save_persistsPlanWithConfiguredTtl() {
    var jobId = UUID.randomUUID();
    var plan =
        new HistoricalChunkPlan(
            HistoricalChunkPlan.PLANNING_VERSION,
            UUID.randomUUID(),
            jobId,
            SubjectType.SOURCE,
            UUID.randomUUID(),
            UUID.randomUUID(),
            Instant.parse("2026-04-06T10:02:17Z"),
            Instant.parse("2026-04-06T12:13:02Z"),
            Instant.parse("2026-04-06T10:02:00Z"),
            Instant.parse("2026-04-06T12:14:00Z"),
            HistoricalResolution.MINUTE_1,
            Instant.parse("2026-04-07T10:00:00Z"),
            2,
            1,
            1,
            List.of(
                new HistoricalChunkPlanEntry(
                    0,
                    "opencop:history:chunk:SOURCE:abc:MINUTE_1:v1:2026-04-06T10:02:00Z",
                    "opencop:history:payload:SOURCE:abc:MINUTE_1:v1:v1_latest_in_bucket:filters:2026-04-06T10:02:00Z",
                    "opencop:history:chunk-state:SOURCE:abc:MINUTE_1:v1:v1_latest_in_bucket:filters:2026-04-06T10:02:00Z",
                    HistoricalChunkCacheStatus.CACHED,
                    Instant.parse("2026-04-06T10:02:00Z"),
                    Instant.parse("2026-04-06T10:03:00Z")),
                new HistoricalChunkPlanEntry(
                    1,
                    "opencop:history:chunk:SOURCE:abc:MINUTE_1:v1:2026-04-06T10:03:00Z",
                    "opencop:history:payload:SOURCE:abc:MINUTE_1:v1:v1_latest_in_bucket:filters:2026-04-06T10:03:00Z",
                    "opencop:history:chunk-state:SOURCE:abc:MINUTE_1:v1:v1_latest_in_bucket:filters:2026-04-06T10:03:00Z",
                    HistoricalChunkCacheStatus.MISSING,
                    Instant.parse("2026-04-06T10:03:00Z"),
                    Instant.parse("2026-04-06T10:04:00Z"))));

    repository.save(plan);

    var persisted = repository.findByJobId(jobId).orElseThrow();
    assertEquals(2, persisted.chunks());
    assertEquals(1, persisted.cachedChunks());
    assertEquals(1, persisted.chunksToCompute());
    assertEquals(2, persisted.entries().size());
    assertEquals(
        Duration.ofHours(24),
        redisTemplate.expirations.get(RedisHistoricalPlanGateway.redisKey(jobId)));
  }

  private static final class RecordingStringRedisTemplate extends StringRedisTemplate {

    private final Map<String, String> values = new HashMap<>();
    private final Map<String, Duration> expirations = new HashMap<>();

    @Override
    @SuppressWarnings("unchecked")
    public org.springframework.data.redis.core.ValueOperations<String, String> opsForValue() {
      return (org.springframework.data.redis.core.ValueOperations<String, String>)
          java.lang.reflect.Proxy.newProxyInstance(
              getClass().getClassLoader(),
              new Class<?>[] {org.springframework.data.redis.core.ValueOperations.class},
              (proxy, method, args) -> {
                return switch (method.getName()) {
                  case "set" -> {
                    values.put((String) args[0], (String) args[1]);
                    yield null;
                  }
                  case "get" -> values.get(args[0]);
                  default -> throw new UnsupportedOperationException(method.getName());
                };
              });
    }

    @Override
    public Boolean expire(String key, Duration timeout) {
      expirations.put(key, timeout);
      return true;
    }
  }
}
