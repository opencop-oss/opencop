// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.usecase.history;

import com.opencop.core.sdk.model.history.HistoricalJobStage;
import com.opencop.core.sdk.observability.ObservedOperationRunner;
import com.opencop.core.sdk.observability.ObservedOperationRunner.Observation;
import com.opencop.core.sdk.observability.history.HistoricalTelemetry.Outcome;
import com.opencop.core.sdk.observability.history.HistoricalTelemetry.SpanError;
import com.opencop.core.sdk.observability.history.HistoricalTraceContext;
import com.opencop.processor.business.model.history.HistoricalSubjectExecutionMeta;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkPlan;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkPlanEntry;
import com.opencop.processor.business.policy.history.HistoricalExecutionFailureClassifier;
import com.opencop.processor.technical.observability.history.HistoricalObservability;
import com.opencop.processor.technical.observability.history.HistoricalObservability.Cached;
import io.opentelemetry.api.trace.Tracer;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public final class HistoricalChunkExecutionTelemetry {
  private static final String CHUNK_COMPUTE_SPAN = "history.chunk.compute";

  private final HistoricalObservability historicalObservability;
  private final ObservedOperationRunner observedOperationRunner;
  private final HistoricalExecutionFailureClassifier failureClassifier;

  public HistoricalChunkExecutionTelemetry(
      HistoricalObservability historicalObservability,
      Tracer tracer,
      HistoricalExecutionFailureClassifier failureClassifier) {
    this.historicalObservability = historicalObservability;
    this.observedOperationRunner = new ObservedOperationRunner(tracer);
    this.failureClassifier = failureClassifier;
  }

  HistoricalChunkExecutionResult execute(
      HistoricalSubjectExecutionMeta laneMetadata,
      HistoricalChunkPlan analyzedPlan,
      HistoricalChunkPlanEntry chunk,
      Cached cached,
      ChunkOperation operation) {
    return observedOperationRunner.run(
        new Observation(
            CHUNK_COMPUTE_SPAN,
            HistoricalTraceContext.spanAttributes(chunkSpanAttributes(laneMetadata, chunk)),
            SpanError.CHUNK_COMPUTE_FAILED.description()),
        () -> normalize(operation.execute()),
        duration -> recordChunkCompute(Outcome.SUCCESS, cached, duration),
        duration -> recordChunkCompute(Outcome.ERROR, cached, duration),
        this::normalizeFailure);
  }

  private HistoricalChunkExecutionResult normalizeFailure(RuntimeException error) {
    if (failureClassifier.classify(error) == HistoricalJobStage.EXECUTION_FAILED_TIMEOUT) {
      throw error;
    }
    return HistoricalChunkExecutionResult.FAILED;
  }

  private void recordChunkCompute(Outcome outcome, Cached cached, Duration duration) {
    if (historicalObservability == null) {
      return;
    }
    historicalObservability.recordChunkCompute(outcome, cached, duration);
  }

  private static HistoricalChunkExecutionResult normalize(HistoricalChunkExecutionResult result) {
    if (result == null) {
      return HistoricalChunkExecutionResult.COMPUTED;
    }
    return result;
  }

  private static Map<String, String> chunkSpanAttributes(
      HistoricalSubjectExecutionMeta laneMetadata, HistoricalChunkPlanEntry chunk) {
    Map<String, String> attributes = new LinkedHashMap<>();
    attributes.put(
        HistoricalTraceContext.ATTRIBUTE_REQUEST_ID, laneMetadata.requestId().toString());
    attributes.put(HistoricalTraceContext.ATTRIBUTE_JOB_ID, laneMetadata.laneJobId().toString());
    attributes.put(
        HistoricalTraceContext.ATTRIBUTE_CHUNK_INDEX, Integer.toString(chunk.chunkIndex()));
    attributes.put(
        HistoricalTraceContext.ATTRIBUTE_SUBJECT_TYPE, laneMetadata.subjectType().name());
    return attributes;
  }

  @FunctionalInterface
  interface ChunkOperation {
    HistoricalChunkExecutionResult execute();
  }
}
