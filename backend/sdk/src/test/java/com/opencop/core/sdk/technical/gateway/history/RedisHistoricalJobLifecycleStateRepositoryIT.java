// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.opencop.core.sdk.component.redis.LocalRedisSupport;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

class RedisHistoricalJobLifecycleStateRepositoryIT {

  private static final String JOB_PREFIX = "opencop:history:job:";

  private static final LettuceConnectionFactory connectionFactory =
      new LettuceConnectionFactory(LocalRedisSupport.host(), LocalRedisSupport.port());
  private static final StringRedisTemplate redisTemplate = new StringRedisTemplate();

  @BeforeAll
  static void setUp() {
    connectionFactory.afterPropertiesSet();
    redisTemplate.setConnectionFactory(connectionFactory);
    redisTemplate.afterPropertiesSet();
  }

  @AfterAll
  static void tearDown() {
    connectionFactory.destroy();
  }

  @AfterEach
  void cleanUp() {
    Set<String> keys = redisTemplate.keys(JOB_PREFIX + "*");
    if (keys != null && !keys.isEmpty()) {
      redisTemplate.delete(keys);
    }
  }

  @Test
  void markFailedDoesNotOverwriteAlreadyTerminalJobHash() {
    var jobId = UUID.fromString("550e8400-e29b-41d4-a716-446655440201");
    var key = HistoricalJobControlKeyLayout.STANDALONE.jobState(jobId);
    putHash(
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
    assertEquals("CANCELLED", getField(key, "status"));
    assertEquals("CANCELLED", getField(key, "stage"));
    assertEquals("user cancelled", getField(key, "failureReason"));
    assertEquals("before", getField(key, "updatedAt"));
  }

  @Test
  void markCompletedUpdatesNonTerminalJobHash() {
    var jobId = UUID.fromString("550e8400-e29b-41d4-a716-446655440202");
    var key = HistoricalJobControlKeyLayout.STANDALONE.jobState(jobId);
    putHash(key, Map.of("status", "RUNNING", "stage", "EXECUTION_RUNNING"));
    var repository = new RedisHistoricalJobLifecycleStateRepository(redisTemplate);

    boolean changed = repository.markCompleted(jobId, Duration.ofMinutes(5));

    assertTrue(changed);
    assertEquals("COMPLETED", getField(key, "status"));
    assertEquals("EXECUTION_COMPLETED", getField(key, "stage"));
  }

  private static void putHash(String key, Map<String, String> values) {
    HashOperations<String, Object, Object> hashOperations = redisTemplate.opsForHash();
    hashOperations.putAll(key, values);
  }

  private static String getField(String key, String field) {
    Object value = redisTemplate.opsForHash().get(key, field);
    return value == null ? null : value.toString();
  }
}
