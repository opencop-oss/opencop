// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.usecase.history;

import com.opencop.processor.business.model.history.HistoricalSubjectExecutionMeta;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkPlan;
import org.springframework.stereotype.Component;

@Component
public class HistoricalChunkExecutionCoordinator {
  private final HistoricalChunkDeliveryProgressReporter deliveryProgressReporter;
  private final HistoricalSequentialChunkExecutor sequentialChunkExecutor;

  public HistoricalChunkExecutionCoordinator(
      HistoricalChunkDeliveryProgressReporter deliveryProgressReporter,
      HistoricalSequentialChunkExecutor sequentialChunkExecutor) {
    this.deliveryProgressReporter = deliveryProgressReporter;
    this.sequentialChunkExecutor = sequentialChunkExecutor;
  }

  public void execute(HistoricalSubjectExecutionMeta metadata, HistoricalChunkPlan analyzedPlan) {
    deliveryProgressReporter.start(analyzedPlan, metadata);
    HistoricalChunkExecutionSummary summary =
        sequentialChunkExecutor.execute(metadata, analyzedPlan);
    deliveryProgressReporter.finish(metadata, summary.computedChunks(), summary.failedChunks());
  }
}
