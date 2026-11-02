// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.history;

import com.opencop.core.sdk.model.SubjectType;
import com.opencop.core.sdk.model.history.HistoricalResolution;
import com.opencop.core.sdk.model.history.HistoryChunkStatus;
import com.opencop.core.sdk.model.history.HistoryChunkStatusEvent;
import com.opencop.core.sdk.model.history.HistoryEvent;
import com.opencop.core.sdk.observability.history.HistoricalTelemetry.Outcome;
import com.opencop.core.sdk.observability.history.HistoricalTraceContext;
import com.opencop.core.sdk.ports.gateway.cache.CachePublisher;
import com.opencop.processor.business.model.history.HistoricalExecutionContext;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkPlanEntry;
import com.opencop.processor.business.policy.history.HistoricalCacheIdentity;
import com.opencop.processor.ports.technical.gateway.history.HistoricalChunkReadyNotifier;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class RedisHistoricalChunkReadyNotifier implements HistoricalChunkReadyNotifier {

  private static final Logger log =
      LoggerFactory.getLogger(RedisHistoricalChunkReadyNotifier.class);
  private static final String REDIS_PUBLISH_SPAN = "history.redis.chunk_ready.publish";

  private final CachePublisher<HistoryEvent> historyEventsPublisher;
  private final Tracer tracer;

  public RedisHistoricalChunkReadyNotifier(
      CachePublisher<HistoryEvent> historyEventsPublisher, Tracer tracer) {
    this.historyEventsPublisher = historyEventsPublisher;
    this.tracer = tracer;
  }

  @Override
  public void notifyChunkReady(
      HistoricalExecutionContext executionContext,
      HistoricalChunkPlanEntry chunk,
      boolean cached,
      int partCount) {
    if (!executionContext.userInitiated()) {
      return;
    }

    Span span = tracer.spanBuilder(REDIS_PUBLISH_SPAN).startSpan();
    try {
      try (Scope ignored = span.makeCurrent()) {
        span.setAllAttributes(
            HistoricalTraceContext.spanAttributes(
                spanAttributes(
                    executionContext.requestId(),
                    executionContext.jobId(),
                    chunk.chunkIndex(),
                    executionContext.subjectType())));
        historyEventsPublisher.publish(
            chunkReadyEvent(
                executionContext.requestedBy(),
                executionContext.requestId(),
                executionContext.jobId(),
                executionContext.subjectType(),
                executionContext.subjectId(),
                chunk,
                executionContext.resolution(),
                cached,
                partCount));
      }
      span.setStatus(StatusCode.OK, Outcome.SUCCESS.value());
      // Per-chunk message — chunkIndex is interpolated into the format string
      // (rather than passed as a {} parameter) so each call has a unique
      // format pattern. Otherwise Logback's DuplicateMessageFilter
      // (`allowedRepetitions=5`) suppresses every log past the 6th chunk
      // because the parameterized format string is the dedup key.
      log.info(
          "Notified chunk " + chunk.chunkIndex() + " ready: job={} cached={} partCount={}",
          executionContext.jobId(),
          cached,
          partCount);
    } catch (Exception e) {
      span.setStatus(StatusCode.ERROR, Outcome.ERROR.value());
      span.recordException(e);
      log.error(
          "Failed to publish history.chunk.status event for job {}", executionContext.jobId(), e);
    } finally {
      span.end();
    }
  }

  private static Map<String, String> spanAttributes(
      UUID requestId, UUID jobId, int chunkIndex, SubjectType subjectType) {
    Map<String, String> attributes = new LinkedHashMap<>();
    attributes.put(HistoricalTraceContext.ATTRIBUTE_REQUEST_ID, requestId.toString());
    attributes.put(HistoricalTraceContext.ATTRIBUTE_JOB_ID, jobId.toString());
    attributes.put(HistoricalTraceContext.ATTRIBUTE_CHUNK_INDEX, Integer.toString(chunkIndex));
    attributes.put(HistoricalTraceContext.ATTRIBUTE_SUBJECT_TYPE, subjectType.name());
    attributes.put(
        HistoricalTraceContext.ATTRIBUTE_SAMPLER_VERSION, HistoricalCacheIdentity.SAMPLING_VERSION);
    return attributes;
  }

  private static HistoryChunkStatusEvent chunkReadyEvent(
      UUID requestedBy,
      UUID requestId,
      UUID jobId,
      SubjectType subjectType,
      UUID subjectId,
      HistoricalChunkPlanEntry chunk,
      HistoricalResolution resolution,
      boolean cached,
      int partCount) {
    return new HistoryChunkStatusEvent(
        requestedBy,
        requestId,
        jobId,
        subjectType,
        subjectId,
        chunk.chunkKey(),
        chunk.chunkStateKey(),
        chunk.chunkIndex(),
        chunk.payloadKey(),
        chunk.from(),
        chunk.to(),
        resolution,
        HistoricalCacheIdentity.SAMPLING_VERSION,
        cached,
        partCount,
        HistoryChunkStatus.AVAILABLE_FOR_DELIVERY,
        HistoricalTraceContext.captureCurrent());
  }
}
