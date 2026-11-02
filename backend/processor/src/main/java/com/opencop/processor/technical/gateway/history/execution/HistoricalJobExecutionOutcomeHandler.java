// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.history.execution;

import com.opencop.core.sdk.model.history.HistoricalJobStage;
import com.opencop.core.sdk.model.job.HistoricalTracksJobSubmission;
import com.opencop.core.sdk.model.job.JobIssuerType;
import com.opencop.core.sdk.observability.history.HistoricalTelemetry.Outcome;
import com.opencop.processor.business.model.history.HistoricalSubjectExecutionMeta;
import com.opencop.processor.business.policy.history.HistoricalExecutionFailureClassifier;
import com.opencop.processor.business.usecase.event.JobStatusNotificationPublisher;
import com.opencop.processor.business.usecase.history.HistoricalJobCancelledException;
import com.opencop.processor.business.usecase.history.HistoricalSubjectLaneJobHandler;
import com.opencop.processor.ports.technical.gateway.history.HistoricalJobStateGateway;
import com.opencop.processor.ports.technical.gateway.history.HistoryEventPublisher;
import com.opencop.processor.technical.observability.history.HistoricalObservability;
import com.opencop.processor.technical.observability.history.HistoricalObservability.JobStage;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public final class HistoricalJobExecutionOutcomeHandler {

  private static final Logger log =
      LoggerFactory.getLogger(HistoricalJobExecutionOutcomeHandler.class);
  private static final Duration MIN_STAGE_DURATION = Duration.ofNanos(1L);

  private final HistoricalJobStateGateway jobStateGateway;
  private final HistoricalSubjectLaneJobHandler historicalSubjectLaneJobHandler;
  private final JobStatusNotificationPublisher notifications;
  private final HistoryEventPublisher historyEventPublisher;
  private final HistoricalObservability historicalObservability;
  private final HistoricalExecutionFailureClassifier failureClassifier;

  public HistoricalJobExecutionOutcomeHandler(
      HistoricalJobStateGateway jobStateGateway,
      HistoricalSubjectLaneJobHandler historicalSubjectLaneJobHandler,
      JobStatusNotificationPublisher notifications,
      HistoryEventPublisher historyEventPublisher,
      HistoricalObservability historicalObservability,
      HistoricalExecutionFailureClassifier failureClassifier) {
    this.jobStateGateway = jobStateGateway;
    this.historicalSubjectLaneJobHandler = historicalSubjectLaneJobHandler;
    this.notifications = notifications;
    this.historyEventPublisher = historyEventPublisher;
    this.historicalObservability = historicalObservability;
    this.failureClassifier = failureClassifier;
  }

  public void execute(HistoricalSubjectExecutionMeta laneMetadata) {
    long executionStartNanos = System.nanoTime();
    try {
      historicalSubjectLaneJobHandler.handle(laneMetadata);
      recordSuccessfulLaneExecution(laneMetadata, executionStartNanos);
    } catch (Throwable failure) {
      recordFailedLaneExecution(laneMetadata, executionStartNanos, failure);
      throw propagate(failure);
    }
  }

  public void recordFailure(HistoricalSubjectExecutionMeta laneMetadata, Throwable failure) {
    recordFailedLaneExecution(laneMetadata, System.nanoTime(), failure);
  }

  public void recordParentFailure(
      HistoricalTracksJobSubmission job,
      HistoricalSubjectExecutionMeta parentMetadata,
      Throwable failure) {
    if (failure instanceof HistoricalJobCancelledException cancelledException) {
      String reason = safeMessage(cancelledException);
      if (jobStateGateway.markCancelled(parentMetadata.laneJobId(), reason)) {
        historicalObservability.recordJobStage(
            JobStage.CANCELLED, Outcome.SUCCESS, MIN_STAGE_DURATION);
      }
      historyEventPublisher.publishFailed(job, HistoricalJobStage.CANCELLED, reason);
      notifications.notifyFailure(job, cancelledException);
      return;
    }
    HistoricalJobStage stage = failureClassifier.classify(failure);
    String reason = safeMessage(failure);
    boolean marked = jobStateGateway.markFailed(parentMetadata.laneJobId(), stage, reason);
    if (!marked) {
      var currentState = jobStateGateway.findByJobId(parentMetadata.laneJobId());
      if (currentState.isPresent() && currentState.get().isTerminal()) {
        stage = currentState.get().stage();
        reason =
            currentState.get().failureReason() == null
                    || currentState.get().failureReason().isBlank()
                ? reason
                : currentState.get().failureReason();
      }
    }
    historyEventPublisher.publishFailed(job, stage, reason);
    notifications.notifyFailure(job, propagate(failure));
  }

  private void recordSuccessfulLaneExecution(
      HistoricalSubjectExecutionMeta laneMetadata, long executionStartNanos) {
    boolean marked =
        laneMetadata.issuer().type() == JobIssuerType.USER
            ? jobStateGateway.markDeliveryPending(laneMetadata.laneJobId())
            : jobStateGateway.markCompleted(laneMetadata.laneJobId());
    if (marked) {
      historicalObservability.recordJobStage(
          JobStage.COMPLETED, Outcome.SUCCESS, elapsedSince(executionStartNanos));
      return;
    }
    log.error(
        "Historical job {} completed execution but final state transition was not persisted",
        laneMetadata.laneJobId());
  }

  private void recordFailedLaneExecution(
      HistoricalSubjectExecutionMeta laneMetadata, long executionStartNanos, Throwable failure) {
    if (failure instanceof HistoricalJobCancelledException cancelledException) {
      if (jobStateGateway.markCancelled(
          laneMetadata.laneJobId(), safeMessage(cancelledException))) {
        historicalObservability.recordJobStage(
            JobStage.CANCELLED, Outcome.SUCCESS, elapsedSince(executionStartNanos));
      }
      return;
    }
    HistoricalJobStage stage = failureClassifier.classify(failure);
    String reason = safeMessage(failure);
    if (jobStateGateway.markFailed(laneMetadata.laneJobId(), stage, reason)) {
      historicalObservability.recordJobStage(
          JobStage.EXECUTION_FAILED, Outcome.ERROR, elapsedSince(executionStartNanos));
    } else {
      log.error(
          "Historical job {} failed during execution but failed state transition was not persisted: {}",
          laneMetadata.laneJobId(),
          reason,
          failure);
    }
    log.error(
        "Historical job {} marked as {} after failure in worker thread",
        laneMetadata.laneJobId(),
        stage,
        failure);
  }

  private static String safeMessage(Throwable exception) {
    return exception.getMessage() == null || exception.getMessage().isBlank()
        ? exception.getClass().getSimpleName()
        : exception.getMessage();
  }

  private static RuntimeException propagate(Throwable failure) {
    if (failure instanceof RuntimeException runtimeException) {
      return runtimeException;
    }
    if (failure instanceof Error error) {
      throw error;
    }
    return new IllegalStateException(safeMessage(failure), failure);
  }

  private static Duration elapsedSince(long startNanos) {
    long elapsedNanos = System.nanoTime() - startNanos;
    if (elapsedNanos <= 0L) {
      return MIN_STAGE_DURATION;
    }
    return Duration.ofNanos(elapsedNanos);
  }
}
