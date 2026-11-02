// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.opencop.core.sdk.component.redis.LocalRedisSupport;
import com.opencop.core.sdk.model.SubjectType;
import com.opencop.core.sdk.model.history.HistoricalResolution;
import com.opencop.core.sdk.model.job.HistoricalTracksJobSubmission;
import com.opencop.core.sdk.model.job.HistoricalTracksSubject;
import com.opencop.core.sdk.model.job.JobIssuer;
import com.opencop.core.sdk.model.job.LayerCopyJobSubmission;
import com.opencop.core.sdk.technical.gateway.history.HistoricalJobControlKeyLayout;
import com.opencop.processor.technical.gateway.history.model.job.HistoricalJobAdmissionDecision.HistoricalJobCancellation;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

class RedisHistoricalJobAdmissionQueuedPayloadsTest {

  private LettuceConnectionFactory connectionFactory;
  private StringRedisTemplate redisTemplate;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    connectionFactory =
        new LettuceConnectionFactory(LocalRedisSupport.host(), LocalRedisSupport.port());
    connectionFactory.afterPropertiesSet();
    redisTemplate = new StringRedisTemplate(connectionFactory);
    redisTemplate.afterPropertiesSet();
    redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    objectMapper = new ObjectMapper();
  }

  @AfterEach
  void tearDown() {
    redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    connectionFactory.destroy();
  }

  @Test
  void restoreJobsToStart_restoresValidHistoricalPayloadAndDeletesPayload() throws Exception {
    var userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440010");
    var job = job(UUID.randomUUID(), userId);
    redisTemplate.opsForValue().set(payloadKey(job.jobId()), objectMapper.writeValueAsString(job));
    var scriptGateway = mock(RedisHistoricalJobAdmissionScriptGateway.class);
    when(scriptGateway.keyLayout()).thenReturn(HistoricalJobControlKeyLayout.STANDALONE);
    when(scriptGateway.readQueuedPayload(eq(userId), eq(job.jobId().toString())))
        .thenAnswer(invocation -> redisTemplate.opsForValue().get(payloadKey(job.jobId())));
    when(scriptGateway.cleanupAdmissionState(any(), any())).thenReturn(null);
    var payloads =
        new RedisHistoricalJobAdmissionQueuedPayloads(redisTemplate, objectMapper, scriptGateway);
    List<HistoricalJobCancellation> cancellations = new ArrayList<>();

    var restored = payloads.restoreJobsToStart(userId, job.jobId().toString(), cancellations);

    assertEquals(
        List.of(job.jobId()), restored.stream().map(HistoricalTracksJobSubmission::jobId).toList());
    assertTrue(cancellations.isEmpty());
    assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(payloadKey(job.jobId()))));
  }

  @Test
  void restoreJobsToStart_missingPayloadCancelsAndRestoresCleanupPromotion() throws Exception {
    var userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440010");
    var missingJobId = UUID.randomUUID();
    var promoted = job(UUID.randomUUID(), userId);
    redisTemplate
        .opsForValue()
        .set(payloadKey(promoted.jobId()), objectMapper.writeValueAsString(promoted));
    List<String> cleaned = new CopyOnWriteArrayList<>();
    var scriptGateway = mock(RedisHistoricalJobAdmissionScriptGateway.class);
    when(scriptGateway.keyLayout()).thenReturn(HistoricalJobControlKeyLayout.STANDALONE);
    when(scriptGateway.readQueuedPayload(any(), any()))
        .thenAnswer(
            invocation ->
                redisTemplate
                    .opsForValue()
                    .get(payloadKey(UUID.fromString(invocation.getArgument(1)))));
    when(scriptGateway.cleanupAdmissionState(eq(userId), any()))
        .thenAnswer(
            invocation -> {
              String tokenArg = invocation.getArgument(1);
              cleaned.add(tokenArg);
              return promoted.jobId().toString();
            });
    var payloads =
        new RedisHistoricalJobAdmissionQueuedPayloads(redisTemplate, objectMapper, scriptGateway);
    List<HistoricalJobCancellation> cancellations = new ArrayList<>();

    var restored = payloads.restoreJobsToStart(userId, missingJobId.toString(), cancellations);

    assertEquals(List.of(missingJobId.toString()), cleaned);
    assertEquals(1, cancellations.size());
    assertEquals(missingJobId, cancellations.getFirst().jobId());
    assertEquals(
        RedisHistoricalJobAdmissionQueuedPayloads.PAYLOAD_RESTORE_FAILURE_REASON,
        cancellations.getFirst().reason());
    assertEquals(
        List.of(promoted.jobId()),
        restored.stream().map(HistoricalTracksJobSubmission::jobId).toList());
    assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(payloadKey(promoted.jobId()))));
  }

  @Test
  void restoreJobsToStart_wrongSubmissionPayloadCancelsAndCleansJob() throws Exception {
    var userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440010");
    var jobId = UUID.randomUUID();
    var wrongType =
        LayerCopyJobSubmission.create(
            JobIssuer.user(userId), UUID.randomUUID(), UUID.randomUUID(), true, true);
    redisTemplate.opsForValue().set(payloadKey(jobId), objectMapper.writeValueAsString(wrongType));
    List<String> cleaned = new CopyOnWriteArrayList<>();
    var scriptGateway = mock(RedisHistoricalJobAdmissionScriptGateway.class);
    when(scriptGateway.keyLayout()).thenReturn(HistoricalJobControlKeyLayout.STANDALONE);
    when(scriptGateway.readQueuedPayload(any(), any()))
        .thenAnswer(
            invocation ->
                redisTemplate
                    .opsForValue()
                    .get(payloadKey(UUID.fromString(invocation.getArgument(1)))));
    when(scriptGateway.cleanupAdmissionState(eq(userId), any()))
        .thenAnswer(
            invocation -> {
              String tokenArg = invocation.getArgument(1);
              cleaned.add(tokenArg);
              return "";
            });
    var payloads =
        new RedisHistoricalJobAdmissionQueuedPayloads(redisTemplate, objectMapper, scriptGateway);
    List<HistoricalJobCancellation> cancellations = new ArrayList<>();

    var restored = payloads.restoreJobsToStart(userId, jobId.toString(), cancellations);

    assertTrue(restored.isEmpty());
    assertEquals(List.of(jobId.toString()), cleaned);
    assertEquals(1, cancellations.size());
    assertEquals(jobId, cancellations.getFirst().jobId());
    assertEquals(
        RedisHistoricalJobAdmissionQueuedPayloads.PAYLOAD_RESTORE_FAILURE_REASON,
        cancellations.getFirst().reason());
  }

  @Test
  void supersededCancellations_cleansMalformedAndValidMembers() {
    var userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440010");
    var validJobId = UUID.randomUUID();
    List<String> cleaned = new CopyOnWriteArrayList<>();
    var scriptGateway = mock(RedisHistoricalJobAdmissionScriptGateway.class);
    when(scriptGateway.keyLayout()).thenReturn(HistoricalJobControlKeyLayout.STANDALONE);
    when(scriptGateway.cleanupAdmissionState(eq(userId), any()))
        .thenAnswer(
            invocation -> {
              String tokenArg = invocation.getArgument(1);
              cleaned.add(tokenArg);
              return "";
            });
    var payloads =
        new RedisHistoricalJobAdmissionQueuedPayloads(redisTemplate, objectMapper, scriptGateway);

    var cancellations = payloads.supersededCancellations(userId, "bad-uuid, " + validJobId + ",");

    assertEquals(List.of("bad-uuid", validJobId.toString()), cleaned);
    assertEquals(1, cancellations.size());
    assertEquals(validJobId, cancellations.getFirst().jobId());
    assertEquals(
        RedisHistoricalJobAdmissionQueuedPayloads.SUPERSEDED_BY_REPLACEMENT_REASON,
        cancellations.getFirst().reason());
  }

  private static HistoricalTracksJobSubmission job(UUID requestId, UUID requestedBy) {
    return new HistoricalTracksJobSubmission(
        UUID.randomUUID(),
        requestId,
        JobIssuer.user(requestedBy),
        List.of(
            new HistoricalTracksSubject(UUID.randomUUID(), SubjectType.SOURCE, UUID.randomUUID())),
        requestedBy,
        Instant.parse("2026-04-07T10:00:00Z"),
        Instant.parse("2026-04-06T10:02:17Z"),
        Instant.parse("2026-04-06T12:00:00Z"),
        Instant.parse("2026-04-06T10:02:00Z"),
        Instant.parse("2026-04-06T12:00:00Z"),
        HistoricalResolution.MINUTE_1,
        118,
        List.of());
  }

  private static String payloadKey(UUID jobId) {
    return "opencop:history:job-payload:" + jobId;
  }
}
