// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.history.admission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.opencop.core.sdk.component.redis.LocalRedisSupport;
import com.opencop.core.sdk.model.SubjectType;
import com.opencop.core.sdk.model.history.HistoricalJobStage;
import com.opencop.core.sdk.model.history.HistoricalResolution;
import com.opencop.core.sdk.model.job.HistoricalTracksJobSubmission;
import com.opencop.core.sdk.model.job.JobIssuer;
import com.opencop.core.sdk.technical.gateway.history.HistoricalJobControlKeyLayout;
import com.opencop.core.sdk.technical.gateway.history.RedisHistoricalJobLifecycleStateRepository;
import com.opencop.core.sdk.technical.gateway.history.RedisHistoricalJobLifecycleUpdater;
import com.opencop.processor.business.policy.history.HistoricalJobAdmissionRecoveryPolicy;
import com.opencop.processor.business.usecase.history.HistoricalJobAdmissionRecovery;
import com.opencop.processor.technical.gateway.history.ProcessorHistoricalRedisScripts;
import com.opencop.processor.technical.gateway.history.RedisHistoricalJobAdmissionGateway;
import com.opencop.processor.technical.gateway.history.RedisHistoricalJobStateGateway;
import com.opencop.processor.technical.gateway.history.model.job.HistoricalJobAdmissionDecision.Action;
import com.opencop.processor.technical.observability.history.HistoricalObservability;
import com.opencop.processor.technical.spring.config.HistoricalExecutionProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

class HistoricalJobAdmissionRecoveryIT {

