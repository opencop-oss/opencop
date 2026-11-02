// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.history;

import static com.opencop.processor.technical.gateway.history.model.job.HistoricalJobState.QUEUE_ROLE_ACTIVE_REQUEST_BACKLOG;
import static com.opencop.processor.technical.gateway.history.model.job.HistoricalJobState.QUEUE_ROLE_QUEUED_REPLACEMENT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.opencop.core.sdk.model.SubjectType;
import com.opencop.core.sdk.model.history.HistoricalJobStage;
import com.opencop.core.sdk.model.history.HistoricalResolution;
import com.opencop.core.sdk.model.job.HistoricalTracksJobSubmission;
import com.opencop.core.sdk.model.job.HistoricalTracksSubject;
import com.opencop.core.sdk.model.job.JobIssuer;
import com.opencop.core.sdk.model.job.JobSubmission;
import com.opencop.processor.business.model.history.HistoricalExecutionContext;
import com.opencop.processor.business.model.history.HistoricalSubjectExecutionMeta;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkPlan;
import com.opencop.processor.business.policy.history.HistoricalCacheIdentity;
import com.opencop.processor.business.policy.history.HistoricalChunkCacheAnalyzer;
import com.opencop.processor.business.policy.history.HistoricalChunkPlanner;
import com.opencop.processor.business.policy.history.HistoricalExecutionFailureClassifier;
import com.opencop.processor.business.policy.history.HistoricalJobAdmissionRecoveryPolicy;
import com.opencop.processor.business.usecase.event.JobStatusNotificationPublisher;
import com.opencop.processor.business.usecase.history.HistoricalChunkExecutionCoordinator;
import com.opencop.processor.business.usecase.history.HistoricalJobAdmissionRecoveryFactory;
import com.opencop.processor.business.usecase.history.HistoricalJobCancelledException;
import com.opencop.processor.business.usecase.history.HistoricalSubjectJobFactory;
import com.opencop.processor.business.usecase.history.HistoricalSubjectLaneJobHandler;
import com.opencop.processor.business.usecase.history.HistoricalSubjectLaneScheduler;
import com.opencop.processor.ports.technical.gateway.history.HistoricalChunkPayloadCacheGateway;
import com.opencop.processor.ports.technical.gateway.history.HistoricalChunkStateGateway;
import com.opencop.processor.ports.technical.gateway.history.HistoricalJobAdmissionCoordinator;
import com.opencop.processor.ports.technical.gateway.history.HistoricalJobStateGateway;
import com.opencop.processor.ports.technical.gateway.history.HistoricalPlanGateway;
import com.opencop.processor.ports.technical.gateway.history.HistoricalSubjectLaneExecutionGateway;
import com.opencop.processor.ports.technical.gateway.history.HistoryEventPublisher;
import com.opencop.processor.technical.gateway.history.cancellation.HistoricalJobCancellationFinalizer;
import com.opencop.processor.technical.gateway.history.execution.HistoricalJobExecutionOutcomeHandler;
import com.opencop.processor.technical.gateway.history.model.chunk.HistoricalChunkState;
import com.opencop.processor.technical.gateway.history.model.chunk.HistoricalChunkStateStatus;
import com.opencop.processor.technical.gateway.history.model.job.HistoricalJobAdmissionDecision;
import com.opencop.processor.technical.gateway.history.model.job.HistoricalJobState;
import com.opencop.processor.technical.gateway.history.model.job.HistoricalJobStatus;
import com.opencop.processor.technical.observability.history.HistoricalObservability;
import com.opencop.processor.technical.spring.config.HistoricalExecutionProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class HistoricalJobIngressServiceTest {

  private static final String CANCELLED_EXIT_EVENT = "history.job.cancelled_exit";

  @Test
  void constructor_exposesFocusedPublicConstructorWithExtractedTechnicalCollaborators() {
    var constructors = HistoricalJobIngressService.class.getConstructors();

    var constructor =
        Arrays.stream(constructors)
            .filter(candidate -> candidate.getParameterCount() == 12)
            .findFirst()
            .orElseThrow();
    assertEquals(HistoricalJobAdmissionCoordinator.class, constructor.getParameterTypes()[1]);
    assertEquals(HistoricalObservability.class, constructor.getParameterTypes()[2]);
    assertEquals(HistoricalJobTerminalCoordinator.class, constructor.getParameterTypes()[5]);
    assertEquals(HistoricalJobExecutionSupervisor.class, constructor.getParameterTypes()[6]);
    assertEquals(HistoricalJobCancellationFinalizer.class, constructor.getParameterTypes()[7]);
    assertEquals(HistoricalJobExecutionOutcomeHandler.class, constructor.getParameterTypes()[8]);
    assertEquals(HistoricalJobIngressRejectionHandler.class, constructor.getParameterTypes()[9]);
    assertEquals(HistoricalJobAdmissionRecoveryFactory.class, constructor.getParameterTypes()[10]);
    assertEquals(HistoricalSubjectLaneScheduler.class, constructor.getParameterTypes()[11]);
  }

  @Test
  void accept_whenAdmissionStartsNow_andQueueOnly_jobInvokesSchedulerOnlyForStartNow() {
    var repository = new MultiJobInMemoryHistoricalJobStateRepository();
    var handler = new RecordingHistoricalSubjectLaneScheduler(repository);
    var notifications = new RecordingJobStatusNotificationPublisher();
    var properties = properties(Duration.ofMinutes(30), Duration.ofMinutes(5));
    var scheduler = new ScheduledThreadPoolExecutor(1);
    var executor =
        new ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
    var queuedJob =
        multiSubjectJob(
            UUID.randomUUID(),
            UUID.fromString("550e8400-e29b-41d4-a716-446655440120"),
            UUID.fromString("550e8400-e29b-41d4-a716-446655441011"),
            UUID.fromString("550e8400-e29b-41d4-a716-446655441012"));
    var startNowJob =
        multiSubjectJob(
            UUID.randomUUID(),
            UUID.fromString("550e8400-e29b-41d4-a716-446655440120"),
            UUID.fromString("550e8400-e29b-41d4-a716-446655441021"),
            UUID.fromString("550e8400-e29b-41d4-a716-446655441022"));
    var coordinator =
        scriptedAdmissionCoordinator()
            .onRegister(
                queuedJob.jobId(),
                HistoricalJobAdmissionDecision.queueOnly(
                    QUEUE_ROLE_QUEUED_REPLACEMENT, true, false))
            .onRegister(startNowJob.jobId(), HistoricalJobAdmissionDecision.startNow());
    var subjectLaneScheduler = mock(HistoricalSubjectLaneScheduler.class);
    var service =
        new HistoricalJobIngressService(
            repository,
            coordinator,
            new HistoricalObservability(new SimpleMeterRegistry()),
            scheduler,
            properties,
            new HistoricalJobTerminalCoordinator(repository, coordinator),
            new HistoricalJobExecutionSupervisor(
                repository,
                notifications,
                new NoOpHistoryEventPublisher(),
                executor,
                executor,
                scheduler,
                properties,
                new HistoricalObservability(new SimpleMeterRegistry())),
            new HistoricalJobCancellationFinalizer(
                repository,
                new NoOpHistoryEventPublisher(),
                new HistoricalObservability(new SimpleMeterRegistry()),
                new HistoryJobLifecycleEventRecorder()),
            new HistoricalJobExecutionOutcomeHandler(
                repository,
                mock(HistoricalSubjectLaneJobHandler.class),
                notifications,
                new NoOpHistoryEventPublisher(),
                new HistoricalObservability(new SimpleMeterRegistry()),
                new HistoricalExecutionFailureClassifier()),
            new HistoricalJobIngressRejectionHandler(
                repository,
                notifications,
                new NoOpHistoryEventPublisher(),
                new HistoricalObservability(new SimpleMeterRegistry())),
            admissionRecoveryFactory(properties),
            subjectLaneScheduler);
    try {
      service.accept(queuedJob);
      verify(subjectLaneScheduler, never()).execute(queuedJob);

      service.accept(startNowJob);
      verify(subjectLaneScheduler, timeout(1000)).execute(startNowJob);
    } finally {
      scheduler.shutdownNow();
      executor.shutdownNow();
    }
  }

  @Test
  void accept_whenQueuedReplacementStartsFromAdmission_usesSubjectLaneScheduler() {
    var repository = new MultiJobInMemoryHistoricalJobStateRepository();
    var handler = new RecordingHistoricalSubjectLaneScheduler(repository);
    var notifications = new RecordingJobStatusNotificationPublisher();
    var properties = properties(Duration.ofMinutes(30), Duration.ofMinutes(5));
    var scheduler = new ScheduledThreadPoolExecutor(1);
    var executor =
        new ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
    var requestedBy = UUID.fromString("550e8400-e29b-41d4-a716-446655440120");
    var requestA = UUID.randomUUID();
    var requestB = UUID.randomUUID();
    var predecessor = job(requestA, requestedBy, UUID.randomUUID());
    var replacement = job(requestB, requestedBy, UUID.randomUUID());
    var coordinator =
        scriptedAdmissionCoordinator()
            .onRegister(predecessor.jobId(), HistoricalJobAdmissionDecision.startNow())
            .onRegister(
                replacement.jobId(),
                HistoricalJobAdmissionDecision.queueOnly(
                    QUEUE_ROLE_QUEUED_REPLACEMENT, true, false))
            .onTerminal(
                predecessor.jobId(),
                HistoricalJobAdmissionDecision.noop(List.of(replacement), List.of(), true));
    var subjectLaneScheduler = mock(HistoricalSubjectLaneScheduler.class);
    var service =
        new HistoricalJobIngressService(
            repository,
            coordinator,
            new HistoricalObservability(new SimpleMeterRegistry()),
            scheduler,
            properties,
            new HistoricalJobTerminalCoordinator(repository, coordinator),
            new HistoricalJobExecutionSupervisor(
                repository,
                notifications,
                new NoOpHistoryEventPublisher(),
                executor,
                executor,
                scheduler,
                properties,
                new HistoricalObservability(new SimpleMeterRegistry())),
            new HistoricalJobCancellationFinalizer(
                repository,
                new NoOpHistoryEventPublisher(),
                new HistoricalObservability(new SimpleMeterRegistry()),
                new HistoryJobLifecycleEventRecorder()),
            new HistoricalJobExecutionOutcomeHandler(
                repository,
                mock(HistoricalSubjectLaneJobHandler.class),
                notifications,
                new NoOpHistoryEventPublisher(),
                new HistoricalObservability(new SimpleMeterRegistry()),
                new HistoricalExecutionFailureClassifier()),
            new HistoricalJobIngressRejectionHandler(
                repository,
                notifications,
                new NoOpHistoryEventPublisher(),
                new HistoricalObservability(new SimpleMeterRegistry())),
            admissionRecoveryFactory(properties),
            subjectLaneScheduler);
    try {
      service.accept(replacement);
      verify(subjectLaneScheduler, never()).execute(replacement);

      service.accept(predecessor);

      verify(subjectLaneScheduler, timeout(1000)).execute(predecessor);
      verify(subjectLaneScheduler, timeout(1000)).execute(replacement);
      assertEquals(null, handler.lastHandledJob);
    } finally {
      scheduler.shutdownNow();
      executor.shutdownNow();
    }
  }

  @Test
  void accept_whenRealChildLaneFails_marksParentFailedAndTerminalizesQueuedSibling() {
    var repository = new MultiJobInMemoryHistoricalJobStateRepository();
    var notifications = new RecordingJobStatusNotificationPublisher();
    var historyEventPublisher = new RecordingHistoryEventPublisher();
    var properties = properties(Duration.ofMinutes(30), Duration.ofMillis(10));
    properties.setMaxConcurrentSubjectsPerJob(2);
    var scheduler = new ScheduledThreadPoolExecutor(1);
    ExecutorService parentExecutor =
        new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
    ExecutorService laneExecutor =
        new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
    var job =
        multiSubjectJob(
            UUID.randomUUID(),
            UUID.fromString("550e8400-e29b-41d4-a716-446655440120"),
            UUID.fromString("550e8400-e29b-41d4-a716-446655442001"),
            UUID.fromString("550e8400-e29b-41d4-a716-446655442002"));
    UUID firstLaneId = job.subjects().get(0).jobId();
    UUID secondLaneId = job.subjects().get(1).jobId();
    var laneHandler = mock(HistoricalSubjectLaneJobHandler.class);
    try {
      org.mockito.Mockito.doAnswer(
              invocation -> {
                HistoricalSubjectExecutionMeta laneMetadata = invocation.getArgument(0);
                repository.markRunning(laneMetadata.laneJobId(), Thread.currentThread().getName());
                if (laneMetadata.laneJobId().equals(firstLaneId)) {
                  awaitCondition(
                      () -> repository.findByJobId(secondLaneId).isPresent(),
                      "second lane was not queued before first lane failed");
                  throw new IllegalStateException("boom");
                }
                throw new AssertionError("queued sibling lane should not execute after failure");
              })
          .when(laneHandler)
          .handle(org.mockito.ArgumentMatchers.any(HistoricalSubjectExecutionMeta.class));
      var historicalObservability = new HistoricalObservability(new SimpleMeterRegistry());
      var coordinator =
          scriptedAdmissionCoordinator()
              .onRegister(job.jobId(), HistoricalJobAdmissionDecision.startNow());
      var terminalCoordinator = new HistoricalJobTerminalCoordinator(repository, coordinator);
      var executionSupervisor =
          new HistoricalJobExecutionSupervisor(
              repository,
              notifications,
              historyEventPublisher,
              parentExecutor,
              laneExecutor,
              scheduler,
              properties,
              historicalObservability);
      var executionOutcomeHandler =
          new HistoricalJobExecutionOutcomeHandler(
              repository,
              laneHandler,
              notifications,
              historyEventPublisher,
              historicalObservability,
              new HistoricalExecutionFailureClassifier());
      var laneGateway =
          new ProcessorHistoricalSubjectLaneExecutionGateway(
              repository, executionSupervisor, executionOutcomeHandler);
      var laneScheduler =
          new HistoricalSubjectLaneScheduler(
              laneGateway,
              new HistoricalSubjectJobFactory(),
              new HistoricalChunkPlanner(),
              properties);
      var service =
          new HistoricalJobIngressService(
              repository,
              coordinator,
              historicalObservability,
              scheduler,
              properties,
              terminalCoordinator,
              executionSupervisor,
              new HistoricalJobCancellationFinalizer(
                  repository,
                  historyEventPublisher,
                  historicalObservability,
                  new HistoryJobLifecycleEventRecorder()),
              executionOutcomeHandler,
              new HistoricalJobIngressRejectionHandler(
                  repository, notifications, historyEventPublisher, historicalObservability),
              admissionRecoveryFactory(properties),
              laneScheduler);

      service.accept(job);
      awaitCondition(
          () -> notifications.failureJob != null, "parent failure notification was not emitted");

      var parentState = repository.findByJobId(job.jobId()).orElseThrow();
      var firstLaneState = repository.findByJobId(firstLaneId).orElseThrow();
      var secondLaneState = repository.findByJobId(secondLaneId).orElseThrow();
      assertEquals(HistoricalJobStatus.FAILED, parentState.status());
      assertEquals(HistoricalJobStage.EXECUTION_FAILED_UNEXPECTED, parentState.stage());
      assertEquals(HistoricalJobStatus.FAILED, firstLaneState.status());
      assertEquals(HistoricalJobStage.EXECUTION_FAILED_UNEXPECTED, firstLaneState.stage());
      assertTrue(
          secondLaneState.status() == HistoricalJobStatus.FAILED
              || secondLaneState.status() == HistoricalJobStatus.CANCELLED);
      assertEquals(job, notifications.failureJob);
      assertEquals(job, historyEventPublisher.lastFailedJob);
      assertEquals(
          HistoricalJobStage.EXECUTION_FAILED_UNEXPECTED, historyEventPublisher.lastFailedStage);
    } finally {
      parentExecutor.shutdownNow();
      laneExecutor.shutdownNow();
      scheduler.shutdownNow();
    }
  }

  @Test
  void accept_whenSameUserReplacementArrives_queuesReplacementAndRequestsPredecessorCancellation() {
    var repository = new MultiJobInMemoryHistoricalJobStateRepository();
    var handler = new LatchingHistoricalSubjectLaneScheduler(repository);
    var notifications = new RecordingJobStatusNotificationPublisher();
    var properties = properties(Duration.ofMinutes(30), Duration.ofMinutes(5));
    var scheduler = new ScheduledThreadPoolExecutor(1);
    var executor =
        new ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
    var requestedBy = UUID.fromString("550e8400-e29b-41d4-a716-446655440120");
    var requestA = UUID.randomUUID();
    var requestB = UUID.randomUUID();
    var a1 = job(requestA, requestedBy, UUID.fromString("550e8400-e29b-41d4-a716-446655441001"));
    var a2 = job(requestA, requestedBy, UUID.fromString("550e8400-e29b-41d4-a716-446655441002"));
    var b1 = job(requestB, requestedBy, UUID.fromString("550e8400-e29b-41d4-a716-446655441003"));
    var coordinator =
        scriptedAdmissionCoordinator()
            .onRegister(
                b1.jobId(),
                new HistoricalJobAdmissionDecision(
                    HistoricalJobAdmissionDecision.Action.QUEUE_ONLY,
                    QUEUE_ROLE_QUEUED_REPLACEMENT,
                    List.of(),
                    List.of(
                        cancellation(a1.jobId(), "replacement"),
                        cancellation(a2.jobId(), "replacement")),
                    true,
                    false,
                    false));
    var service =
        newService(
            repository,
            handler,
            notifications,
            new NoOpHistoryEventPublisher(),
            executor,
            scheduler,
            properties,
            coordinator,
            new HistoricalObservability(new SimpleMeterRegistry()),
            GlobalOpenTelemetry.getTracer(HistoricalJobIngressServiceTest.class.getName()),
            new HistoricalJobTerminalCoordinator(repository, coordinator),
            new HistoricalExecutionFailureClassifier());

    try {
      handler.blockJob(a1.jobId());
      handler.blockJob(a2.jobId());
      service.accept(a1);
      service.accept(a2);
      handler.awaitStarted(a1.jobId());
      handler.awaitStarted(a2.jobId());

      service.accept(b1);

      var queuedState = repository.findByJobId(b1.jobId()).orElseThrow();
      assertEquals(HistoricalJobStatus.QUEUED, queuedState.status());
      assertEquals(HistoricalJobStage.INGRESS_QUEUED, queuedState.stage());
      assertEquals(QUEUE_ROLE_QUEUED_REPLACEMENT, queuedState.queueRole());
      assertFalse(handler.hasStarted(b1.jobId()));
      assertCancellationRequestedOrCancelled(repository.findByJobId(a1.jobId()).orElseThrow());
      assertCancellationRequestedOrCancelled(repository.findByJobId(a2.jobId()).orElseThrow());
    } finally {
      handler.releaseAll();
      executor.shutdownNow();
      scheduler.shutdownNow();
    }
  }

  @Test
  void accept_whenPredecessorCancels_startsQueuedReplacementOnlyAfterSecondTerminalState() {
    var repository = new MultiJobInMemoryHistoricalJobStateRepository();
    var handler = new LatchingHistoricalSubjectLaneScheduler(repository);
    var notifications = new RecordingJobStatusNotificationPublisher();
    var properties = properties(Duration.ofMinutes(30), Duration.ofMinutes(5));
    var scheduler = new ScheduledThreadPoolExecutor(1);
    var executor =
        new ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
    var requestedBy = UUID.fromString("550e8400-e29b-41d4-a716-446655440120");
    var requestA = UUID.randomUUID();
    var requestB = UUID.randomUUID();
    var a1 = job(requestA, requestedBy, UUID.fromString("550e8400-e29b-41d4-a716-446655441011"));
    var a2 = job(requestA, requestedBy, UUID.fromString("550e8400-e29b-41d4-a716-446655441012"));
    var b1 = job(requestB, requestedBy, UUID.fromString("550e8400-e29b-41d4-a716-446655441013"));
    var coordinator =
        scriptedAdmissionCoordinator()
            .onRegister(
                b1.jobId(),
                HistoricalJobAdmissionDecision.queueOnly(
                    QUEUE_ROLE_QUEUED_REPLACEMENT, true, false))
            .onTerminal(
                a1.jobId(), HistoricalJobAdmissionDecision.noop(List.of(), List.of(), false))
            .onTerminal(
                a2.jobId(), HistoricalJobAdmissionDecision.noop(List.of(b1), List.of(), true));
    var service =
        newService(
            repository,
            handler,
            notifications,
            new NoOpHistoryEventPublisher(),
            executor,
            scheduler,
            properties,
            coordinator,
            new HistoricalObservability(new SimpleMeterRegistry()),
            GlobalOpenTelemetry.getTracer(HistoricalJobIngressServiceTest.class.getName()),
            new HistoricalJobTerminalCoordinator(repository, coordinator),
            new HistoricalExecutionFailureClassifier());

    try {
      handler.blockJob(a1.jobId());
      handler.blockJob(a2.jobId());
      handler.throwCancelled(a1.jobId());
      handler.throwCancelled(a2.jobId());
      handler.blockJob(b1.jobId());
      service.accept(a1);
      service.accept(a2);
      handler.awaitStarted(a1.jobId());
      handler.awaitStarted(a2.jobId());
      service.accept(b1);
      handler.release(a1.jobId());

      long firstCancellationDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
      while (System.nanoTime() < firstCancellationDeadline) {
        var a1State = repository.findByJobId(a1.jobId()).orElseThrow();
        if (a1State.stage() == HistoricalJobStage.CANCELLED) {
          break;
        }
      }
      int firstCancelledIndex =
          repository.transitionIndex(a1.jobId(), HistoricalJobStage.CANCELLED);
      assertTrue(firstCancelledIndex >= 0);
      assertFalse(handler.hasStarted(b1.jobId()));

      handler.release(a2.jobId());

      long secondCancellationDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
      while (System.nanoTime() < secondCancellationDeadline) {
        var a2State = repository.findByJobId(a2.jobId()).orElseThrow();
        if (a2State.stage() == HistoricalJobStage.CANCELLED) {
          break;
        }
      }

      int secondCancelledIndex =
          repository.transitionIndex(a2.jobId(), HistoricalJobStage.CANCELLED);
      int replacementStartedIndex = -1;
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
      while (System.nanoTime() < deadline && replacementStartedIndex < 0) {
        replacementStartedIndex =
            repository.transitionIndex(b1.jobId(), HistoricalJobStage.EXECUTION_RUNNING);
      }
      assertTrue(secondCancelledIndex >= 0);
      assertTrue(replacementStartedIndex > secondCancelledIndex);
    } finally {
      handler.releaseAll();
      executor.shutdownNow();
      scheduler.shutdownNow();
    }
  }

  @Test
  void
      accept_whenUserPredecessorDeliveredCompleted_startsQueuedReplacementAfterLifecycleTerminal() {
    var repository = new MultiJobInMemoryHistoricalJobStateRepository();
    var handler = new LatchingHistoricalSubjectLaneScheduler(repository);
    var notifications = new RecordingJobStatusNotificationPublisher();
    var properties = properties(Duration.ofMinutes(30), Duration.ofMinutes(5));
    var scheduler = new ScheduledThreadPoolExecutor(1);
    var executor =
        new ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
    var requestedBy = UUID.fromString("550e8400-e29b-41d4-a716-446655440120");
    var requestA = UUID.randomUUID();
    var requestB = UUID.randomUUID();
    var a1 = job(requestA, requestedBy, UUID.fromString("550e8400-e29b-41d4-a716-446655441111"));
    var b1 = job(requestB, requestedBy, UUID.fromString("550e8400-e29b-41d4-a716-446655441112"));
    var coordinator =
        scriptedAdmissionCoordinator()
            .onRegister(
                b1.jobId(),
                HistoricalJobAdmissionDecision.queueOnly(
                    QUEUE_ROLE_QUEUED_REPLACEMENT, true, false))
            .onTerminal(
                a1.jobId(), HistoricalJobAdmissionDecision.noop(List.of(b1), List.of(), true));
    var terminalCoordinator = new HistoricalJobTerminalCoordinator(repository, coordinator);
    var service =
        newService(
            repository,
            handler,
            notifications,
            new NoOpHistoryEventPublisher(),
            executor,
            scheduler,
            properties,
            coordinator,
            new HistoricalObservability(new SimpleMeterRegistry()),
            GlobalOpenTelemetry.getTracer(HistoricalJobIngressServiceTest.class.getName()),
            terminalCoordinator,
            new HistoricalExecutionFailureClassifier());
    var lifecycleUpdater =
        new ProcessorHistoricalJobLifecycleUpdater(repository, terminalCoordinator);

    try {
      handler.blockJob(a1.jobId());
      handler.blockJob(b1.jobId());
      service.accept(a1);
      handler.awaitStarted(a1.jobId());
      service.accept(b1);
      handler.release(a1.jobId());

      long pendingDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
      while (System.nanoTime() < pendingDeadline) {
        var aState = repository.findByJobId(a1.jobId()).orElseThrow();
        if (aState.stage() == HistoricalJobStage.DELIVERY_PENDING) {
          break;
        }
      }
      assertFalse(handler.hasStarted(b1.jobId()));
      lifecycleUpdater.markCompleted(a1.jobId(), Duration.ofHours(1));
      handler.awaitStarted(b1.jobId());
      var a1State = repository.findByJobId(a1.jobId()).orElseThrow();
      assertEquals(HistoricalJobStage.EXECUTION_COMPLETED, a1State.stage());
      assertTrue(handler.hasStarted(b1.jobId()));
    } finally {
      handler.releaseAll();
      executor.shutdownNow();
      scheduler.shutdownNow();
    }
  }

  @Test
  void accept_whenUserPredecessorDeliveredFailed_startsQueuedReplacementAfterLifecycleTerminal() {
    var repository = new MultiJobInMemoryHistoricalJobStateRepository();
    var handler = new LatchingHistoricalSubjectLaneScheduler(repository);
    var notifications = new RecordingJobStatusNotificationPublisher();
    var properties = properties(Duration.ofMinutes(30), Duration.ofMinutes(5));
    var scheduler = new ScheduledThreadPoolExecutor(1);
    var executor =
        new ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
    var requestedBy = UUID.fromString("550e8400-e29b-41d4-a716-446655440120");
    var requestA = UUID.randomUUID();
    var requestB = UUID.randomUUID();
    var a1 = job(requestA, requestedBy, UUID.fromString("550e8400-e29b-41d4-a716-446655441121"));
    var b1 = job(requestB, requestedBy, UUID.fromString("550e8400-e29b-41d4-a716-446655441122"));
    var coordinator =
        scriptedAdmissionCoordinator()
            .onRegister(
                b1.jobId(),
                HistoricalJobAdmissionDecision.queueOnly(
                    QUEUE_ROLE_QUEUED_REPLACEMENT, true, false))
            .onTerminal(
                a1.jobId(), HistoricalJobAdmissionDecision.noop(List.of(b1), List.of(), true));
    var terminalCoordinator = new HistoricalJobTerminalCoordinator(repository, coordinator);
    var service =
        newService(
            repository,
            handler,
            notifications,
            new NoOpHistoryEventPublisher(),
            executor,
            scheduler,
            properties,
            coordinator,
            new HistoricalObservability(new SimpleMeterRegistry()),
            GlobalOpenTelemetry.getTracer(HistoricalJobIngressServiceTest.class.getName()),
            terminalCoordinator,
            new HistoricalExecutionFailureClassifier());
    var lifecycleUpdater =
        new ProcessorHistoricalJobLifecycleUpdater(repository, terminalCoordinator);

    try {
      handler.blockJob(a1.jobId());
      handler.blockJob(b1.jobId());
      service.accept(a1);
      handler.awaitStarted(a1.jobId());
      service.accept(b1);
      handler.release(a1.jobId());

      long pendingDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
      while (System.nanoTime() < pendingDeadline) {
        var aState = repository.findByJobId(a1.jobId()).orElseThrow();
        if (aState.stage() == HistoricalJobStage.DELIVERY_PENDING) {
          break;
        }
      }
      assertFalse(handler.hasStarted(b1.jobId()));
      lifecycleUpdater.markFailed(a1.jobId(), "delivery failed", Duration.ofHours(1));
      handler.awaitStarted(b1.jobId());
      var a1State = repository.findByJobId(a1.jobId()).orElseThrow();
      assertEquals(HistoricalJobStage.DELIVERY_FAILED, a1State.stage());
      assertTrue(handler.hasStarted(b1.jobId()));
    } finally {
      handler.releaseAll();
      executor.shutdownNow();
      scheduler.shutdownNow();
    }
  }

  @Test
  void accept_whenServiceClosed_deliveryTerminalCallbackDoesNotStartQueuedReplacement() {
    var repository = new MultiJobInMemoryHistoricalJobStateRepository();
    var handler = new LatchingHistoricalSubjectLaneScheduler(repository);
    var notifications = new RecordingJobStatusNotificationPublisher();
    var properties = properties(Duration.ofMinutes(30), Duration.ofMinutes(5));
    var scheduler = new ScheduledThreadPoolExecutor(1);
    var executor =
        new ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
    var requestedBy = UUID.fromString("550e8400-e29b-41d4-a716-446655440120");
    var requestA = UUID.randomUUID();
    var requestB = UUID.randomUUID();
    var a1 = job(requestA, requestedBy, UUID.fromString("550e8400-e29b-41d4-a716-446655441131"));
    var b1 = job(requestB, requestedBy, UUID.fromString("550e8400-e29b-41d4-a716-446655441132"));
    var coordinator =
        scriptedAdmissionCoordinator()
            .onRegister(
                b1.jobId(),
                HistoricalJobAdmissionDecision.queueOnly(
                    QUEUE_ROLE_QUEUED_REPLACEMENT, true, false))
            .onTerminal(
                a1.jobId(), HistoricalJobAdmissionDecision.noop(List.of(b1), List.of(), true));
    var terminalCoordinator = new HistoricalJobTerminalCoordinator(repository, coordinator);
    var service =
        newService(
            repository,
            handler,
            notifications,
            new NoOpHistoryEventPublisher(),
            executor,
            scheduler,
            properties,
            coordinator,
            new HistoricalObservability(new SimpleMeterRegistry()),
            GlobalOpenTelemetry.getTracer(HistoricalJobIngressServiceTest.class.getName()),
            terminalCoordinator,
            new HistoricalExecutionFailureClassifier());
    var lifecycleUpdater =
        new ProcessorHistoricalJobLifecycleUpdater(repository, terminalCoordinator);

    try {
      handler.blockJob(a1.jobId());
      handler.blockJob(b1.jobId());
      service.accept(a1);
      handler.awaitStarted(a1.jobId());
      service.accept(b1);
      handler.release(a1.jobId());

      long pendingDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
      while (System.nanoTime() < pendingDeadline) {
        var aState = repository.findByJobId(a1.jobId()).orElseThrow();
        if (aState.stage() == HistoricalJobStage.DELIVERY_PENDING) {
          break;
        }
      }

      service.close();
      lifecycleUpdater.markCompleted(a1.jobId(), Duration.ofHours(1));
      assertFalse(handler.hasStarted(b1.jobId()));
    } finally {
      handler.releaseAll();
      executor.shutdownNow();
      scheduler.shutdownNow();
    }
  }

  @Test
  void accept_whenNewerReplacementArrives_supersedesOlderQueuedReplacement() {
    var repository = new MultiJobInMemoryHistoricalJobStateRepository();
    var handler = new LatchingHistoricalSubjectLaneScheduler(repository);
    var notifications = new RecordingJobStatusNotificationPublisher();
    var properties = properties(Duration.ofMinutes(30), Duration.ofMinutes(5));
    var scheduler = new ScheduledThreadPoolExecutor(1);
    var executor =
        new ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
    var requestedBy = UUID.fromString("550e8400-e29b-41d4-a716-446655440120");
    var requestA = UUID.randomUUID();
    var requestB = UUID.fromString("550e8400-e29b-41d4-a716-446655440321");
    var requestC = UUID.fromString("550e8400-e29b-41d4-a716-446655440322");
    var a1 = job(requestA, requestedBy, UUID.fromString("550e8400-e29b-41d4-a716-446655441021"));
    var b1 = job(requestB, requestedBy, UUID.fromString("550e8400-e29b-41d4-a716-446655441022"));
    var c1 = job(requestC, requestedBy, UUID.fromString("550e8400-e29b-41d4-a716-446655441023"));
    var coordinator =
        scriptedAdmissionCoordinator()
            .onRegister(
                b1.jobId(),
                HistoricalJobAdmissionDecision.queueOnly(
                    QUEUE_ROLE_QUEUED_REPLACEMENT, true, false))
            .onRegister(
                c1.jobId(),
                new HistoricalJobAdmissionDecision(
                    HistoricalJobAdmissionDecision.Action.QUEUE_ONLY,
                    QUEUE_ROLE_QUEUED_REPLACEMENT,
                    List.of(),
                    List.of(
                        cancellation(b1.jobId(), "superseded"),
                        cancellation(a1.jobId(), "replacement")),
                    false,
                    true,
                    false))
            .onTerminal(
                a1.jobId(),
                HistoricalJobAdmissionDecision.noop(
                    List.of(c1), List.of(cancellation(b1.jobId(), "superseded")), true));
    var service =
        newService(
            repository,
            handler,
            notifications,
            new NoOpHistoryEventPublisher(),
            executor,
            scheduler,
            properties,
            coordinator,
            new HistoricalObservability(new SimpleMeterRegistry()),
            GlobalOpenTelemetry.getTracer(HistoricalJobIngressServiceTest.class.getName()),
            new HistoricalJobTerminalCoordinator(repository, coordinator),
            new HistoricalExecutionFailureClassifier());

    try {
      handler.blockJob(a1.jobId());
      handler.throwCancelled(a1.jobId());
      service.accept(a1);
      handler.awaitStarted(a1.jobId());
      service.accept(b1);
      service.accept(c1);
      handler.release(a1.jobId());
      handler.awaitStarted(c1.jobId());

      var bState = repository.findByJobId(b1.jobId()).orElseThrow();
      assertEquals(HistoricalJobStatus.CANCELLED, bState.status());
      assertEquals(HistoricalJobStage.CANCELLED, bState.stage());
      assertFalse(handler.hasStarted(b1.jobId()));
      assertTrue(handler.hasStarted(c1.jobId()));
    } finally {
      handler.releaseAll();
      executor.shutdownNow();
      scheduler.shutdownNow();
    }
  }

  @Test
  void cancellationBeforeQueuedWorkerStartsReleasesAdmissionAndStartsReplacement() {
    var repository = new MultiJobInMemoryHistoricalJobStateRepository();
    var handler = new LatchingHistoricalSubjectLaneScheduler(repository);
    var notifications = new RecordingJobStatusNotificationPublisher();
    var properties = properties(Duration.ofMinutes(30), Duration.ofMinutes(5));
    var scheduler = new ScheduledThreadPoolExecutor(1);
    var executor = new ManualQueuedExecutorService();
    var requestedBy = UUID.fromString("550e8400-e29b-41d4-a716-446655440120");
    var requestA = UUID.randomUUID();
    var requestB = UUID.randomUUID();
    var requestC = UUID.randomUUID();
    var a1 = job(requestA, requestedBy, UUID.fromString("550e8400-e29b-41d4-a716-446655441301"));
    var b1 = job(requestB, requestedBy, UUID.fromString("550e8400-e29b-41d4-a716-446655441302"));
    var c1 = job(requestC, requestedBy, UUID.fromString("550e8400-e29b-41d4-a716-446655441303"));
    var coordinator =
        scriptedAdmissionCoordinator()
            .onRegister(
                c1.jobId(),
                new HistoricalJobAdmissionDecision(
                    HistoricalJobAdmissionDecision.Action.QUEUE_ONLY,
                    QUEUE_ROLE_QUEUED_REPLACEMENT,
                    List.of(),
                    List.of(cancellation(b1.jobId(), "superseded before start")),
                    false,
                    true,
                    false))
            .onTerminal(
                b1.jobId(), HistoricalJobAdmissionDecision.noop(List.of(c1), List.of(), true));
    var service =
        newService(
            repository,
            handler,
            notifications,
            new NoOpHistoryEventPublisher(),
            executor,
            scheduler,
            properties,
            coordinator,
            new HistoricalObservability(new SimpleMeterRegistry()),
            GlobalOpenTelemetry.getTracer(HistoricalJobIngressServiceTest.class.getName()),
            new HistoricalJobTerminalCoordinator(repository, coordinator),
            new HistoricalExecutionFailureClassifier());

    try {
      handler.blockJob(a1.jobId());

      service.accept(a1);
      executor.runNextAsync();
      handler.awaitStarted(a1.jobId());

      service.accept(b1);
      assertFalse(handler.hasStarted(b1.jobId()));

      service.accept(c1);
      assertEquals(
          HistoricalJobStage.CANCELLED, repository.findByJobId(b1.jobId()).orElseThrow().stage());

      executor.runNextAsync();
      assertFalse(handler.hasStarted(b1.jobId()));
      executor.runNextAsync();
      handler.awaitStarted(c1.jobId());

      assertTrue(handler.hasStarted(c1.jobId()));
    } finally {
      handler.releaseAll();
      executor.shutdownNow();
      scheduler.shutdownNow();
    }
  }

  @Test
  void accept_whenReplacementRequestHasThreeSubjects_recordsGroupScopedLifecycleMetrics() {
    var repository = new MultiJobInMemoryHistoricalJobStateRepository();
    var handler = new LatchingHistoricalSubjectLaneScheduler(repository);
    var notifications = new RecordingJobStatusNotificationPublisher();
    var properties = properties(Duration.ofMinutes(30), Duration.ofMinutes(5));
    var scheduler = new ScheduledThreadPoolExecutor(1);
    var executor =
        new ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
    var meterRegistry = new SimpleMeterRegistry();
    var observability = new HistoricalObservability(meterRegistry);
    var requestedBy = UUID.fromString("550e8400-e29b-41d4-a716-446655440120");
    var requestA = UUID.randomUUID();
    var requestB = UUID.fromString("550e8400-e29b-41d4-a716-446655440421");
    var requestC = UUID.fromString("550e8400-e29b-41d4-a716-446655440422");
    var a1 = job(requestA, requestedBy, UUID.fromString("550e8400-e29b-41d4-a716-446655441221"));
    var b1 = job(requestB, requestedBy, UUID.fromString("550e8400-e29b-41d4-a716-446655441222"));
    var c1 = job(requestC, requestedBy, UUID.fromString("550e8400-e29b-41d4-a716-446655441223"));
    var c2 = job(requestC, requestedBy, UUID.fromString("550e8400-e29b-41d4-a716-446655441224"));
    var c3 = job(requestC, requestedBy, UUID.fromString("550e8400-e29b-41d4-a716-446655441225"));
    Tracer tracer = mock(Tracer.class);
    var coordinator =
        scriptedAdmissionCoordinator()
            .onRegister(
                b1.jobId(),
                HistoricalJobAdmissionDecision.queueOnly(
                    QUEUE_ROLE_QUEUED_REPLACEMENT, true, false))
            .onRegister(
                c1.jobId(),
                new HistoricalJobAdmissionDecision(
                    HistoricalJobAdmissionDecision.Action.QUEUE_ONLY,
                    QUEUE_ROLE_QUEUED_REPLACEMENT,
                    List.of(),
                    List.of(
                        cancellation(a1.jobId(), "replacement"),
                        cancellation(b1.jobId(), "superseded")),
                    false,
                    true,
                    false))
            .onRegister(c2.jobId(), HistoricalJobAdmissionDecision.startNow())
            .onRegister(
                c3.jobId(),
                HistoricalJobAdmissionDecision.queueOnly(
                    QUEUE_ROLE_ACTIVE_REQUEST_BACKLOG, false, false))
            .onTerminal(
                a1.jobId(),
                HistoricalJobAdmissionDecision.noop(
                    List.of(c1), List.of(cancellation(b1.jobId(), "superseded")), true));
    var service =
        newService(
            repository,
            handler,
            notifications,
            new NoOpHistoryEventPublisher(),
            executor,
            scheduler,
            properties,
            coordinator,
            observability,
            tracer,
            new HistoricalJobTerminalCoordinator(repository, coordinator),
            new HistoricalExecutionFailureClassifier());

    try {
      handler.blockJob(a1.jobId());
      handler.throwCancelled(a1.jobId());
      handler.blockJob(c1.jobId());
      handler.blockJob(c2.jobId());
      handler.blockJob(c3.jobId());
      service.accept(a1);
      handler.awaitStarted(a1.jobId());
      service.accept(b1);
      service.accept(c1);
      service.accept(c2);
      service.accept(c3);
      handler.release(a1.jobId());
      handler.awaitStarted(c1.jobId());
      handler.awaitStarted(c2.jobId());

      assertFalse(handler.hasStarted(b1.jobId()));
      assertTrue(handler.startIndex(c1.jobId()) > handler.startIndex(a1.jobId()));

      assertTrue(
          meterRegistry
                  .find("opencop_history_job_stage_total")
                  .tags("stage", "CANCELLATION_REQUESTED", "outcome", "success")
                  .counter()
                  .count()
              >= 1.0d);
      assertEquals(
          1.0d,
          meterRegistry
              .find("opencop_history_job_stage_total")
              .tags("stage", "REPLACEMENT_QUEUED", "outcome", "success")
              .counter()
              .count());
      assertEquals(
          1.0d,
          meterRegistry
              .find("opencop_history_job_stage_total")
              .tags("stage", "REPLACEMENT_SUPERSEDED", "outcome", "success")
              .counter()
              .count());
      assertEquals(
          1.0d,
          meterRegistry
              .find("opencop_history_job_stage_total")
              .tags("stage", "REPLACEMENT_STARTED", "outcome", "success")
              .counter()
              .count());
      assertEquals(
          1.0d,
          meterRegistry
              .find("opencop_history_job_stage_total")
              .tags("stage", "CANCELLED", "outcome", "success")
              .counter()
              .count());

      assertEquals(
          1.0d,
          meterRegistry
              .find("opencop_history_job_stage_total")
              .tags("stage", "REPLACEMENT_STARTED", "outcome", "success")
              .counter()
              .count());
    } finally {
      handler.releaseAll();
      executor.shutdownNow();
      scheduler.shutdownNow();
    }
  }

  @Test
  void
      accept_whenCurrentSpanIsValidAtReplacementSubmit_recordsQueuedAndStartedEventsWithoutCreatingLifecycleSpan() {
    var repository = new MultiJobInMemoryHistoricalJobStateRepository();
    var handler = new LatchingHistoricalSubjectLaneScheduler(repository);
    var notifications = new RecordingJobStatusNotificationPublisher();
    var properties = properties(Duration.ofMinutes(30), Duration.ofMinutes(5));
    var scheduler = new ScheduledThreadPoolExecutor(1);
    var executor =
        new ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
    Tracer tracer = mock(Tracer.class);
    Span currentSpan =
        spy(
            Span.wrap(
                SpanContext.create(
                    "4bf92f3577b34da6a3ce929d0e0e4736",
                    "00f067aa0ba902b7",
                    TraceFlags.getSampled(),
                    TraceState.getDefault())));
    var requestedBy = UUID.fromString("550e8400-e29b-41d4-a716-446655440120");
    var requestA = UUID.randomUUID();
    var requestB = UUID.fromString("550e8400-e29b-41d4-a716-446655440811");
    var a1 = job(requestA, requestedBy, UUID.fromString("550e8400-e29b-41d4-a716-446655441811"));
    var b1 = job(requestB, requestedBy, UUID.fromString("550e8400-e29b-41d4-a716-446655441812"));
    var coordinator =
        scriptedAdmissionCoordinator()
            .onRegister(
                b1.jobId(),
                HistoricalJobAdmissionDecision.queueOnly(
                    QUEUE_ROLE_QUEUED_REPLACEMENT, true, false))
            .onTerminal(
                a1.jobId(), HistoricalJobAdmissionDecision.noop(List.of(b1), List.of(), true));
    var service =
        newService(
            repository,
            handler,
            notifications,
            new NoOpHistoryEventPublisher(),
            executor,
            scheduler,
            properties,
            coordinator,
            new HistoricalObservability(new SimpleMeterRegistry()),
            tracer,
            new HistoricalJobTerminalCoordinator(repository, coordinator),
            new HistoricalExecutionFailureClassifier());

    try {
      handler.blockJob(a1.jobId());
      handler.throwCancelled(a1.jobId());
      service.accept(a1);
      handler.awaitStarted(a1.jobId());
      try (Scope ignored = Context.current().with(currentSpan).makeCurrent()) {
        service.accept(b1);
      }
      handler.release(a1.jobId());
      handler.awaitStarted(b1.jobId());

      verify(currentSpan, timeout(2000)).addEvent("history.job.replacement_queued");
      verify(currentSpan, timeout(2000)).addEvent("history.job.replacement_started");
      verify(tracer, never()).spanBuilder("history.job.lifecycle");
    } finally {
      handler.releaseAll();
      executor.shutdownNow();
      scheduler.shutdownNow();
    }
  }

  @Test
  void accept_whenNoValidCurrentSpan_recordsLifecycleMetricsWithoutCreatingLifecycleSpan() {
    var repository = new MultiJobInMemoryHistoricalJobStateRepository();
    var handler = new LatchingHistoricalSubjectLaneScheduler(repository);
    var notifications = new RecordingJobStatusNotificationPublisher();
    var properties = properties(Duration.ofMinutes(30), Duration.ofMinutes(5));
    var scheduler = new ScheduledThreadPoolExecutor(1);
    var executor =
        new ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
    var meterRegistry = new SimpleMeterRegistry();
    var observability = new HistoricalObservability(meterRegistry);
    var requestedBy = UUID.fromString("550e8400-e29b-41d4-a716-446655440120");
    var requestA = UUID.randomUUID();
    var requestB = UUID.fromString("550e8400-e29b-41d4-a716-446655440812");
    var a1 = job(requestA, requestedBy, UUID.fromString("550e8400-e29b-41d4-a716-446655441813"));
    var b1 = job(requestB, requestedBy, UUID.fromString("550e8400-e29b-41d4-a716-446655441814"));
    Tracer tracer = mock(Tracer.class);
    var coordinator =
        scriptedAdmissionCoordinator()
            .onRegister(
                b1.jobId(),
                HistoricalJobAdmissionDecision.queueOnly(
                    QUEUE_ROLE_QUEUED_REPLACEMENT, true, false));
    var service =
        newService(
            repository,
            handler,
            notifications,
            new NoOpHistoryEventPublisher(),
            executor,
            scheduler,
            properties,
            coordinator,
            observability,
            tracer,
            new HistoricalJobTerminalCoordinator(repository, defaultAdmissionCoordinator()),
            new HistoricalExecutionFailureClassifier());

    try {
      handler.blockJob(a1.jobId());
      service.accept(a1);
      handler.awaitStarted(a1.jobId());
      service.accept(b1);

      assertEquals(
          1.0d,
          meterRegistry
              .find("opencop_history_job_stage_total")
              .tags("stage", "REPLACEMENT_QUEUED", "outcome", "success")
              .counter()
              .count());
      verify(tracer, times(0)).spanBuilder("history.job.lifecycle");
    } finally {
      handler.releaseAll();
      executor.shutdownNow();
      scheduler.shutdownNow();
    }
  }

  @Test
  void accept_whenDifferentUsersSubmit_runsConcurrentlyWithoutCrossCancellation() {
    var repository = new MultiJobInMemoryHistoricalJobStateRepository();
    var handler = new LatchingHistoricalSubjectLaneScheduler(repository);
    var notifications = new RecordingJobStatusNotificationPublisher();
    var properties = properties(Duration.ofMinutes(30), Duration.ofMinutes(5));
    var scheduler = new ScheduledThreadPoolExecutor(1);
    var executor =
        new ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
    var userA = UUID.fromString("550e8400-e29b-41d4-a716-446655440120");
    var userB = UUID.fromString("550e8400-e29b-41d4-a716-446655440999");
    var requestA = UUID.randomUUID();
    var requestB = UUID.randomUUID();
    var a1 = job(requestA, userA, UUID.fromString("550e8400-e29b-41d4-a716-446655441031"));
    var b1 = job(requestB, userB, UUID.fromString("550e8400-e29b-41d4-a716-446655441032"));
    var service =
        newService(
            repository,
            handler,
            notifications,
            new NoOpHistoryEventPublisher(),
            executor,
            scheduler,
            properties,
            defaultAdmissionCoordinator(),
            new HistoricalObservability(new SimpleMeterRegistry()),
            GlobalOpenTelemetry.getTracer(HistoricalJobIngressServiceTest.class.getName()),
            new HistoricalJobTerminalCoordinator(repository, defaultAdmissionCoordinator()),
            new HistoricalExecutionFailureClassifier());

    try {
      handler.blockJob(a1.jobId());
      handler.blockJob(b1.jobId());
      service.accept(a1);
      service.accept(b1);
      handler.awaitStarted(a1.jobId());
      handler.awaitStarted(b1.jobId());

      assertTrue(handler.hasStarted(a1.jobId()));
      assertTrue(handler.hasStarted(b1.jobId()));
      assertFalse(
          HistoricalJobStage.CANCELLATION_REQUESTED.equals(
              repository.findByJobId(a1.jobId()).orElseThrow().stage()));
      assertFalse(
          HistoricalJobStage.CANCELLATION_REQUESTED.equals(
              repository.findByJobId(b1.jobId()).orElseThrow().stage()));
    } finally {
      handler.releaseAll();
      executor.shutdownNow();
      scheduler.shutdownNow();
    }
  }

  @Test
  void accept_whenThirdSameRequestSubjectQueued_persistsActiveRequestBacklogQueueRole() {
    var repository = new MultiJobInMemoryHistoricalJobStateRepository();
    var handler = new LatchingHistoricalSubjectLaneScheduler(repository);
    var notifications = new RecordingJobStatusNotificationPublisher();
    var properties = properties(Duration.ofMinutes(30), Duration.ofMinutes(5));
    var scheduler = new ScheduledThreadPoolExecutor(1);
    var executor =
        new ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
    var requestedBy = UUID.fromString("550e8400-e29b-41d4-a716-446655440120");
    var requestId = UUID.fromString("550e8400-e29b-41d4-a716-446655442201");
    var first =
        job(requestId, requestedBy, UUID.fromString("550e8400-e29b-41d4-a716-446655442211"));
    var second =
        job(requestId, requestedBy, UUID.fromString("550e8400-e29b-41d4-a716-446655442212"));
    var third =
        job(requestId, requestedBy, UUID.fromString("550e8400-e29b-41d4-a716-446655442213"));
    var coordinator =
        scriptedAdmissionCoordinator()
            .onRegister(
                third.jobId(),
                HistoricalJobAdmissionDecision.queueOnly(
                    QUEUE_ROLE_ACTIVE_REQUEST_BACKLOG, false, false));
    var service =
        newService(
            repository,
            handler,
            notifications,
            new NoOpHistoryEventPublisher(),
            executor,
            scheduler,
            properties,
            coordinator,
            new HistoricalObservability(new SimpleMeterRegistry()),
            GlobalOpenTelemetry.getTracer(HistoricalJobIngressServiceTest.class.getName()),
            new HistoricalJobTerminalCoordinator(repository, coordinator),
            new HistoricalExecutionFailureClassifier());

    try {
      handler.blockJob(first.jobId());
      handler.blockJob(second.jobId());
      service.accept(first);
      service.accept(second);
      handler.awaitStarted(first.jobId());
      handler.awaitStarted(second.jobId());

      service.accept(third);

      var thirdState = repository.findByJobId(third.jobId()).orElseThrow();
      assertEquals(HistoricalJobStatus.QUEUED, thirdState.status());
      assertEquals(HistoricalJobStage.INGRESS_QUEUED, thirdState.stage());
      assertEquals(QUEUE_ROLE_ACTIVE_REQUEST_BACKLOG, thirdState.queueRole());
      assertFalse(handler.hasStarted(third.jobId()));
    } finally {
      handler.releaseAll();
      executor.shutdownNow();
      scheduler.shutdownNow();
    }
  }

  @Test
  void
      accept_whenInterleavedReplacementChildrenArrive_cancelsOlderLateChildrenAndStartsNewestAfterPredecessorTerminal() {
    var repository = new MultiJobInMemoryHistoricalJobStateRepository();
    var handler = new LatchingHistoricalSubjectLaneScheduler(repository);
    var notifications = new RecordingJobStatusNotificationPublisher();
    var properties = properties(Duration.ofMinutes(30), Duration.ofMinutes(5));
    var scheduler = new ScheduledThreadPoolExecutor(1);
    var executor =
        new ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
    var requestedBy = UUID.fromString("550e8400-e29b-41d4-a716-446655440120");
    var requestA = UUID.fromString("550e8400-e29b-41d4-a716-446655440710");
    var requestB = UUID.fromString("550e8400-e29b-41d4-a716-446655440711");
    var requestC = UUID.fromString("550e8400-e29b-41d4-a716-446655440712");
    var a1 = job(requestA, requestedBy, UUID.fromString("550e8400-e29b-41d4-a716-446655441710"));
    var b1 =
        job(
            requestB,
            requestedBy,
            UUID.fromString("550e8400-e29b-41d4-a716-446655441711"),
            Instant.parse("2026-04-07T10:00:01Z"));
    var c1 =
        job(
            requestC,
            requestedBy,
            UUID.fromString("550e8400-e29b-41d4-a716-446655441712"),
            Instant.parse("2026-04-07T10:00:02Z"));
    var b2 =
        job(
            requestB,
            requestedBy,
            UUID.fromString("550e8400-e29b-41d4-a716-446655441713"),
            Instant.parse("2026-04-07T10:00:01Z"));
    var coordinator =
        scriptedAdmissionCoordinator()
            .onRegister(
                b1.jobId(),
                HistoricalJobAdmissionDecision.queueOnly(
                    QUEUE_ROLE_QUEUED_REPLACEMENT, true, false))
            .onRegister(
                c1.jobId(),
                new HistoricalJobAdmissionDecision(
                    HistoricalJobAdmissionDecision.Action.QUEUE_ONLY,
                    QUEUE_ROLE_QUEUED_REPLACEMENT,
                    List.of(),
                    List.of(
                        cancellation(a1.jobId(), "replacement"),
                        cancellation(b1.jobId(), "superseded")),
                    false,
                    true,
                    false))
            .onRegister(
                b2.jobId(),
                new HistoricalJobAdmissionDecision(
                    HistoricalJobAdmissionDecision.Action.QUEUE_ONLY,
                    QUEUE_ROLE_ACTIVE_REQUEST_BACKLOG,
                    List.of(),
                    List.of(
                        cancellation(b1.jobId(), "superseded"),
                        cancellation(b2.jobId(), "superseded")),
                    false,
                    true,
                    false))
            .onTerminal(
                a1.jobId(), HistoricalJobAdmissionDecision.noop(List.of(c1), List.of(), true));
    var service =
        newService(
            repository,
            handler,
            notifications,
            new NoOpHistoryEventPublisher(),
            executor,
            scheduler,
            properties,
            coordinator,
            new HistoricalObservability(new SimpleMeterRegistry()),
            GlobalOpenTelemetry.getTracer(HistoricalJobIngressServiceTest.class.getName()),
            new HistoricalJobTerminalCoordinator(repository, coordinator),
            new HistoricalExecutionFailureClassifier());

    try {
      handler.blockJob(a1.jobId());
      handler.throwCancelled(a1.jobId());
      handler.blockJob(c1.jobId());
      service.accept(a1);
      handler.awaitStarted(a1.jobId());

      service.accept(b1);
      service.accept(c1);
      service.accept(b2);

      handler.release(a1.jobId());
      handler.awaitStarted(c1.jobId());

      var b1State = repository.findByJobId(b1.jobId()).orElseThrow();
      var b2State = repository.findByJobId(b2.jobId()).orElseThrow();
      assertEquals(HistoricalJobStage.CANCELLED, b1State.stage());
      assertEquals(HistoricalJobStage.CANCELLED, b2State.stage());
      assertTrue(handler.hasStarted(c1.jobId()));
      assertFalse(handler.hasStarted(b1.jobId()));
      assertFalse(handler.hasStarted(b2.jobId()));
      assertTrue(handler.startIndex(c1.jobId()) > handler.startIndex(a1.jobId()));
    } finally {
      handler.releaseAll();
      executor.shutdownNow();
      scheduler.shutdownNow();
    }
  }

  @Test
  void accept_whenOtherUserSubmitsReplacementWhileUserAActive_startsWithoutCancellingUserA() {
    var repository = new MultiJobInMemoryHistoricalJobStateRepository();
    var handler = new LatchingHistoricalSubjectLaneScheduler(repository);
    var notifications = new RecordingJobStatusNotificationPublisher();
    var properties = properties(Duration.ofMinutes(30), Duration.ofMinutes(5));
    var scheduler = new ScheduledThreadPoolExecutor(1);
    var executor =
        new ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
    var service =
        newService(
            repository,
            handler,
            notifications,
            new NoOpHistoryEventPublisher(),
            executor,
            scheduler,
            properties,
            defaultAdmissionCoordinator(),
            new HistoricalObservability(new SimpleMeterRegistry()),
            GlobalOpenTelemetry.getTracer(HistoricalJobIngressServiceTest.class.getName()),
            new HistoricalJobTerminalCoordinator(repository, defaultAdmissionCoordinator()),
            new HistoricalExecutionFailureClassifier());
    var userA = UUID.fromString("550e8400-e29b-41d4-a716-446655440120");
    var userB = UUID.fromString("550e8400-e29b-41d4-a716-446655440999");
    var requestA = UUID.fromString("550e8400-e29b-41d4-a716-446655440730");
    var requestB = UUID.fromString("550e8400-e29b-41d4-a716-446655440731");
    var a1 = job(requestA, userA, UUID.fromString("550e8400-e29b-41d4-a716-446655441730"));
    var b1 = job(requestB, userB, UUID.fromString("550e8400-e29b-41d4-a716-446655441731"));

    try {
      handler.blockJob(a1.jobId());
      handler.blockJob(b1.jobId());
      service.accept(a1);
      handler.awaitStarted(a1.jobId());

      service.accept(b1);
      handler.awaitStarted(b1.jobId());

      var aState = repository.findByJobId(a1.jobId()).orElseThrow();
      assertTrue(handler.hasStarted(a1.jobId()));
      assertTrue(handler.hasStarted(b1.jobId()));
      assertFalse(HistoricalJobStage.CANCELLATION_REQUESTED.equals(aState.stage()));
    } finally {
      handler.releaseAll();
      executor.shutdownNow();
      scheduler.shutdownNow();
    }
  }

  @Test
  void accept_whenTwoThreadsSubmitSameJob_submitsHandlerOnceAndCreatesStateOnce() throws Exception {
    var repository = new MultiJobInMemoryHistoricalJobStateRepository();
    var handler = new LatchingHistoricalSubjectLaneScheduler(repository);
    var notifications = new RecordingJobStatusNotificationPublisher();
    var properties = properties(Duration.ofMinutes(30), Duration.ofMinutes(5));
    var scheduler = new ScheduledThreadPoolExecutor(1);
    var executor =
        new ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
    var service =
        newService(
            repository,
            handler,
            notifications,
            new NoOpHistoryEventPublisher(),
            executor,
            scheduler,
            properties,
            defaultAdmissionCoordinator(),
            new HistoricalObservability(new SimpleMeterRegistry()),
            GlobalOpenTelemetry.getTracer(HistoricalJobIngressServiceTest.class.getName()),
            new HistoricalJobTerminalCoordinator(repository, defaultAdmissionCoordinator()),
            new HistoricalExecutionFailureClassifier());
    var sameJob = job();
    var acceptGate = new CountDownLatch(1);
    var done = new CountDownLatch(2);

    Thread first =
        new Thread(
            () -> {
              awaitLatch(acceptGate);
              service.accept(sameJob);
              done.countDown();
            });
    Thread second =
        new Thread(
            () -> {
              awaitLatch(acceptGate);
              service.accept(sameJob);
              done.countDown();
            });

    try {
      handler.blockJob(sameJob.jobId());
      first.start();
      second.start();
      acceptGate.countDown();
      assertTrue(done.await(2, TimeUnit.SECONDS));
      handler.awaitStarted(sameJob.jobId());

      assertEquals(1, handler.startCount());
      assertEquals(1, repository.createReceivedSuccessCount());
    } finally {
      handler.releaseAll();
      executor.shutdownNow();
      scheduler.shutdownNow();
      first.join(2000);
      second.join(2000);
    }
  }

  @Test
  void accept_whenPredecessorFailsWithTimeout_startsQueuedReplacementAfterFailurePersisted() {
    var repository = new MultiJobInMemoryHistoricalJobStateRepository();
    var handler = new LatchingHistoricalSubjectLaneScheduler(repository);
    var notifications = new RecordingJobStatusNotificationPublisher();
    var properties = properties(Duration.ofMillis(60), Duration.ofMillis(10));
    var scheduler = new ScheduledThreadPoolExecutor(1);
    var executor =
        new ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
    var requestedBy = UUID.fromString("550e8400-e29b-41d4-a716-446655440120");
    var requestA = UUID.randomUUID();
    var requestB = UUID.randomUUID();
    var a1 = job(requestA, requestedBy, UUID.fromString("550e8400-e29b-41d4-a716-446655441041"));
    var b1 = job(requestB, requestedBy, UUID.fromString("550e8400-e29b-41d4-a716-446655441042"));
    var coordinator =
        scriptedAdmissionCoordinator()
            .onRegister(
                b1.jobId(),
                HistoricalJobAdmissionDecision.queueOnly(
                    QUEUE_ROLE_QUEUED_REPLACEMENT, true, false))
            .onExecutionExit(
                a1.jobId(), HistoricalJobAdmissionDecision.noop(List.of(b1), List.of(), true))
            .onTerminal(
                a1.jobId(), HistoricalJobAdmissionDecision.noop(List.of(b1), List.of(), true));
    var service =
        newService(
            repository,
            handler,
            notifications,
            new NoOpHistoryEventPublisher(),
            executor,
            scheduler,
            properties,
            coordinator,
            new HistoricalObservability(new SimpleMeterRegistry()),
            GlobalOpenTelemetry.getTracer(HistoricalJobIngressServiceTest.class.getName()),
            new HistoricalJobTerminalCoordinator(repository, coordinator),
            new HistoricalExecutionFailureClassifier());

    try {
      handler.blockJob(a1.jobId());
      handler.blockJob(b1.jobId());
      service.accept(a1);
      handler.awaitStarted(a1.jobId());
      service.accept(b1);

      int timeoutIndex = -1;
      long timeoutDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
      while (System.nanoTime() < timeoutDeadline && timeoutIndex < 0) {
        timeoutIndex = repository.transitionIndex(a1.jobId(), HistoricalJobStage.TIMEOUT);
      }
      int replacementStartIndex = -1;
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
      while (System.nanoTime() < deadline && replacementStartIndex < 0) {
        replacementStartIndex =
            repository.transitionIndex(b1.jobId(), HistoricalJobStage.EXECUTION_RUNNING);
      }
      assertTrue(timeoutIndex >= 0);
      assertTrue(replacementStartIndex > timeoutIndex);
    } finally {
      handler.releaseAll();
      executor.shutdownNow();
      scheduler.shutdownNow();
    }
  }

  @Test
  void accept_whenThreeSubjectsShareRequestId_runsOnlyTwoAtATime() {
    var repository = new MultiJobInMemoryHistoricalJobStateRepository();
    var handler = new LatchingHistoricalSubjectLaneScheduler(repository);
    var notifications = new RecordingJobStatusNotificationPublisher();
    var properties = properties(Duration.ofMinutes(30), Duration.ofMinutes(5));
    properties.setMaxConcurrentSubjectsPerJob(2);
    var scheduler = new ScheduledThreadPoolExecutor(1);
    var executor =
        new ThreadPoolExecutor(3, 3, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
    var requestedBy = UUID.fromString("550e8400-e29b-41d4-a716-446655440120");
    var requestId = UUID.randomUUID();
    var first =
        systemJob(requestId, requestedBy, UUID.fromString("550e8400-e29b-41d4-a716-446655441301"));
    var second =
        systemJob(requestId, requestedBy, UUID.fromString("550e8400-e29b-41d4-a716-446655441302"));
    var third =
        systemJob(requestId, requestedBy, UUID.fromString("550e8400-e29b-41d4-a716-446655441303"));
    var coordinator =
        scriptedAdmissionCoordinator()
            .onRegister(
                third.jobId(),
                HistoricalJobAdmissionDecision.queueOnly(
                    QUEUE_ROLE_ACTIVE_REQUEST_BACKLOG, false, false))
            .onTerminal(
                first.jobId(),
                HistoricalJobAdmissionDecision.noop(List.of(third), List.of(), false));
    var service =
        newService(
            repository,
            handler,
            notifications,
            new NoOpHistoryEventPublisher(),
            executor,
            scheduler,
            properties,
            coordinator,
            new HistoricalObservability(new SimpleMeterRegistry()),
            GlobalOpenTelemetry.getTracer(HistoricalJobIngressServiceTest.class.getName()),
            new HistoricalJobTerminalCoordinator(repository, coordinator),
            new HistoricalExecutionFailureClassifier());

    try {
      handler.blockJob(first.jobId());
      handler.blockJob(second.jobId());
      handler.blockJob(third.jobId());
      service.accept(first);
      service.accept(second);
      handler.awaitStarted(first.jobId());
      handler.awaitStarted(second.jobId());

      service.accept(third);

      assertFalse(handler.hasStarted(third.jobId()));
      assertEquals(2, handler.maxSimultaneousInvocations());

      handler.release(first.jobId());
      handler.awaitStarted(third.jobId());

      assertTrue(handler.startIndex(third.jobId()) > handler.startIndex(first.jobId()));
      assertTrue(handler.startIndex(third.jobId()) > handler.startIndex(second.jobId()));
      assertEquals(2, handler.maxSimultaneousInvocations());
    } finally {
      handler.releaseAll();
      executor.shutdownNow();
      scheduler.shutdownNow();
    }
  }

  @Test
  void accept_whenMarkQueuedFailsAfterRegistration_cleansAdmissionStateForThatJob() {
    var repository = new MultiJobInMemoryHistoricalJobStateRepository();
    repository.failMarkQueued(true);
    var handler = new LatchingHistoricalSubjectLaneScheduler(repository);
    var notifications = new RecordingJobStatusNotificationPublisher();
    var properties = properties(Duration.ofMinutes(30), Duration.ofMinutes(5));
    var scheduler = new ScheduledThreadPoolExecutor(1);
    var executor =
        new ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
    var coordinator = scriptedAdmissionCoordinator();
    var service =
        newService(
            repository,
            handler,
            notifications,
            new NoOpHistoryEventPublisher(),
            executor,
            scheduler,
            properties,
            coordinator,
            new HistoricalObservability(new SimpleMeterRegistry()),
            GlobalOpenTelemetry.getTracer(HistoricalJobIngressServiceTest.class.getName()),
            new HistoricalJobTerminalCoordinator(repository, coordinator),
            new HistoricalExecutionFailureClassifier());
    var job = job();

    try {
      service.accept(job);

      assertEquals(1, coordinator.executionExitCallsFor(job.jobId()));
      assertFalse(handler.hasStarted(job.jobId()));
    } finally {
      handler.releaseAll();
      executor.shutdownNow();
      scheduler.shutdownNow();
    }
  }

  @Test
  void accept_whenExecutorRejectsAfterRegistration_cleansAdmissionAndStartsQueuedReplacement() {
    var repository = new MultiJobInMemoryHistoricalJobStateRepository();
    var handler = new LatchingHistoricalSubjectLaneScheduler(repository);
    var notifications = new RecordingJobStatusNotificationPublisher();
    var properties = properties(Duration.ofMinutes(30), Duration.ofMinutes(5));
    var scheduler = new ScheduledThreadPoolExecutor(1);
    var executor = new RejectingAfterFirstExecutionExecutorService();
    var requestedBy = UUID.fromString("550e8400-e29b-41d4-a716-446655440120");
    var requestA = UUID.randomUUID();
    var requestB = UUID.randomUUID();
    var accepted = job(requestA, requestedBy, UUID.randomUUID());
    var rejected = job(requestA, requestedBy, UUID.randomUUID());
    var replacement = job(requestB, requestedBy, UUID.randomUUID());
    var coordinator =
        scriptedAdmissionCoordinator()
            .onRegister(rejected.jobId(), HistoricalJobAdmissionDecision.startNow())
            .onExecutionExit(
                rejected.jobId(),
                HistoricalJobAdmissionDecision.noop(List.of(replacement), List.of(), false));
    var service =
        newService(
            repository,
            handler,
            notifications,
            new NoOpHistoryEventPublisher(),
            executor,
            scheduler,
            properties,
            coordinator,
            new HistoricalObservability(new SimpleMeterRegistry()),
            GlobalOpenTelemetry.getTracer(HistoricalJobIngressServiceTest.class.getName()),
            new HistoricalJobTerminalCoordinator(repository, coordinator),
            new HistoricalExecutionFailureClassifier());

    try {
      handler.blockJob(accepted.jobId());
      handler.blockJob(replacement.jobId());
      repository.createReceived(replacement);
      service.accept(accepted);
      handler.awaitStarted(accepted.jobId());

      assertThrows(IllegalStateException.class, () -> service.accept(rejected));
      handler.awaitStarted(replacement.jobId());

      assertEquals(1, coordinator.executionExitCallsFor(rejected.jobId()));
      assertTrue(handler.hasStarted(replacement.jobId()));
    } finally {
      handler.releaseAll();
      executor.shutdownNow();
      scheduler.shutdownNow();
    }
  }

  @Test
  void accept_whenBackloggedSubjectBecomesCancellationRequested_skipsHandlerAndMarksCancelled()
      throws Exception {
    var repository = new MultiJobInMemoryHistoricalJobStateRepository();
    var handler = new LatchingHistoricalSubjectLaneScheduler(repository);
    var notifications = new RecordingJobStatusNotificationPublisher();
    var properties = properties(Duration.ofMinutes(30), Duration.ofMinutes(5));
    properties.setMaxConcurrentSubjectsPerJob(2);
    var scheduler = new ScheduledThreadPoolExecutor(1);
    var executor =
        new ThreadPoolExecutor(3, 3, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
    var requestedBy = UUID.fromString("550e8400-e29b-41d4-a716-446655440120");
    var requestId = UUID.randomUUID();
    var first =
        systemJob(requestId, requestedBy, UUID.fromString("550e8400-e29b-41d4-a716-446655441611"));
    var second =
        systemJob(requestId, requestedBy, UUID.fromString("550e8400-e29b-41d4-a716-446655441612"));
    var third =
        systemJob(requestId, requestedBy, UUID.fromString("550e8400-e29b-41d4-a716-446655441613"));
    var coordinator =
        scriptedAdmissionCoordinator()
            .onRegister(
                third.jobId(),
                HistoricalJobAdmissionDecision.queueOnly(
                    QUEUE_ROLE_ACTIVE_REQUEST_BACKLOG, false, false))
            .onTerminal(
                first.jobId(),
                HistoricalJobAdmissionDecision.noop(List.of(third), List.of(), false))
            .onTerminal(
                second.jobId(), HistoricalJobAdmissionDecision.noop(List.of(), List.of(), false));
    var service =
        newService(
            repository,
            handler,
            notifications,
            new NoOpHistoryEventPublisher(),
            executor,
            scheduler,
            properties,
            coordinator,
            new HistoricalObservability(new SimpleMeterRegistry()),
            GlobalOpenTelemetry.getTracer(HistoricalJobIngressServiceTest.class.getName()),
            new HistoricalJobTerminalCoordinator(repository, coordinator),
            new HistoricalExecutionFailureClassifier());

    try {
      handler.blockJob(first.jobId());
      handler.blockJob(second.jobId());
      service.accept(first);
      service.accept(second);
      handler.awaitStarted(first.jobId());
      handler.awaitStarted(second.jobId());
      service.accept(third);

      assertTrue(repository.markCancellationRequested(third.jobId(), "requested"));

      handler.release(first.jobId());
      handler.release(second.jobId());
      Thread.sleep(200);

      var thirdState = repository.findByJobId(third.jobId()).orElseThrow();
      assertEquals(HistoricalJobStatus.CANCELLED, thirdState.status());
      assertEquals(HistoricalJobStage.CANCELLED, thirdState.stage());
      assertFalse(handler.hasStarted(third.jobId()));
    } finally {
      handler.releaseAll();
      executor.shutdownNow();
      scheduler.shutdownNow();
    }
  }

  @Test
  void
      accept_whenQueuedReplacementBecomesCancellationRequestedBeforePredecessorTerminal_skipsHandlerAndMarksCancelled()
          throws Exception {
    var repository = new MultiJobInMemoryHistoricalJobStateRepository();
    var handler = new LatchingHistoricalSubjectLaneScheduler(repository);
    var notifications = new RecordingJobStatusNotificationPublisher();
    var properties = properties(Duration.ofMinutes(30), Duration.ofMinutes(5));
    var scheduler = new ScheduledThreadPoolExecutor(1);
    var executor =
        new ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
    var requestedBy = UUID.fromString("550e8400-e29b-41d4-a716-446655440120");
    var requestA = UUID.randomUUID();
    var requestB = UUID.randomUUID();
    var predecessor =
        job(requestA, requestedBy, UUID.fromString("550e8400-e29b-41d4-a716-446655441621"));
    var replacement =
        job(requestB, requestedBy, UUID.fromString("550e8400-e29b-41d4-a716-446655441622"));
    var coordinator =
        scriptedAdmissionCoordinator()
            .onRegister(
                replacement.jobId(),
                HistoricalJobAdmissionDecision.queueOnly(
                    QUEUE_ROLE_QUEUED_REPLACEMENT, true, false))
            .onTerminal(
                predecessor.jobId(),
                HistoricalJobAdmissionDecision.noop(List.of(replacement), List.of(), true));
    var service =
        newService(
            repository,
            handler,
            notifications,
            new NoOpHistoryEventPublisher(),
            executor,
            scheduler,
            properties,
            coordinator,
            new HistoricalObservability(new SimpleMeterRegistry()),
            GlobalOpenTelemetry.getTracer(HistoricalJobIngressServiceTest.class.getName()),
            new HistoricalJobTerminalCoordinator(repository, coordinator),
            new HistoricalExecutionFailureClassifier());

    try {
      handler.blockJob(predecessor.jobId());
      handler.throwCancelled(predecessor.jobId());
      service.accept(predecessor);
      handler.awaitStarted(predecessor.jobId());
      service.accept(replacement);

      assertTrue(repository.markCancellationRequested(replacement.jobId(), "requested"));
      handler.release(predecessor.jobId());
      Thread.sleep(200);

      var replacementState = repository.findByJobId(replacement.jobId()).orElseThrow();
      assertEquals(HistoricalJobStatus.CANCELLED, replacementState.status());
      assertEquals(HistoricalJobStage.CANCELLED, replacementState.stage());
      assertFalse(handler.hasStarted(replacement.jobId()));
    } finally {
      handler.releaseAll();
      executor.shutdownNow();
      scheduler.shutdownNow();
    }
  }

  @Test
  void accept_whenBacklogHeadIsCancellationRequested_startsNextBackloggedSubject()
      throws Exception {
    var repository = new MultiJobInMemoryHistoricalJobStateRepository();
    var handler = new LatchingHistoricalSubjectLaneScheduler(repository);
    var notifications = new RecordingJobStatusNotificationPublisher();
    var properties = properties(Duration.ofMinutes(30), Duration.ofMinutes(5));
    properties.setMaxConcurrentSubjectsPerJob(1);
    var scheduler = new ScheduledThreadPoolExecutor(1);
    var executor =
        new ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
    var requestedBy = UUID.fromString("550e8400-e29b-41d4-a716-446655440120");
    var requestId = UUID.randomUUID();
    var first =
        systemJob(requestId, requestedBy, UUID.fromString("550e8400-e29b-41d4-a716-446655441651"));
    var second =
        systemJob(requestId, requestedBy, UUID.fromString("550e8400-e29b-41d4-a716-446655441652"));
    var third =
        systemJob(requestId, requestedBy, UUID.fromString("550e8400-e29b-41d4-a716-446655441653"));
    var coordinator =
        scriptedAdmissionCoordinator()
            .onRegister(
                second.jobId(),
                HistoricalJobAdmissionDecision.queueOnly(
                    QUEUE_ROLE_ACTIVE_REQUEST_BACKLOG, false, false))
            .onRegister(
                third.jobId(),
                HistoricalJobAdmissionDecision.queueOnly(
                    QUEUE_ROLE_ACTIVE_REQUEST_BACKLOG, false, false))
            .onTerminal(
                first.jobId(),
                HistoricalJobAdmissionDecision.noop(List.of(second, third), List.of(), false));
    var service =
        newService(
            repository,
            handler,
            notifications,
            new NoOpHistoryEventPublisher(),
            executor,
            scheduler,
            properties,
            coordinator,
            new HistoricalObservability(new SimpleMeterRegistry()),
            GlobalOpenTelemetry.getTracer(HistoricalJobIngressServiceTest.class.getName()),
            new HistoricalJobTerminalCoordinator(repository, coordinator),
            new HistoricalExecutionFailureClassifier());

    try {
      handler.blockJob(first.jobId());
      handler.blockJob(third.jobId());
      service.accept(first);
      handler.awaitStarted(first.jobId());
      service.accept(second);
      service.accept(third);

      assertTrue(repository.markCancellationRequested(second.jobId(), "requested"));
      handler.release(first.jobId());
      handler.awaitStarted(third.jobId());

      var secondState = repository.findByJobId(second.jobId()).orElseThrow();
      assertEquals(HistoricalJobStatus.CANCELLED, secondState.status());
      assertEquals(HistoricalJobStage.CANCELLED, secondState.stage());
      assertFalse(handler.hasStarted(second.jobId()));
      assertTrue(handler.hasStarted(third.jobId()));
    } finally {
      handler.releaseAll();
      executor.shutdownNow();
      scheduler.shutdownNow();
    }
  }

  @Test
  void
      accept_whenExecutorQueuedJobBecomesCancellationRequestedBeforeExecution_skipsHandlerAndMarksCancelled()
          throws Exception {
    var repository = new MultiJobInMemoryHistoricalJobStateRepository();
    var handler = new LatchingHistoricalSubjectLaneScheduler(repository);
    var notifications = new RecordingJobStatusNotificationPublisher();
    var properties = properties(Duration.ofMinutes(30), Duration.ofMinutes(5));
    var scheduler = new ScheduledThreadPoolExecutor(1);
    var executor =
        new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
    var service =
        newService(
            repository,
            handler,
            notifications,
            new NoOpHistoryEventPublisher(),
            executor,
            scheduler,
            properties,
            defaultAdmissionCoordinator(),
            new HistoricalObservability(new SimpleMeterRegistry()),
            GlobalOpenTelemetry.getTracer(HistoricalJobIngressServiceTest.class.getName()),
            new HistoricalJobTerminalCoordinator(repository, defaultAdmissionCoordinator()),
            new HistoricalExecutionFailureClassifier());
    var requestedBy = UUID.fromString("550e8400-e29b-41d4-a716-446655440120");
    var requestId = UUID.randomUUID();
    var first =
        job(requestId, requestedBy, UUID.fromString("550e8400-e29b-41d4-a716-446655441631"));
    var second =
        job(requestId, requestedBy, UUID.fromString("550e8400-e29b-41d4-a716-446655441632"));

    try {
      handler.blockJob(first.jobId());
      service.accept(first);
      handler.awaitStarted(first.jobId());
      service.accept(second);

      assertTrue(repository.markCancellationRequested(second.jobId(), "requested"));
      handler.release(first.jobId());
      Thread.sleep(200);

      var secondState = repository.findByJobId(second.jobId()).orElseThrow();
      assertEquals(HistoricalJobStatus.CANCELLED, secondState.status());
      assertEquals(HistoricalJobStage.CANCELLED, secondState.stage());
      assertFalse(handler.hasStarted(second.jobId()));
    } finally {
      handler.releaseAll();
      executor.shutdownNow();
      scheduler.shutdownNow();
    }
  }

  @Test
  void accept_whenQueuedJobAlreadyCancellationRequestedBeforeStart_skipsHandlerAndMarksCancelled() {
    var repository = new MultiJobInMemoryHistoricalJobStateRepository();
    var notifications = new RecordingJobStatusNotificationPublisher();
    var properties = properties(Duration.ofMinutes(30), Duration.ofMinutes(5));
    var scheduler = new ScheduledThreadPoolExecutor(1);
    var executor = new DirectExecutorService();
    var handler = new RecordingHistoricalSubjectLaneScheduler(repository);
    var service =
        newService(
            repository,
            handler,
            notifications,
            new NoOpHistoryEventPublisher(),
            executor,
            scheduler,
            properties,
            defaultAdmissionCoordinator(),
            new HistoricalObservability(new SimpleMeterRegistry()),
            GlobalOpenTelemetry.getTracer(HistoricalJobIngressServiceTest.class.getName()),
            new HistoricalJobTerminalCoordinator(repository, defaultAdmissionCoordinator()),
            new HistoricalExecutionFailureClassifier());
    var job = job();

    try {
      assertTrue(repository.createReceivedIfAbsent(job));
      assertTrue(repository.markCancellationRequested(job.jobId(), "requested"));
      service.accept(job);
    } finally {
      scheduler.shutdownNow();
    }

    var state = repository.findByJobId(job.jobId()).orElseThrow();
    assertEquals(HistoricalJobStage.CANCELLED, state.stage());
    assertEquals(HistoricalJobStatus.CANCELLED, state.status());
    assertEquals(null, handler.lastHandledJob);
  }

  @Test
  void
      accept_whenDuplicateRedeliveryHitsAlreadyCancelledState_doesNotRepublishTerminalCancelledEvent() {
    var repository = new MultiJobInMemoryHistoricalJobStateRepository();
    var historyEventPublisher = new RecordingHistoryEventPublisher();
    var notifications = new RecordingJobStatusNotificationPublisher();
    var properties = properties(Duration.ofMinutes(30), Duration.ofMinutes(5));
    var scheduler = new ScheduledThreadPoolExecutor(1);
    var executor = new DirectExecutorService();
    var handler = new RecordingHistoricalSubjectLaneScheduler(repository);
    var service =
        newService(
            repository,
            handler,
            notifications,
            historyEventPublisher,
            executor,
            scheduler,
            properties,
            defaultAdmissionCoordinator(),
            new HistoricalObservability(new SimpleMeterRegistry()),
            GlobalOpenTelemetry.getTracer(HistoricalJobIngressServiceTest.class.getName()),
            new HistoricalJobTerminalCoordinator(repository, defaultAdmissionCoordinator()),
            new HistoricalExecutionFailureClassifier());
    var job = job();

    try {
      assertTrue(repository.createReceivedIfAbsent(job));
      assertTrue(repository.markCancellationRequested(job.jobId(), "requested"));
      service.accept(job);
      service.accept(job);
    } finally {
      scheduler.shutdownNow();
    }

    var state = repository.findByJobId(job.jobId()).orElseThrow();
    assertEquals(HistoricalJobStage.CANCELLED, state.stage());
    assertEquals(1, historyEventPublisher.failedCount);
    assertEquals(null, handler.lastHandledJob);
  }

  @Test
  void historicalJobCancelledException_hasJobId() {
    UUID jobId = UUID.randomUUID();
    HistoricalJobCancelledException exception =
        new HistoricalJobCancelledException(jobId, "cancelled");

    assertEquals(jobId, exception.jobId());
    assertEquals("cancelled", exception.getMessage());
  }

  @Test
  void accept_whenExecutorRejects_marksJobFailed() {
    var repository = new InMemoryHistoricalJobStateRepository();
    var handler = new RecordingHistoricalSubjectLaneScheduler(repository);
    var notifications = new RecordingJobStatusNotificationPublisher();
    var properties = properties(Duration.ofMinutes(30), Duration.ofMinutes(5));
    ExecutorService rejectingExecutor = new RejectingExecutorService();
    var scheduler = new ScheduledThreadPoolExecutor(1);
    var service =
        newService(
            repository,
            handler,
            notifications,
            new NoOpHistoryEventPublisher(),
            rejectingExecutor,
            scheduler,
            properties,
            defaultAdmissionCoordinator(),
            new HistoricalObservability(new SimpleMeterRegistry()),
            GlobalOpenTelemetry.getTracer(HistoricalJobIngressServiceTest.class.getName()),
            new HistoricalJobTerminalCoordinator(repository, defaultAdmissionCoordinator()),
            new HistoricalExecutionFailureClassifier());
    var job = job();

    try {
      assertThrows(IllegalStateException.class, () -> service.accept(job));
    } finally {
      scheduler.shutdownNow();
      rejectingExecutor.shutdownNow();
    }

    var state = repository.findByJobId(job.jobId()).orElseThrow();
    assertEquals(HistoricalJobStatus.FAILED, state.status());
    assertEquals(HistoricalJobStage.INGRESS_REJECTED, state.stage());
    assertEquals(job, notifications.failureJob);
  }

  @Test
  void accept_whenRejected_recordsRejectedMetricWithoutRequestedByTagValue() {
    var repository = new InMemoryHistoricalJobStateRepository();
    var handler = new RecordingHistoricalSubjectLaneScheduler(repository);
    var notifications = new RecordingJobStatusNotificationPublisher();
    var properties = properties(Duration.ofMinutes(30), Duration.ofMinutes(5));
    ExecutorService rejectingExecutor = new RejectingExecutorService();
    var scheduler = new ScheduledThreadPoolExecutor(1);
    var meterRegistry = new SimpleMeterRegistry();
    var observability = new HistoricalObservability(meterRegistry);
    var service =
        newService(
            repository,
            handler,
            notifications,
            new NoOpHistoryEventPublisher(),
            rejectingExecutor,
            scheduler,
            properties,
            defaultAdmissionCoordinator(),
            observability,
            GlobalOpenTelemetry.getTracer(HistoricalJobIngressServiceTest.class.getName()),
            new HistoricalJobTerminalCoordinator(repository, defaultAdmissionCoordinator()),
            new HistoricalExecutionFailureClassifier());
    var job = job();

    try {
      assertThrows(IllegalStateException.class, () -> service.accept(job));
    } finally {
      scheduler.shutdownNow();
      rejectingExecutor.shutdownNow();
    }

    var rejectedCounter =
        meterRegistry
            .find("opencop_history_job_stage_total")
            .tags("stage", "INGRESS_REJECTED", "outcome", "error")
            .counter();
    assertEquals(1.0d, rejectedCounter.count());
    var meterTags =
        meterRegistry.find("opencop_history_job_stage_total").meters().stream()
            .flatMap(meter -> meter.getId().getTags().stream())
            .map(tag -> tag.getValue())
            .toList();
    assertTrue(meterTags.stream().noneMatch(value -> value.equals(job.requestedBy().toString())));
  }

  @Test
  void submitsWhenJobStateAbsent() {
    var repository = new InMemoryHistoricalJobStateRepository();
    var handler = new RecordingHistoricalSubjectLaneScheduler(repository);
    var notifications = new RecordingJobStatusNotificationPublisher();
    var properties = properties(Duration.ofMinutes(30), Duration.ofMinutes(5));
    var scheduler = new ScheduledThreadPoolExecutor(1);
    var countingExecutor = new CountingExecutorService();
    var service =
        newService(
            repository,
            handler,
            notifications,
            new NoOpHistoryEventPublisher(),
            countingExecutor,
            scheduler,
            properties,
            defaultAdmissionCoordinator(),
            new HistoricalObservability(new SimpleMeterRegistry()),
            GlobalOpenTelemetry.getTracer(HistoricalJobIngressServiceTest.class.getName()),
            new HistoricalJobTerminalCoordinator(repository, defaultAdmissionCoordinator()),
            new HistoricalExecutionFailureClassifier());
    var job = job();

    try {
      service.accept(job);
    } finally {
      scheduler.shutdownNow();
    }

    assertEquals(1, countingExecutor.submitCount);
    assertEquals(1, repository.createReceivedCount);
  }

  @Test
  void skipsSubmissionWhenJobAlreadyInNonTerminalState() {
    var repository = new InMemoryHistoricalJobStateRepository();
    var handler = new RecordingHistoricalSubjectLaneScheduler(repository);
    var notifications = new RecordingJobStatusNotificationPublisher();
    var properties = properties(Duration.ofMinutes(30), Duration.ofMinutes(5));
    var scheduler = new ScheduledThreadPoolExecutor(1);
    // Use a direct executor but count submissions through the repository
    var countingExecutor = new CountingExecutorService();
    var service =
        newService(
            repository,
            handler,
            notifications,
            new NoOpHistoryEventPublisher(),
            countingExecutor,
            scheduler,
            properties,
            defaultAdmissionCoordinator(),
            new HistoricalObservability(new SimpleMeterRegistry()),
            GlobalOpenTelemetry.getTracer(HistoricalJobIngressServiceTest.class.getName()),
            new HistoricalJobTerminalCoordinator(repository, defaultAdmissionCoordinator()),
            new HistoricalExecutionFailureClassifier());
    var job = job();

    // First call: seeds state (RUNNING stage after handler runs)
    try {
      service.accept(job);
    } finally {
      scheduler.shutdownNow();
    }

    int submitCountAfterFirst = countingExecutor.submitCount;
    int createReceivedAfterFirst = repository.createReceivedCount;

    // Second call: job is already in a non-terminal stage — must be skipped
    var scheduler2 = new ScheduledThreadPoolExecutor(1);
    try {
      service.accept(job);
    } finally {
      scheduler2.shutdownNow();
    }

    assertEquals(
        submitCountAfterFirst,
        countingExecutor.submitCount,
        "executor must not be called again for a job already in flight");
    assertEquals(
        createReceivedAfterFirst,
        repository.createReceivedCount,
        "createReceived must not be called again for a job already in flight");
  }

  @Test
  void terminalStateAlsoSkipsSubmission() {
    var repository = new InMemoryHistoricalJobStateRepository();
    // Use a system-issuer job so the handler marks COMPLETED (not DELIVERY_PENDING)
    var job =
        HistoricalTracksJobSubmission.create(
            UUID.randomUUID(),
            JobIssuer.system("history-test"),
            com.opencop.core.sdk.model.SubjectType.SOURCE,
            UUID.randomUUID(),
            UUID.fromString("550e8400-e29b-41d4-a716-446655440120"),
            Instant.parse("2026-04-07T10:00:00Z"),
            Instant.parse("2026-04-06T10:02:17Z"),
            Instant.parse("2026-04-06T12:00:00Z"),
            Instant.parse("2026-04-06T10:02:00Z"),
            Instant.parse("2026-04-06T12:00:00Z"),
            HistoricalResolution.MINUTE_1,
            118,
            List.of());
    var handler = new RecordingHistoricalSubjectLaneScheduler(repository);
    var notifications = new RecordingJobStatusNotificationPublisher();
    var properties = properties(Duration.ofMinutes(30), Duration.ofMinutes(5));
    var countingExecutor = new CountingExecutorService();

    var scheduler = new ScheduledThreadPoolExecutor(1);
    var service =
        newService(
            repository,
            handler,
            notifications,
            new NoOpHistoryEventPublisher(),
            countingExecutor,
            scheduler,
            properties,
            defaultAdmissionCoordinator(),
            new HistoricalObservability(new SimpleMeterRegistry()),
            GlobalOpenTelemetry.getTracer(HistoricalJobIngressServiceTest.class.getName()),
            new HistoricalJobTerminalCoordinator(repository, defaultAdmissionCoordinator()),
            new HistoricalExecutionFailureClassifier());

    // First call: runs job to completion (COMPLETED terminal state)
    try {
      service.accept(job);
    } finally {
      scheduler.shutdownNow();
    }

    int submitCountAfterFirst = countingExecutor.submitCount;
    int createReceivedAfterFirst = repository.createReceivedCount;

    // Second call: job is already COMPLETED — must be skipped, not re-run
    var scheduler2 = new ScheduledThreadPoolExecutor(1);
    try {
      service.accept(job);
    } finally {
      scheduler2.shutdownNow();
    }

    assertEquals(
        submitCountAfterFirst,
        countingExecutor.submitCount,
        "executor must not be called again for a terminal job");
    assertEquals(
        createReceivedAfterFirst,
        repository.createReceivedCount,
        "createReceived must not be called again for a terminal job");
  }

  private static HistoricalExecutionProperties properties(
      Duration maxRuntime, Duration ttlRefreshInterval) {
    HistoricalExecutionProperties properties = new HistoricalExecutionProperties();
    properties.setJobStateTtl(Duration.ofHours(24));
    properties.setMaxRuntime(maxRuntime);
    properties.setTtlRefreshInterval(ttlRefreshInterval);
    return properties;
  }

  private static HistoricalJobIngressService newService(
      HistoricalJobStateGateway jobStateGateway,
      HistoricalSubjectLaneScheduler historicalSubjectLaneScheduler,
      JobStatusNotificationPublisher notifications,
      HistoryEventPublisher historyEventPublisher,
      ExecutorService historicalJobExecutor,
      ScheduledExecutorService historicalJobScheduler,
      HistoricalExecutionProperties properties,
      HistoricalJobAdmissionCoordinator admissionCoordinator,
      HistoricalObservability historicalObservability,
      Tracer ignoredTracer,
      HistoricalJobTerminalCoordinator terminalCoordinator,
      HistoricalExecutionFailureClassifier failureClassifier) {
    var executionSupervisor =
        new HistoricalJobExecutionSupervisor(
            jobStateGateway,
            notifications,
            historyEventPublisher,
            historicalJobExecutor,
            historicalJobExecutor,
            historicalJobScheduler,
            properties,
            historicalObservability);
    var cancellationFinalizer =
        new HistoricalJobCancellationFinalizer(
            jobStateGateway,
            historyEventPublisher,
            historicalObservability,
            new HistoryJobLifecycleEventRecorder() {
              @Override
              public void record(String eventName) {
                recordLifecycleEvent(eventName);
              }
            });
    var executionOutcomeHandler =
        new HistoricalJobExecutionOutcomeHandler(
            jobStateGateway,
            mock(HistoricalSubjectLaneJobHandler.class),
            notifications,
            historyEventPublisher,
            historicalObservability,
            failureClassifier);
    var rejectionHandler =
        new HistoricalJobIngressRejectionHandler(
            jobStateGateway, notifications, historyEventPublisher, historicalObservability);
    return new HistoricalJobIngressService(
        jobStateGateway,
        admissionCoordinator,
        historicalObservability,
        historicalJobScheduler,
        properties,
        terminalCoordinator,
        executionSupervisor,
        cancellationFinalizer,
        executionOutcomeHandler,
        rejectionHandler,
        admissionRecoveryFactory(properties),
        historicalSubjectLaneScheduler);
  }

  private static HistoricalJobAdmissionRecoveryFactory admissionRecoveryFactory(
      HistoricalExecutionProperties properties) {
    return new HistoricalJobAdmissionRecoveryFactory(
        properties.getAdmissionRecoveryInterval(),
        properties.getTtlRefreshInterval(),
        properties.getMaxRuntime(),
        properties.getJobStateTtl(),
        new HistoricalJobAdmissionRecoveryPolicy());
  }

  private static void recordLifecycleEvent(String eventName) {
    recordLifecycleEvent(eventName, Context.current());
  }

  private static void recordLifecycleEvent(String eventName, Context lifecycleContext) {
    try (Scope ignored = lifecycleContext.makeCurrent()) {
      Span currentSpan = Span.current();
      if (currentSpan.getSpanContext().isValid()) {
        currentSpan.addEvent(eventName);
      }
    }
  }

  private static void assertCancellationRequestedOrCancelled(HistoricalJobState state) {
    assertTrue(
        state.stage() == HistoricalJobStage.CANCELLATION_REQUESTED
            || state.stage() == HistoricalJobStage.CANCELLED,
        () -> "Expected cancellation-requested or cancelled stage but was " + state.stage());
  }

  private static HistoricalTracksJobSubmission job() {
    return HistoricalTracksJobSubmission.create(
        UUID.randomUUID(),
        JobIssuer.user(UUID.fromString("550e8400-e29b-41d4-a716-446655440120")),
        SubjectType.SOURCE,
        UUID.randomUUID(),
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

  private static HistoricalTracksJobSubmission job(
      UUID requestId, UUID requestedBy, UUID subjectId) {
    return job(requestId, requestedBy, subjectId, Instant.parse("2026-04-07T10:00:00Z"));
  }

  private static HistoricalTracksJobSubmission job(
      UUID requestId, UUID requestedBy, UUID subjectId, Instant requestedAt) {
    return HistoricalTracksJobSubmission.create(
        requestId,
        JobIssuer.user(requestedBy),
        SubjectType.SOURCE,
        subjectId,
        requestedBy,
        requestedAt,
        Instant.parse("2026-04-06T10:02:17Z"),
        Instant.parse("2026-04-06T12:00:00Z"),
        Instant.parse("2026-04-06T10:02:00Z"),
        Instant.parse("2026-04-06T12:00:00Z"),
        HistoricalResolution.MINUTE_1,
        118,
        List.of());
  }

  private static HistoricalTracksJobSubmission multiSubjectJob(
      UUID requestId, UUID requestedBy, UUID firstSubjectId, UUID secondSubjectId) {
    return HistoricalTracksJobSubmission.create(
        requestId,
        JobIssuer.user(requestedBy),
        List.of(
            new HistoricalTracksSubject(UUID.randomUUID(), SubjectType.SOURCE, firstSubjectId),
            new HistoricalTracksSubject(UUID.randomUUID(), SubjectType.LAYER, secondSubjectId)),
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

  private static HistoricalTracksJobSubmission systemJob(
      UUID requestId, UUID requestedBy, UUID subjectId) {
    return HistoricalTracksJobSubmission.create(
        requestId,
        JobIssuer.system("history-test"),
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

  private static final class MultiJobInMemoryHistoricalJobStateRepository
      implements HistoricalJobStateGateway {
    private final Map<UUID, HistoricalJobState> states = new ConcurrentHashMap<>();
    private final List<String> transitions = new CopyOnWriteArrayList<>();
    private final HistoricalExecutionProperties properties = new HistoricalExecutionProperties();
    private volatile boolean failMarkQueued;

    @Override
    public void createReceived(HistoricalTracksJobSubmission job) {
      states.put(
          job.jobId(),
          HistoricalJobState.received(
              job, Instant.parse("2026-04-07T12:00:00Z"), properties.getJobStateTtl()));
      transitions.add(job.jobId() + ":" + HistoricalJobStage.INGRESS_RECEIVED.name());
    }

    @Override
    public boolean createReceivedIfAbsent(HistoricalTracksJobSubmission job) {
      HistoricalJobState created =
          HistoricalJobState.received(
              job, Instant.parse("2026-04-07T12:00:00Z"), properties.getJobStateTtl());
      HistoricalJobState previous = states.putIfAbsent(job.jobId(), created);
      if (previous != null) {
        return false;
      }
      transitions.add(job.jobId() + ":" + HistoricalJobStage.INGRESS_RECEIVED.name());
      return true;
    }

    @Override
    public void createReceived(HistoricalSubjectExecutionMeta laneMetadata) {
      createReceivedIfAbsent(laneMetadata);
    }

    @Override
    public boolean createReceivedIfAbsent(HistoricalSubjectExecutionMeta laneMetadata) {
      HistoricalJobState created =
          HistoricalJobState.received(
              laneMetadata, Instant.parse("2026-04-07T12:00:00Z"), properties.getJobStateTtl());
      HistoricalJobState previous = states.putIfAbsent(laneMetadata.laneJobId(), created);
      if (previous != null) {
        return false;
      }
      transitions.add(laneMetadata.laneJobId() + ":" + HistoricalJobStage.INGRESS_RECEIVED.name());
      return true;
    }

    @Override
    public boolean markQueued(UUID jobId) {
      if (failMarkQueued) {
        return false;
      }
      return updateGuardingCancellation(
          jobId,
          current ->
              current.queued(Instant.parse("2026-04-07T12:00:01Z"), properties.getJobStateTtl()));
    }

    @Override
    public boolean markQueued(UUID jobId, String queueRole) {
      if (failMarkQueued) {
        return false;
      }
      return updateGuardingCancellation(
          jobId,
          current ->
              current.queued(
                  Instant.parse("2026-04-07T12:00:01Z"), properties.getJobStateTtl(), queueRole));
    }

    @Override
    public boolean markRunning(UUID jobId, String workerId) {
      return updateGuardingCancellation(
          jobId,
          current ->
              current.running(
                  Instant.parse("2026-04-07T12:00:02Z"), properties.getJobStateTtl(), workerId));
    }

    @Override
    public boolean markPlanning(UUID jobId) {
      return updateGuardingCancellation(
          jobId,
          current ->
              current.planning(Instant.parse("2026-04-07T12:00:02Z"), properties.getJobStateTtl()));
    }

    @Override
    public boolean markPlanned(UUID jobId) {
      return updateGuardingCancellation(
          jobId,
          current ->
              current.planned(Instant.parse("2026-04-07T12:00:02Z"), properties.getJobStateTtl()));
    }

    @Override
    public boolean markCompleted(UUID jobId) {
      return updateGuardingCancellation(
          jobId,
          current ->
              current.completed(
                  Instant.parse("2026-04-07T12:00:03Z"), properties.getJobStateTtl()));
    }

    @Override
    public boolean markDeliveredCompleted(UUID jobId) {
      return markCompleted(jobId);
    }

    @Override
    public boolean markDeliveryPending(UUID jobId) {
      return updateGuardingCancellation(
          jobId,
          current ->
              current.deliveryPending(
                  Instant.parse("2026-04-07T12:00:03Z"), properties.getJobStateTtl()));
    }

    @Override
    public boolean markDeliveryFailed(UUID jobId, String failureReason) {
      return markFailed(jobId, HistoricalJobStage.DELIVERY_FAILED, failureReason);
    }

    @Override
    public boolean markFailed(UUID jobId, HistoricalJobStage stage, String failureReason) {
      return update(
          jobId,
          current ->
              current.failed(
                  Instant.parse("2026-04-07T12:00:03Z"),
                  properties.getJobStateTtl(),
                  stage,
                  failureReason));
    }

    @Override
    public boolean markCancellationRequested(UUID jobId, String reason) {
      return update(
          jobId,
          current ->
              current.cancellationRequested(
                  Instant.parse("2026-04-07T12:00:03Z"), properties.getJobStateTtl(), reason));
    }

    @Override
    public boolean markCancelled(UUID jobId, String reason) {
      return update(
          jobId, current -> current.cancelled(Instant.parse("2026-04-07T12:00:03Z"), reason));
    }

    @Override
    public boolean refreshActiveTtl(UUID jobId) {
      return update(
          jobId,
          current ->
              current.refresh(Instant.parse("2026-04-07T12:00:03Z"), properties.getJobStateTtl()));
    }

    @Override
    public Optional<HistoricalJobState> findByJobId(UUID jobId) {
      return Optional.ofNullable(states.get(jobId));
    }

    int transitionIndex(UUID jobId, HistoricalJobStage stage) {
      String needle = jobId + ":" + stage.name();
      for (int i = 0; i < transitions.size(); i++) {
        if (needle.equals(transitions.get(i))) {
          return i;
        }
      }
      return -1;
    }

    int createReceivedSuccessCount() {
      return transitionCount(HistoricalJobStage.INGRESS_RECEIVED);
    }

    void failMarkQueued(boolean failMarkQueued) {
      this.failMarkQueued = failMarkQueued;
    }

    private int transitionCount(HistoricalJobStage stage) {
      String suffix = ":" + stage.name();
      int count = 0;
      for (String transition : transitions) {
        if (transition.endsWith(suffix)) {
          count++;
        }
      }
      return count;
    }

    private boolean update(UUID jobId, UnaryOperator<HistoricalJobState> mutator) {
      HistoricalJobState current = states.get(jobId);
      if (current == null || current.isTerminal()) {
        return false;
      }
      HistoricalJobState next = mutator.apply(current);
      transitions.add(jobId + ":" + next.stage().name());
      states.put(jobId, next);
      return true;
    }

    private boolean updateGuardingCancellation(
        UUID jobId, UnaryOperator<HistoricalJobState> mutator) {
      HistoricalJobState current = states.get(jobId);
      if (current == null
          || current.isTerminal()
          || current.stage() == HistoricalJobStage.CANCELLATION_REQUESTED) {
        return false;
      }
      HistoricalJobState next = mutator.apply(current);
      transitions.add(jobId + ":" + next.stage().name());
      states.put(jobId, next);
      return true;
    }
  }

  private static final class LatchingHistoricalSubjectLaneScheduler
      extends HistoricalSubjectLaneScheduler {
    private final Map<UUID, CountDownLatch> blockLatches = new ConcurrentHashMap<>();
    private final Map<UUID, CountDownLatch> startedLatches = new ConcurrentHashMap<>();
    private final Map<UUID, RuntimeException> failureModes = new ConcurrentHashMap<>();
    private final List<UUID> startOrder = new CopyOnWriteArrayList<>();
    private final HistoricalJobStateGateway repository;
    private final AtomicInteger activeInvocations = new AtomicInteger();
    private final AtomicInteger maxSimultaneousInvocations = new AtomicInteger();

    private LatchingHistoricalSubjectLaneScheduler(HistoricalJobStateGateway jobStateRepository) {
      super(
          noOpLaneExecutionGateway(),
          new HistoricalSubjectJobFactory(),
          new HistoricalChunkPlanner(),
          schedulerProperties());
      this.repository = jobStateRepository;
    }

    void blockJob(UUID jobId) {
      blockLatches.put(jobId, new CountDownLatch(1));
    }

    void throwCancelled(UUID jobId) {
      failureModes.put(jobId, new HistoricalJobCancelledException(jobId, "cancelled"));
    }

    void throwTimeout(UUID jobId) {
      failureModes.put(jobId, new RuntimeException(new TimeoutException("timed out")));
    }

    void release(UUID jobId) {
      CountDownLatch latch = blockLatches.get(jobId);
      if (latch != null) {
        latch.countDown();
      }
    }

    void releaseAll() {
      for (CountDownLatch latch : blockLatches.values()) {
        latch.countDown();
      }
    }

    void awaitStarted(UUID jobId) {
      try {
        assertTrue(
            startedLatches
                .computeIfAbsent(jobId, ignored -> new CountDownLatch(1))
                .await(2, TimeUnit.SECONDS));
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new AssertionError(e);
      }
    }

    boolean hasStarted(UUID jobId) {
      return startOrder.contains(jobId);
    }

    int startIndex(UUID jobId) {
      return startOrder.indexOf(jobId);
    }

    int maxSimultaneousInvocations() {
      return maxSimultaneousInvocations.get();
    }

    int startCount() {
      return startOrder.size();
    }

    @Override
    public void execute(HistoricalTracksJobSubmission job) {
      startOrder.add(job.jobId());
      repository.markRunning(job.jobId(), Thread.currentThread().getName());
      int active = activeInvocations.incrementAndGet();
      maxSimultaneousInvocations.updateAndGet(previous -> Math.max(previous, active));
      startedLatches.computeIfAbsent(job.jobId(), ignored -> new CountDownLatch(1)).countDown();
      CountDownLatch latch = blockLatches.get(job.jobId());
      try {
        if (latch != null) {
          try {
            latch.await(2, TimeUnit.SECONDS);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        }
        RuntimeException failure = failureModes.get(job.jobId());
        if (failure != null) {
          throw failure;
        }
      } finally {
        activeInvocations.decrementAndGet();
      }
    }
  }

  private static class InMemoryHistoricalJobStateRepository implements HistoricalJobStateGateway {

    private final AtomicReference<HistoricalJobState> state = new AtomicReference<>();
    private final HistoricalExecutionProperties properties = new HistoricalExecutionProperties();
    private int createReceivedCount;

    @Override
    public void createReceived(HistoricalTracksJobSubmission job) {
      createReceivedCount++;
      state.set(
          HistoricalJobState.received(
              job, Instant.parse("2026-04-07T12:00:00Z"), properties.getJobStateTtl()));
    }

    @Override
    public boolean createReceivedIfAbsent(HistoricalTracksJobSubmission job) {
      HistoricalJobState created =
          HistoricalJobState.received(
              job, Instant.parse("2026-04-07T12:00:00Z"), properties.getJobStateTtl());
      if (!state.compareAndSet(null, created)) {
        return false;
      }
      createReceivedCount++;
      return true;
    }

    @Override
    public boolean markQueued(UUID jobId) {
      return updateGuardingCancellation(
          current ->
              current.queued(Instant.parse("2026-04-07T12:00:01Z"), properties.getJobStateTtl()));
    }

    @Override
    public boolean markQueued(UUID jobId, String queueRole) {
      return updateGuardingCancellation(
          current ->
              current.queued(
                  Instant.parse("2026-04-07T12:00:01Z"), properties.getJobStateTtl(), queueRole));
    }

    @Override
    public boolean markRunning(UUID jobId, String workerId) {
      return updateGuardingCancellation(
          current ->
              current.running(
                  Instant.parse("2026-04-07T12:00:02Z"), properties.getJobStateTtl(), workerId));
    }

    @Override
    public boolean markPlanning(UUID jobId) {
      return updateGuardingCancellation(
          current ->
              current.planning(Instant.parse("2026-04-07T12:00:02Z"), properties.getJobStateTtl()));
    }

    @Override
    public boolean markPlanned(UUID jobId) {
      return updateGuardingCancellation(
          current ->
              current.planned(Instant.parse("2026-04-07T12:00:02Z"), properties.getJobStateTtl()));
    }

    @Override
    public boolean markCompleted(UUID jobId) {
      return updateGuardingCancellation(
          current ->
              current.completed(
                  Instant.parse("2026-04-07T12:00:03Z"), properties.getJobStateTtl()));
    }

    @Override
    public boolean markDeliveredCompleted(UUID jobId) {
      return markCompleted(jobId);
    }

    @Override
    public boolean markDeliveryPending(UUID jobId) {
      return updateGuardingCancellation(
          current ->
              current.deliveryPending(
                  Instant.parse("2026-04-07T12:00:03Z"), properties.getJobStateTtl()));
    }

    @Override
    public boolean markDeliveryFailed(UUID jobId, String failureReason) {
      return markFailed(jobId, HistoricalJobStage.DELIVERY_FAILED, failureReason);
    }

    @Override
    public boolean markFailed(UUID jobId, HistoricalJobStage stage, String failureReason) {
      return update(
          current ->
              current.failed(
                  Instant.parse("2026-04-07T12:00:03Z"),
                  properties.getJobStateTtl(),
                  stage,
                  failureReason));
    }

    @Override
    public boolean markCancellationRequested(UUID jobId, String reason) {
      return update(
          current ->
              current.cancellationRequested(
                  Instant.parse("2026-04-07T12:00:03Z"), properties.getJobStateTtl(), reason));
    }

    @Override
    public boolean markCancelled(UUID jobId, String reason) {
      return update(current -> current.cancelled(Instant.parse("2026-04-07T12:00:03Z"), reason));
    }

    @Override
    public boolean refreshActiveTtl(UUID jobId) {
      return update(
          current ->
              current.refresh(Instant.parse("2026-04-07T12:00:02Z"), properties.getJobStateTtl()));
    }

    @Override
    public Optional<HistoricalJobState> findByJobId(UUID jobId) {
      HistoricalJobState current = state.get();
      if (current == null || !current.jobId().equals(jobId)) {
        return Optional.empty();
      }
      return Optional.of(current);
    }

    private boolean update(UnaryOperator<HistoricalJobState> mutator) {
      HistoricalJobState current = state.get();
      if (current == null || current.isTerminal()) {
        return false;
      }
      state.set(mutator.apply(current));
      return true;
    }

    private boolean updateGuardingCancellation(UnaryOperator<HistoricalJobState> mutator) {
      HistoricalJobState current = state.get();
      if (current == null
          || current.isTerminal()
          || current.stage() == HistoricalJobStage.CANCELLATION_REQUESTED) {
        return false;
      }
      state.set(mutator.apply(current));
      return true;
    }
  }

  private static void awaitLatch(CountDownLatch latch) {
    try {
      latch.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError(e);
    }
  }

  private static void awaitCondition(BooleanSupplier condition, String failureMessage) {
    long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
    try {
      while (System.nanoTime() < deadlineNanos) {
        if (condition.getAsBoolean()) {
          return;
        }
        Thread.sleep(10);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError(e);
    }
    throw new AssertionError(failureMessage);
  }

  private static final class RecordingJobStatusNotificationPublisher
      extends JobStatusNotificationPublisher {

    private JobSubmission successJob;
    private JobSubmission failureJob;

    private RecordingJobStatusNotificationPublisher() {
      super(null, new ObjectMapper());
    }

    @Override
    public void notifySuccess(JobSubmission job) {
      this.successJob = job;
    }

    @Override
    public void notifyFailure(JobSubmission job, Throwable exception) {
      this.failureJob = job;
    }
  }

  private static class RecordingHistoricalSubjectLaneScheduler
      extends HistoricalSubjectLaneScheduler {

    private HistoricalTracksJobSubmission lastHandledJob;

    private RecordingHistoricalSubjectLaneScheduler(HistoricalJobStateGateway jobStateRepository) {
      super(
          noOpLaneExecutionGateway(),
          new HistoricalSubjectJobFactory(),
          new HistoricalChunkPlanner(),
          schedulerProperties());
    }

    @Override
    public void execute(HistoricalTracksJobSubmission job) {
      lastHandledJob = job;
      super.execute(job);
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

  private static final class DirectExecutorService extends AbstractExecutorService {

    private boolean shutdown;

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

  private static final class RejectingExecutorService extends AbstractExecutorService {

    @Override
    public void shutdown() {}

    @Override
    public List<Runnable> shutdownNow() {
      return List.of();
    }

    @Override
    public boolean isShutdown() {
      return false;
    }

    @Override
    public boolean isTerminated() {
      return false;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) {
      return true;
    }

    @Override
    public void execute(Runnable command) {
      throw new RejectedExecutionException("rejected");
    }
  }

  private static final class ManualQueuedExecutorService extends AbstractExecutorService {
    private final LinkedBlockingQueue<Runnable> commands = new LinkedBlockingQueue<>();
    private final List<Thread> runningThreads = new CopyOnWriteArrayList<>();
    private boolean shutdown;

    @Override
    public void shutdown() {
      shutdown = true;
    }

    @Override
    public List<Runnable> shutdownNow() {
      shutdown = true;
      for (Thread thread : runningThreads) {
        thread.interrupt();
      }
      return List.copyOf(commands);
    }

    @Override
    public boolean isShutdown() {
      return shutdown;
    }

    @Override
    public boolean isTerminated() {
      return shutdown && runningThreads.stream().noneMatch(Thread::isAlive);
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) {
      return true;
    }

    @Override
    public void execute(Runnable command) {
      commands.add(command);
    }

    private void runNextAsync() {
      Runnable command = commands.poll();
      assertNotNull(command, "Expected a queued command");
      Thread thread = new Thread(command, "manual-history-worker");
      runningThreads.add(thread);
      thread.start();
    }
  }

  private static final class RejectingAfterFirstExecutionExecutorService
      extends AbstractExecutorService {
    private final AtomicInteger executions = new AtomicInteger();
    private boolean shutdown;

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
      int executionNumber = executions.incrementAndGet();
      if (executionNumber != 2) {
        command.run();
        return;
      }
      throw new RejectedExecutionException("rejected");
    }
  }

  private static final class CountingExecutorService extends AbstractExecutorService {

    private int submitCount;
    private boolean shutdown;

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
      submitCount++;
      command.run();
    }
  }

  private static final class InMemoryHistoricalPlanRepository implements HistoricalPlanGateway {

    private HistoricalChunkPlan plan;

    @Override
    public void save(HistoricalChunkPlan plan) {
      this.plan = plan;
    }

    @Override
    public Optional<HistoricalChunkPlan> findByJobId(UUID jobId) {
      return Optional.ofNullable(plan);
    }
  }

  private static HistoricalChunkCacheAnalyzer noOpCacheAnalyzer() {
    return new HistoricalChunkCacheAnalyzer(
        new NoOpHistoricalChunkPayloadCacheGateway(),
        new NoOpHistoricalChunkStateGateway(),
        new HistoricalCacheIdentity(new ObjectMapper()),
        new HistoricalObservability(new SimpleMeterRegistry()));
  }

  private static final class NoOpHistoricalChunkPayloadCacheGateway
      implements HistoricalChunkPayloadCacheGateway {

    @Override
    public Map<String, Optional<java.time.Duration>> remainingPayloadTtl(Set<String> payloadKeys) {
      return Map.of();
    }

    @Override
    public void put(String payloadKey, byte[] payloadBytes, java.time.Duration ttl) {}

    @Override
    public Optional<byte[]> find(String payloadKey) {
      return Optional.empty();
    }

    @Override
    public Map<String, Optional<byte[]>> findAll(List<String> payloadKeys) {
      return payloadKeys.stream()
          .collect(
              java.util.stream.Collectors.toMap(
                  key -> key,
                  key -> Optional.<byte[]>empty(),
                  (left, right) -> right,
                  java.util.LinkedHashMap::new));
    }
  }

  private static final class NoOpHistoricalChunkStateGateway
      implements HistoricalChunkStateGateway {

    @Override
    public void initialize(HistoricalChunkPlan plan) {}

    @Override
    public Optional<HistoricalChunkState> findByChunkStateKey(String chunkStateKey) {
      return Optional.empty();
    }

    @Override
    public void transition(String chunkStateKey, HistoricalChunkStateStatus status) {}

    @Override
    public void updatePartCount(String chunkStateKey, int partCount) {}
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

  private static final class RecordingHistoryEventPublisher implements HistoryEventPublisher {
    private int failedCount;
    private HistoricalJobStage lastFailedStage;
    private HistoricalTracksJobSubmission lastFailedJob;
    private String lastFailureReason;

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
        HistoricalTracksJobSubmission job, HistoricalJobStage stage, String failureReason) {
      failedCount++;
      lastFailedStage = stage;
      lastFailedJob = job;
      lastFailureReason = failureReason;
    }
  }

  private static final class NoOpHistoricalChunkExecutionCoordinator
      extends HistoricalChunkExecutionCoordinator {

    private NoOpHistoricalChunkExecutionCoordinator() {
      super(null, null);
    }
  }

  private static final class NoOpProducer
      implements com.opencop.core.sdk.ports.gateway.broker.MessageBrokerProducer<byte[]> {

    @Override
    public void send(byte[] value, java.util.Map<String, String> headers) {}

    @Override
    public void sendDlt(byte[] value) {}

    @Override
    public void send(byte[] value) {}
  }

  private static HistoricalJobAdmissionCoordinator defaultAdmissionCoordinator() {
    return scriptedAdmissionCoordinator();
  }

  private static ScriptedAdmissionCoordinator scriptedAdmissionCoordinator() {
    return new ScriptedAdmissionCoordinator();
  }

  private static HistoricalJobAdmissionDecision.HistoricalJobCancellation cancellation(
      UUID jobId, String reason) {
    return new HistoricalJobAdmissionDecision.HistoricalJobCancellation(jobId, reason);
  }

  private static final class ScriptedAdmissionCoordinator
      implements HistoricalJobAdmissionCoordinator {
    private final Map<UUID, HistoricalJobAdmissionDecision> registerDecisions =
        new ConcurrentHashMap<>();
    private final Map<UUID, HistoricalJobAdmissionDecision> executionExitDecisions =
        new ConcurrentHashMap<>();
    private final Map<UUID, HistoricalJobAdmissionDecision> terminalDecisions =
        new ConcurrentHashMap<>();
    private final Map<UUID, AtomicInteger> executionExitCalls = new ConcurrentHashMap<>();

    @Override
    public HistoricalJobAdmissionDecision register(HistoricalTracksJobSubmission job) {
      return registerDecisions.getOrDefault(job.jobId(), HistoricalJobAdmissionDecision.startNow());
    }

    @Override
    public HistoricalJobAdmissionDecision recordExecutionExit(UUID jobId) {
      executionExitCalls.computeIfAbsent(jobId, ignored -> new AtomicInteger()).incrementAndGet();
      return executionExitDecisions.getOrDefault(
          jobId, HistoricalJobAdmissionDecision.noop(List.of(), List.of(), false));
    }

    @Override
    public HistoricalJobAdmissionDecision recordTerminal(UUID jobId) {
      return terminalDecisions.getOrDefault(
          jobId, HistoricalJobAdmissionDecision.noop(List.of(), List.of(), false));
    }

    private ScriptedAdmissionCoordinator onRegister(
        UUID jobId, HistoricalJobAdmissionDecision decision) {
      registerDecisions.put(jobId, decision);
      return this;
    }

    private ScriptedAdmissionCoordinator onExecutionExit(
        UUID jobId, HistoricalJobAdmissionDecision decision) {
      executionExitDecisions.put(jobId, decision);
      return this;
    }

    private ScriptedAdmissionCoordinator onTerminal(
        UUID jobId, HistoricalJobAdmissionDecision decision) {
      terminalDecisions.put(jobId, decision);
      return this;
    }

    private int executionExitCallsFor(UUID jobId) {
      return executionExitCalls.getOrDefault(jobId, new AtomicInteger()).get();
    }
  }
}
