// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.usecase.history;

import com.opencop.processor.business.model.history.HistoricalExecutionContext;
import com.opencop.processor.business.model.history.HistoricalSubjectExecutionMeta;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkCacheStatus;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkPlan;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkPlanEntry;
import com.opencop.processor.technical.observability.history.HistoricalObservability.Cached;
import org.springframework.stereotype.Component;

@Component
public final class HistoricalChunkOperationExecutor {

  private final HistoricalChunkExecutionTelemetry executionTelemetry;
  private final HistoricalChunkAvailabilityHandler availabilityHandler;
  private final HistoricalChunkComputationService computationService;

  public HistoricalChunkOperationExecutor(
      HistoricalChunkExecutionTelemetry executionTelemetry,
      HistoricalChunkAvailabilityHandler availabilityHandler,
      HistoricalChunkComputationService computationService) {
    this.executionTelemetry = executionTelemetry;
    this.availabilityHandler = availabilityHandler;
    this.computationService = computationService;
  }

  HistoricalChunkExecutionResult execute(
      HistoricalChunkPlan analyzedPlan,
      HistoricalSubjectExecutionMeta metadata,
      HistoricalChunkPlanEntry chunk) {
    HistoricalExecutionContext executionContext = HistoricalExecutionContext.from(metadata);
    if (chunk.cacheStatus() == HistoricalChunkCacheStatus.CACHED) {
      return executeCachedChunk(analyzedPlan, metadata, executionContext, chunk);
    }
    return executeMissingChunk(analyzedPlan, metadata, executionContext, chunk);
  }

  private HistoricalChunkExecutionResult executeCachedChunk(
      HistoricalChunkPlan analyzedPlan,
      HistoricalSubjectExecutionMeta metadata,
      HistoricalExecutionContext executionContext,
      HistoricalChunkPlanEntry chunk) {
    return executeChunkOperationWithTelemetry(
        analyzedPlan,
        metadata,
        chunk,
        Cached.TRUE,
        () -> {
          if (availabilityHandler.reuseCachedChunkIfAvailable(executionContext, chunk)) {
            return HistoricalChunkExecutionResult.REUSED_FROM_CACHE;
          }
          computationService.computeMissingChunk(executionContext, analyzedPlan, chunk);
          return HistoricalChunkExecutionResult.COMPUTED;
        });
  }

  private HistoricalChunkExecutionResult executeMissingChunk(
      HistoricalChunkPlan analyzedPlan,
      HistoricalSubjectExecutionMeta metadata,
      HistoricalExecutionContext executionContext,
      HistoricalChunkPlanEntry chunk) {
    return executeChunkOperationWithTelemetry(
        analyzedPlan,
        metadata,
        chunk,
        Cached.FALSE,
        () -> computeMissingChunk(executionContext, analyzedPlan, chunk));
  }

  private HistoricalChunkExecutionResult computeMissingChunk(
      HistoricalExecutionContext executionContext,
      HistoricalChunkPlan analyzedPlan,
      HistoricalChunkPlanEntry chunk) {
    computationService.computeMissingChunk(executionContext, analyzedPlan, chunk);
    return HistoricalChunkExecutionResult.COMPUTED;
  }

  private HistoricalChunkExecutionResult executeChunkOperationWithTelemetry(
      HistoricalChunkPlan analyzedPlan,
      HistoricalSubjectExecutionMeta metadata,
      HistoricalChunkPlanEntry chunk,
      Cached cached,
      HistoricalChunkExecutionTelemetry.ChunkOperation operation) {
    HistoricalChunkInterruption.ensureNotInterrupted();
    return executionTelemetry.execute(metadata, analyzedPlan, chunk, cached, operation);
  }
}
