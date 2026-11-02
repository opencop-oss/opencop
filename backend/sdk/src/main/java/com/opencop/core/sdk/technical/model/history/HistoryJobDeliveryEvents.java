// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.model.history;

import com.opencop.core.sdk.model.history.HistoryJobCompletedEvent;
import com.opencop.core.sdk.model.history.HistoryJobFailedEvent;
import com.opencop.core.sdk.model.history.HistoryJobProgressEvent;

/**
 * Builds history job lifecycle events from a {@link HistoricalJobDeliverySnapshot}, matching {@code
 * HistoryJobPlanPublisher} formulas for remaining chunks and percent complete.
 */
public final class HistoryJobDeliveryEvents {

  private HistoryJobDeliveryEvents() {}

  public static HistoryJobProgressEvent progress(HistoricalJobDeliverySnapshot s) {
    long remainingChunks = Math.max(0L, s.totalChunks() - s.deliveredChunks() - s.failedChunks());
    int percentComplete =
        s.totalChunks() == 0
            ? 100
            : (int) Math.min(100L, (s.deliveredChunks() * 100L) / s.totalChunks());
    return new HistoryJobProgressEvent(
        s.targetUserId(),
        s.requestId(),
        s.jobId(),
        s.subjectType(),
        s.subjectId(),
        s.totalChunks(),
        s.cachedChunks(),
        s.computedChunks(),
        s.deliveredChunks(),
        s.failedChunks(),
        remainingChunks,
        percentComplete);
  }

  public static HistoryJobCompletedEvent completed(HistoricalJobDeliverySnapshot s) {
    return new HistoryJobCompletedEvent(
        s.targetUserId(),
        s.requestId(),
        s.jobId(),
        s.subjectType(),
        s.subjectId(),
        s.totalChunks(),
        s.cachedChunks(),
        s.computedChunks(),
        s.deliveredChunks(),
        s.failedChunks());
  }

  public static HistoryJobFailedEvent failed(HistoricalJobDeliverySnapshot s, String stage) {
    return new HistoryJobFailedEvent(
        s.targetUserId(),
        s.requestId(),
        s.jobId(),
        s.subjectType(),
        s.subjectId(),
        stage,
        s.failureReason() == null || s.failureReason().isBlank()
            ? "Historical delivery failed"
            : s.failureReason());
  }
}
