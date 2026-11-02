// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.usecase.history;

import com.opencop.core.sdk.model.job.JobIssuerType;
import com.opencop.core.sdk.technical.gateway.history.HistoryJobDeliveryCoordinator;
import com.opencop.processor.business.model.history.HistoricalExecutionContext;
import com.opencop.processor.business.model.history.HistoricalSubjectExecutionMeta;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkPlan;
import com.opencop.processor.ports.technical.gateway.history.HistoryEventPublisher;

public final class HistoricalChunkDeliveryProgressReporter {

  private final HistoryEventPublisher historyEventPublisher;
  private final HistoryJobDeliveryCoordinator jobDeliveryCoordinator;

  public HistoricalChunkDeliveryProgressReporter(
      HistoryEventPublisher historyEventPublisher,
      HistoryJobDeliveryCoordinator jobDeliveryCoordinator) {
    this.historyEventPublisher = historyEventPublisher;
    this.jobDeliveryCoordinator = jobDeliveryCoordinator;
  }

  public void start(HistoricalChunkPlan analyzedPlan, HistoricalSubjectExecutionMeta metadata) {
    if (userHistoryLifecycle(metadata) && jobDeliveryCoordinator != null) {
      jobDeliveryCoordinator.onExecutionStarted(
          metadata.laneJobId(),
          metadata.requestedBy(),
          metadata.requestId(),
          metadata.subjectType(),
          metadata.subjectId(),
          analyzedPlan.chunks(),
          analyzedPlan.cachedChunks());
    }
  }

  public void recordProgress(
      HistoricalChunkPlan analyzedPlan,
      HistoricalSubjectExecutionMeta metadata,
      long computedChunks,
      long failedChunks) {
    if (userHistoryLifecycle(metadata) && jobDeliveryCoordinator != null) {
      jobDeliveryCoordinator.recordExecutionProgress(
          metadata.laneJobId(), computedChunks, failedChunks);
    }
    historyEventPublisher.publishExecutionProgress(
        analyzedPlan, HistoricalExecutionContext.from(metadata), computedChunks, failedChunks);
  }

  public void finish(
      HistoricalSubjectExecutionMeta metadata, long computedChunks, long failedChunks) {
    if (userHistoryLifecycle(metadata) && jobDeliveryCoordinator != null) {
      jobDeliveryCoordinator.onExecutionFinished(
          metadata.laneJobId(), computedChunks, failedChunks);
    }
  }

  private static boolean userHistoryLifecycle(HistoricalSubjectExecutionMeta metadata) {
    return metadata != null && metadata.issuer().type() == JobIssuerType.USER;
  }
}
