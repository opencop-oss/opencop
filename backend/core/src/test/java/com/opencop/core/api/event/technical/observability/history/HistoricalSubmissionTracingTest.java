// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.technical.observability.history;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.sdk.model.SubjectType;
import com.opencop.core.sdk.model.history.HistoricalResolution;
import com.opencop.core.sdk.model.job.HistoricalTracksJobSubmission;
import com.opencop.core.sdk.model.job.HistoricalTracksSubject;
import com.opencop.core.sdk.model.job.JobIssuer;
import com.opencop.core.sdk.observability.history.HistoricalTraceContext;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.api.trace.Tracer;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HistoricalSubmissionTracingTest {

  @Test
  void traceApiSubmit_runsOperationWithCurrentApiSubmitSpanContext() {
    Tracer tracer = mock(Tracer.class);
    SpanBuilder spanBuilder = mock(SpanBuilder.class);
    Span span =
        Span.wrap(
            SpanContext.create(
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                "0102030405060708",
                TraceFlags.getSampled(),
                TraceState.getDefault()));
    when(tracer.spanBuilder("history.api.submit")).thenReturn(spanBuilder);
    when(spanBuilder.startSpan()).thenReturn(span);

    HistoricalSubmissionTracing tracing = new HistoricalSubmissionTracing(tracer);

    String traceparent =
        tracing.traceApiSubmit(
            "SOURCE", () -> HistoricalTraceContext.captureCurrent().get("traceparent"));

    assertNotNull(traceparent);
    assertTrue(traceparent.startsWith("00-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa-"));
  }

  @Test
  void traceKafkaPublish_whenSubmissionHasMixedSubjects_setsAggregateSubjectTypeAttribute() {
    Tracer tracer = mock(Tracer.class);
    SpanBuilder spanBuilder = mock(SpanBuilder.class);
    Span span = mock(Span.class);
    when(tracer.spanBuilder("history.kafka.publish")).thenReturn(spanBuilder);
    when(spanBuilder.startSpan()).thenReturn(span);
    HistoricalSubmissionTracing tracing = new HistoricalSubmissionTracing(tracer);
    HistoricalTracksJobSubmission submission =
        HistoricalTracksJobSubmission.create(
            UUID.fromString("550e8400-e29b-41d4-a716-446655440001"),
            JobIssuer.user(UUID.fromString("550e8400-e29b-41d4-a716-446655440120")),
            List.of(
                new HistoricalTracksSubject(
                    UUID.fromString("550e8400-e29b-41d4-a716-446655440210"),
                    SubjectType.SOURCE,
                    UUID.fromString("550e8400-e29b-41d4-a716-446655440211")),
                new HistoricalTracksSubject(
                    UUID.fromString("550e8400-e29b-41d4-a716-446655440220"),
                    SubjectType.LAYER,
                    UUID.fromString("550e8400-e29b-41d4-a716-446655440221"))),
            UUID.fromString("550e8400-e29b-41d4-a716-446655440120"),
            Instant.parse("2026-04-07T10:00:00Z"),
            Instant.parse("2026-04-06T10:02:17Z"),
            Instant.parse("2026-04-06T12:13:02Z"),
            Instant.parse("2026-04-06T10:02:00Z"),
            Instant.parse("2026-04-06T12:14:00Z"),
            HistoricalResolution.MINUTE_1,
            132,
            List.of());

    tracing.traceKafkaPublish(submission, () -> {});

    var attributesCaptor = org.mockito.ArgumentCaptor.forClass(Attributes.class);
    verify(span).setAllAttributes(attributesCaptor.capture());
    Attributes attributes = attributesCaptor.getValue();
    assertTrue("SOURCE,LAYER".equals(attributes.get(AttributeKey.stringKey("subjectType"))));
  }
}
