// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.history;

import com.opencop.core.sdk.model.history.HistoricalJobStage;
import com.opencop.core.sdk.model.job.HistoricalTracksJobSubmission;
import com.opencop.core.sdk.observability.history.HistoricalTelemetry.Outcome;
import com.opencop.processor.business.usecase.event.JobStatusNotificationPublisher;
import com.opencop.processor.ports.technical.gateway.history.HistoricalJobStateGateway;
import com.opencop.processor.ports.technical.gateway.history.HistoryEventPublisher;
import com.opencop.processor.technical.observability.history.HistoricalObservability;
import com.opencop.processor.technical.observability.history.HistoricalObservability.JobStage;
import java.time.Duration;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public final class HistoricalJobIngressRejectionHandler {

  private static final Logger log =
      LoggerFactory.getLogger(HistoricalJobIngressRejectionHandler.class);
  private static final String REJECTION_REASON = "Historical worker queue is full";

  private final HistoricalJobStateGateway jobStateGateway;
  private final JobStatusNotificationPublisher notifications;
  private final HistoryEventPublisher historyEventPublisher;
  private final HistoricalObservability historicalObservability;

  public HistoricalJobIngressRejectionHandler(
      HistoricalJobStateGateway jobStateGateway,
      JobStatusNotificationPublisher notifications,
      HistoryEventPublisher historyEventPublisher,
      HistoricalObservability historicalObservability) {
    this.jobStateGateway = jobStateGateway;
    this.notifications = notifications;
    this.historyEventPublisher = historyEventPublisher;
    this.historicalObservability = historicalObservability;
  }

  public void reject(
      HistoricalTracksJobSubmission job,
      Duration queuedDuration,
      RejectedExecutionException cause) {
    jobStateGateway.markFailed(job.jobId(), HistoricalJobStage.INGRESS_REJECTED, REJECTION_REASON);
    historicalObservability.recordJobStage(
        JobStage.INGRESS_REJECTED, Outcome.ERROR, queuedDuration);
    historyEventPublisher.publishFailed(job, HistoricalJobStage.INGRESS_REJECTED, REJECTION_REASON);
    notifications.notifyFailure(job, new IllegalStateException(REJECTION_REASON, cause));
    log.error("Historical job {} rejected at ingress: {}", job.jobId(), REJECTION_REASON, cause);
    throw new IllegalStateException(REJECTION_REASON, cause);
  }
}
