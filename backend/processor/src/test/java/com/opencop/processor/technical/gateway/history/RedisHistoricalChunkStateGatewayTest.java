// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.history;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.opencop.core.sdk.model.SubjectType;
import com.opencop.core.sdk.model.history.HistoricalResolution;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkCacheStatus;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkPlan;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkPlanEntry;
import com.opencop.processor.technical.gateway.history.model.chunk.HistoricalChunkStateStatus;
import com.opencop.processor.technical.spring.config.HistoricalExecutionProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

class RedisHistoricalChunkStateGatewayTest {

  private final HistoricalExecutionProperties properties = new HistoricalExecutionProperties();
  private final Clock clock = Clock.fixed(Instant.parse("2026-04-07T12:00:00Z"), ZoneOffset.UTC);

  private RedisHistoricalChunkStateGateway gateway;
  private RecordingStringRedisTemplate redisTemplate;

  @BeforeEach
  void setUp() {
    redisTemplate = new RecordingStringRedisTemplate();
    properties.setJobStateTtl(Duration.ofHours(24));
    gateway = new RedisHistoricalChunkStateGateway(redisTemplate, properties, clock);
  }

  @Test
  void initialize_persistsChunkStatesWithConfiguredTtl() {
    var chunkStateKey =
        "opencop:history:chunk-state:SOURCE:abc:MINUTE_1:v1:v1_latest_in_bucket:filters:2026-04-06T10:02:00Z";
    var plan =
        new HistoricalChunkPlan(
            HistoricalChunkPlan.PLANNING_VERSION,
            UUID.randomUUID(),
            UUID.randomUUID(),
            SubjectType.SOURCE,
            UUID.randomUUID(),
            UUID.randomUUID(),
            Instant.parse("2026-04-06T10:02:17Z"),
            Instant.parse("2026-04-06T12:13:02Z"),
            Instant.parse("2026-04-06T10:02:00Z"),
            Instant.parse("2026-04-06T12:14:00Z"),
            HistoricalResolution.MINUTE_1,
            Instant.parse("2026-04-07T10:00:00Z"),
            1,
            1,
            0,
            List.of(
                new HistoricalChunkPlanEntry(
                    0,
                    "opencop:history:chunk:SOURCE:abc:MINUTE_1:v1:2026-04-06T10:02:00Z",
                    "opencop:history:payload:SOURCE:abc:MINUTE_1:v1:v1_latest_in_bucket:filters:2026-04-06T10:02:00Z",
                    chunkStateKey,
                    HistoricalChunkCacheStatus.CACHED,
                    Instant.parse("2026-04-06T10:02:00Z"),
                    Instant.parse("2026-04-06T10:03:00Z"))));

    gateway.initialize(plan);

    var state = gateway.findByChunkStateKey(chunkStateKey).orElseThrow();
    assertEquals(HistoricalChunkStateStatus.CACHED, state.status());
    assertEquals(1, state.partCount());
    assertEquals(Duration.ofHours(24), redisTemplate.expirations.get(chunkStateKey));
  }

