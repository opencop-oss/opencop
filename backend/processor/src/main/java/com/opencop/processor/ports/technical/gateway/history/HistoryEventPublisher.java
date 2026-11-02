// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.ports.technical.gateway.history;

import com.opencop.core.sdk.model.history.HistoricalJobStage;
import com.opencop.core.sdk.model.job.HistoricalTracksJobSubmission;
import com.opencop.processor.business.model.history.HistoricalExecutionContext;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkPlan;

public interface HistoryEventPublisher {

  void publishPlan(HistoricalChunkPlan plan, HistoricalExecutionContext executionContext);

  void publishProgress(
      HistoricalChunkPlan plan,
      HistoricalTracksJobSubmission job,
      long computedChunks,
      long deliveredChunks,
      long failedChunks);

  /** Internal execution progress hook. User-facing websocket progress is delivery-driven only. */
  void publishExecutionProgress(
      HistoricalChunkPlan plan,
      HistoricalExecutionContext executionContext,
      long computedChunks,
      long failedChunks);

  void publishCompleted(
      HistoricalChunkPlan plan,
      HistoricalTracksJobSubmission job,
      long computedChunks,
      long deliveredChunks,
      long failedChunks);

  void publishFailed(
      HistoricalTracksJobSubmission job, HistoricalJobStage stage, String failureReason);
}
