// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.opencop.core.sdk.model.SubjectType;
import com.opencop.core.sdk.model.history.HistoricalResolution;
import com.opencop.core.sdk.model.job.HistoricalTracksJobSubmission;
import com.opencop.core.sdk.model.job.HistoricalTracksSubject;
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

class RedisHistoricalJobStateCreatorTest {

  private final RecordingStringRedisTemplate redisTemplate = new RecordingStringRedisTemplate();
  private final RedisScript<Long> script = RedisScript.of("return 1", Long.class);
  private final RedisHistoricalJobStateCreator creator =
      new RedisHistoricalJobStateCreator(redisTemplate, script);

  @Test
  void createReceivedIfAbsent_buildsScriptKeysAndArguments() {
    redisTemplate.result = 1L;
    var job = job();
    var state = HistoricalJobState.received(job, now(), Duration.ofHours(24));

    boolean created = creator.createReceivedIfAbsent(job, state, Duration.ofHours(24).toMillis());

    assertTrue(created);
    assertSame(script, redisTemplate.script);
    assertEquals(
        List.of(
            HistoricalJobControlKeyLayout.STANDALONE.jobState(job.jobId()),
            HistoricalJobControlKeyLayout.STANDALONE.userJobIndex(job.requestedBy()),
            HistoricalJobControlKeyLayout.STANDALONE.userRequestJobIndex(
                job.requestedBy(), job.requestId()),
            HistoricalJobControlKeyLayout.STANDALONE.userRequestIndex(job.requestedBy()),
            HistoricalJobControlKeyLayout.STANDALONE.userJobSequence(job.requestedBy()),
            HistoricalJobControlKeyLayout.STANDALONE.userRequestJobSequence(
                job.requestedBy(), job.requestId())),
        redisTemplate.keys);
    assertContainsPair(redisTemplate.args, "jobId", job.jobId().toString());
    assertContainsPair(redisTemplate.args, "requestId", job.requestId().toString());
    assertEquals(
        Long.toString(Duration.ofHours(24).toMillis()),
        redisTemplate.args[redisTemplate.args.length - 3]);
    assertEquals(job.jobId().toString(), redisTemplate.args[redisTemplate.args.length - 2]);
    assertEquals(job.requestId().toString(), redisTemplate.args[redisTemplate.args.length - 1]);
  }

  @Test
  void createReceivedIfAbsent_usesTheSelectedClusterControlSlot() {
    redisTemplate.result = 1L;
    var job = job();
    var state = HistoricalJobState.received(job, now(), Duration.ofHours(24));
    var clusterCreator =
        new RedisHistoricalJobStateCreator(
            redisTemplate, HistoricalJobControlKeyLayout.CLUSTER, script);

    assertTrue(clusterCreator.createReceivedIfAbsent(job, state, Duration.ofHours(24).toMillis()));
    var layout = HistoricalJobControlKeyLayout.CLUSTER;
    assertEquals(
        List.of(
            layout.jobState(job.jobId()),
            layout.userJobIndex(job.requestedBy()),
            layout.userRequestJobIndex(job.requestedBy(), job.requestId()),
            layout.userRequestIndex(job.requestedBy()),
            layout.userJobSequence(job.requestedBy()),
            layout.userRequestJobSequence(job.requestedBy(), job.requestId())),
        redisTemplate.keys);
  }

  @Test
  void createReceivedIfAbsent_whenScriptReturnsZero_returnsFalse() {
    redisTemplate.result = 0L;
    var job = job();
    var state = HistoricalJobState.received(job, now(), Duration.ofHours(24));

    assertFalse(creator.createReceivedIfAbsent(job, state, Duration.ofHours(24).toMillis()));
  }

  @Test
  void createReceivedIfAbsent_whenScriptReturnsNull_returnsFalse() {
    var job = job();
    var state = HistoricalJobState.received(job, now(), Duration.ofHours(24));

    assertFalse(creator.createReceivedIfAbsent(job, state, Duration.ofHours(24).toMillis()));
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
    return new HistoricalTracksJobSubmission(
        UUID.fromString("550e8400-e29b-41d4-a716-446655440001"),
        UUID.fromString("550e8400-e29b-41d4-a716-446655440002"),
        JobIssuer.user(UUID.fromString("550e8400-e29b-41d4-a716-446655440120")),
        List.of(
            new HistoricalTracksSubject(
                UUID.fromString("550e8400-e29b-41d4-a716-446655440110"),
                SubjectType.SOURCE,
                UUID.fromString("550e8400-e29b-41d4-a716-446655440121"))),
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
