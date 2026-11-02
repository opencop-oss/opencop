// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.history.cancellation;

import com.opencop.core.sdk.model.history.HistoricalJobStage;
import com.opencop.core.sdk.model.job.HistoricalTracksJobSubmission;
import com.opencop.core.sdk.observability.history.HistoricalTelemetry.Outcome;
import com.opencop.processor.ports.technical.gateway.history.HistoricalJobStateGateway;
import com.opencop.processor.ports.technical.gateway.history.HistoryEventPublisher;
import com.opencop.processor.technical.gateway.history.HistoryJobLifecycleEventRecorder;
import com.opencop.processor.technical.observability.history.HistoricalObservability;
import com.opencop.processor.technical.observability.history.HistoricalObservability.JobStage;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public final class HistoricalJobCancellationFinalizer {

  private static final Logger log =
      LoggerFactory.getLogger(HistoricalJobCancellationFinalizer.class);
  private static final Duration MIN_STAGE_DURATION = Duration.ofNanos(1L);
  private static final String CANCELLED_EXIT_EVENT = "history.job.cancelled_exit";

  private final HistoricalJobStateGateway jobStateGateway;
  private final HistoryEventPublisher historyEventPublisher;
  private final HistoricalObservability historicalObservability;
  private final HistoryJobLifecycleEventRecorder lifecycleEventRecorder;

  public HistoricalJobCancellationFinalizer(
      HistoricalJobStateGateway jobStateGateway,
      HistoryEventPublisher historyEventPublisher,
      HistoricalObservability historicalObservability,
      HistoryJobLifecycleEventRecorder lifecycleEventRecorder) {
    this.jobStateGateway = jobStateGateway;
    this.historyEventPublisher = historyEventPublisher;
    this.historicalObservability = historicalObservability;
    this.lifecycleEventRecorder = lifecycleEventRecorder;
  }

  public boolean finalizeRequestedCancellation(HistoricalTracksJobSubmission job) {
    var currentState = jobStateGateway.findByJobId(job.jobId());
    if (currentState.isEmpty()
        || currentState.get().stage() != HistoricalJobStage.CANCELLATION_REQUESTED) {
      return false;
    }
    return markRequestedCancellation(
        job,
        HistoricalJobCancellationReasonPolicy.requestedCancellationReason(
            currentState.get().failureReason()));
  }

  public boolean finalizeRequestedCancellation(
      HistoricalTracksJobSubmission job, UUID replacementRequestId) {
    var currentState = jobStateGateway.findByJobId(job.jobId());
    if (currentState.isEmpty()
        || currentState.get().stage() != HistoricalJobStage.CANCELLATION_REQUESTED) {
      return false;
    }
    return markRequestedCancellation(
        job,
        HistoricalJobCancellationReasonPolicy.requestedCancellationReason(
            currentState.get().failureReason(), replacementRequestId));
  }

  private boolean markRequestedCancellation(HistoricalTracksJobSubmission job, String reason) {
    if (jobStateGateway.markCancelled(job.jobId(), reason)) {
      recordCancelled(job, reason);
    } else {
      log.error("Historical job {} cancellation was not persisted", job.jobId());
    }
    return true;
  }

  public void finalizeThrownCancellation(
      HistoricalTracksJobSubmission job, String reason, Throwable cause) {
    if (!jobStateGateway.markCancelled(job.jobId(), reason)) {
      log.error("Historical job {} cancellation was not persisted", job.jobId(), cause);
      historyEventPublisher.publishFailed(job, HistoricalJobStage.CANCELLED, reason);
      return;
    }
    recordCancelled(job, reason);
  }

  private void recordCancelled(HistoricalTracksJobSubmission job, String reason) {
    historicalObservability.recordJobStage(JobStage.CANCELLED, Outcome.SUCCESS, MIN_STAGE_DURATION);
    lifecycleEventRecorder.record(CANCELLED_EXIT_EVENT);
    historyEventPublisher.publishFailed(job, HistoricalJobStage.CANCELLED, reason);
  }
}
