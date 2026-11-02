// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.opencop.core.sdk.component.redis.LocalRedisSupport;
import com.opencop.core.sdk.model.SubjectType;
import com.opencop.core.sdk.model.history.HistoricalJobStage;
import com.opencop.core.sdk.model.history.HistoricalResolution;
import com.opencop.core.sdk.model.job.HistoricalTracksJobSubmission;
import com.opencop.core.sdk.model.job.HistoricalTracksSubject;
import com.opencop.core.sdk.model.job.JobIssuer;
import com.opencop.core.sdk.technical.gateway.history.HistoricalJobControlKeyLayout;
import com.opencop.processor.technical.gateway.history.model.job.HistoricalJobStatus;
import com.opencop.processor.technical.spring.config.HistoricalExecutionProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

class RedisHistoricalJobStateGatewayIT {

  private LettuceConnectionFactory connectionFactory;
  private StringRedisTemplate redisTemplate;
  private HistoricalExecutionProperties properties;
  private Clock clock;

  @BeforeEach
  void setUp() {
    connectionFactory =
        new LettuceConnectionFactory(LocalRedisSupport.host(), LocalRedisSupport.port());
    connectionFactory.afterPropertiesSet();
    redisTemplate = new StringRedisTemplate(connectionFactory);
    redisTemplate.afterPropertiesSet();
    redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    properties = new HistoricalExecutionProperties();
    properties.setJobStateTtl(Duration.ofHours(24));
    clock = Clock.fixed(Instant.parse("2026-04-07T12:00:00Z"), ZoneOffset.UTC);
  }

  @AfterEach
  void tearDown() {
    if (redisTemplate != null && redisTemplate.getConnectionFactory() != null) {
      redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }
    if (connectionFactory != null) {
      connectionFactory.destroy();
    }
  }

  @Test
  void acceptance41_markRunningCannotOverwriteCancellationRequestedAcrossGateways() {
    var job = job();
    var g1 =
        new RedisHistoricalJobStateGateway(
            redisTemplate, properties, clock, new ProcessorHistoricalRedisScripts());
    var g2 =
        new RedisHistoricalJobStateGateway(
            redisTemplate, properties, clock, new ProcessorHistoricalRedisScripts());
    g1.createReceived(job);
    assertTrue(g1.markQueued(job.jobId()));
    assertTrue(g1.markCancellationRequested(job.jobId(), "replacement"));

    assertFalse(g2.markRunning(job.jobId(), "history-job-2"));

    var state = g1.findByJobId(job.jobId()).orElseThrow();
    assertEquals(HistoricalJobStage.CANCELLATION_REQUESTED, state.stage());
    assertNull(state.workerId());
  }

  @Test
  void acceptance42_markCancellationRequestedCannotOverwriteCompletedAcrossGateways() {
    var job = job();
    var g1 =
        new RedisHistoricalJobStateGateway(
            redisTemplate, properties, clock, new ProcessorHistoricalRedisScripts());
    var g2 =
        new RedisHistoricalJobStateGateway(
            redisTemplate, properties, clock, new ProcessorHistoricalRedisScripts());
    g1.createReceived(job);
    assertTrue(g1.markQueued(job.jobId()));
    assertTrue(g1.markRunning(job.jobId(), "history-job-1"));
    assertTrue(g1.markCompleted(job.jobId()));

    assertFalse(g2.markCancellationRequested(job.jobId(), "replacement"));

    var state = g1.findByJobId(job.jobId()).orElseThrow();
    assertEquals(HistoricalJobStatus.COMPLETED, state.status());
    assertEquals(HistoricalJobStage.EXECUTION_COMPLETED, state.stage());
  }

  @Test
  void acceptance53_markCancelledAndMarkFailedCannotOverwriteCompletedAcrossGateways() {
    var job = job();
    var g1 =
        new RedisHistoricalJobStateGateway(
            redisTemplate, properties, clock, new ProcessorHistoricalRedisScripts());
    var g2 =
        new RedisHistoricalJobStateGateway(
            redisTemplate, properties, clock, new ProcessorHistoricalRedisScripts());
    g1.createReceived(job);
    assertTrue(g1.markQueued(job.jobId()));
    assertTrue(g1.markRunning(job.jobId(), "history-job-1"));
    assertTrue(g1.markCompleted(job.jobId()));

    assertFalse(g2.markCancelled(job.jobId(), "replacement"));
    assertFalse(g1.markFailed(job.jobId(), HistoricalJobStage.EXECUTION_FAILED_UNKNOWN, "boom"));

    var state = g2.findByJobId(job.jobId()).orElseThrow();
    assertEquals(HistoricalJobStatus.COMPLETED, state.status());
    assertEquals(HistoricalJobStage.EXECUTION_COMPLETED, state.stage());
  }

