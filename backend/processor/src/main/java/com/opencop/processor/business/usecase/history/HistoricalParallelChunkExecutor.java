// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.usecase.history;

import com.opencop.processor.business.model.history.HistoricalExecutionContext;
import com.opencop.processor.business.model.history.HistoricalSubjectExecutionMeta;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkFailureDecision;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkPlan;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkPlanEntry;
import com.opencop.processor.business.policy.history.HistoricalChunkFailurePolicy;
import com.opencop.processor.business.usecase.history.HistoricalChunkExecutionProgress.NeutralResultHandling;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class HistoricalParallelChunkExecutor {
  private static final Logger log = LoggerFactory.getLogger(HistoricalParallelChunkExecutor.class);

  private final Supplier<ExecutorService> chunkExecutorFactory;
  private final int maxConcurrentChunksPerJob;
  private final HistoricalChunkFailurePolicy failurePolicy;
  private final HistoricalChunkCancellationGuard cancellationGuard;
  private final HistoricalChunkDeliveryProgressReporter deliveryProgressReporter;
  private final HistoricalChunkOperationExecutor chunkOperationExecutor;
  private final HistoricalChunkComputeKeepalive computeKeepalive;

  public HistoricalParallelChunkExecutor(
      Supplier<ExecutorService> chunkExecutorFactory,
      int maxConcurrentChunksPerJob,
      HistoricalChunkFailurePolicy failurePolicy,
      HistoricalChunkCancellationGuard cancellationGuard,
      HistoricalChunkDeliveryProgressReporter deliveryProgressReporter,
      HistoricalChunkOperationExecutor chunkOperationExecutor,
      HistoricalChunkComputeKeepalive computeKeepalive) {
    this.chunkExecutorFactory = chunkExecutorFactory;
    this.maxConcurrentChunksPerJob = Math.clamp(maxConcurrentChunksPerJob, 1, Integer.MAX_VALUE);
    this.failurePolicy = failurePolicy;
    this.cancellationGuard = cancellationGuard;
    this.deliveryProgressReporter = deliveryProgressReporter;
    this.chunkOperationExecutor = chunkOperationExecutor;
    this.computeKeepalive = computeKeepalive;
  }

  HistoricalChunkExecutionSummary execute(
      HistoricalSubjectExecutionMeta metadata, HistoricalChunkPlan analyzedPlan) {
    ExecutorService chunkExecutor = chunkExecutorFactory.get();
    int totalChunks = analyzedPlan.entries().size();
    LaneRunState runState = null;

    try {
      throwIfCancellationRequested(metadata, List.of(), totalChunks);
      runState = startLaneRun(metadata, analyzedPlan, chunkExecutor);

      try {
        completeSubmittedLaneChunks(metadata, analyzedPlan, runState);
      } catch (InterruptedException e) {
        throw interruptedDuringExecution(runState, e);
      } catch (ExecutionException e) {
        throw unwrapExecutionFailure(runState, e);
      }

      throwIfCancellationRequested(metadata, List.of(), 0);
      return runState.progress.summary();
    } finally {
      if (runState != null) {
        cancelOutstanding(runState.futures);
      }
      chunkExecutor.shutdownNow();
    }
  }

  private LaneRunState startLaneRun(
      HistoricalSubjectExecutionMeta metadata,
      HistoricalChunkPlan analyzedPlan,
      ExecutorService chunkExecutor) {
    CompletionService<HistoricalChunkExecutionResult> completionService =
        new ExecutorCompletionService<>(chunkExecutor);
    List<Future<HistoricalChunkExecutionResult>> futures =
        new ArrayList<>(analyzedPlan.entries().size());
    LaneRunState runState =
        new LaneRunState(
            completionService, futures, 0, new HistoricalChunkExecutionProgress(failurePolicy));
    runState.submitted = submitInitialLaneChunks(metadata, analyzedPlan, runState);
    return runState;
  }

  private void completeSubmittedLaneChunks(
      HistoricalSubjectExecutionMeta metadata,
      HistoricalChunkPlan analyzedPlan,
      LaneRunState runState)
      throws InterruptedException, ExecutionException {
    for (int completed = 0; completed < analyzedPlan.entries().size(); completed++) {
      completeNextLaneChunk(metadata, analyzedPlan, runState);
      submitNextLaneChunkIfAvailable(metadata, analyzedPlan, runState);
    }
  }

  private void completeNextLaneChunk(
      HistoricalSubjectExecutionMeta metadata,
      HistoricalChunkPlan analyzedPlan,
      LaneRunState runState)
      throws InterruptedException, ExecutionException {
    HistoricalChunkInterruption.ensureNotInterrupted();
    HistoricalChunkExecutionResult result = runState.completionService.take().get();
    recordLaneResult(metadata, analyzedPlan.entries().size(), runState, result);
    deliveryProgressReporter.recordProgress(
        analyzedPlan,
        metadata,
        runState.progress.computedChunks(),
        runState.progress.failedChunks());
  }

  private void recordLaneResult(
      HistoricalSubjectExecutionMeta metadata,
      int totalChunks,
      LaneRunState runState,
      HistoricalChunkExecutionResult result) {
    HistoricalChunkFailureDecision decision =
        runState.progress.applyResult(
            metadata.laneJobId(), result, totalChunks, NeutralResultHandling.RESET_FAILURE_STREAK);
    checkCircuitBreaker(runState.futures, decision);
  }

  private void submitNextLaneChunkIfAvailable(
      HistoricalSubjectExecutionMeta metadata,
      HistoricalChunkPlan analyzedPlan,
      LaneRunState runState) {
    int totalChunks = analyzedPlan.entries().size();
    if (runState.submitted >= totalChunks) {
      return;
    }
    throwIfCancellationRequested(metadata, runState.futures, totalChunks - runState.submitted);
    submitLaneChunk(metadata, analyzedPlan, runState, runState.submitted);
    runState.submitted++;
  }

  private static IllegalStateException interruptedDuringExecution(
      LaneRunState runState, InterruptedException error) {
    Thread.currentThread().interrupt();
    cancelOutstanding(runState.futures);
    return new IllegalStateException(HistoricalChunkInterruption.interruptedMessage(), error);
  }

  private static RuntimeException unwrapExecutionFailure(
      LaneRunState runState, ExecutionException error) {
    cancelOutstanding(runState.futures);
    Throwable cause = error.getCause();
    if (cause instanceof Error fatal) {
      throw fatal;
    }
    if (cause instanceof RuntimeException runtimeException) {
      return runtimeException;
    }
    return new IllegalStateException(cause);
  }

  private int submitInitialLaneChunks(
      HistoricalSubjectExecutionMeta metadata,
      HistoricalChunkPlan analyzedPlan,
      LaneRunState runState) {
    int totalChunks = analyzedPlan.entries().size();
    int concurrencyLimit = initialSubmissionLimit(totalChunks);
    int submitted = 0;
    while (submitted < totalChunks && submitted < concurrencyLimit) {
      submitLaneChunk(metadata, analyzedPlan, runState, submitted);
      submitted++;
    }
    return submitted;
  }

  private int initialSubmissionLimit(int totalChunks) {
    if (totalChunks == 0) {
      return 0;
    }
    return Math.clamp(maxConcurrentChunksPerJob, 1, totalChunks);
  }

  private void submitLaneChunk(
      HistoricalSubjectExecutionMeta metadata,
      HistoricalChunkPlan analyzedPlan,
      LaneRunState runState,
      int chunkIndex) {
    HistoricalChunkPlanEntry chunk = analyzedPlan.entries().get(chunkIndex);
    runState.futures.add(
        runState.completionService.submit(
            () ->
                computeKeepalive.execute(
                    analyzedPlan,
                    metadata,
                    runState.progress::computedChunks,
                    runState.progress::failedChunks,
                    () -> chunkOperationExecutor.execute(analyzedPlan, metadata, chunk))));
  }

  private static void checkCircuitBreaker(
      List<Future<HistoricalChunkExecutionResult>> futures,
      HistoricalChunkFailureDecision decision) {
    if (!decision.shouldAbort()) {
      return;
    }
    log.warn(decision.reason());
    cancelOutstanding(futures);
    throw new IllegalStateException(decision.reason());
  }

  private void throwIfCancellationRequested(
      HistoricalSubjectExecutionMeta metadata,
      List<Future<HistoricalChunkExecutionResult>> futures,
      int skippedNotYetStartedChunks) {
    cancellationGuard.throwIfCancellationRequested(
        HistoricalExecutionContext.from(metadata),
        skippedNotYetStartedChunks,
        () -> cancelOutstanding(futures));
  }

  private static void cancelOutstanding(List<Future<HistoricalChunkExecutionResult>> futures) {
    for (Future<HistoricalChunkExecutionResult> future : futures) {
      future.cancel(true);
    }
  }

  private static final class LaneRunState {
    private final CompletionService<HistoricalChunkExecutionResult> completionService;
    private final List<Future<HistoricalChunkExecutionResult>> futures;
    private final HistoricalChunkExecutionProgress progress;
    private int submitted;

    private LaneRunState(
        CompletionService<HistoricalChunkExecutionResult> completionService,
        List<Future<HistoricalChunkExecutionResult>> futures,
        int submitted,
        HistoricalChunkExecutionProgress progress) {
      this.completionService = completionService;
      this.futures = futures;
      this.submitted = submitted;
      this.progress = progress;
    }
  }
}
