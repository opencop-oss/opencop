// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.usecase.history;

import com.opencop.processor.business.model.history.chunk.HistoricalChunkFailureDecision;
import com.opencop.processor.business.policy.history.HistoricalChunkFailurePolicy;
import java.util.UUID;

final class HistoricalChunkExecutionProgress {

  private final HistoricalChunkFailurePolicy failurePolicy;
  private long computedChunks;
  private long failedChunks;
  private int consecutiveFailures;

  HistoricalChunkExecutionProgress(HistoricalChunkFailurePolicy failurePolicy) {
    this.failurePolicy = failurePolicy;
  }

  synchronized HistoricalChunkFailureDecision applyResult(
      UUID jobId,
      HistoricalChunkExecutionResult result,
      int totalChunks,
      NeutralResultHandling neutralResultHandling) {
    if (result == HistoricalChunkExecutionResult.COMPUTED) {
      computedChunks++;
      consecutiveFailures = 0;
      return continueExecution(totalChunks);
    }
    if (result == HistoricalChunkExecutionResult.FAILED) {
      failedChunks++;
      consecutiveFailures++;
      return failurePolicy.evaluate(jobId, consecutiveFailures, failedChunks, totalChunks);
    }
    if (neutralResultHandling == NeutralResultHandling.RESET_FAILURE_STREAK) {
      consecutiveFailures = 0;
    }
    return continueExecution(totalChunks);
  }

  synchronized long computedChunks() {
    return computedChunks;
  }

  synchronized long failedChunks() {
    return failedChunks;
  }

  synchronized HistoricalChunkExecutionSummary summary() {
    return new HistoricalChunkExecutionSummary(computedChunks, failedChunks);
  }

  private HistoricalChunkFailureDecision continueExecution(int totalChunks) {
    double failureRatio = totalChunks == 0 ? 0.0d : (double) failedChunks / totalChunks;
    return HistoricalChunkFailureDecision.continueExecution(
        consecutiveFailures, failedChunks, totalChunks, failureRatio);
  }

  enum NeutralResultHandling {
    PRESERVE_FAILURE_STREAK,
    RESET_FAILURE_STREAK
  }
}