  @Test
  void acceptance43_createReceivedIfAbsentIsConcurrentAndOnlyOneRequestIdWins() throws Exception {
    var first = job();
    var second =
        new HistoricalTracksJobSubmission(
            first.jobId(),
            UUID.fromString("550e8400-e29b-41d4-a716-446655440123"),
            first.issuer(),
            List.of(
                new HistoricalTracksSubject(
                    UUID.randomUUID(),
                    first.subjects().getFirst().subjectType(),
                    first.subjects().getFirst().subjectId())),
            first.requestedBy(),
            first.requestedAt(),
            first.requestedFrom(),
            first.requestedTo(),
            first.effectiveFrom(),
            first.effectiveTo(),
            first.resolution(),
            first.plannedChunks(),
            first.filters());

    var g1 =
        new RedisHistoricalJobStateGateway(
            redisTemplate, properties, clock, new ProcessorHistoricalRedisScripts());
    var g2 =
        new RedisHistoricalJobStateGateway(
            redisTemplate, properties, clock, new ProcessorHistoricalRedisScripts());
    var gate = new CountDownLatch(1);
    boolean firstCreated;
    boolean secondCreated;

    try (var pool = Executors.newFixedThreadPool(2)) {
      Future<Boolean> firstFuture =
          pool.submit(
              () -> {
                gate.await(2, TimeUnit.SECONDS);
                return g1.createReceivedIfAbsent(first);
              });
      Future<Boolean> secondFuture =
          pool.submit(
              () -> {
                gate.await(2, TimeUnit.SECONDS);
                return g2.createReceivedIfAbsent(second);
              });

      gate.countDown();
      firstCreated = firstFuture.get(2, TimeUnit.SECONDS);
      secondCreated = secondFuture.get(2, TimeUnit.SECONDS);
    }

    assertTrue(firstCreated ^ secondCreated);
    var state = g1.findByJobId(first.jobId()).orElseThrow();
    assertEquals(firstCreated ? first.requestId() : second.requestId(), state.requestId());
  }

  @Test
  void createReceivedIfAbsent_writesDeterministicSequencesAndIndexes() {
    var userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440120");
    var requestId = UUID.fromString("550e8400-e29b-41d4-a716-446655440121");
    var first = job(userId, requestId, UUID.fromString("550e8400-e29b-41d4-a716-446655440122"));
    var second = job(userId, requestId, UUID.fromString("550e8400-e29b-41d4-a716-446655440123"));
    var gateway =
        new RedisHistoricalJobStateGateway(
            redisTemplate, properties, clock, new ProcessorHistoricalRedisScripts());

    assertTrue(gateway.createReceivedIfAbsent(first));
    assertTrue(gateway.createReceivedIfAbsent(second));

    var firstState = gateway.findByJobId(first.jobId()).orElseThrow();
    var secondState = gateway.findByJobId(second.jobId()).orElseThrow();
    assertEquals(1L, firstState.userSequence());
    assertEquals(1L, firstState.requestSequence());
    assertEquals(2L, secondState.userSequence());
    assertEquals(2L, secondState.requestSequence());

    Set<String> userOrder =
        redisTemplate
            .opsForZSet()
            .range(HistoricalJobControlKeyLayout.STANDALONE.userJobIndex(userId), 0, -1);
    Set<String> requestOrder =
        redisTemplate
            .opsForZSet()
            .range(
                HistoricalJobControlKeyLayout.STANDALONE.userRequestJobIndex(userId, requestId),
                0,
                -1);
    assertEquals(
        List.of(first.jobId().toString(), second.jobId().toString()), List.copyOf(userOrder));
    assertEquals(
        List.of(first.jobId().toString(), second.jobId().toString()), List.copyOf(requestOrder));
  }

  @Test
  void createReceivedIfAbsent_concurrentRaceCreatesSingleWinnerAndSingleIndexMember()
      throws Exception {
    var userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440120");
    var jobId = UUID.fromString("550e8400-e29b-41d4-a716-446655440122");
    var first = job(userId, UUID.fromString("550e8400-e29b-41d4-a716-446655440121"), jobId);
    var second = job(userId, UUID.fromString("550e8400-e29b-41d4-a716-446655440124"), jobId);

    var g1 =
        new RedisHistoricalJobStateGateway(
            redisTemplate, properties, clock, new ProcessorHistoricalRedisScripts());
    var g2 =
        new RedisHistoricalJobStateGateway(
            redisTemplate, properties, clock, new ProcessorHistoricalRedisScripts());
    var gate = new CountDownLatch(1);
    boolean firstCreated;
    boolean secondCreated;

    try (var pool = Executors.newFixedThreadPool(2)) {
      Future<Boolean> firstFuture =
          pool.submit(
              () -> {
                gate.await(2, TimeUnit.SECONDS);
                return g1.createReceivedIfAbsent(first);
              });
      Future<Boolean> secondFuture =
          pool.submit(
              () -> {
                gate.await(2, TimeUnit.SECONDS);
                return g2.createReceivedIfAbsent(second);
              });
      gate.countDown();
      firstCreated = firstFuture.get(2, TimeUnit.SECONDS);
      secondCreated = secondFuture.get(2, TimeUnit.SECONDS);
    }

    assertTrue(firstCreated ^ secondCreated);
    var persisted = g1.findByJobId(jobId).orElseThrow();
    assertEquals(firstCreated ? first.requestId() : second.requestId(), persisted.requestId());
    Set<String> userJobs =
        redisTemplate
            .opsForZSet()
            .range(HistoricalJobControlKeyLayout.STANDALONE.userJobIndex(userId), 0, -1);
    assertEquals(List.of(jobId.toString()), List.copyOf(userJobs));
    assertEquals(1L, persisted.userSequence());
  }

  private static HistoricalTracksJobSubmission job() {
    UUID userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440120");
    return job(userId, UUID.randomUUID(), UUID.randomUUID());
  }

  private static HistoricalTracksJobSubmission job(UUID userId, UUID requestId, UUID jobId) {
    return new HistoricalTracksJobSubmission(
        jobId,
        requestId,
        JobIssuer.user(userId),
        List.of(
            new HistoricalTracksSubject(UUID.randomUUID(), SubjectType.SOURCE, UUID.randomUUID())),
        userId,
        Instant.parse("2026-04-07T10:00:00Z"),
        Instant.parse("2026-04-06T10:02:17Z"),
        Instant.parse("2026-04-06T12:00:00Z"),
        Instant.parse("2026-04-06T10:02:00Z"),
        Instant.parse("2026-04-06T12:00:00Z"),
        HistoricalResolution.MINUTE_1,
        118,
        List.of());
  }
}
