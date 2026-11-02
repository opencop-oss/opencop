// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.opencop.core.sdk.component.redis.LocalRedisSupport;
import com.opencop.core.sdk.model.SubjectType;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

class RedisHistoricalJobDeliveryRepositoryIT {

  private static final String JOB_DELIVERY_PREFIX = "opencop:history:job-delivery:";

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
    Set<String> keys = redisTemplate.keys(JOB_DELIVERY_PREFIX + "*");
    if (keys != null && !keys.isEmpty()) {
      redisTemplate.delete(keys);
    }
  }

  @Test
  void deliveryFailureSurvivesLaterExecutionProgressAndCompletionWrites() {
    var repository = new RedisHistoricalJobDeliveryRepository(redisTemplate);
    UUID jobId = UUID.fromString("550e8400-e29b-41d4-a716-446655441001");

    repository.ensureInitialized(
        jobId,
        UUID.fromString("550e8400-e29b-41d4-a716-446655441002"),
        UUID.fromString("550e8400-e29b-41d4-a716-446655441003"),
        SubjectType.SOURCE,
        UUID.fromString("550e8400-e29b-41d4-a716-446655441004"),
        2,
        0,
        true,
        Duration.ofHours(24));

    assertEquals(1, repository.incrementFailedIfTracked(jobId, Duration.ofHours(24), "lost"));
    repository.recordExecutionProgress(jobId, 1, 0, Duration.ofHours(24));
    repository.markExecutionComplete(jobId, 2, 0, Duration.ofHours(24));

    var snapshot = repository.loadSnapshot(jobId).orElseThrow();
    assertEquals(1, snapshot.failedChunks());
    assertEquals("lost", snapshot.failureReason());
    assertTrue(repository.hasDeliveryFailure(jobId));
    assertFalse(repository.tryClaimCompletionPublication(jobId, Duration.ofHours(24)));
    assertTrue(repository.tryClaimFailurePublication(jobId, Duration.ofHours(24)));
  }
}
