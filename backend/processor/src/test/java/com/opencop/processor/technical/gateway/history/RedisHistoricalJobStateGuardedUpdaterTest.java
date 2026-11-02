// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.opencop.core.sdk.model.SubjectType;
import com.opencop.core.sdk.model.history.HistoricalJobStage;
import com.opencop.core.sdk.model.history.HistoricalResolution;
import com.opencop.core.sdk.model.job.HistoricalTracksJobSubmission;
import com.opencop.core.sdk.model.job.JobIssuer;
import com.opencop.core.sdk.technical.gateway.history.HistoricalJobControlKeyLayout;
import com.opencop.processor.technical.gateway.history.model.job.HistoricalJobState;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

class RedisHistoricalJobStateGuardedUpdaterTest {

  private final RecordingStringRedisTemplate redisTemplate = new RecordingStringRedisTemplate();
  private final RedisScript<Long> script = RedisScript.of("return 1", Long.class);
  private final RedisHistoricalJobStateGuardedUpdater updater =
      new RedisHistoricalJobStateGuardedUpdater(redisTemplate, script);

  @Test
  void update_buildsGuardArgsHashArgsAndReturnsTrueWhenScriptUpdates() {
    redisTemplate.result = 1L;
    var state = HistoricalJobState.received(job(), now(), Duration.ofHours(24));
    var target = state.running(now(), Duration.ofHours(24), "worker-1");

    boolean updated =
        updater.update(
            target.jobId(),
            target,
            List.of("COMPLETED", "FAILED", "CANCELLED"),
            List.of(HistoricalJobStage.CANCELLATION_REQUESTED.name()),
            Duration.ofHours(24).toMillis());

    assertTrue(updated);
    assertSame(script, redisTemplate.script);
    assertEquals(
        List.of(HistoricalJobControlKeyLayout.STANDALONE.jobState(target.jobId())),
        redisTemplate.keys);
    assertEquals("3", redisTemplate.args[0]);
    assertEquals("COMPLETED", redisTemplate.args[1]);
    assertEquals("FAILED", redisTemplate.args[2]);
    assertEquals("CANCELLED", redisTemplate.args[3]);
    assertEquals("1", redisTemplate.args[4]);
    assertEquals(HistoricalJobStage.CANCELLATION_REQUESTED.name(), redisTemplate.args[5]);
    assertEquals(Long.toString(Duration.ofHours(24).toMillis()), redisTemplate.args[6]);
    assertContainsPair(redisTemplate.args, "stage", HistoricalJobStage.EXECUTION_RUNNING.name());
    assertContainsPair(redisTemplate.args, "workerId", "worker-1");
  }

  @Test
  void update_whenTtlIsZero_sendsBlankTtlArgument() {
    redisTemplate.result = 1L;
    var state = HistoricalJobState.received(job(), now(), Duration.ofHours(24));
    var target = state.cancelled(now(), "replacement");

    assertTrue(updater.update(target.jobId(), target, List.of("COMPLETED"), List.of(), 0L));

    assertEquals("", redisTemplate.args[3]);
    assertContainsPair(redisTemplate.args, "stage", HistoricalJobStage.CANCELLED.name());
    assertContainsPair(redisTemplate.args, "failureReason", "replacement");
  }

  @Test
  void update_whenScriptReturnsNull_returnsFalse() {
    var state = HistoricalJobState.received(job(), now(), Duration.ofHours(24));

    assertFalse(updater.update(state.jobId(), state, List.of(), List.of(), 1L));
  }

  private static void assertContainsPair(Object[] args, String field, String value) {
    for (int index = 0; index < args.length - 1; index++) {
      if (field.equals(args[index]) && value.equals(args[index + 1])) {
        return;
      }
    }
    throw new AssertionError("Expected argument pair " + field + "=" + value);
  }

  private static Instant now() {
    return Instant.parse("2026-04-07T12:00:00Z");
  }

  private static HistoricalTracksJobSubmission job() {
    return HistoricalTracksJobSubmission.create(
        UUID.fromString("550e8400-e29b-41d4-a716-446655440001"),
        JobIssuer.user(UUID.fromString("550e8400-e29b-41d4-a716-446655440120")),
        SubjectType.SOURCE,
        UUID.fromString("550e8400-e29b-41d4-a716-446655440121"),
        UUID.fromString("550e8400-e29b-41d4-a716-446655440120"),
        Instant.parse("2026-04-07T10:00:00Z"),
        Instant.parse("2026-04-06T10:02:17Z"),
        Instant.parse("2026-04-06T12:00:00Z"),
        Instant.parse("2026-04-06T10:02:00Z"),
        Instant.parse("2026-04-06T12:00:00Z"),
        HistoricalResolution.MINUTE_1,
        118,
        List.of());
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
