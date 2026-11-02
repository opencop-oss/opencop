// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.history;

import com.opencop.core.sdk.model.history.HistoricalJobStage;
import com.opencop.core.sdk.model.job.HistoricalTracksJobSubmission;
import com.opencop.core.sdk.model.job.HistoricalTracksSubject;
import com.opencop.core.sdk.model.job.JobIssuerType;
import com.opencop.processor.business.model.history.HistoricalSubjectExecutionMeta;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkWindowPlan;
import com.opencop.processor.business.usecase.history.HistoricalJobAdmissionRecovery;
import com.opencop.processor.business.usecase.history.HistoricalJobAdmissionRecoveryFactory;
import com.opencop.processor.business.usecase.history.HistoricalSubjectLaneScheduler;
import com.opencop.processor.ports.technical.gateway.history.HistoricalJobAdmissionCoordinator;
import com.opencop.processor.ports.technical.gateway.history.HistoricalJobService;
import com.opencop.processor.ports.technical.gateway.history.HistoricalJobStateGateway;
import com.opencop.processor.technical.gateway.history.admission.HistoricalJobAdmissionLifecycle;
import com.opencop.processor.technical.gateway.history.cancellation.HistoricalJobCancellationFinalizer;
import com.opencop.processor.technical.gateway.history.execution.HistoricalJobExecutionOutcomeHandler;
import com.opencop.processor.technical.gateway.history.model.job.HistoricalJobAdmissionDecision;
import com.opencop.processor.technical.observability.history.HistoricalObservability;
import com.opencop.processor.technical.spring.config.HistoricalExecutionProperties;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Entry point for historical track jobs.
 *
 * <p>This service receives a historical job, stores its initial state, coordinates admission, and
 * delegates worker supervision to {@link HistoricalJobExecutionSupervisor}.
 *
 * <p>When the job finishes, this service updates the final state and sends the appropriate success
 * or failure notifications.
 */
@Component
public class HistoricalJobIngressService implements HistoricalJobService {

  private static final Logger log = LoggerFactory.getLogger(HistoricalJobIngressService.class);
  private static final Duration MIN_STAGE_DURATION = Duration.ofNanos(1L);

  private final HistoricalJobStateGateway jobStateGateway;
  private final HistoricalJobAdmissionCoordinator admissionCoordinator;
  private final HistoricalJobTerminalCoordinator terminalCoordinator;
  private final HistoricalJobCancellationFinalizer cancellationFinalizer;
  private final HistoricalJobAdmissionLifecycle admissionLifecycle;
  private final HistoricalJobAdmissionRecovery admissionRecovery;
  private final HistoricalJobExecutionSupervisor executionSupervisor;
  private final HistoricalJobExecutionOutcomeHandler executionOutcomeHandler;
  private final HistoricalJobIngressRejectionHandler rejectionHandler;
  private final HistoricalSubjectLaneScheduler historicalSubjectLaneScheduler;
  private final Runnable unregisterTerminalListener;

  public HistoricalJobIngressService(
      HistoricalJobStateGateway jobStateGateway,
      HistoricalJobAdmissionCoordinator admissionCoordinator,
      HistoricalObservability historicalObservability,
      @Qualifier("historicalJobScheduler") ScheduledExecutorService historicalJobScheduler,
      HistoricalExecutionProperties properties,
      HistoricalJobTerminalCoordinator terminalCoordinator,
      HistoricalJobExecutionSupervisor executionSupervisor,
      HistoricalJobCancellationFinalizer cancellationFinalizer,
      HistoricalJobExecutionOutcomeHandler executionOutcomeHandler,
      HistoricalJobIngressRejectionHandler rejectionHandler,
      HistoricalJobAdmissionRecoveryFactory admissionRecoveryFactory,
      HistoricalSubjectLaneScheduler historicalSubjectLaneScheduler) {
    this.jobStateGateway = jobStateGateway;
    this.admissionCoordinator = admissionCoordinator;
    this.terminalCoordinator = terminalCoordinator;
    this.cancellationFinalizer = cancellationFinalizer;
    this.executionSupervisor = executionSupervisor;
    this.executionOutcomeHandler = executionOutcomeHandler;
    this.rejectionHandler = rejectionHandler;
    this.historicalSubjectLaneScheduler =
        Objects.requireNonNull(historicalSubjectLaneScheduler, "historicalSubjectLaneScheduler");
    this.admissionLifecycle =
        new HistoricalJobAdmissionLifecycle(
            jobStateGateway,
            admissionCoordinator,
            historicalObservability,
            executionSupervisor::isTracking,
            executionSupervisor::cancelBeforeStartIfTracked,
            this::submitAdmittedJob,
            this::terminalCancelBeforeStart,
            this::recordLifecycleEvent);
    this.admissionRecovery =
        admissionRecoveryFactory.create(
            admissionCoordinator,
            jobStateGateway,
            historicalJobScheduler,
            executionSupervisor::isTracking,
            admissionLifecycle::processRecoveredDecision);
    this.unregisterTerminalListener =
        terminalCoordinator.register(admissionLifecycle::onTerminalDecision);
  }

  @PreDestroy
  public void close() {
    admissionRecovery.close();
    unregisterTerminalListener.run();
  }

