// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.usecase.history;

import com.opencop.processor.business.model.history.HistoricalExecutionContext;
import com.opencop.processor.business.model.history.HistoricalSubjectExecutionMeta;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkCacheStatus;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkFailureDecision;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkPlan;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkPlanEntry;
import com.opencop.processor.business.policy.history.HistoricalChunkFailurePolicy;
import com.opencop.processor.business.usecase.history.HistoricalChunkExecutionProgress.NeutralResultHandling;
import com.opencop.processor.technical.observability.history.HistoricalObservability.Cached;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public final class HistoricalSequentialChunkExecutor {
  private static final Logger log =
      LoggerFactory.getLogger(HistoricalSequentialChunkExecutor.class);

  private final HistoricalStateReuseExecutor stateReuseExecutor;
  private final HistoricalChunkExecutionTelemetry executionTelemetry;
  private final HistoricalChunkFailurePolicy failurePolicy;
  private final HistoricalChunkCancellationGuard cancellationGuard;
  private final HistoricalChunkDeliveryProgressReporter deliveryProgressReporter;
  private final HistoricalChunkComputeKeepalive computeKeepalive;

  public HistoricalSequentialChunkExecutor(
      HistoricalStateReuseExecutor stateReuseExecutor,
      HistoricalChunkExecutionTelemetry executionTelemetry,
      HistoricalChunkFailurePolicy failurePolicy,
      HistoricalChunkCancellationGuard cancellationGuard,
      HistoricalChunkDeliveryProgressReporter deliveryProgressReporter,
      HistoricalChunkComputeKeepalive computeKeepalive) {
    this.stateReuseExecutor = stateReuseExecutor;
    this.executionTelemetry = executionTelemetry;
    this.failurePolicy = failurePolicy;
    this.cancellationGuard = cancellationGuard;
    this.deliveryProgressReporter = deliveryProgressReporter;
    this.computeKeepalive = computeKeepalive;
  }

  HistoricalChunkExecutionSummary execute(
      HistoricalSubjectExecutionMeta metadata, HistoricalChunkPlan analyzedPlan) {
    var progress = new HistoricalChunkExecutionProgress(failurePolicy);
    var stateReuseSession = stateReuseExecutor.newSession();

    for (int chunkPosition = 0; chunkPosition < analyzedPlan.entries().size(); chunkPosition++) {
      executeLaneChunk(metadata, analyzedPlan, stateReuseSession, progress, chunkPosition);
    }

    return progress.summary();
  }

  private void executeLaneChunk(
      HistoricalSubjectExecutionMeta metadata,
      HistoricalChunkPlan analyzedPlan,
      HistoricalStateReuseExecutor.Session stateReuseSession,
      HistoricalChunkExecutionProgress progress,
      int chunkPosition) {
    int totalChunks = analyzedPlan.entries().size();
    HistoricalChunkPlanEntry chunk = analyzedPlan.entries().get(chunkPosition);
    HistoricalExecutionContext executionContext = HistoricalExecutionContext.from(metadata);
    throwIfCancellationRequested(metadata, totalChunks - chunkPosition);

    HistoricalChunkExecutionResult result =
        executeWithTelemetry(
            metadata,
            analyzedPlan,
            chunk,
            progress,
            () -> stateReuseSession.executeChunk(executionContext, analyzedPlan, chunk));

    recordLaneResult(metadata, totalChunks, progress, result);
    deliveryProgressReporter.recordProgress(
        analyzedPlan, metadata, progress.computedChunks(), progress.failedChunks());
  }

  private HistoricalChunkExecutionResult executeWithTelemetry(
      HistoricalSubjectExecutionMeta metadata,
      HistoricalChunkPlan analyzedPlan,
      HistoricalChunkPlanEntry chunk,
      HistoricalChunkExecutionProgress progress,
      HistoricalChunkExecutionTelemetry.ChunkOperation operation) {
    HistoricalChunkInterruption.ensureNotInterrupted();
    return computeKeepalive.execute(
        analyzedPlan,
        metadata,
        progress::computedChunks,
        progress::failedChunks,
        () ->
            executionTelemetry.execute(
                metadata, analyzedPlan, chunk, cachedTelemetry(chunk), operation));
  }

  private void recordLaneResult(
      HistoricalSubjectExecutionMeta metadata,
      int totalChunks,
      HistoricalChunkExecutionProgress progress,
      HistoricalChunkExecutionResult result) {
    HistoricalChunkFailureDecision decision =
        progress.applyResult(
            metadata.laneJobId(),
            result,
            totalChunks,
            NeutralResultHandling.PRESERVE_FAILURE_STREAK);
    checkCircuitBreaker(decision);
  }

  private static void checkCircuitBreaker(HistoricalChunkFailureDecision decision) {
    if (!decision.shouldAbort()) {
      return;
    }
    log.warn(decision.reason());
    throw new IllegalStateException(decision.reason());
  }

  private void throwIfCancellationRequested(
      HistoricalSubjectExecutionMeta metadata, int skippedNotYetStartedChunks) {
    cancellationGuard.throwIfCancellationRequested(
        HistoricalExecutionContext.from(metadata), skippedNotYetStartedChunks, () -> {});
  }

  private static Cached cachedTelemetry(HistoricalChunkPlanEntry chunk) {
    if (chunk.cacheStatus() == HistoricalChunkCacheStatus.CACHED) {
      return Cached.TRUE;
    }
    return Cached.FALSE;
  }
}
