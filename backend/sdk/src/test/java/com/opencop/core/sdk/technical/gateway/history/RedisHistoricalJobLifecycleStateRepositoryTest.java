// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

class RedisHistoricalJobLifecycleStateRepositoryTest {

  @Test
  void markCompleted_updatesPrimaryHistoricalJobHash() {
    var jobId = UUID.fromString("550e8400-e29b-41d4-a716-446655440111");
    var key = HistoricalJobControlKeyLayout.STANDALONE.jobState(jobId);
    var redisTemplate = new RecordingStringRedisTemplate();
    redisTemplate.putHash(
        key, Map.of("status", "RUNNING", "stage", "EXECUTION_RUNNING", "updatedAt", "before"));
    var repository = new RedisHistoricalJobLifecycleStateRepository(redisTemplate);

    boolean changed = repository.markCompleted(jobId, Duration.ofMinutes(5));

    assertTrue(changed);
    assertEquals("opencop:history:job:" + jobId, key);
    assertEquals("COMPLETED", redisTemplate.getHash(key, "status"));
    assertEquals("EXECUTION_COMPLETED", redisTemplate.getHash(key, "stage"));
    assertEquals(Duration.ofMinutes(5), redisTemplate.expirations.get(key));
  }

  @Test
  void markCompleted_usesSelectedClusterControlKeyLayout() {
    var jobId = UUID.fromString("550e8400-e29b-41d4-a716-446655440119");
    var key = HistoricalJobControlKeyLayout.CLUSTER.jobState(jobId);
    var redisTemplate = new RecordingStringRedisTemplate();
    redisTemplate.putHash(
        key, Map.of("status", "RUNNING", "stage", "EXECUTION_RUNNING", "updatedAt", "before"));
    var repository =
        new RedisHistoricalJobLifecycleStateRepository(
            redisTemplate, HistoricalJobControlKeyLayout.CLUSTER);

    assertTrue(repository.markCompleted(jobId, Duration.ofMinutes(5)));
    assertEquals("COMPLETED", redisTemplate.getHash(key, "status"));
    assertEquals(Duration.ofMinutes(5), redisTemplate.expirations.get(key));
  }

  @Test
  void markFailed_rejectsAlreadyFailedJobHash() {
    var jobId = UUID.fromString("550e8400-e29b-41d4-a716-446655440112");
    var key = HistoricalJobControlKeyLayout.STANDALONE.jobState(jobId);
    var redisTemplate = new RecordingStringRedisTemplate();
    redisTemplate.putHash(
        key,
        Map.of(
            "status",
            "FAILED",
            "stage",
            "TIMEOUT",
            "failureReason",
            "processor timeout",
            "updatedAt",
            "before"));
    var repository = new RedisHistoricalJobLifecycleStateRepository(redisTemplate);

    boolean changed =
        repository.markFailed(jobId, "stale websocket failure", Duration.ofMinutes(5));

    assertFalse(changed);
    assertEquals("FAILED", redisTemplate.getHash(key, "status"));
    assertEquals("TIMEOUT", redisTemplate.getHash(key, "stage"));
    assertEquals("processor timeout", redisTemplate.getHash(key, "failureReason"));
    assertEquals("before", redisTemplate.getHash(key, "updatedAt"));
    assertFalse(redisTemplate.expirations.containsKey(key));
  }

  @Test
  void markFailed_rejectsCancelledJobHash() {
    var jobId = UUID.fromString("550e8400-e29b-41d4-a716-446655440113");
    var key = HistoricalJobControlKeyLayout.STANDALONE.jobState(jobId);
    var redisTemplate = new RecordingStringRedisTemplate();
    redisTemplate.putHash(
        key,
        Map.of(
            "status",
            "CANCELLED",
            "stage",
            "CANCELLED",
            "failureReason",
            "user cancelled",
            "updatedAt",
            "before"));
    var repository = new RedisHistoricalJobLifecycleStateRepository(redisTemplate);

    boolean changed =
        repository.markFailed(jobId, "stale websocket failure", Duration.ofMinutes(5));

    assertFalse(changed);
    assertEquals("CANCELLED", redisTemplate.getHash(key, "status"));
    assertEquals("CANCELLED", redisTemplate.getHash(key, "stage"));
    assertEquals("user cancelled", redisTemplate.getHash(key, "failureReason"));
    assertEquals("before", redisTemplate.getHash(key, "updatedAt"));
    assertFalse(redisTemplate.expirations.containsKey(key));
  }

