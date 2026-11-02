// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.policy.history;

import com.opencop.processor.business.model.history.chunk.HistoricalChunkFailureDecision;
import java.util.UUID;

public final class HistoricalChunkFailurePolicy {

  private final int maxConsecutiveChunkFailures;
  private final double maxFailureRatio;

  public HistoricalChunkFailurePolicy(int maxConsecutiveChunkFailures, double maxFailureRatio) {
    this.maxConsecutiveChunkFailures = maxConsecutiveChunkFailures;
    this.maxFailureRatio = maxFailureRatio;
  }

  public HistoricalChunkFailureDecision evaluate(
      UUID jobId, int consecutiveFailures, long failedChunks, int totalChunks) {
    double ratio = (double) failedChunks / totalChunks;
    boolean consecutiveTripped =
        maxConsecutiveChunkFailures > 0 && consecutiveFailures > maxConsecutiveChunkFailures;
    boolean ratioTripped = ratio > maxFailureRatio;
    if (!consecutiveTripped && !ratioTripped) {
      return HistoricalChunkFailureDecision.continueExecution(
          consecutiveFailures, failedChunks, totalChunks, ratio);
    }
    return HistoricalChunkFailureDecision.abort(
        consecutiveTripped,
        ratioTripped,
        consecutiveFailures,
        failedChunks,
        totalChunks,
        ratio,
        String.format(
            "Aborting job %s: failure ratio exceeded threshold "
                + "(consecutiveFailures=%d, failedChunks=%d, totalChunks=%d, ratio=%.4f)",
            jobId, consecutiveFailures, failedChunks, totalChunks, ratio));
  }
}