  /**
   * Accepts a new historical job and submits it to the worker pool.
   *
   * <p>The job is first marked as received and queued. Then the service starts internal tracking so
   * the job TTL is refreshed and a timeout can be enforced. If the worker pool refuses the job, the
   * job is marked as failed immediately and failure notifications are sent.
   *
   * @param job the historical job request to execute
   * @throws IllegalStateException when the worker queue is full and the job cannot be accepted
   */
  public void accept(HistoricalTracksJobSubmission job) {
    long queuedStartNanos = System.nanoTime();
    if (!jobStateGateway.createReceivedIfAbsent(job)) {
      var existingState = jobStateGateway.findByJobId(job.jobId());
      if (existingState.isEmpty()) {
        log.warn("Historical job {} duplicate submission detected with missing state", job.jobId());
        return;
      }
      var state = existingState.get();
      if (state.stage() == HistoricalJobStage.CANCELLATION_REQUESTED) {
        terminalCancelBeforeStart(job);
        return;
      }
      if (state.isTerminal()) {
        log.info(
            "Historical job {} already reached terminal stage {}, skipping duplicate submission",
            job.jobId(),
            state.stage());
      } else {
        log.warn(
            "Historical job {} already in non-terminal stage {}, skipping duplicate submission",
            job.jobId(),
            state.stage());
      }
      return;
    }

    HistoricalJobAdmissionDecision decision = admissionCoordinator.register(job);
    admissionLifecycle.recordRegistrationDecision(decision, job, Context.current());

    if (!admissionLifecycle.recordQueuedAdmission(decision, job, elapsedSince(queuedStartNanos))) {
      return;
    }
    if (decision.action() != HistoricalJobAdmissionDecision.Action.START_NOW) {
      admissionRecovery.recoverStaleAdmission();
      return;
    }
    if (terminalCancelBeforeStart(job)) {
      return;
    }
    submitAdmittedJob(job, queuedStartNanos);
  }

  private void executeSubjectLanes(
      HistoricalTracksJobSubmission job, HistoricalSubjectExecutionMeta jobMetadata) {
    try {
      if (terminalCancelBeforeStart(job)) {
        return;
      }
      historicalSubjectLaneScheduler.execute(job);
      if (!markSuccessfulExecution(job) && !terminalCancelBeforeStart(job)) {
        log.error(
            "Historical job {} completed but final state transition was not persisted",
            job.jobId());
      }
    } catch (RuntimeException e) {
      executionOutcomeHandler.recordParentFailure(job, jobMetadata, e);
      throw e;
    }
  }

  private void submitAdmittedJob(HistoricalTracksJobSubmission job, long queuedStartNanos) {
    HistoricalSubjectExecutionMeta jobMetadata = parentExecutionMeta(job);
    try {
      executionSupervisor.submitParent(
          jobMetadata,
          ignored -> executeSubjectLanes(job, jobMetadata),
          ignored -> onExecutionComplete(job),
          ignored -> onCancelledBeforeStart(job));
    } catch (RejectedExecutionException e) {
      admissionLifecycle.recordRejectedExecutionExit(job.jobId(), job.requestId());
      rejectionHandler.reject(job, elapsedSince(queuedStartNanos), e);
    }
  }

  private void onTerminalIfNeeded(HistoricalTracksJobSubmission job) {
    onTerminalIfNeeded(job.jobId());
  }

  private void onTerminalIfNeeded(UUID jobId) {
    terminalCoordinator.finalizeIfTerminal(jobId);
  }

  private void recordLifecycleEvent(String eventName, Context lifecycleContext) {
    try (var _ = lifecycleContext.makeCurrent()) {
      Span currentSpan = Span.current();
      if (currentSpan.getSpanContext().isValid()) {
        currentSpan.addEvent(eventName);
      }
    }
  }

  private boolean terminalCancelBeforeStart(HistoricalTracksJobSubmission job) {
    return cancellationFinalizer.finalizeRequestedCancellation(job);
  }

  private boolean terminalCancelBeforeStart(
      HistoricalTracksJobSubmission job, UUID replacementRequestId) {
    return cancellationFinalizer.finalizeRequestedCancellation(job, replacementRequestId);
  }

  private static Duration elapsedSince(long startNanos) {
    long elapsedNanos = System.nanoTime() - startNanos;
    if (elapsedNanos <= 0L) {
      return MIN_STAGE_DURATION;
    }
    return Duration.ofNanos(elapsedNanos);
  }

  private void onExecutionComplete(HistoricalTracksJobSubmission job) {
    admissionLifecycle.onExecutionExit(job);
    onTerminalIfNeeded(job);
  }

  private void onCancelledBeforeStart(HistoricalTracksJobSubmission job) {
    terminalCancelBeforeStart(job);
    onExecutionComplete(job);
  }

  private boolean markSuccessfulExecution(HistoricalTracksJobSubmission job) {
    var currentState = jobStateGateway.findByJobId(job.jobId());
    boolean schedulerClaimedRunning =
        currentState
            .map(state -> state.stage() == HistoricalJobStage.EXECUTION_RUNNING)
            .orElse(false);
    if (job.issuer().type() == JobIssuerType.USER && schedulerClaimedRunning) {
      return jobStateGateway.markDeliveryPending(job.jobId());
    }
    return jobStateGateway.markCompleted(job.jobId());
  }

  private static HistoricalSubjectExecutionMeta parentExecutionMeta(
      HistoricalTracksJobSubmission job) {
    HistoricalTracksSubject firstSubject = job.subjects().getFirst();
    return new HistoricalSubjectExecutionMeta(
        job.jobId(),
        job.jobId(),
        job.requestId(),
        job.issuer(),
        job.requestedBy(),
        job.requestedAt(),
        job.requestedFrom(),
        job.requestedTo(),
        job.effectiveFrom(),
        job.effectiveTo(),
        job.resolution(),
        job.plannedChunks(),
        job.filters(),
        firstSubject.subjectType(),
        firstSubject.subjectId(),
        emptyWindowPlan(job));
  }

  private static HistoricalChunkWindowPlan emptyWindowPlan(HistoricalTracksJobSubmission job) {
    return new HistoricalChunkWindowPlan(
        job.effectiveFrom(), job.effectiveTo(), job.plannedChunks(), job.resolution(), List.of());
  }
}