  @Test
  void markFailed_rejectsCancellationRequestedJobHash() {
    var jobId = UUID.fromString("550e8400-e29b-41d4-a716-446655440114");
    var key = HistoricalJobControlKeyLayout.STANDALONE.jobState(jobId);
    var redisTemplate = new RecordingStringRedisTemplate();
    redisTemplate.putHash(
        key,
        Map.of(
            "status",
            "RUNNING",
            "stage",
            "CANCELLATION_REQUESTED",
            "failureReason",
            "user requested cancellation",
            "updatedAt",
            "before"));
    var repository = new RedisHistoricalJobLifecycleStateRepository(redisTemplate);

    boolean changed =
        repository.markFailed(jobId, "stale websocket failure", Duration.ofMinutes(5));

    assertFalse(changed);
    assertEquals("RUNNING", redisTemplate.getHash(key, "status"));
    assertEquals("CANCELLATION_REQUESTED", redisTemplate.getHash(key, "stage"));
    assertEquals("user requested cancellation", redisTemplate.getHash(key, "failureReason"));
    assertEquals("before", redisTemplate.getHash(key, "updatedAt"));
    assertFalse(redisTemplate.expirations.containsKey(key));
  }

  @Test
  void markCompleted_rejectsAlreadyCompletedJobHash() {
    var jobId = UUID.fromString("550e8400-e29b-41d4-a716-446655440115");
    var key = HistoricalJobControlKeyLayout.STANDALONE.jobState(jobId);
    var redisTemplate = new RecordingStringRedisTemplate();
    redisTemplate.putHash(
        key,
        Map.of(
            "status",
            "COMPLETED",
            "stage",
            "EXECUTION_COMPLETED",
            "failureReason",
            "",
            "updatedAt",
            "before"));
    var repository = new RedisHistoricalJobLifecycleStateRepository(redisTemplate);

    boolean changed = repository.markCompleted(jobId, Duration.ofMinutes(5));

    assertFalse(changed);
    assertEquals("COMPLETED", redisTemplate.getHash(key, "status"));
    assertEquals("EXECUTION_COMPLETED", redisTemplate.getHash(key, "stage"));
    assertEquals("before", redisTemplate.getHash(key, "updatedAt"));
    assertFalse(redisTemplate.expirations.containsKey(key));
  }

  private static final class RecordingStringRedisTemplate extends StringRedisTemplate {
    private final Map<String, Map<Object, Object>> hashes = new HashMap<>();
    private final Map<String, Duration> expirations = new HashMap<>();

    @Override
    @SuppressWarnings("unchecked")
    public HashOperations<String, Object, Object> opsForHash() {
      return (HashOperations<String, Object, Object>)
          Proxy.newProxyInstance(
              getClass().getClassLoader(),
              new Class<?>[] {HashOperations.class},
              (proxy, method, args) -> {
                return switch (method.getName()) {
                  case "get" -> hashes.getOrDefault((String) args[0], Map.of()).get(args[1]);
                  case "putAll" -> {
                    hashes.put((String) args[0], new HashMap<>((Map<?, ?>) args[1]));
                    yield null;
                  }
                  default -> throw new UnsupportedOperationException(method.getName());
                };
              });
    }

    @Override
    public Boolean hasKey(String key) {
      return hashes.containsKey(key);
    }

    @Override
    public Boolean expire(String key, Duration timeout) {
      expirations.put(key, timeout);
      return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T execute(RedisScript<T> script, List<String> keys, Object... args) {
      String source = script.getScriptAsString();
      if (!source.contains("local blockedStatusCount = tonumber(ARGV[index])")) {
        throw new UnsupportedOperationException("Unsupported script: " + source);
      }
      String key = keys.getFirst();
      Map<Object, Object> hash = hashes.get(key);
      if (hash == null || hash.isEmpty()) {
        return (T) Long.valueOf(0L);
      }
      int index = 0;
      int blockedStatusCount = Integer.parseInt(args[index++].toString());
      String currentStatus = hash.getOrDefault("status", "").toString();
      for (int i = 0; i < blockedStatusCount; i++) {
        if (currentStatus.equals(args[index++].toString())) {
          return (T) Long.valueOf(0L);
        }
      }
      int blockedStageCount = Integer.parseInt(args[index++].toString());
      String currentStage = hash.getOrDefault("stage", "").toString();
      for (int i = 0; i < blockedStageCount; i++) {
        if (currentStage.equals(args[index++].toString())) {
          return (T) Long.valueOf(0L);
        }
      }
      long ttlMillis = Long.parseLong(args[index++].toString());
      for (int i = index; i < args.length; i += 2) {
        hash.put(args[i], args[i + 1]);
      }
      expirations.put(key, Duration.ofMillis(ttlMillis));
      return (T) Long.valueOf(1L);
    }

    void putHash(String key, Map<String, String> values) {
      hashes.put(key, new HashMap<>(values));
    }

    String getHash(String key, String field) {
      Object value = hashes.getOrDefault(key, Map.of()).get(field);
      return value == null ? null : value.toString();
    }
  }
}
