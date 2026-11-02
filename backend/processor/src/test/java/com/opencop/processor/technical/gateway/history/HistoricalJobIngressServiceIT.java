// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.history;

import static com.opencop.processor.technical.gateway.history.model.job.HistoricalJobState.QUEUE_ROLE_QUEUED_REPLACEMENT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.opencop.core.sdk.component.redis.LocalRedisSupport;
import com.opencop.core.sdk.model.SubjectType;
import com.opencop.core.sdk.model.history.HistoricalJobStage;
import com.opencop.core.sdk.model.history.HistoricalResolution;
import com.opencop.core.sdk.model.job.HistoricalTracksJobSubmission;
import com.opencop.core.sdk.model.job.JobIssuer;
import com.opencop.processor.business.model.history.HistoricalExecutionContext;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkPlan;
import com.opencop.processor.business.policy.history.HistoricalChunkPlanner;
import com.opencop.processor.business.policy.history.HistoricalExecutionFailureClassifier;
import com.opencop.processor.business.policy.history.HistoricalJobAdmissionRecoveryPolicy;
import com.opencop.processor.business.usecase.event.JobStatusNotificationPublisher;
import com.opencop.processor.business.usecase.history.HistoricalJobAdmissionRecoveryFactory;
import com.opencop.processor.business.usecase.history.HistoricalJobCancelledException;
import com.opencop.processor.business.usecase.history.HistoricalSubjectJobFactory;
import com.opencop.processor.business.usecase.history.HistoricalSubjectLaneJobHandler;
import com.opencop.processor.business.usecase.history.HistoricalSubjectLaneScheduler;
import com.opencop.processor.ports.technical.gateway.history.HistoricalSubjectLaneExecutionGateway;
import com.opencop.processor.ports.technical.gateway.history.HistoryEventPublisher;
import com.opencop.processor.technical.gateway.history.cancellation.HistoricalJobCancellationFinalizer;
import com.opencop.processor.technical.gateway.history.execution.HistoricalJobExecutionOutcomeHandler;
import com.opencop.processor.technical.observability.history.HistoricalObservability;
import com.opencop.processor.technical.spring.config.HistoricalExecutionProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

class HistoricalJobIngressServiceIT {
  private static final Duration ASYNC_WAIT_TIMEOUT = Duration.ofSeconds(10);

  private LettuceConnectionFactory connectionFactory;
  private StringRedisTemplate redisTemplate;
  private HistoricalExecutionProperties properties;
  private RedisHistoricalJobStateGateway stateGateway;
  private RedisHistoricalJobAdmissionGateway coordinator;
  private CountingScheduler handler;
  private ExecutorService executorR1;
  private ExecutorService executorR2;
  private ScheduledExecutorService schedulerR1;
  private ScheduledExecutorService schedulerR2;
  private HistoricalJobIngressService replicaOne;
  private HistoricalJobIngressService replicaTwo;
  private HistoricalJobTerminalCoordinator terminalCoordinatorR1;
  private HistoricalJobTerminalCoordinator terminalCoordinatorR2;

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
    handler = new CountingScheduler(stateGateway);
    executorR1 =
        new ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
    executorR2 =
        new ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
    schedulerR1 = new ScheduledThreadPoolExecutor(1);
    schedulerR2 = new ScheduledThreadPoolExecutor(1);