  @Test
  void transition_updatesStatusAndRefreshesTtl() {
    var chunkStateKey =
        "opencop:history:chunk-state:SOURCE:def:MINUTE_1:v1:v1_latest_in_bucket:filters:2026-04-06T10:03:00Z";
    var plan =
        new HistoricalChunkPlan(
            HistoricalChunkPlan.PLANNING_VERSION,
            UUID.randomUUID(),
            UUID.randomUUID(),
            SubjectType.SOURCE,
            UUID.randomUUID(),
            UUID.randomUUID(),
            Instant.parse("2026-04-06T10:03:17Z"),
            Instant.parse("2026-04-06T12:13:02Z"),
            Instant.parse("2026-04-06T10:03:00Z"),
            Instant.parse("2026-04-06T12:14:00Z"),
            HistoricalResolution.MINUTE_1,
            Instant.parse("2026-04-07T10:00:00Z"),
            1,
            0,
            1,
            List.of(
                new HistoricalChunkPlanEntry(
                    0,
                    "opencop:history:chunk:SOURCE:def:MINUTE_1:v1:2026-04-06T10:03:00Z",
                    "opencop:history:payload:SOURCE:def:MINUTE_1:v1:v1_latest_in_bucket:filters:2026-04-06T10:03:00Z",
                    chunkStateKey,
                    HistoricalChunkCacheStatus.MISSING,
                    Instant.parse("2026-04-06T10:03:00Z"),
                    Instant.parse("2026-04-06T10:04:00Z"))));
    gateway.initialize(plan);

    gateway.transition(chunkStateKey, HistoricalChunkStateStatus.RECOMPUTE_NEEDED);

    var state = gateway.findByChunkStateKey(chunkStateKey).orElseThrow();
    assertEquals(HistoricalChunkStateStatus.RECOMPUTE_NEEDED, state.status());
    assertEquals(Duration.ofHours(24), redisTemplate.expirations.get(chunkStateKey));
  }

  @Test
  void updatePartCount_persistsPartCountAndRefreshesTtl() {
    var chunkStateKey =
        "opencop:history:chunk-state:SOURCE:def:MINUTE_1:v1:v1_latest_in_bucket:filters:2026-04-06T10:03:00Z";
    var plan =
        new HistoricalChunkPlan(
            HistoricalChunkPlan.PLANNING_VERSION,
            UUID.randomUUID(),
            UUID.randomUUID(),
            SubjectType.SOURCE,
            UUID.randomUUID(),
            UUID.randomUUID(),
            Instant.parse("2026-04-06T10:03:17Z"),
            Instant.parse("2026-04-06T12:13:02Z"),
            Instant.parse("2026-04-06T10:03:00Z"),
            Instant.parse("2026-04-06T12:14:00Z"),
            HistoricalResolution.MINUTE_1,
            Instant.parse("2026-04-07T10:00:00Z"),
            1,
            0,
            1,
            List.of(
                new HistoricalChunkPlanEntry(
                    0,
                    "opencop:history:chunk:SOURCE:def:MINUTE_1:v1:2026-04-06T10:03:00Z",
                    "opencop:history:payload:SOURCE:def:MINUTE_1:v1:v1_latest_in_bucket:filters:2026-04-06T10:03:00Z",
                    chunkStateKey,
                    HistoricalChunkCacheStatus.MISSING,
                    Instant.parse("2026-04-06T10:03:00Z"),
                    Instant.parse("2026-04-06T10:04:00Z"))));
    gateway.initialize(plan);

    gateway.updatePartCount(chunkStateKey, 4);

    var state = gateway.findByChunkStateKey(chunkStateKey).orElseThrow();
    assertEquals(4, state.partCount());
    assertEquals(Duration.ofHours(24), redisTemplate.expirations.get(chunkStateKey));
  }

  private static final class RecordingStringRedisTemplate extends StringRedisTemplate {

    private final Map<String, Map<Object, Object>> hashes = new HashMap<>();
    private final Map<String, Duration> expirations = new HashMap<>();

    @Override
    @SuppressWarnings("unchecked")
    public org.springframework.data.redis.core.HashOperations<String, Object, Object> opsForHash() {
      return (org.springframework.data.redis.core.HashOperations<String, Object, Object>)
          java.lang.reflect.Proxy.newProxyInstance(
              getClass().getClassLoader(),
              new Class<?>[] {org.springframework.data.redis.core.HashOperations.class},
              (proxy, method, args) -> {
                return switch (method.getName()) {
                  case "putAll" -> {
                    hashes.put((String) args[0], new HashMap<>((Map<?, ?>) args[1]));
                    yield null;
                  }
                  case "entries" -> new HashMap<>(hashes.getOrDefault((String) args[0], Map.of()));
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
