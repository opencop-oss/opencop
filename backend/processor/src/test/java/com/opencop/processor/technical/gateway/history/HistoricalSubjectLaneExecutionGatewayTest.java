// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.history;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.opencop.core.sdk.model.SubjectType;
import com.opencop.core.sdk.model.history.HistoricalJobStage;
import com.opencop.core.sdk.model.history.HistoricalResolution;
import com.opencop.core.sdk.model.job.HistoricalTracksJobSubmission;
import com.opencop.core.sdk.model.job.HistoricalTracksSubject;
import com.opencop.core.sdk.model.job.JobIssuer;
import com.opencop.core.sdk.model.job.JobSubmission;
import com.opencop.core.sdk.model.job.JobType;
import com.opencop.processor.business.model.history.HistoricalExecutionContext;
import com.opencop.processor.business.model.history.HistoricalSubjectExecutionMeta;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkPlan;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkPlanEntry;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkWindowPlan;
import com.opencop.processor.business.policy.history.HistoricalChunkPlanner;
import com.opencop.processor.business.policy.history.HistoricalExecutionFailureClassifier;
import com.opencop.processor.business.usecase.event.JobStatusNotificationPublisher;
import com.opencop.processor.business.usecase.history.HistoricalSubjectJobFactory;
import com.opencop.processor.business.usecase.history.HistoricalSubjectLaneJobHandler;
import com.opencop.processor.business.usecase.history.HistoricalSubjectLaneScheduler;
import com.opencop.processor.ports.technical.gateway.history.HistoricalJobStateGateway;
import com.opencop.processor.ports.technical.gateway.history.HistoryEventPublisher;
import com.opencop.processor.technical.gateway.history.execution.HistoricalJobExecutionOutcomeHandler;
import com.opencop.processor.technical.gateway.history.model.job.HistoricalJobState;
import com.opencop.processor.technical.gateway.history.model.job.HistoricalJobStatus;
import com.opencop.processor.technical.observability.history.HistoricalObservability;
import com.opencop.processor.technical.spring.config.HistoricalExecutionProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class HistoricalSubjectLaneExecutionGatewayTest {

  private static final UUID REQUEST_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440300");
  private static final UUID REQUESTED_BY = UUID.fromString("550e8400-e29b-41d4-a716-446655440301");
  private static final UUID SUBJECT_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440302");
  private static final UUID JOB_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440303");
  private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440304");
  private static final Instant REQUESTED_AT = Instant.parse("2026-04-07T10:00:00Z");
  private static final Instant REQUESTED_FROM = Instant.parse("2026-04-07T10:01:00Z");
  private static final Instant REQUESTED_TO = Instant.parse("2026-04-07T10:02:00Z");
  private static final Instant EFFECTIVE_FROM = Instant.parse("2026-04-07T10:01:00Z");
  private static final Instant EFFECTIVE_TO = Instant.parse("2026-04-07T10:02:00Z");
  private static final long PLANNED_CHUNKS = 42L;

  @Test
  void execute_whenChildLaneStarts_createsReceivedAndQueuedBeforeExecutionMarksRunning()
      throws Exception {
    var job = childSubmission();
    var metadata = subjectMetadata(job);
    var stateGateway = new TrackingHistoricalJobStateGateway();
    var handler = mock(HistoricalSubjectLaneJobHandler.class);
    var events = new CopyOnWriteArrayList<String>();
    var handlerStarted = new CountDownLatch(1);
    var callbackStarted = new AtomicBoolean();

    doAnswer(
            invocation -> {
              events.add("handler-enter");
              assertTrue(
                  stateGateway.markRunning(job.jobId(), "worker-1"),
                  "handler expected job state to be markable as running");
              events.add("handler-running");
              handlerStarted.countDown();
              return null;
            })
        .when(handler)
        .handle(metadata);

    var outcomeHandler = buildOutcomeHandler(stateGateway, handler);
    var scheduler = Executors.newSingleThreadScheduledExecutor();
    try {
      var supervisor =
          new HistoricalJobExecutionSupervisor(
              stateGateway,
              new NoOpJobStatusNotificationPublisher(),
              new NoOpHistoryEventPublisher(),
              new DirectExecutorService(),
              new DirectExecutorService(),
              scheduler,
              executionProperties(
                  Duration.ofHours(24), Duration.ofMinutes(30), Duration.ofMillis(10)),
              new HistoricalObservability(new SimpleMeterRegistry()));

      var gateway =
          new ProcessorHistoricalSubjectLaneExecutionGateway(
              stateGateway, supervisor, outcomeHandler);
      gateway.execute(
          metadata,
          () -> {
            events.add("lane-started");
            callbackStarted.set(true);
            var currentState = stateGateway.findByJobId(metadata.laneJobId()).orElseThrow();
            assertEquals(HistoricalJobStatus.QUEUED, currentState.status());
          });
    } finally {
      scheduler.shutdownNow();
    }

    assertTrue(callbackStarted.get(), "lane-started callback should run before execution");
    assertTrue(handlerStarted.await(1, SECONDS), "handler did not start");
    verify(handler).handle(metadata);
    assertEquals("received", stateGateway.transitions().get(0));
    assertEquals("queued", stateGateway.transitions().get(1));
    assertTrue(
        events.indexOf("lane-started") < events.indexOf("handler-enter"),
        "laneStarted must fire before handler execution");
    assertEquals(List.of("received", "queued", "running"), stateGateway.transitions());
  }

  @Test
  void execute_forLongRunning_refreshesTrackedActiveTtl() throws Exception {
    var job = childSubmission();
    var metadata = subjectMetadata(job);
    var stateGateway = new TrackingHistoricalJobStateGateway();
    var handler = mock(HistoricalSubjectLaneJobHandler.class);
    var startedExecution = new CountDownLatch(1);
    var allowCompletion = new CountDownLatch(1);

    doAnswer(
            invocation -> {
              assertTrue(stateGateway.markRunning(job.jobId(), "worker-1"));
              startedExecution.countDown();
              assertTrue(allowCompletion.await(3, SECONDS));
              return null;
            })
        .when(handler)
        .handle(metadata);

    var outcomeHandler = buildOutcomeHandler(stateGateway, handler);
    ExecutorService executor = Executors.newSingleThreadExecutor();
    ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    try {
      var supervisor =
          new HistoricalJobExecutionSupervisor(
              stateGateway,
              new NoOpJobStatusNotificationPublisher(),
              new NoOpHistoryEventPublisher(),
              executor,
              executor,
              scheduler,
              executionProperties(
                  Duration.ofHours(24), Duration.ofMillis(500), Duration.ofMillis(10)),
              new HistoricalObservability(new SimpleMeterRegistry()));
      var gateway =
          new ProcessorHistoricalSubjectLaneExecutionGateway(
              stateGateway, supervisor, outcomeHandler);

      CompletableFuture<Void> execution =
          CompletableFuture.runAsync(() -> gateway.execute(metadata, () -> {}));
      assertTrue(startedExecution.await(1, SECONDS));
      assertTrue(awaitRefresh(stateGateway, metadata.laneJobId(), Duration.ofMillis(250)));
      allowCompletion.countDown();
      execution.get(1, SECONDS);
    } finally {
      executor.shutdownNow();
      scheduler.shutdownNow();
    }

    assertTrue(stateGateway.refreshCount() > 0);
    assertEquals(metadata.laneJobId(), stateGateway.lastRefreshJobId());
    verify(handler).handle(metadata);
  }

  @Test
  void execute_waitsForAsyncLaneCompletionBeforeReturning() throws Exception {
    var job = childSubmission();
    var metadata = subjectMetadata(job);
    var stateGateway = new TrackingHistoricalJobStateGateway();
    var handler = mock(HistoricalSubjectLaneJobHandler.class);
    var startedExecution = new CountDownLatch(1);
    var allowCompletion = new CountDownLatch(1);

    doAnswer(
            invocation -> {
              assertTrue(stateGateway.markRunning(job.jobId(), "worker-1"));
              startedExecution.countDown();
              assertTrue(allowCompletion.await(3, SECONDS));
              return null;
            })
        .when(handler)
        .handle(metadata);

    var outcomeHandler = buildOutcomeHandler(stateGateway, handler);
    ExecutorService executor = Executors.newSingleThreadExecutor();
    ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    try {
      var supervisor =
          new HistoricalJobExecutionSupervisor(
              stateGateway,
              new NoOpJobStatusNotificationPublisher(),
              new NoOpHistoryEventPublisher(),
              executor,
              executor,
              scheduler,
              executionProperties(
                  Duration.ofHours(24), Duration.ofMinutes(30), Duration.ofMillis(10)),
              new HistoricalObservability(new SimpleMeterRegistry()));
      var gateway =
          new ProcessorHistoricalSubjectLaneExecutionGateway(
              stateGateway, supervisor, outcomeHandler);

      CompletableFuture<Void> execution =
          CompletableFuture.runAsync(() -> gateway.execute(metadata, () -> {}));
      assertTrue(startedExecution.await(1, SECONDS));
      assertFalse(execution.isDone(), "gateway returned before supervised lane completion");

      allowCompletion.countDown();
      execution.get(1, SECONDS);
    } finally {
      executor.shutdownNow();
      scheduler.shutdownNow();
    }

    verify(handler).handle(metadata);
  }

  @Test
  void parentAndLaneExecutionsUseSeparatePoolsSoConcurrentParentsDoNotStarveLanes()
      throws Exception {
    var firstJob = parentSubmission(UUID.randomUUID(), UUID.randomUUID());
    var secondJob = parentSubmission(UUID.randomUUID(), UUID.randomUUID());
    var stateGateway = new MultiJobTrackingHistoricalJobStateGateway();
    var handler = mock(HistoricalSubjectLaneJobHandler.class);
    var lanesStarted = new CountDownLatch(2);
    var allowCompletion = new CountDownLatch(1);

    doAnswer(
            invocation -> {
              HistoricalSubjectExecutionMeta metadata = invocation.getArgument(0);
              assertTrue(stateGateway.markRunning(metadata.laneJobId(), "worker-1"));
              lanesStarted.countDown();
              assertTrue(allowCompletion.await(3, SECONDS));
              return null;
            })
        .when(handler)
        .handle(any(HistoricalSubjectExecutionMeta.class));

    var properties =
        executionProperties(Duration.ofHours(24), Duration.ofSeconds(5), Duration.ofMillis(10));
    properties.setMaxConcurrentSubjectsPerJob(1);
    var outcomeHandler = buildOutcomeHandler(stateGateway, handler);
    ExecutorService parentExecutor =
        new ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
    ExecutorService laneExecutor =
        new ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
    ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    var parentsCompleted = new CountDownLatch(2);
    AtomicReference<RuntimeException> parentFailure = new AtomicReference<>();
    try {
      var supervisor =
          new HistoricalJobExecutionSupervisor(
              stateGateway,
              new NoOpJobStatusNotificationPublisher(),
              new NoOpHistoryEventPublisher(),
              parentExecutor,
              laneExecutor,
              scheduler,
              properties,
              new HistoricalObservability(new SimpleMeterRegistry()));
      var gateway =
          new ProcessorHistoricalSubjectLaneExecutionGateway(
              stateGateway, supervisor, outcomeHandler);
      var laneScheduler =
          new HistoricalSubjectLaneScheduler(
              gateway, new HistoricalSubjectJobFactory(), new HistoricalChunkPlanner(), properties);

      supervisor.submitParent(
          parentMetadata(firstJob),
          ignored -> executeParentLaneScheduler(laneScheduler, firstJob, parentFailure),
          ignored -> parentsCompleted.countDown(),
          ignored -> {});
      supervisor.submitParent(
          parentMetadata(secondJob),
          ignored -> executeParentLaneScheduler(laneScheduler, secondJob, parentFailure),
          ignored -> parentsCompleted.countDown(),
          ignored -> {});

      assertTrue(
          lanesStarted.await(1, SECONDS),
          () ->
              "lane workers should not wait behind parents; parent failure=" + parentFailure.get());
      allowCompletion.countDown();
      assertTrue(parentsCompleted.await(2, SECONDS), "parents should finish after lanes complete");
      if (parentFailure.get() != null) {
        throw parentFailure.get();
      }
    } finally {
      parentExecutor.shutdownNow();
      laneExecutor.shutdownNow();
      scheduler.shutdownNow();
    }

    verify(handler, times(2)).handle(any(HistoricalSubjectExecutionMeta.class));
  }

  @Test
  void execute_whenChildLaneAlreadyQueuedOrReceived_skipsHandlingButAcknowledgesStart()
      throws Exception {
    var job = childSubmission();
    var metadata = subjectMetadata(job);
    var stateGateway = new TrackingHistoricalJobStateGateway();
    stateGateway.createReceived(job);
    var handler = mock(HistoricalSubjectLaneJobHandler.class);
    var handlerStarted = new CountDownLatch(1);
    var startAcknowledged = new CountDownLatch(1);

    doAnswer(
            invocation -> {
              handlerStarted.countDown();
              return null;
            })
        .when(handler)
        .handle(metadata);

    var outcomeHandler = buildOutcomeHandler(stateGateway, handler);
    var scheduler = Executors.newSingleThreadScheduledExecutor();
    try {
      var supervisor =
          new HistoricalJobExecutionSupervisor(
              stateGateway,
              new NoOpJobStatusNotificationPublisher(),
              new NoOpHistoryEventPublisher(),
              new DirectExecutorService(),
              new DirectExecutorService(),
              scheduler,
              executionProperties(
                  Duration.ofHours(24), Duration.ofMinutes(30), Duration.ofMillis(10)),
              new HistoricalObservability(new SimpleMeterRegistry()));

      var gateway =
          new ProcessorHistoricalSubjectLaneExecutionGateway(
              stateGateway, supervisor, outcomeHandler);
      gateway.execute(
          metadata,
          () -> {
            assertEquals(
                HistoricalJobStatus.RECEIVED, stateGateway.findByJobId(job.jobId()).get().status());
            startAcknowledged.countDown();
          });
    } finally {
      scheduler.shutdownNow();
    }

    assertTrue(
        startAcknowledged.await(1, SECONDS), "start callback must be invoked on skipped lane");
    assertFalse(
        handlerStarted.await(250, TimeUnit.MILLISECONDS),
        "skipped child lane must not invoke the execution outcome handler");
    verify(handler, never()).handle(any(HistoricalSubjectExecutionMeta.class));
  }

  private static HistoricalJobExecutionOutcomeHandler buildOutcomeHandler(
      HistoricalJobStateGateway stateGateway, HistoricalSubjectLaneJobHandler handler) {
    return new HistoricalJobExecutionOutcomeHandler(
        stateGateway,
        handler,
        new NoOpJobStatusNotificationPublisher(),
        new NoOpHistoryEventPublisher(),
        new HistoricalObservability(new SimpleMeterRegistry()),
        new HistoricalExecutionFailureClassifier());
  }

  private static void executeParentLaneScheduler(
      HistoricalSubjectLaneScheduler laneScheduler,
      HistoricalTracksJobSubmission job,
      AtomicReference<RuntimeException> parentFailure) {
    try {
      laneScheduler.execute(job);
    } catch (RuntimeException e) {
      parentFailure.compareAndSet(null, e);
      throw e;
    }
  }

  private static HistoricalExecutionProperties executionProperties(
      Duration jobStateTtl, Duration maxRuntime, Duration ttlRefreshInterval) {
    HistoricalExecutionProperties properties = new HistoricalExecutionProperties();
    properties.setJobStateTtl(jobStateTtl);
    properties.setMaxRuntime(maxRuntime);
    properties.setTtlRefreshInterval(ttlRefreshInterval);
    return properties;
  }

  private static boolean awaitRefresh(
      TrackingHistoricalJobStateGateway stateGateway, UUID jobId, Duration timeout)
      throws InterruptedException {
    long deadlineNanos = System.nanoTime() + timeout.toNanos();
    while (System.nanoTime() < deadlineNanos) {
      if (stateGateway.refreshCount() > 0 && jobId.equals(stateGateway.lastRefreshJobId())) {
        return true;
      }
      Thread.sleep(10);
    }
    return false;
  }

  private static HistoricalTracksJobSubmission childSubmission() {
    return new HistoricalTracksJobSubmission(
        JOB_ID,
        REQUEST_ID,
        JobIssuer.user(USER_ID),
        List.of(new HistoricalTracksSubject(JOB_ID, SubjectType.SOURCE, SUBJECT_ID)),
        REQUESTED_BY,
        REQUESTED_AT,
        REQUESTED_FROM,
        REQUESTED_TO,
        EFFECTIVE_FROM,
        EFFECTIVE_TO,
        HistoricalResolution.MINUTE_1,
        PLANNED_CHUNKS,
        List.of());
  }

  private static HistoricalTracksJobSubmission parentSubmission(UUID parentJobId, UUID laneJobId) {
    return new HistoricalTracksJobSubmission(
        parentJobId,
        UUID.randomUUID(),
        JobIssuer.user(USER_ID),
        List.of(new HistoricalTracksSubject(laneJobId, SubjectType.SOURCE, SUBJECT_ID)),
        REQUESTED_BY,
        REQUESTED_AT,
        REQUESTED_FROM,
        REQUESTED_TO,
        EFFECTIVE_FROM,
        EFFECTIVE_TO,
        HistoricalResolution.MINUTE_1,
        1L,
        List.of());
  }

  private static HistoricalSubjectExecutionMeta parentMetadata(
      HistoricalTracksJobSubmission parentSubmission) {
    return new HistoricalSubjectExecutionMeta(
        parentSubmission.jobId(),
        parentSubmission.jobId(),
        parentSubmission.requestId(),
        parentSubmission.issuer(),
        parentSubmission.requestedBy(),
        parentSubmission.requestedAt(),
        parentSubmission.requestedFrom(),
        parentSubmission.requestedTo(),
        parentSubmission.effectiveFrom(),
        parentSubmission.effectiveTo(),
        parentSubmission.resolution(),
        parentSubmission.plannedChunks(),
        parentSubmission.filters(),
        parentSubmission.subjects().getFirst().subjectType(),
        parentSubmission.subjects().getFirst().subjectId(),
        new HistoricalChunkWindowPlan(
            parentSubmission.effectiveFrom(),
            parentSubmission.effectiveTo(),
            parentSubmission.plannedChunks(),
            parentSubmission.resolution(),
            List.of()));
  }

  private static HistoricalSubjectExecutionMeta subjectMetadata(
      HistoricalTracksJobSubmission childSubmission) {
    return new HistoricalSubjectExecutionMeta(
        childSubmission.jobId(),
        childSubmission.jobId(),
        childSubmission.requestId(),
        childSubmission.issuer(),
        childSubmission.requestedBy(),
        childSubmission.requestedAt(),
        childSubmission.requestedFrom(),
        childSubmission.requestedTo(),
        childSubmission.effectiveFrom(),
        childSubmission.effectiveTo(),
        childSubmission.resolution(),
        childSubmission.plannedChunks(),
        childSubmission.filters(),
        childSubmission.subjects().getFirst().subjectType(),
        childSubmission.subjects().getFirst().subjectId(),
        new HistoricalChunkWindowPlan(
            childSubmission.effectiveFrom(),
            childSubmission.effectiveTo(),
            childSubmission.plannedChunks(),
            childSubmission.resolution(),
            List.of(
                new HistoricalChunkPlanEntry(
                    0,
                    "chunk-key",
                    UUID.randomUUID().toString(),
                    null,
                    null,
                    childSubmission.effectiveFrom(),
                    childSubmission.effectiveFrom().plusSeconds(60)))));
  }

  private static final class TrackingHistoricalJobStateGateway
      implements HistoricalJobStateGateway {
    private volatile HistoricalJobState currentState;
    private final List<String> transitions = new ArrayList<>();
    private final AtomicInteger refreshCount = new AtomicInteger();
    private final AtomicReference<UUID> lastRefreshJobId = new AtomicReference<>();
    private final HistoricalExecutionProperties properties =
        executionProperties(Duration.ofHours(24), Duration.ofMinutes(30), Duration.ofMillis(10));

    List<String> transitions() {
      return transitions;
    }

    int refreshCount() {
      return refreshCount.get();
    }

    UUID lastRefreshJobId() {
      return lastRefreshJobId.get();
    }

    @Override
    public void createReceived(HistoricalTracksJobSubmission job) {
      createReceivedIfAbsent(job);
    }

    @Override
    public void createReceived(HistoricalSubjectExecutionMeta laneMetadata) {
      createReceivedIfAbsent(laneMetadata);
    }

    @Override
    public synchronized boolean createReceivedIfAbsent(HistoricalTracksJobSubmission job) {
      if (currentState != null) {
        return false;
      }
      transitions.add("received");
      currentState = HistoricalJobState.received(job, Instant.now(), properties.getJobStateTtl());
      return true;
    }

    @Override
    public synchronized boolean createReceivedIfAbsent(
        HistoricalSubjectExecutionMeta laneMetadata) {
      if (currentState != null) {
        return false;
      }
      transitions.add("received");
      Instant now = Instant.now();
      currentState =
          new HistoricalJobState(
              laneMetadata.laneJobId(),
              laneMetadata.requestId(),
              JobType.HISTORICAL_TRACKS.name(),
              laneMetadata.subjectType(),
              laneMetadata.subjectId(),
              laneMetadata.requestedBy(),
              laneMetadata.requestedAt(),
              laneMetadata.requestedFrom(),
              laneMetadata.requestedTo(),
              laneMetadata.effectiveFrom(),
              laneMetadata.effectiveTo(),
              laneMetadata.resolution(),
              laneMetadata.plannedChunks(),
              HistoricalJobStatus.RECEIVED,
              HistoricalJobStage.INGRESS_RECEIVED,
              null,
              now,
              now.plus(properties.getJobStateTtl()),
              null,
              null,
              0L,
              0L,
              "");
      return true;
    }

    @Override
    public synchronized boolean markQueued(UUID jobId) {
      return markQueued(jobId, HistoricalJobState.QUEUE_ROLE_DISPATCH_READY);
    }

    @Override
    public synchronized boolean markQueued(UUID jobId, String queueRole) {
      if (currentState == null
          || !currentState.jobId().equals(jobId)
          || currentState.isTerminal()) {
        return false;
      }
      transitions.add("queued");
      currentState = currentState.queued(Instant.now(), properties.getJobStateTtl(), queueRole);
      return true;
    }

    @Override
    public synchronized boolean markRunning(UUID jobId, String workerId) {
      if (currentState == null
          || !currentState.jobId().equals(jobId)
          || currentState.isTerminal()) {
        return false;
      }
      transitions.add("running");
      currentState = currentState.running(Instant.now(), properties.getJobStateTtl(), workerId);
      return true;
    }

    @Override
    public synchronized boolean markPlanning(UUID jobId) {
      return true;
    }

    @Override
    public synchronized boolean markPlanned(UUID jobId) {
      return true;
    }

    @Override
    public synchronized boolean markCompleted(UUID jobId) {
      return true;
    }

    @Override
    public synchronized boolean markDeliveredCompleted(UUID jobId) {
      return true;
    }

    @Override
    public synchronized boolean markDeliveryPending(UUID jobId) {
      return true;
    }

    @Override
    public synchronized boolean markDeliveryFailed(UUID jobId, String failureReason) {
      return true;
    }

    @Override
    public synchronized boolean markFailed(
        UUID jobId, HistoricalJobStage stage, String failureReason) {
      return true;
    }

    @Override
    public synchronized boolean markCancellationRequested(UUID jobId, String reason) {
      return true;
    }

    @Override
    public synchronized boolean markCancelled(UUID jobId, String reason) {
      return true;
    }

    @Override
    public synchronized boolean refreshActiveTtl(UUID jobId) {
      if (currentState == null || !currentState.jobId().equals(jobId)) {
        return false;
      }
      refreshCount.incrementAndGet();
      lastRefreshJobId.set(jobId);
      return true;
    }

    @Override
    public synchronized Optional<HistoricalJobState> findByJobId(UUID jobId) {
      if (currentState != null && currentState.jobId().equals(jobId)) {
        return Optional.of(currentState);
      }
      return Optional.empty();
    }
  }

  private static final class MultiJobTrackingHistoricalJobStateGateway
      implements HistoricalJobStateGateway {
    private final Map<UUID, HistoricalJobState> states = new ConcurrentHashMap<>();
    private final HistoricalExecutionProperties properties =
        executionProperties(Duration.ofHours(24), Duration.ofMinutes(30), Duration.ofMillis(10));

    @Override
    public void createReceived(HistoricalTracksJobSubmission job) {
      createReceivedIfAbsent(job);
    }

    @Override
    public boolean createReceivedIfAbsent(HistoricalTracksJobSubmission job) {
      return states.putIfAbsent(
              job.jobId(),
              HistoricalJobState.received(job, Instant.now(), properties.getJobStateTtl()))
          == null;
    }

    @Override
    public void createReceived(HistoricalSubjectExecutionMeta laneMetadata) {
      createReceivedIfAbsent(laneMetadata);
    }

    @Override
    public boolean createReceivedIfAbsent(HistoricalSubjectExecutionMeta laneMetadata) {
      Instant now = Instant.now();
      HistoricalJobState state =
          new HistoricalJobState(
              laneMetadata.laneJobId(),
              laneMetadata.requestId(),
              JobType.HISTORICAL_TRACKS.name(),
              laneMetadata.subjectType(),
              laneMetadata.subjectId(),
              laneMetadata.requestedBy(),
              laneMetadata.requestedAt(),
              laneMetadata.requestedFrom(),
              laneMetadata.requestedTo(),
              laneMetadata.effectiveFrom(),
              laneMetadata.effectiveTo(),
              laneMetadata.resolution(),
              laneMetadata.plannedChunks(),
              HistoricalJobStatus.RECEIVED,
              HistoricalJobStage.INGRESS_RECEIVED,
              null,
              now,
              now.plus(properties.getJobStateTtl()),
              null,
              null,
              0L,
              0L,
              "");
      return states.putIfAbsent(laneMetadata.laneJobId(), state) == null;
    }

    @Override
    public boolean markQueued(UUID jobId, String queueRole) {
      return update(
          jobId, state -> state.queued(Instant.now(), properties.getJobStateTtl(), queueRole));
    }

    @Override
    public boolean markRunning(UUID jobId, String workerId) {
      return update(
          jobId, state -> state.running(Instant.now(), properties.getJobStateTtl(), workerId));
    }

    @Override
    public boolean markPlanning(UUID jobId) {
      return true;
    }

    @Override
    public boolean markPlanned(UUID jobId) {
      return true;
    }

    @Override
    public boolean markCompleted(UUID jobId) {
      return update(jobId, state -> state.completed(Instant.now(), properties.getJobStateTtl()));
    }

    @Override
    public boolean markDeliveredCompleted(UUID jobId) {
      return markCompleted(jobId);
    }

    @Override
    public boolean markDeliveryPending(UUID jobId) {
      return update(
          jobId, state -> state.deliveryPending(Instant.now(), properties.getJobStateTtl()));
    }

    @Override
    public boolean markDeliveryFailed(UUID jobId, String failureReason) {
      return markFailed(jobId, HistoricalJobStage.DELIVERY_FAILED, failureReason);
    }

    @Override
    public boolean markFailed(UUID jobId, HistoricalJobStage stage, String failureReason) {
      return update(
          jobId,
          state -> state.failed(Instant.now(), properties.getJobStateTtl(), stage, failureReason));
    }

    @Override
    public boolean markCancellationRequested(UUID jobId, String reason) {
      return true;
    }

    @Override
    public boolean markCancelled(UUID jobId, String reason) {
      return update(jobId, state -> state.cancelled(Instant.now(), reason));
    }

    @Override
    public boolean refreshActiveTtl(UUID jobId) {
      return states.containsKey(jobId);
    }

    @Override
    public Optional<HistoricalJobState> findByJobId(UUID jobId) {
      return Optional.ofNullable(states.get(jobId));
    }

    private boolean update(
        UUID jobId, java.util.function.Function<HistoricalJobState, HistoricalJobState> update) {
      AtomicBoolean updated = new AtomicBoolean();
      states.computeIfPresent(
          jobId,
          (ignored, current) -> {
            updated.set(true);
            return update.apply(current);
          });
      return updated.get();
    }
  }

  private static final class DirectExecutorService extends AbstractExecutorService {
    private volatile boolean shutdown;

    @Override
    public void shutdown() {
      shutdown = true;
    }

    @Override
    public List<Runnable> shutdownNow() {
      shutdown = true;
      return List.of();
    }

    @Override
    public boolean isShutdown() {
      return shutdown;
    }

    @Override
    public boolean isTerminated() {
      return shutdown;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) {
      return true;
    }

    @Override
    public void execute(Runnable command) {
      command.run();
    }
  }

  private static final class NoOpJobStatusNotificationPublisher
      extends JobStatusNotificationPublisher {
    private NoOpJobStatusNotificationPublisher() {
      super(null, new ObjectMapper());
    }

    @Override
    public void notifySuccess(JobSubmission job) {}

    @Override
    public void notifyFailure(JobSubmission job, Throwable exception) {}
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
