// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.model.history.chunk;

public record HistoricalChunkFailureDecision(
    boolean shouldAbort,
    boolean consecutiveThresholdExceeded,
    boolean ratioThresholdExceeded,
    int consecutiveFailures,
    long failedChunks,
    int totalChunks,
    double failureRatio,
    String reason) {

  public static HistoricalChunkFailureDecision continueExecution(
      int consecutiveFailures, long failedChunks, int totalChunks, double failureRatio) {
    return new HistoricalChunkFailureDecision(
        false, false, false, consecutiveFailures, failedChunks, totalChunks, failureRatio, null);
  }

  public static HistoricalChunkFailureDecision abort(
      boolean consecutiveThresholdExceeded,
      boolean ratioThresholdExceeded,
      int consecutiveFailures,
      long failedChunks,
      int totalChunks,
      double failureRatio,
      String reason) {
    return new HistoricalChunkFailureDecision(
        true,
        consecutiveThresholdExceeded,
        ratioThresholdExceeded,
        consecutiveFailures,
        failedChunks,
        totalChunks,
        failureRatio,
        reason);
  }
}
