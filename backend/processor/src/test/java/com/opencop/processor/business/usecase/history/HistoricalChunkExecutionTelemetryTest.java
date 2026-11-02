// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.usecase.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.sdk.model.history.HistoricalResolution;
import com.opencop.core.sdk.model.job.JobIssuer;
import com.opencop.processor.business.model.history.HistoricalSubjectExecutionMeta;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkCacheStatus;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkPlan;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkPlanEntry;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkWindowPlan;
import com.opencop.processor.business.policy.history.HistoricalExecutionFailureClassifier;
import com.opencop.processor.technical.observability.history.HistoricalObservability;
import com.opencop.processor.technical.observability.history.HistoricalObservability.Cached;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.QueryTimeoutException;

class HistoricalChunkExecutionTelemetryTest {

  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final HistoricalObservability observability = new HistoricalObservability(meterRegistry);
  private final Tracer tracer = mock(Tracer.class);
  private final SpanBuilder spanBuilder = mock(SpanBuilder.class);
  private final Span span = mock(Span.class);
  private final Scope scope = mock(Scope.class);
  private final HistoricalChunkExecutionTelemetry telemetry =
      new HistoricalChunkExecutionTelemetry(
          observability, tracer, new HistoricalExecutionFailureClassifier());

  @Test
  void execute_whenOperationSucceeds_recordsSuccessTelemetryAndSpanAttributes() {
    wireSpan();
    var laneMetadata = laneMetadata();
    var chunk = planEntry(4, HistoricalChunkCacheStatus.MISSING);
    var plan = HistoricalChunkPlan.forSubject(laneMetadata, laneMetadata.windowPlan());

    var result =
        telemetry.execute(
            laneMetadata, plan, chunk, Cached.FALSE, () -> HistoricalChunkExecutionResult.COMPUTED);

    assertEquals(HistoricalChunkExecutionResult.COMPUTED, result);
    assertEquals(
        1L,
        meterRegistry
            .find("opencop_history_chunk_compute_duration_seconds")
            .tags("outcome", "success", "cached", "false")
            .timer()
            .count());
    verify(tracer).spanBuilder("history.chunk.compute");
    verify(span).setStatus(StatusCode.OK);
    verify(span).end();
    ArgumentCaptor<Attributes> attributesCaptor = ArgumentCaptor.forClass(Attributes.class);
    verify(spanBuilder).setAllAttributes(attributesCaptor.capture());
    var attributes = attributesCaptor.getValue();
    assertEquals(
        laneMetadata.requestId().toString(), attributes.get(AttributeKey.stringKey("requestId")));
    assertEquals(
        laneMetadata.laneJobId().toString(), attributes.get(AttributeKey.stringKey("jobId")));
    assertEquals("4", attributes.get(AttributeKey.stringKey("chunkIndex")));
    assertEquals("LAYER", attributes.get(AttributeKey.stringKey("subjectType")));
  }

  @Test
  void execute_whenOperationReturnsNull_normalizesToComputed() {
    var telemetry =
        new HistoricalChunkExecutionTelemetry(
            null, null, new HistoricalExecutionFailureClassifier());
    var laneMetadata = laneMetadata();
    var chunk = planEntry(0, HistoricalChunkCacheStatus.MISSING);
    var plan = HistoricalChunkPlan.forSubject(laneMetadata, laneMetadata.windowPlan());

    var result = telemetry.execute(laneMetadata, plan, chunk, Cached.FALSE, () -> null);

    assertEquals(HistoricalChunkExecutionResult.COMPUTED, result);
  }