  private LettuceConnectionFactory connectionFactory;
  private StringRedisTemplate redisTemplate;
  private HistoricalExecutionProperties properties;
  private RedisHistoricalJobStateGateway stateGateway;
  private RedisHistoricalJobAdmissionGateway coordinator;
  private List<HistoricalTracksJobSubmission> submittedJobs;

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
    properties.setTtlRefreshInterval(Duration.ofMinutes(1));
    properties.setMaxRuntime(Duration.ofMinutes(5));
    properties.setMaxConcurrentSubjectsPerJob(2);
    stateGateway =
        new RedisHistoricalJobStateGateway(
            redisTemplate, properties, Clock.systemUTC(), new ProcessorHistoricalRedisScripts());
    coordinator =
        RedisHistoricalJobAdmissionGateway.create(
            redisTemplate, new ObjectMapper(), properties, new ProcessorHistoricalRedisScripts());
    submittedJobs = new ArrayList<>();
  }

  @AfterEach
  void tearDown() {
    redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    connectionFactory.destroy();
  }

  @Test
  void recoverStaleAdmission_whenDeliveryFailedActiveRequestIsTerminal_promotesReplacement() {
    UUID user = UUID.fromString("550e8400-e29b-41d4-a716-446655440120");
    var requestA = UUID.randomUUID();
    var requestB = UUID.randomUUID();
    var active = userJob(requestA, user, UUID.fromString("550e8400-e29b-41d4-a716-446655441017"));
    var replacement =
        userJob(requestB, user, UUID.fromString("550e8400-e29b-41d4-a716-446655441018"));

    registerRunning(active);
    registerQueuedReplacement(replacement);

    var lifecycleUpdater =
        new RedisHistoricalJobLifecycleUpdater(
            new RedisHistoricalJobLifecycleStateRepository(redisTemplate));
    assertTrue(
        lifecycleUpdater.markFailed(
            active.jobId(), "websocket-marked-delivery-failed", properties.getAdmissionTtl()));

    recovery(ignored -> false).recoverStaleAdmission();

    assertEquals(List.of(replacement), submittedJobs);
  }

  @Test
  void recoverStaleAdmission_whenDeliveryFailedActiveRequestIsTracked_doesNotPromoteReplacement() {
    UUID user = UUID.fromString("550e8400-e29b-41d4-a716-446655440120");
    var requestA = UUID.randomUUID();
    var requestB = UUID.randomUUID();
    var active = userJob(requestA, user, UUID.fromString("550e8400-e29b-41d4-a716-446655441019"));
    var replacement =
        userJob(requestB, user, UUID.fromString("550e8400-e29b-41d4-a716-446655441020"));

    registerRunning(active);
    registerQueuedReplacement(replacement);

    var lifecycleUpdater =
        new RedisHistoricalJobLifecycleUpdater(
            new RedisHistoricalJobLifecycleStateRepository(redisTemplate));
    assertTrue(
        lifecycleUpdater.markFailed(
            active.jobId(), "websocket-marked-delivery-failed", properties.getAdmissionTtl()));

    recovery(active.jobId()::equals).recoverStaleAdmission();

    assertEquals(List.of(), submittedJobs);
  }

  @Test
  void recoverStaleAdmission_whenActiveRunningMemberIsAlreadyTerminal_promotesReplacement() {
    UUID user = UUID.fromString("550e8400-e29b-41d4-a716-446655440120");
    var requestA = UUID.randomUUID();
    var requestB = UUID.randomUUID();
    var active = job(requestA, user, UUID.fromString("550e8400-e29b-41d4-a716-446655441014"));
    var replacement = job(requestB, user, UUID.fromString("550e8400-e29b-41d4-a716-446655441015"));

    registerRunning(active);
    assertTrue(stateGateway.markCompleted(active.jobId()));
    registerQueuedReplacement(replacement);

    recovery(ignored -> false).recoverStaleAdmission();

    assertEquals(List.of(replacement), submittedJobs);
  }

  @Test
  void recoverStaleAdmission_whenCancellationRequestedActiveRunningJobIsStale_cancelsAndPromotes() {
    UUID user = UUID.fromString("550e8400-e29b-41d4-a716-446655440120");
    var requestA = UUID.randomUUID();
    var requestB = UUID.randomUUID();
    var active = job(requestA, user, UUID.fromString("550e8400-e29b-41d4-a716-446655441016"));
    var replacement = job(requestB, user, UUID.fromString("550e8400-e29b-41d4-a716-446655441017"));

    registerRunning(active);
    markStaleCancellationRequested(active, "Superseded by request " + requestB);
    registerQueuedReplacement(replacement);

    recovery(ignored -> false).recoverStaleAdmission();

    assertEquals(
        HistoricalJobStage.CANCELLED,
        stateGateway.findByJobId(active.jobId()).orElseThrow().stage());
    assertEquals(List.of(replacement), submittedJobs);
  }

  @Test
  void recoverStaleAdmission_whenRunningOrphanIsStale_failsAndPromotesBacklog() {
    properties.setMaxConcurrentSubjectsPerJob(1);
    UUID user = UUID.fromString("550e8400-e29b-41d4-a716-446655440120");
    UUID request = UUID.randomUUID();
    var active = job(request, user, UUID.fromString("550e8400-e29b-41d4-a716-446655441026"));
    var backlogged = job(request, user, UUID.fromString("550e8400-e29b-41d4-a716-446655441027"));

    registerRunning(active);
    markStaleHeartbeat(active);
    registerQueuedReplacement(backlogged);

    recovery(ignored -> false).recoverStaleAdmission();

    var orphanFinal = stateGateway.findByJobId(active.jobId()).orElseThrow();
    assertEquals(HistoricalJobStage.EXECUTION_FAILED_INTERRUPTED, orphanFinal.stage());
    assertEquals(List.of(backlogged), submittedJobs);
  }

  @Test
  void recoverStaleAdmission_whenRunningJobIsTracked_doesNotTerminalizeIt() {
    UUID user = UUID.fromString("550e8400-e29b-41d4-a716-446655440120");
    UUID request = UUID.randomUUID();
    var active = job(request, user, UUID.fromString("550e8400-e29b-41d4-a716-446655441028"));

    registerRunning(active);
    markStaleHeartbeat(active);

    recovery(active.jobId()::equals).recoverStaleAdmission();

    var afterRecovery = stateGateway.findByJobId(active.jobId()).orElseThrow();
    assertFalse(afterRecovery.isTerminal(), "tracked worker must not be terminalized");
    assertEquals(HistoricalJobStage.EXECUTION_RUNNING, afterRecovery.stage());
    assertEquals(List.of(), submittedJobs);
  }

  @Test
  void recoverStaleAdmission_whenFirstObservationScanFails_doesNotDispatchOrMutate() {
    properties.setMaxConcurrentSubjectsPerJob(1);
    UUID user = UUID.fromString("550e8400-e29b-41d4-a716-446655440121");
    var active =
        job(UUID.randomUUID(), user, UUID.fromString("550e8400-e29b-41d4-a716-446655441029"));
    registerRunning(active);
    assertTrue(stateGateway.markCompleted(active.jobId()));

    var failingRedis = new ScanFailureRedisTemplate(connectionFactory);
    failingRedis.afterPropertiesSet();
    failingRedis.failOnScanCall(
        1, new RedisConnectionFailureException("first admission scan unavailable"));
    var failingCoordinator =
        RedisHistoricalJobAdmissionGateway.create(
            failingRedis, new ObjectMapper(), properties, new ProcessorHistoricalRedisScripts());
    var recovery = recovery(failingCoordinator, ignored -> false);
    try {
      assertThrows(RedisConnectionFailureException.class, recovery::recoverStaleAdmission);
    } finally {
      recovery.close();
    }

    assertTrue(
        redisTemplate
            .opsForSet()
            .isMember(
                HistoricalJobControlKeyLayout.STANDALONE.activeRunningKey(user),
                active.jobId().toString()));
    assertTrue(submittedJobs.isEmpty());
  }

  @Test
  void recoverStaleAdmission_whenTerminalDiscoveryFailsAfterValidRead_doesNotReleaseEntry() {
    properties.setMaxConcurrentSubjectsPerJob(1);
    UUID user = UUID.fromString("550e8400-e29b-41d4-a716-446655440123");
    UUID requestId = UUID.randomUUID();
    var active = job(requestId, user, UUID.fromString("550e8400-e29b-41d4-a716-446655441032"));
    var replacement = job(requestId, user, UUID.fromString("550e8400-e29b-41d4-a716-446655441033"));
    registerRunning(active);
    registerQueuedReplacement(replacement);
    assertTrue(stateGateway.markCompleted(active.jobId()));

    var failingRedis = new ScanFailureRedisTemplate(connectionFactory);
    failingRedis.afterPropertiesSet();
    failingRedis.failAfterFirstKey(
        new RedisConnectionFailureException("terminal discovery failed after state read"));
    var failingCoordinator =
        RedisHistoricalJobAdmissionGateway.create(
            failingRedis, new ObjectMapper(), properties, new ProcessorHistoricalRedisScripts());
    var recovery = recovery(failingCoordinator, ignored -> false);
    try {
      assertThrows(RedisConnectionFailureException.class, recovery::recoverStaleAdmission);
    } finally {
      recovery.close();
    }

    var activeRunningKey = HistoricalJobControlKeyLayout.STANDALONE.activeRunningKey(user);
    assertTrue(redisTemplate.opsForSet().isMember(activeRunningKey, active.jobId().toString()));
    assertTrue(
        redisTemplate.hasKey(
            HistoricalJobControlKeyLayout.STANDALONE
                .queuedPayload(user, replacement.jobId())
                .redisKey()));
    assertTrue(submittedJobs.isEmpty());
  }

  @Test
  void
      recoverStaleAdmission_whenSecondObservationScanFails_retainsFirstPassMutationWithoutDispatch() {
    properties.setMaxConcurrentSubjectsPerJob(1);
    UUID user = UUID.fromString("550e8400-e29b-41d4-a716-446655440122");
    UUID requestId = UUID.randomUUID();
    var active = job(requestId, user, UUID.fromString("550e8400-e29b-41d4-a716-446655441030"));
    var replacement = job(requestId, user, UUID.fromString("550e8400-e29b-41d4-a716-446655441031"));
    registerRunning(active);
    registerQueuedReplacement(replacement);
    assertTrue(stateGateway.markCompleted(active.jobId()));

    var failingRedis = new ScanFailureRedisTemplate(connectionFactory);
    failingRedis.afterPropertiesSet();
    failingRedis.failOnScanCall(
        2, new RedisConnectionFailureException("second admission scan unavailable"));
    var failingCoordinator =
        RedisHistoricalJobAdmissionGateway.create(
            failingRedis, new ObjectMapper(), properties, new ProcessorHistoricalRedisScripts());
    var recovery = recovery(failingCoordinator, ignored -> false);
    try {
      assertThrows(RedisConnectionFailureException.class, recovery::recoverStaleAdmission);
    } finally {
      recovery.close();
    }

    var activeRunningKey = HistoricalJobControlKeyLayout.STANDALONE.activeRunningKey(user);
    assertFalse(redisTemplate.opsForSet().isMember(activeRunningKey, active.jobId().toString()));
    assertTrue(
        redisTemplate.opsForSet().isMember(activeRunningKey, replacement.jobId().toString()));
    assertFalse(
        redisTemplate.hasKey(
            HistoricalJobControlKeyLayout.STANDALONE
                .queuedPayload(user, replacement.jobId())
                .redisKey()));
    assertTrue(submittedJobs.isEmpty());
  }

  private HistoricalJobAdmissionRecovery recovery(java.util.function.Predicate<UUID> jobIsTracked) {
    return recovery(coordinator, jobIsTracked);
  }

  private HistoricalJobAdmissionRecovery recovery(
      RedisHistoricalJobAdmissionGateway admissionCoordinator,
      java.util.function.Predicate<UUID> jobIsTracked) {
    var lifecycle =
        new HistoricalJobAdmissionLifecycle(
            stateGateway,
            admissionCoordinator,
            new HistoricalObservability(new SimpleMeterRegistry()),
            jobIsTracked,
            ignored -> {},
            (job, ignored) -> submittedJobs.add(job),
            (job, replacementRequestId) -> false,
            (eventName, context) -> {});
    return new HistoricalJobAdmissionRecovery(
        admissionCoordinator,
        stateGateway,
        mock(ScheduledExecutorService.class),
        properties.getAdmissionRecoveryInterval(),
        properties.getTtlRefreshInterval(),
        properties.getMaxRuntime(),
        properties.getJobStateTtl(),
        new HistoricalJobAdmissionRecoveryPolicy(),
        jobIsTracked,
        lifecycle::processRecoveredDecision);
  }

  private void registerRunning(HistoricalTracksJobSubmission job) {
    stateGateway.createReceived(job);
    assertEquals(Action.START_NOW, coordinator.register(job).action());
    assertTrue(stateGateway.markQueued(job.jobId()));
    assertTrue(stateGateway.markRunning(job.jobId(), "history-job-recovery"));
  }

  private void registerQueuedReplacement(HistoricalTracksJobSubmission job) {
    stateGateway.createReceived(job);
    var decision = coordinator.register(job);
    assertEquals(Action.QUEUE_ONLY, decision.action());
    assertTrue(stateGateway.markQueued(job.jobId(), decision.queueRole()));
  }

  private void markStaleCancellationRequested(
      HistoricalTracksJobSubmission job, String cancellationReason) {
    Instant staleHeartbeat =
        Instant.now().minus(properties.getTtlRefreshInterval().multipliedBy(3));
    redisTemplate
        .opsForHash()
        .putAll(
            HistoricalJobControlKeyLayout.STANDALONE.jobState(job.jobId()),
            Map.of(
                "stage",
                HistoricalJobStage.CANCELLATION_REQUESTED.name(),
                "failureReason",
                cancellationReason,
                "updatedAt",
                Instant.now().toString(),
                "expiresAt",
                staleHeartbeat.plus(properties.getJobStateTtl()).toString()));
  }

  private void markStaleHeartbeat(HistoricalTracksJobSubmission job) {
    Instant staleHeartbeat =
        Instant.now().minus(properties.getTtlRefreshInterval().multipliedBy(3));
    redisTemplate
        .opsForHash()
        .put(
            HistoricalJobControlKeyLayout.STANDALONE.jobState(job.jobId()),
            "expiresAt",
            staleHeartbeat.plus(properties.getJobStateTtl()).toString());
  }

  private static final class ScanFailureRedisTemplate extends StringRedisTemplate {

    private int scanCalls;
    private int failOnScanCall;
    private RuntimeException scanFailure;
    private RuntimeException lateScanFailure;

    private ScanFailureRedisTemplate(RedisConnectionFactory connectionFactory) {
      super(connectionFactory);
    }

    private void failOnScanCall(int call, RuntimeException failure) {
      failOnScanCall = call;
      scanFailure = failure;
    }

    private void failAfterFirstKey(RuntimeException failure) {
      lateScanFailure = failure;
    }

    @Override
    public Cursor<String> scan(ScanOptions options) {
      scanCalls++;
      if (scanCalls == failOnScanCall) {
        throw scanFailure;
      }
      var cursor = super.scan(options);
      return lateScanFailure == null
          ? cursor
          : new FailingAfterFirstCursor(cursor, lateScanFailure);
    }
  }

  private static final class FailingAfterFirstCursor implements Cursor<String> {

    private final Cursor<String> delegate;
    private final RuntimeException failure;
    private boolean yielded;

    private FailingAfterFirstCursor(Cursor<String> delegate, RuntimeException failure) {
      this.delegate = delegate;
      this.failure = failure;
    }

    @Override
    public boolean hasNext() {
      if (yielded) {
        throw failure;
      }
      return delegate.hasNext();
    }

    @Override
    public String next() {
      yielded = true;
      return delegate.next();
    }

    @Override
    public void close() {
      delegate.close();
    }

    @Override
    public boolean isClosed() {
      return delegate.isClosed();
    }

    @Override
    public long getPosition() {
      return delegate.getPosition();
    }

    @Override
    public long getCursorId() {
      return delegate.getCursorId();
    }

    @Override
    public Cursor.CursorId getId() {
      return delegate.getId();
    }

    @Override
    public void remove() {
      delegate.remove();
    }
  }

  private static HistoricalTracksJobSubmission job(
      UUID requestId, UUID requestedBy, UUID subjectId) {
    return HistoricalTracksJobSubmission.create(
        requestId,
        JobIssuer.system("historical-it"),
        SubjectType.SOURCE,
        subjectId,
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

  private static HistoricalTracksJobSubmission userJob(
      UUID requestId, UUID requestedBy, UUID subjectId) {
    return HistoricalTracksJobSubmission.create(
        requestId,
        JobIssuer.user(requestedBy),
        SubjectType.SOURCE,
        subjectId,
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
}
