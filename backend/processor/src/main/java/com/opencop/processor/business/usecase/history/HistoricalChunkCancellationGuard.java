// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.usecase.history;

import com.opencop.core.sdk.model.history.HistoricalJobStage;
import com.opencop.processor.business.model.history.HistoricalExecutionContext;
import com.opencop.processor.ports.technical.gateway.history.HistoricalJobStateGateway;
import com.opencop.processor.technical.observability.history.HistoricalObservability;
import com.opencop.processor.technical.observability.history.HistoricalObservability.CancellationReason;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public final class HistoricalChunkCancellationGuard {

  private static final Logger log = LoggerFactory.getLogger(HistoricalChunkCancellationGuard.class);
  private static final String CHUNK_CANCELLED_SPAN = "history.chunk.cancelled";
  private static final String ATTRIBUTE_SKIPPED_CHUNKS = "historical.skipped_chunks";
  private static final String CHUNK_CANCELLED_TELEMETRY_FAILED_MESSAGE =
      "history.chunk.cancelled telemetry failed";
  private static final String JOB_CANCELLED_MESSAGE = "Historical job cancelled";

  private final HistoricalJobStateGateway jobStateGateway;
  private final HistoricalObservability historicalObservability;
  private final Tracer tracer;

  public HistoricalChunkCancellationGuard(
      HistoricalJobStateGateway jobStateGateway,
      HistoricalObservability historicalObservability,
      Tracer tracer) {
    this.jobStateGateway = jobStateGateway;
    this.historicalObservability = historicalObservability;
    this.tracer = tracer;
  }

  public void throwIfCancellationRequested(
      HistoricalExecutionContext executionContext,
      int skippedNotYetStartedChunks,
      Runnable cancellationHandler) {
    var currentState = jobStateGateway.findByJobId(executionContext.jobId());
    if (currentState.isEmpty()
        || currentState.get().stage() != HistoricalJobStage.CANCELLATION_REQUESTED) {
      return;
    }
    cancellationHandler.run();
    recordChunkCancellation(executionContext.jobId(), skippedNotYetStartedChunks);
    throw new HistoricalJobCancelledException(executionContext.jobId(), JOB_CANCELLED_MESSAGE);
  }

  private void recordChunkCancellation(UUID jobId, int skippedNotYetStartedChunks) {
    try {
      if (historicalObservability != null) {
        historicalObservability.recordChunkCancellation(
            CancellationReason.JOB_CANCELLED, skippedNotYetStartedChunks);
      }
      if (tracer != null) {
        Span span = tracer.spanBuilder(CHUNK_CANCELLED_SPAN).startSpan();
        try (Scope ignored = span.makeCurrent()) {
          span.setAttribute(ATTRIBUTE_SKIPPED_CHUNKS, (long) skippedNotYetStartedChunks);
        } finally {
          span.end();
        }
      }
    } catch (Exception telemetryFailure) {
      log.debug(
          "{} jobId={} skippedNotYetStartedChunks={}",
          CHUNK_CANCELLED_TELEMETRY_FAILED_MESSAGE,
          jobId,
          skippedNotYetStartedChunks,
          telemetryFailure);
    }
  }
}
