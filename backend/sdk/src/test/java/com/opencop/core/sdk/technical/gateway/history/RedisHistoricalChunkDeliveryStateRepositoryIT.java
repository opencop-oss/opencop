// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.history;

import static org.assertj.core.api.Assertions.assertThat;
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

class RedisHistoricalChunkDeliveryStateRepositoryIT {

  private static final String CHUNK_STATE_PREFIX = "opencop:history:chunk-state:";

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
    Set<String> keys = redisTemplate.keys(CHUNK_STATE_PREFIX + "*");
    if (keys != null && !keys.isEmpty()) {
      redisTemplate.delete(keys);
    }
    Set<String> markerKeys = redisTemplate.keys("opencop:history:chunk-delivery-marker:*");
    if (markerKeys != null && !markerKeys.isEmpty()) {
      redisTemplate.delete(markerKeys);
    }
    redisTemplate.delete("opencop:unrelated:key");
  }

  @Test
  void tryMarkDelivered_transitionsFromAvailableForDelivery() {
    var chunkStateKey = CHUNK_STATE_PREFIX + "subject:123";
    var hashOperations = redisTemplate.opsForHash();
    putHash(
        hashOperations,
        chunkStateKey,
        Map.of(
            "status",
            "AVAILABLE_FOR_DELIVERY",
            "updatedAt",
            "2026-06-03T10:00:00Z",
            "expiresAt",
            "2026-06-03T10:01:00Z"));
    var repository = new RedisHistoricalChunkDeliveryStateRepository(redisTemplate);

    String previousUpdatedAt = getField(hashOperations, chunkStateKey, "updatedAt");
    String previousExpiresAt = getField(hashOperations, chunkStateKey, "expiresAt");

    boolean changed = repository.tryMarkDelivered(chunkStateKey, Duration.ofMinutes(30));

    assertTrue(changed);
    assertEquals("DELIVERED", getField(hashOperations, chunkStateKey, "status"));
    assertEquals("", getField(hashOperations, chunkStateKey, "failureReason"));
    assertThat(getField(hashOperations, chunkStateKey, "updatedAt"))
        .isNotEqualTo(previousUpdatedAt);
    assertThat(getField(hashOperations, chunkStateKey, "expiresAt"))
        .isNotEqualTo(previousExpiresAt);
  }

  @Test
  void tryMarkDelivered_doesNotOverwriteDeliveredTerminalState() {
    var chunkStateKey = CHUNK_STATE_PREFIX + "subject:456";
    var hashOperations = redisTemplate.opsForHash();
    putHash(
        hashOperations,
        chunkStateKey,
        Map.of(
            "status",
            "DELIVERED",
            "failureReason",
            "already-delivered",
            "updatedAt",
            "2026-06-03T11:00:00Z",
            "expiresAt",
            "2026-06-03T11:01:00Z"));
    var repository = new RedisHistoricalChunkDeliveryStateRepository(redisTemplate);

    String previousUpdatedAt = getField(hashOperations, chunkStateKey, "updatedAt");
    String previousExpiresAt = getField(hashOperations, chunkStateKey, "expiresAt");
    String previousFailureReason = getField(hashOperations, chunkStateKey, "failureReason");

    boolean changed = repository.tryMarkDelivered(chunkStateKey, Duration.ofMinutes(30));

    assertFalse(changed);
    assertEquals("DELIVERED", getField(hashOperations, chunkStateKey, "status"));
    assertEquals(previousFailureReason, getField(hashOperations, chunkStateKey, "failureReason"));
    assertEquals(previousUpdatedAt, getField(hashOperations, chunkStateKey, "updatedAt"));
    assertEquals(previousExpiresAt, getField(hashOperations, chunkStateKey, "expiresAt"));
  }

  @Test
  void tryMarkDeliveredForJob_allowsDifferentJobsToShareContentChunkState() {
    var chunkStateKey = CHUNK_STATE_PREFIX + "subject:shared";
    var jobA = UUID.fromString("550e8400-e29b-41d4-a716-446655440301");
    var jobB = UUID.fromString("550e8400-e29b-41d4-a716-446655440302");
    var hashOperations = redisTemplate.opsForHash();
    putHash(
        hashOperations,
        chunkStateKey,
        Map.of(
            "status",
            "AVAILABLE_FOR_DELIVERY",
            "updatedAt",
            "2026-06-03T10:00:00Z",
            "expiresAt",
            "2026-06-03T10:01:00Z"));
    var repository = new RedisHistoricalChunkDeliveryStateRepository(redisTemplate);

    assertTrue(repository.tryMarkDeliveredForJob(jobA, chunkStateKey, Duration.ofMinutes(30)));
    assertTrue(repository.tryMarkDeliveredForJob(jobB, chunkStateKey, Duration.ofMinutes(30)));
    assertFalse(repository.tryMarkDeliveredForJob(jobA, chunkStateKey, Duration.ofMinutes(30)));
    assertEquals("DELIVERED", getField(hashOperations, chunkStateKey, "status"));
  }

  @Test
  void tryMarkFailed_transitionsFromAvailableForDelivery() {
    var chunkStateKey = CHUNK_STATE_PREFIX + "subject:789";
    var hashOperations = redisTemplate.opsForHash();
    var failureReason = "NO_ACTIVE_HISTORY_SUBSCRIPTION";
    putHash(
        hashOperations,
        chunkStateKey,
        Map.of(
            "status",
            "AVAILABLE_FOR_DELIVERY",
            "updatedAt",
            "2026-06-03T10:00:00Z",
            "expiresAt",
            "2026-06-03T10:01:00Z"));
    var repository = new RedisHistoricalChunkDeliveryStateRepository(redisTemplate);
    String previousUpdatedAt = getField(hashOperations, chunkStateKey, "updatedAt");
    String previousExpiresAt = getField(hashOperations, chunkStateKey, "expiresAt");

    boolean changed =
        repository.tryMarkFailed(chunkStateKey, Duration.ofMinutes(30), failureReason);

    assertTrue(changed);
    assertEquals("FAILED", getField(hashOperations, chunkStateKey, "status"));
    assertEquals(failureReason, getField(hashOperations, chunkStateKey, "failureReason"));
    assertThat(getField(hashOperations, chunkStateKey, "updatedAt"))
        .isNotEqualTo(previousUpdatedAt);
    assertThat(getField(hashOperations, chunkStateKey, "expiresAt"))
        .isNotEqualTo(previousExpiresAt);
  }

  @Test
  void tryMarkFailed_preservesFailureReasonWhenAlreadyFailed() {
    var chunkStateKey = CHUNK_STATE_PREFIX + "subject:101";
    var hashOperations = redisTemplate.opsForHash();
    putHash(
        hashOperations,
        chunkStateKey,
        Map.of(
            "status",
            "FAILED",
            "failureReason",
            "previous-failure",
            "updatedAt",
            "2026-06-03T12:00:00Z",
            "expiresAt",
            "2026-06-03T12:01:00Z"));
    var repository = new RedisHistoricalChunkDeliveryStateRepository(redisTemplate);

    boolean changed =
        repository.tryMarkFailed(chunkStateKey, Duration.ofMinutes(30), "second-failure");

    assertFalse(changed);
    assertEquals("FAILED", getField(hashOperations, chunkStateKey, "status"));
    assertEquals("previous-failure", getField(hashOperations, chunkStateKey, "failureReason"));
    assertEquals("2026-06-03T12:00:00Z", getField(hashOperations, chunkStateKey, "updatedAt"));
    assertEquals("2026-06-03T12:01:00Z", getField(hashOperations, chunkStateKey, "expiresAt"));
  }

  @Test
  void tryMarkFailedForJob_allowsDifferentJobsToShareContentChunkState() {
    var chunkStateKey = CHUNK_STATE_PREFIX + "subject:shared-failure";
    var jobA = UUID.fromString("550e8400-e29b-41d4-a716-446655440303");
    var jobB = UUID.fromString("550e8400-e29b-41d4-a716-446655440304");
    var hashOperations = redisTemplate.opsForHash();
    putHash(
        hashOperations,
        chunkStateKey,
        Map.of(
            "status",
            "AVAILABLE_FOR_DELIVERY",
            "updatedAt",
            "2026-06-03T10:00:00Z",
            "expiresAt",
            "2026-06-03T10:01:00Z"));
    var repository = new RedisHistoricalChunkDeliveryStateRepository(redisTemplate);

    assertTrue(
        repository.tryMarkFailedForJob(
            jobA, chunkStateKey, Duration.ofMinutes(30), "NO_ACTIVE_HISTORY_SUBSCRIPTION"));
    assertTrue(
        repository.tryMarkFailedForJob(
            jobB, chunkStateKey, Duration.ofMinutes(30), "NO_ACTIVE_HISTORY_SUBSCRIPTION"));
    assertFalse(
        repository.tryMarkFailedForJob(
            jobA, chunkStateKey, Duration.ofMinutes(30), "NO_ACTIVE_HISTORY_SUBSCRIPTION"));
    assertEquals("FAILED", getField(hashOperations, chunkStateKey, "status"));
  }

  @Test
  void tryMarkFailed_rejectsMalformedChunkStateKey() {
    var unrelatedKey = "opencop:unrelated:key";
    var hashOperations = redisTemplate.opsForHash();
    putHash(
        hashOperations,
        unrelatedKey,
        Map.of(
            "status",
            "AVAILABLE_FOR_DELIVERY",
            "failureReason",
            "queued",
            "updatedAt",
            "2026-06-03T13:00:00Z",
            "expiresAt",
            "2026-06-03T13:01:00Z"));
    var repository = new RedisHistoricalChunkDeliveryStateRepository(redisTemplate);

    boolean changed =
        repository.tryMarkFailed(
            unrelatedKey, Duration.ofMinutes(30), "NO_ACTIVE_HISTORY_SUBSCRIPTION");

    assertFalse(changed);
    assertEquals("AVAILABLE_FOR_DELIVERY", getField(hashOperations, unrelatedKey, "status"));
    assertEquals("queued", getField(hashOperations, unrelatedKey, "failureReason"));
    assertEquals("2026-06-03T13:00:00Z", getField(hashOperations, unrelatedKey, "updatedAt"));
  }

  @Test
  void tryMarkFailed_rejectsDeliveredTerminalState() {
    var chunkStateKey = CHUNK_STATE_PREFIX + "subject:202";
    var hashOperations = redisTemplate.opsForHash();
    putHash(
        hashOperations,
        chunkStateKey,
        Map.of(
            "status",
            "DELIVERED",
            "failureReason",
            "already-delivered",
            "updatedAt",
            "2026-06-03T14:00:00Z",
            "expiresAt",
            "2026-06-03T14:01:00Z"));
    var repository = new RedisHistoricalChunkDeliveryStateRepository(redisTemplate);

    boolean changed =
        repository.tryMarkFailed(
            chunkStateKey, Duration.ofMinutes(30), "NO_ACTIVE_HISTORY_SUBSCRIPTION");

    assertFalse(changed);
    assertEquals("DELIVERED", getField(hashOperations, chunkStateKey, "status"));
    assertEquals("already-delivered", getField(hashOperations, chunkStateKey, "failureReason"));
    assertEquals("2026-06-03T14:00:00Z", getField(hashOperations, chunkStateKey, "updatedAt"));
    assertEquals("2026-06-03T14:01:00Z", getField(hashOperations, chunkStateKey, "expiresAt"));
  }

  @Test
  void tryMarkFailed_ignoresUnknownCurrentStatus() {
    var chunkStateKey = CHUNK_STATE_PREFIX + "subject:303";
    var hashOperations = redisTemplate.opsForHash();
    putHash(
        hashOperations,
        chunkStateKey,
        Map.of(
            "status",
            "COMPUTING",
            "updatedAt",
            "2026-06-03T15:00:00Z",
            "expiresAt",
            "2026-06-03T15:01:00Z",
            "failureReason",
            ""));
    var repository = new RedisHistoricalChunkDeliveryStateRepository(redisTemplate);

    boolean changed =
        repository.tryMarkFailed(
            chunkStateKey, Duration.ofMinutes(30), "NO_ACTIVE_HISTORY_SUBSCRIPTION");

    assertFalse(changed);
    assertEquals("COMPUTING", getField(hashOperations, chunkStateKey, "status"));
    assertEquals("", getField(hashOperations, chunkStateKey, "failureReason"));
  }

  private static void putHash(
      HashOperations<String, Object, Object> hashOperations,
      String key,
      Map<String, String> values) {
    hashOperations.putAll(key, values);
  }

  private static String getField(
      HashOperations<String, Object, Object> hashOperations, String key, String field) {
    var value = hashOperations.get(key, field);
    return value == null ? null : value.toString();
  }
}