    terminalCoordinatorR1 = new HistoricalJobTerminalCoordinator(stateGateway, coordinator);
    terminalCoordinatorR2 = new HistoricalJobTerminalCoordinator(stateGateway, coordinator);
    replicaOne = createService(executorR1, schedulerR1, terminalCoordinatorR1);
    replicaTwo = createService(executorR2, schedulerR2, terminalCoordinatorR2);
  }

  @AfterEach
  void tearDown() {
    replicaOne.close();
    replicaTwo.close();
    executorR1.shutdownNow();
    executorR2.shutdownNow();
    schedulerR1.shutdownNow();
    schedulerR2.shutdownNow();
    redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    connectionFactory.destroy();
  }

  @Test
  void
      acceptance41_replacementRemainsQueuedDuringDeliveryPendingAndActiveRequestStaysPredecessor() {
    UUID user = UUID.fromString("550e8400-e29b-41d4-a716-446655440120");
    var requestA = UUID.randomUUID();
    var requestB = UUID.randomUUID();
    var a1 = userJob(requestA, user, UUID.fromString("550e8400-e29b-41d4-a716-446655441001"));
    var b1 = userJob(requestB, user, UUID.fromString("550e8400-e29b-41d4-a716-446655441002"));

    handler.block(a1.jobId());
    replicaOne.accept(a1);
    handler.awaitStarted(a1.jobId());

    handler.block(b1.jobId());
    replicaTwo.accept(b1);
    var queuedState = stateGateway.findByJobId(b1.jobId()).orElseThrow();
    assertEquals(HistoricalJobStage.INGRESS_QUEUED, queuedState.stage());
    assertEquals(QUEUE_ROLE_QUEUED_REPLACEMENT, queuedState.queueRole());
    assertFalse(handler.hasStarted(b1.jobId()));
    handler.release(a1.jobId());
    waitUntil(
        () ->
            stateGateway.findByJobId(a1.jobId()).orElseThrow().stage()
                == HistoricalJobStage.DELIVERY_PENDING);
    assertFalse(handler.hasStarted(b1.jobId()));
    String activeRequestKey = "opencop:history:admission:user:%s:active-request".formatted(user);
    assertEquals(requestA.toString(), redisTemplate.opsForValue().get(activeRequestKey));
  }

  @Test
  void acceptance42_terminalOfDeliveryPendingUserJobPromotesReplacementViaLifecycleUpdater() {
    UUID user = UUID.fromString("550e8400-e29b-41d4-a716-446655440120");
    var requestA = UUID.randomUUID();
    var requestB = UUID.randomUUID();
    var a1 = userJob(requestA, user, UUID.fromString("550e8400-e29b-41d4-a716-446655441001"));
    var b1 = userJob(requestB, user, UUID.fromString("550e8400-e29b-41d4-a716-446655441002"));
    var lifecycleUpdater =
        new ProcessorHistoricalJobLifecycleUpdater(stateGateway, terminalCoordinatorR1);

    handler.block(a1.jobId());
    replicaOne.accept(a1);
    handler.awaitStarted(a1.jobId());

    handler.block(b1.jobId());
    replicaTwo.accept(b1);

    handler.release(a1.jobId());
    waitUntil(
        () ->
            stateGateway.findByJobId(a1.jobId()).orElseThrow().stage()
                == HistoricalJobStage.DELIVERY_PENDING);
    assertEquals(0, handler.startCount(b1.jobId()));
    lifecycleUpdater.markCompleted(a1.jobId(), Duration.ofHours(1));
    waitUntil(() -> handler.startCount(b1.jobId()) == 1);
    assertEquals(1, handler.startCount(b1.jobId()));
  }

  @Test
  void acceptance33_backlogPromotionRespectsMaxConcurrentSubjectsAcrossReplicas() {
    UUID user = UUID.fromString("550e8400-e29b-41d4-a716-446655440120");
    UUID request = UUID.randomUUID();
    var a1 = job(request, user, UUID.fromString("550e8400-e29b-41d4-a716-446655441011"));
    var a2 = job(request, user, UUID.fromString("550e8400-e29b-41d4-a716-446655441012"));
    var a3 = job(request, user, UUID.fromString("550e8400-e29b-41d4-a716-446655441013"));

    handler.block(a1.jobId());
    handler.block(a2.jobId());
    handler.block(a3.jobId());
    replicaOne.accept(a1);
    replicaTwo.accept(a2);
    replicaOne.accept(a3);
    handler.awaitStarted(a1.jobId());
    handler.awaitStarted(a2.jobId());
    assertFalse(handler.hasStarted(a3.jobId()));

    handler.release(a1.jobId());
    waitUntil(() -> handler.startCount(a3.jobId()) == 1);
    assertEquals(1, handler.startCount(a3.jobId()));
  }

  @Test
  void queuedReplacementWithThreeSubjectsStartsInArrivalOrderWithConcurrencyLimit() {
    UUID user = UUID.fromString("550e8400-e29b-41d4-a716-446655440120");
    UUID requestA = UUID.fromString("550e8400-e29b-41d4-a716-446655440720");
    UUID requestC = UUID.fromString("550e8400-e29b-41d4-a716-446655440721");
    var a1 = job(requestA, user, UUID.fromString("550e8400-e29b-41d4-a716-446655441720"));
    var c1 = job(requestC, user, UUID.fromString("550e8400-e29b-41d4-a716-446655441721"));
    var c2 = job(requestC, user, UUID.fromString("550e8400-e29b-41d4-a716-446655441722"));
    var c3 = job(requestC, user, UUID.fromString("550e8400-e29b-41d4-a716-446655441723"));

    handler.block(a1.jobId());
    handler.block(c1.jobId());
    handler.block(c2.jobId());
    handler.block(c3.jobId());
    replicaOne.accept(a1);
    handler.awaitStarted(a1.jobId());

    replicaTwo.accept(c1);
    replicaOne.accept(c2);
    replicaTwo.accept(c3);
    assertFalse(handler.hasStarted(c1.jobId()));
    assertFalse(handler.hasStarted(c2.jobId()));
    assertFalse(handler.hasStarted(c3.jobId()));

    handler.release(a1.jobId());
    waitUntil(() -> handler.hasStarted(c1.jobId()) && handler.hasStarted(c2.jobId()));

    assertTrue(handler.hasStarted(c1.jobId()));
    assertTrue(handler.hasStarted(c2.jobId()));
    assertFalse(handler.hasStarted(c3.jobId()));

    handler.release(c1.jobId());
    waitUntil(() -> handler.hasStarted(c3.jobId()));
    assertTrue(handler.startIndex(c3.jobId()) > handler.startIndex(c1.jobId()));
    assertTrue(handler.startIndex(c3.jobId()) > handler.startIndex(c2.jobId()));
  }

  @Test
  void acceptance34_cancellationRequestedReplacementReturnedFromTerminalIsNotStarted() {
    UUID user = UUID.fromString("550e8400-e29b-41d4-a716-446655440120");
    var requestA = UUID.randomUUID();
    var requestB = UUID.randomUUID();
    var a1 = job(requestA, user, UUID.fromString("550e8400-e29b-41d4-a716-446655441021"));
    var b1 = job(requestB, user, UUID.fromString("550e8400-e29b-41d4-a716-446655441022"));

    handler.block(a1.jobId());
    replicaOne.accept(a1);
    handler.awaitStarted(a1.jobId());

    replicaTwo.accept(b1);
    assertTrue(stateGateway.markCancellationRequested(b1.jobId(), "requested"));
    handler.release(a1.jobId());
    waitUntil(() -> stateGateway.findByJobId(a1.jobId()).orElseThrow().isTerminal());

    waitUntil(() -> stateGateway.findByJobId(b1.jobId()).orElseThrow().isTerminal());
    var bState = stateGateway.findByJobId(b1.jobId()).orElseThrow();
    assertEquals(HistoricalJobStage.CANCELLED, bState.stage());
    assertEquals(0, handler.startCount(b1.jobId()));
  }

  @Test
  void acceptance35_duplicateTerminalCallbacksAcrossReplicasPromoteOnce() {
    UUID user = UUID.fromString("550e8400-e29b-41d4-a716-446655440120");
    var requestA = UUID.randomUUID();
    var requestB = UUID.randomUUID();
    var a1 = job(requestA, user, UUID.fromString("550e8400-e29b-41d4-a716-446655441031"));
    var b1 = job(requestB, user, UUID.fromString("550e8400-e29b-41d4-a716-446655441032"));

    handler.block(a1.jobId());
    replicaOne.accept(a1);
    handler.awaitStarted(a1.jobId());
    replicaTwo.accept(b1);
    handler.release(a1.jobId());
    waitUntil(() -> handler.startCount(b1.jobId()) == 1);

    terminalCoordinatorR1.finalizeIfTerminal(a1.jobId());
    terminalCoordinatorR2.finalizeIfTerminal(a1.jobId());

    assertEquals(1, handler.startCount(b1.jobId()));
  }

  @Test
  void acceptance36_cancelledBacklogDrainPromotesQueuedReplacementAcrossReplicas() {
    properties.setMaxConcurrentSubjectsPerJob(1);
    UUID user = UUID.fromString("550e8400-e29b-41d4-a716-446655440120");
    var requestA = UUID.randomUUID();
    var requestB = UUID.randomUUID();
    var a1 = job(requestA, user, UUID.fromString("550e8400-e29b-41d4-a716-446655441041"));
    var a2 = job(requestA, user, UUID.fromString("550e8400-e29b-41d4-a716-446655441042"));
    var b1 = job(requestB, user, UUID.fromString("550e8400-e29b-41d4-a716-446655441043"));

    handler.block(a1.jobId());
    handler.block(b1.jobId());
    replicaOne.accept(a1);
    handler.awaitStarted(a1.jobId());
    replicaTwo.accept(a2);
    replicaOne.accept(b1);
    assertFalse(handler.hasStarted(a2.jobId()));
    assertFalse(handler.hasStarted(b1.jobId()));

    assertTrue(stateGateway.markCancellationRequested(a2.jobId(), "requested"));
    handler.release(a1.jobId());
    waitUntil(() -> handler.startCount(b1.jobId()) == 1);

    var a2State = stateGateway.findByJobId(a2.jobId()).orElseThrow();
    assertEquals(HistoricalJobStage.CANCELLED, a2State.stage());
    assertEquals(0, handler.startCount(a2.jobId()));
    assertEquals(1, handler.startCount(b1.jobId()));

    terminalCoordinatorR1.finalizeIfTerminal(a1.jobId());
    terminalCoordinatorR2.finalizeIfTerminal(a2.jobId());
    assertEquals(1, handler.startCount(b1.jobId()));
  }

  private HistoricalJobIngressService createService(
      ExecutorService executor,
      ScheduledExecutorService scheduler,
      HistoricalJobTerminalCoordinator terminalCoordinator) {
    var notifications = new NoOpNotifications();
    var historyEventPublisher = new NoOpHistoryEventPublisher();
    var historicalObservability = new HistoricalObservability(new SimpleMeterRegistry());
    var failureClassifier = new HistoricalExecutionFailureClassifier();
    var executionSupervisor =
        new HistoricalJobExecutionSupervisor(
            stateGateway,
            notifications,
            historyEventPublisher,
            executor,
            executor,
            scheduler,
            properties,
            historicalObservability);
    var cancellationFinalizer =
        new HistoricalJobCancellationFinalizer(
            stateGateway,
            historyEventPublisher,
            historicalObservability,
            new HistoryJobLifecycleEventRecorder());
    var executionOutcomeHandler =
        new HistoricalJobExecutionOutcomeHandler(
            stateGateway,
            mock(HistoricalSubjectLaneJobHandler.class),
            notifications,
            historyEventPublisher,
            historicalObservability,
            failureClassifier);
    var rejectionHandler =
        new HistoricalJobIngressRejectionHandler(
            stateGateway, notifications, historyEventPublisher, historicalObservability);
    return new HistoricalJobIngressService(
        stateGateway,
        coordinator,
        historicalObservability,
        scheduler,
        properties,
        terminalCoordinator,
        executionSupervisor,
        cancellationFinalizer,
        executionOutcomeHandler,
        rejectionHandler,
        new HistoricalJobAdmissionRecoveryFactory(
            properties.getAdmissionRecoveryInterval(),
            properties.getTtlRefreshInterval(),
            properties.getMaxRuntime(),
            properties.getJobStateTtl(),
            new HistoricalJobAdmissionRecoveryPolicy()),
        handler);
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

  private static void waitUntil(Check check) {
    long deadline = System.nanoTime() + ASYNC_WAIT_TIMEOUT.toNanos();
    while (System.nanoTime() < deadline) {
      if (check.satisfied()) {
        return;
      }
      try {
        Thread.sleep(10L);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      }
    }
    assertTrue(check.satisfied());
  }

  @FunctionalInterface
  private interface Check {
    boolean satisfied();
  }

  private static final class CountingScheduler extends HistoricalSubjectLaneScheduler {
    private final ConcurrentMap<UUID, CountDownLatch> blocks = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, CountDownLatch> started = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, AtomicInteger> startCounts = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, RuntimeException> failures = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<UUID> startOrder = new CopyOnWriteArrayList<>();
    private final RedisHistoricalJobStateGateway stateGateway;

    private CountingScheduler(RedisHistoricalJobStateGateway stateGateway) {
      super(
          noOpLaneExecutionGateway(),
          mock(HistoricalSubjectJobFactory.class),
          mock(HistoricalChunkPlanner.class),
          schedulerProperties());
      this.stateGateway = stateGateway;
    }

    void block(UUID jobId) {
      blocks.put(jobId, new CountDownLatch(1));
    }

    void release(UUID jobId) {
      CountDownLatch latch = blocks.get(jobId);
      if (latch != null) {
        latch.countDown();
      }
    }

    void awaitStarted(UUID jobId) {
      try {
        assertTrue(
            started
                .computeIfAbsent(jobId, ignored -> new CountDownLatch(1))
                .await(ASYNC_WAIT_TIMEOUT.toNanos(), TimeUnit.NANOSECONDS));
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }

    boolean hasStarted(UUID jobId) {
      return startCount(jobId) > 0;
    }

    int startCount(UUID jobId) {
      return startCounts.getOrDefault(jobId, new AtomicInteger(0)).get();
    }

    int startIndex(UUID jobId) {
      return startOrder.indexOf(jobId);
    }

    @Override
    public void execute(HistoricalTracksJobSubmission job) {
      assertTrue(stateGateway.markRunning(job.jobId(), Thread.currentThread().getName()));
      startOrder.add(job.jobId());
      startCounts.computeIfAbsent(job.jobId(), ignored -> new AtomicInteger()).incrementAndGet();
      started.computeIfAbsent(job.jobId(), ignored -> new CountDownLatch(1)).countDown();
      CountDownLatch latch = blocks.get(job.jobId());
      if (latch != null) {
        try {
          latch.await(ASYNC_WAIT_TIMEOUT.toNanos(), TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }
      if (stateGateway
          .findByJobId(job.jobId())
          .filter(state -> HistoricalJobStage.CANCELLATION_REQUESTED.equals(state.stage()))
          .isPresent()) {
        throw new HistoricalJobCancelledException(job.jobId(), "cancelled");
      }
      RuntimeException failure = failures.get(job.jobId());
      if (failure != null) {
        throw failure;
      }
    }
  }

  private static HistoricalSubjectLaneExecutionGateway noOpLaneExecutionGateway() {
    return (laneMetadata, laneStarted) -> laneStarted.run();
  }

  private static HistoricalExecutionProperties schedulerProperties() {
    var properties = new HistoricalExecutionProperties();
    properties.setMaxConcurrentSubjectsPerJob(1);
    return properties;
  }

  private static final class NoOpNotifications extends JobStatusNotificationPublisher {
    private NoOpNotifications() {
      super(null, new ObjectMapper());
    }
  }

  private static final class NoOpHistoryEventPublisher implements HistoryEventPublisher {
    @Override
    public void publishPlan(
        HistoricalChunkPlan plan, HistoricalExecutionContext executionContext) {}

    @Override
    public void publishProgress(
        HistoricalChunkPlan plan,
        HistoricalTracksJobSubmission job,
        long computedChunks,
        long deliveredChunks,
        long failedChunks) {}

    @Override
    public void publishExecutionProgress(
        HistoricalChunkPlan plan,
        HistoricalExecutionContext executionContext,
        long computedChunks,
        long failedChunks) {}

    @Override
    public void publishCompleted(
        HistoricalChunkPlan plan,
        HistoricalTracksJobSubmission job,
        long computedChunks,
        long deliveredChunks,
        long failedChunks) {}

    @Override
    public void publishFailed(
        HistoricalTracksJobSubmission job, HistoricalJobStage stage, String failureReason) {}
  }
}
