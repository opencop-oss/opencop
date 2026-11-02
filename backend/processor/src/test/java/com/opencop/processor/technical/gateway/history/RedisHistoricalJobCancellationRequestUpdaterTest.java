// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.opencop.core.sdk.model.history.HistoricalJobStage;
import com.opencop.core.sdk.technical.gateway.history.HistoricalJobControlKeyLayout;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

class RedisHistoricalJobCancellationRequestUpdaterTest {

  private final RecordingStringRedisTemplate redisTemplate = new RecordingStringRedisTemplate();
  private final RedisScript<Long> script = RedisScript.of("return 1", Long.class);
  private final RedisHistoricalJobCancellationRequestUpdater updater =
      new RedisHistoricalJobCancellationRequestUpdater(redisTemplate, script);

  @Test
  void markRequested_buildsScriptKeysAndArguments() {
    redisTemplate.result = 1L;
    var jobId = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");
    var updatedAt = Instant.parse("2026-04-07T12:00:00Z");
    var expiresAt = Instant.parse("2026-04-08T12:00:00Z");

    boolean updated =
        updater.markRequested(
            jobId,
            "replacement accepted",
            updatedAt,
            expiresAt,
            List.of("COMPLETED", "FAILED", "CANCELLED"),
            Duration.ofHours(24).toMillis());

    assertTrue(updated);
    assertSame(script, redisTemplate.script);
    assertEquals(
        List.of(HistoricalJobControlKeyLayout.STANDALONE.jobState(jobId)), redisTemplate.keys);
    assertEquals("3", redisTemplate.args[0]);
    assertEquals("COMPLETED", redisTemplate.args[1]);
    assertEquals("FAILED", redisTemplate.args[2]);
    assertEquals("CANCELLED", redisTemplate.args[3]);
    assertEquals(HistoricalJobStage.CANCELLATION_REQUESTED.name(), redisTemplate.args[4]);
    assertEquals("replacement accepted", redisTemplate.args[5]);
    assertEquals(updatedAt.toString(), redisTemplate.args[6]);
    assertEquals(expiresAt.toString(), redisTemplate.args[7]);
    assertEquals(Long.toString(Duration.ofHours(24).toMillis()), redisTemplate.args[8]);
  }

  @Test
  void markRequested_whenScriptReturnsZero_returnsFalse() {
    redisTemplate.result = 0L;

    assertFalse(
        updater.markRequested(
            UUID.fromString("550e8400-e29b-41d4-a716-446655440001"),
            "replacement accepted",
            Instant.parse("2026-04-07T12:00:00Z"),
            Instant.parse("2026-04-08T12:00:00Z"),
            List.of("COMPLETED"),
            Duration.ofHours(24).toMillis()));
  }

  @Test
  void markRequested_whenScriptReturnsNull_returnsFalse() {
    assertFalse(
        updater.markRequested(
            UUID.fromString("550e8400-e29b-41d4-a716-446655440001"),
            "replacement accepted",
            Instant.parse("2026-04-07T12:00:00Z"),
            Instant.parse("2026-04-08T12:00:00Z"),
            List.of("COMPLETED"),
            Duration.ofHours(24).toMillis()));
  }

  private static final class RecordingStringRedisTemplate extends StringRedisTemplate {
    private RedisScript<?> script;
    private List<String> keys;
    private Object[] args;
    private Long result;

    @Override
    @SuppressWarnings("unchecked")
    public <T> T execute(RedisScript<T> script, List<String> keys, Object... args) {
      this.script = script;
      this.keys = keys;
      this.args = args;
      return (T) result;
    }
  }
}