  @Test
  void execute_whenOperationFails_recordsErrorTelemetryAndReturnsFailed() {
    wireSpan();
    var laneMetadata = laneMetadata();
    var chunk = planEntry(2, HistoricalChunkCacheStatus.CACHED);
    var plan = HistoricalChunkPlan.forSubject(laneMetadata, laneMetadata.windowPlan());
    var failure = new IllegalStateException("simulated failure");

    var result =
        telemetry.execute(
            laneMetadata,
            plan,
            chunk,
            Cached.TRUE,
            () -> {
              throw failure;
            });

    assertEquals(HistoricalChunkExecutionResult.FAILED, result);
    assertTrue(
        meterRegistry
                .find("opencop_history_chunk_compute_duration_seconds")
                .tags("outcome", "error", "cached", "true")
                .timer()
                .count()
            >= 1L);
    verify(span).setStatus(eq(StatusCode.ERROR), eq("chunk compute failed"));
    verify(span).recordException(failure);
    verify(span).end();
  }

  @Test
  void execute_whenOperationTimesOut_recordsErrorTelemetryAndRethrows() {
    wireSpan();
    var laneMetadata = laneMetadata();
    var chunk = planEntry(2, HistoricalChunkCacheStatus.MISSING);
    var plan = HistoricalChunkPlan.forSubject(laneMetadata, laneMetadata.windowPlan());
    var failure = new QueryTimeoutException("active snapshot query timed out");

    var thrown =
        assertThrows(
            QueryTimeoutException.class,
            () ->
                telemetry.execute(
                    laneMetadata,
                    plan,
                    chunk,
                    Cached.FALSE,
                    () -> {
                      throw failure;
                    }));

    assertEquals(failure, thrown);
    assertTrue(
        meterRegistry
                .find("opencop_history_chunk_compute_duration_seconds")
                .tags("outcome", "error", "cached", "false")
                .timer()
                .count()
            >= 1L);
    verify(span).setStatus(eq(StatusCode.ERROR), eq("chunk compute failed"));
    verify(span).recordException(failure);
    verify(span).end();
  }

  private void wireSpan() {
    when(tracer.spanBuilder("history.chunk.compute")).thenReturn(spanBuilder);
    when(spanBuilder.setAllAttributes(any(Attributes.class))).thenReturn(spanBuilder);
    when(spanBuilder.startSpan()).thenReturn(span);
    when(span.makeCurrent()).thenReturn(scope);
  }

  private static HistoricalSubjectExecutionMeta laneMetadata() {
    return new HistoricalSubjectExecutionMeta(
        UUID.fromString("550e8400-e29b-41d4-a716-446655440134"),
        UUID.fromString("550e8400-e29b-41d4-a716-446655440130"),
        UUID.fromString("550e8400-e29b-41d4-a716-446655440136"),
        JobIssuer.user(UUID.fromString("550e8400-e29b-41d4-a716-446655440131")),
        UUID.fromString("550e8400-e29b-41d4-a716-446655440137"),
        Instant.parse("2026-04-07T10:00:00Z"),
        Instant.parse("2026-04-06T10:02:17Z"),
        Instant.parse("2026-04-06T12:13:02Z"),
        Instant.parse("2026-04-06T10:02:00Z"),
        Instant.parse("2026-04-06T12:14:00Z"),
        HistoricalResolution.MINUTE_1,
        2,
        List.of(),
        com.opencop.core.sdk.model.SubjectType.LAYER,
        UUID.fromString("550e8400-e29b-41d4-a716-446655440138"),
        new HistoricalChunkWindowPlan(
            Instant.parse("2026-04-06T10:02:00Z"),
            Instant.parse("2026-04-06T12:14:00Z"),
            2,
            HistoricalResolution.MINUTE_1,
            List.of(
                planEntry(0, HistoricalChunkCacheStatus.MISSING),
                planEntry(1, HistoricalChunkCacheStatus.MISSING))));
  }

  private static HistoricalChunkPlanEntry planEntry(
      int index, HistoricalChunkCacheStatus cacheStatus) {
    return new HistoricalChunkPlanEntry(
        index,
        "chunk-" + index,
        "payload-" + index,
        "state-" + index,
        cacheStatus,
        Instant.parse("2026-04-06T10:02:00Z").plusSeconds(index * 60L),
        Instant.parse("2026-04-06T10:03:00Z").plusSeconds(index * 60L));
  }
}
