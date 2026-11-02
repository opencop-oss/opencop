// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.history.admission;

import static com.opencop.processor.technical.gateway.history.model.job.HistoricalJobState.QUEUE_ROLE_DISPATCH_READY;

import com.opencop.core.sdk.model.history.HistoricalJobStage;
import com.opencop.core.sdk.model.job.HistoricalTracksJobSubmission;
import com.opencop.core.sdk.model.job.JobIssuerType;
import com.opencop.core.sdk.observability.history.HistoricalTelemetry.Outcome;
import com.opencop.processor.ports.technical.gateway.history.HistoricalJobAdmissionCoordinator;
import com.opencop.processor.ports.technical.gateway.history.HistoricalJobStateGateway;
import com.opencop.processor.technical.gateway.history.HistoricalJobTerminalDecision;
import com.opencop.processor.technical.gateway.history.model.job.HistoricalJobAdmissionDecision;
import com.opencop.processor.technical.gateway.history.model.job.HistoricalJobState;
import com.opencop.processor.technical.observability.history.HistoricalObservability;
import com.opencop.processor.technical.observability.history.HistoricalObservability.JobStage;
import io.opentelemetry.context.Context;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class HistoricalJobAdmissionLifecycle {

  private static final Logger log = LoggerFactory.getLogger(HistoricalJobAdmissionLifecycle.class);
  private static final Duration MIN_STAGE_DURATION = Duration.ofNanos(1L);
  private static final String REPLACEMENT_QUEUED_EVENT = "history.job.replacement_queued";
  private static final String REPLACEMENT_SUPERSEDED_EVENT = "history.job.replacement_superseded";
  private static final String REPLACEMENT_STARTED_EVENT = "history.job.replacement_started";

  private final HistoricalJobStateGateway jobStateGateway;
  private final HistoricalJobAdmissionCoordinator admissionCoordinator;
  private final HistoricalObservability historicalObservability;
  private final Predicate<UUID> jobIsTracked;
  private final Consumer<UUID> trackedJobCanceller;
  private final BiConsumer<HistoricalTracksJobSubmission, Long> jobSubmitter;
  private final BiPredicate<HistoricalTracksJobSubmission, UUID> cancellationFinalizer;
  private final BiConsumer<String, Context> lifecycleEventRecorder;
  private final Map<UUID, Context> replacementLifecycleContexts = new ConcurrentHashMap<>();

  public HistoricalJobAdmissionLifecycle(
      HistoricalJobStateGateway jobStateGateway,
      HistoricalJobAdmissionCoordinator admissionCoordinator,
      HistoricalObservability historicalObservability,
      Predicate<UUID> jobIsTracked,
      Consumer<UUID> trackedJobCanceller,
      BiConsumer<HistoricalTracksJobSubmission, Long> jobSubmitter,
      BiPredicate<HistoricalTracksJobSubmission, UUID> cancellationFinalizer,
      BiConsumer<String, Context> lifecycleEventRecorder) {
    this.jobStateGateway = jobStateGateway;
    this.admissionCoordinator = admissionCoordinator;
    this.historicalObservability = historicalObservability;
    this.jobIsTracked = jobIsTracked;
    this.trackedJobCanceller = trackedJobCanceller;
    this.jobSubmitter = jobSubmitter;
    this.cancellationFinalizer = cancellationFinalizer;
    this.lifecycleEventRecorder = lifecycleEventRecorder;
  }

  public void recordRegistrationDecision(
      HistoricalJobAdmissionDecision decision,
      HistoricalTracksJobSubmission job,
      Context lifecycleContext) {
    applyCoordinatorCancellations(decision);
    recordCoordinatorEvents(decision, job, lifecycleContext);
  }

  public boolean recordQueuedAdmission(
      HistoricalJobAdmissionDecision decision,
      HistoricalTracksJobSubmission job,
      Duration duration) {
    String queueRole = admissionQueueRole(decision);
    boolean queuedMarked =
        QUEUE_ROLE_DISPATCH_READY.equals(queueRole)
            ? jobStateGateway.markQueued(job.jobId())
            : jobStateGateway.markQueued(job.jobId(), queueRole);
    if (!queuedMarked) {
      cleanupAfterRegistrationFailure(job.jobId(), job.requestId());
      return false;
    }
    historicalObservability.recordJobStage(JobStage.QUEUED, Outcome.SUCCESS, duration);
    return true;
  }

  public void cleanupAfterRegistrationFailure(UUID jobId, UUID requestId) {
    HistoricalJobAdmissionDecision cleanupDecision =
        admissionCoordinator.cleanupAfterRegistrationFailure(jobId);
    applyDecisionAndStartJobs(cleanupDecision, requestId);
  }

  public void recordRejectedExecutionExit(UUID jobId, UUID requestId) {
    HistoricalJobAdmissionDecision decision = admissionCoordinator.recordExecutionExit(jobId);
    applyDecisionAndStartJobs(decision, requestId);
  }

  public void onExecutionExit(HistoricalTracksJobSubmission job) {
    var currentState = jobStateGateway.findByJobId(job.jobId());
    if (currentState.isPresent() && currentState.get().isTerminal()) {
      return;
    }
    if (currentState.isPresent()
        && job.issuer().type() == JobIssuerType.USER
        && currentState.get().stage() == HistoricalJobStage.DELIVERY_PENDING) {
      return;
    }
    HistoricalJobAdmissionDecision decision = admissionCoordinator.recordExecutionExit(job.jobId());
    applyDecisionAndStartJobs(decision, job.requestId());
  }

  public void onTerminalDecision(HistoricalJobTerminalDecision terminalDecision) {
    HistoricalJobAdmissionDecision decision = terminalDecision.admissionDecision();
    applyCoordinatorCancellations(decision);
    if (decision.replacementStarted() || !decision.jobsToStart().isEmpty()) {
      historicalObservability.recordJobStage(
          JobStage.REPLACEMENT_STARTED, Outcome.SUCCESS, MIN_STAGE_DURATION);
      Context lifecycleContext =
          decision.jobsToStart().isEmpty()
              ? Context.current()
              : replacementLifecycleContexts.getOrDefault(
                  decision.jobsToStart().getFirst().requestId(), Context.current());
      lifecycleEventRecorder.accept(REPLACEMENT_STARTED_EVENT, lifecycleContext);
    }
    if (!decision.jobsToStart().isEmpty()) {
      replacementLifecycleContexts.remove(decision.jobsToStart().getFirst().requestId());
    }
    startCoordinatorJobs(decision.jobsToStart(), terminalDecision.requestId());
  }

  private String admissionQueueRole(HistoricalJobAdmissionDecision decision) {
    return decision.queueRole() == null || decision.queueRole().isBlank()
        ? QUEUE_ROLE_DISPATCH_READY
        : decision.queueRole();
  }

  public void processRecoveredDecision(HistoricalJobAdmissionDecision decision) {
    if (decision.jobsToStart().isEmpty() && decision.jobsToCancel().isEmpty()) {
      return;
    }
    log.info(
        "Historical admission recovery released stale terminal active job; starting {} queued jobs",
        decision.jobsToStart().size());
    applyDecisionAndStartJobs(decision, null);
  }

  private void applyDecisionAndStartJobs(
      HistoricalJobAdmissionDecision decision, UUID replacementRequestId) {
    applyCoordinatorCancellations(decision);
    startCoordinatorJobs(decision.jobsToStart(), replacementRequestId);
  }

  private void applyCoordinatorCancellations(HistoricalJobAdmissionDecision decision) {
    for (HistoricalJobAdmissionDecision.HistoricalJobCancellation cancellation :
        decision.jobsToCancel()) {
      var existing = jobStateGateway.findByJobId(cancellation.jobId());
      if (shouldRequestCancellation(cancellation.jobId(), existing)) {
        if (jobStateGateway.markCancellationRequested(
            cancellation.jobId(), cancellation.reason())) {
          historicalObservability.recordJobStage(
              JobStage.CANCELLATION_REQUESTED, Outcome.SUCCESS, MIN_STAGE_DURATION);
        }
        trackedJobCanceller.accept(cancellation.jobId());
      } else {
        jobStateGateway.markCancelled(cancellation.jobId(), cancellation.reason());
      }
    }
  }

  private boolean shouldRequestCancellation(UUID jobId, Optional<HistoricalJobState> existing) {
    return jobIsTracked.test(jobId)
        || existing.map(this::isCancellableAdmissionStage).orElse(false);
  }

  private boolean isCancellableAdmissionStage(HistoricalJobState state) {
    return HistoricalJobStage.EXECUTION_RUNNING.equals(state.stage())
        || HistoricalJobStage.PLANNING.equals(state.stage())
        || HistoricalJobStage.PLANNED.equals(state.stage());
  }

  private void recordCoordinatorEvents(
      HistoricalJobAdmissionDecision decision,
      HistoricalTracksJobSubmission job,
      Context lifecycleContext) {
    if (decision.replacementQueued()) {
      replacementLifecycleContexts.put(job.requestId(), lifecycleContext);
      historicalObservability.recordJobStage(
          JobStage.REPLACEMENT_QUEUED, Outcome.SUCCESS, MIN_STAGE_DURATION);
      lifecycleEventRecorder.accept(REPLACEMENT_QUEUED_EVENT, lifecycleContext);
    }
    if (decision.replacementSuperseded()) {
      historicalObservability.recordJobStage(
          JobStage.REPLACEMENT_SUPERSEDED, Outcome.SUCCESS, MIN_STAGE_DURATION);
      lifecycleEventRecorder.accept(REPLACEMENT_SUPERSEDED_EVENT, lifecycleContext);
    }
  }

  private void startCoordinatorJobs(
      Iterable<HistoricalTracksJobSubmission> jobsToStart, UUID replacementRequestId) {
    for (HistoricalTracksJobSubmission queuedJob : jobsToStart) {
      if (cancellationFinalizer.test(queuedJob, replacementRequestId)) {
        HistoricalJobAdmissionDecision decision =
            admissionCoordinator.recordExecutionExit(queuedJob.jobId());
        applyDecisionAndStartJobs(decision, replacementRequestId);
        continue;
      }
      jobStateGateway
          .findByJobId(queuedJob.jobId())
          .filter(state -> !state.isTerminal())
          .ifPresent(ignored -> jobSubmitter.accept(queuedJob, System.nanoTime()));
    }
  }
}
