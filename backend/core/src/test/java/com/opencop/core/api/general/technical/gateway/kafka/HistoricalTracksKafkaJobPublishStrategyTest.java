// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.kafka;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.api.event.technical.observability.history.HistoricalSubmissionTracing;
import com.opencop.core.sdk.model.SubjectType;
import com.opencop.core.sdk.model.history.HistoricalResolution;
import com.opencop.core.sdk.model.job.HistoricalTracksJobSubmission;
import com.opencop.core.sdk.model.job.JobIssuer;
import com.opencop.core.sdk.model.job.LayerCopyJobSubmission;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.KafkaException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.ObjectMapper;

class HistoricalTracksKafkaJobPublishStrategyTest {

  @Test
  void supports_onlyHistoricalTrackSubmissions() {
    @SuppressWarnings("unchecked")
    KafkaTemplate<String, byte[]> kafkaTemplate = mock(KafkaTemplate.class);
    var strategy = new HistoricalTracksKafkaJobPublishStrategy(kafkaTemplate, tracing());

    assertTrue(strategy.supports(historicalSubmission()));
    assertFalse(strategy.supports(layerCopySubmission()));
  }

  @Test
  void publish_recordsHistoricalKafkaPublishSpanAndOnlyAllowlistedAttributes() {
    @SuppressWarnings("unchecked")
    KafkaTemplate<String, byte[]> kafkaTemplate = mock(KafkaTemplate.class);
    Tracer tracer = mock(Tracer.class);
    SpanBuilder spanBuilder = mock(SpanBuilder.class);
    Span span = mock(Span.class);
    when(tracer.spanBuilder("history.kafka.publish")).thenReturn(spanBuilder);
    when(spanBuilder.startSpan()).thenReturn(span);
    when(kafkaTemplate.send(any(ProducerRecord.class)))
        .thenReturn(CompletableFuture.completedFuture(null));
    var strategy =
        new HistoricalTracksKafkaJobPublishStrategy(
            kafkaTemplate, new HistoricalSubmissionTracing(tracer));
    var submission = historicalSubmission();

    strategy.publish(submission, new byte[] {1, 2, 3});

    verify(tracer).spanBuilder("history.kafka.publish");
    var attributesCaptor = ArgumentCaptor.forClass(Attributes.class);
    verify(span).setAllAttributes(attributesCaptor.capture());
    assertEquals(
        submission.requestId().toString(),
        attributesCaptor.getValue().get(AttributeKey.stringKey("requestId")));
    assertEquals(
        submission.jobId().toString(),
        attributesCaptor.getValue().get(AttributeKey.stringKey("jobId")));
    assertEquals(
        submission.subjects().getFirst().subjectType().name(),
        attributesCaptor.getValue().get(AttributeKey.stringKey("subjectType")));
    verify(span).end();
  }

  @Test
  void publish_withActiveTraceContext_writesSanitizedTraceHeadersOnlyAndPayloadUnchanged()
      throws Exception {
    @SuppressWarnings("unchecked")
    KafkaTemplate<String, byte[]> kafkaTemplate = mock(KafkaTemplate.class);
    when(kafkaTemplate.send(any(ProducerRecord.class)))
        .thenReturn(CompletableFuture.completedFuture(null));
    var strategy = new HistoricalTracksKafkaJobPublishStrategy(kafkaTemplate, tracing());
    var submission = historicalSubmission();
    byte[] payload = new ObjectMapper().writeValueAsBytes(submission);

    var spanContext =
        SpanContext.create(
            "4bf92f3577b34da6a3ce929d0e0e4736",
            "00f067aa0ba902b7",
            TraceFlags.getSampled(),
            TraceState.builder().put("vendor", "ok").build());
    Span span = Span.wrap(spanContext);
    try (Scope ignored = span.makeCurrent()) {
      strategy.publish(submission, payload);
    }

    @SuppressWarnings("unchecked")
    var recordCaptor = ArgumentCaptor.forClass(ProducerRecord.class);
    verify(kafkaTemplate).send(recordCaptor.capture());
    ProducerRecord<String, byte[]> record = recordCaptor.getValue();
    assertEquals(submission.topic(), record.topic());
    assertEquals(submission.key(), record.key());
    assertTrue(Arrays.equals(payload, record.value()));
    assertEquals(2, record.headers().toArray().length);
    assertEquals(
        "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01",
        new String(record.headers().lastHeader("traceparent").value()));
    assertEquals("vendor=ok", new String(record.headers().lastHeader("tracestate").value()));
    assertNull(record.headers().lastHeader("x-user-id"));
    Set<String> headerKeys = new HashSet<>();
    record.headers().forEach(header -> headerKeys.add(header.key()));
    assertEquals(Set.of("traceparent", "tracestate"), headerKeys);
  }

  @Test
  void publish_usesKafkaPublishSpanContextForTraceHeadersWithoutUserHeaders() {
    @SuppressWarnings("unchecked")
    KafkaTemplate<String, byte[]> kafkaTemplate = mock(KafkaTemplate.class);
    when(kafkaTemplate.send(any(ProducerRecord.class)))
        .thenReturn(CompletableFuture.completedFuture(null));
    Tracer tracer = mock(Tracer.class);
    SpanBuilder spanBuilder = mock(SpanBuilder.class);
    Span publishSpan =
        Span.wrap(
            SpanContext.create(
                "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                "abcdefabcdefabcd",
                TraceFlags.getSampled(),
                TraceState.getDefault()));
    when(tracer.spanBuilder(any(String.class))).thenReturn(spanBuilder);
    when(spanBuilder.startSpan()).thenReturn(publishSpan);
    var strategy =
        new HistoricalTracksKafkaJobPublishStrategy(
            kafkaTemplate, new HistoricalSubmissionTracing(tracer));

    strategy.publish(historicalSubmission(), new byte[] {1, 2, 3});

    @SuppressWarnings("unchecked")
    var recordCaptor = ArgumentCaptor.forClass(ProducerRecord.class);
    verify(kafkaTemplate).send(recordCaptor.capture());
    ProducerRecord<String, byte[]> record = recordCaptor.getValue();
    String traceparent = new String(record.headers().lastHeader("traceparent").value());
    assertTrue(traceparent.startsWith("00-bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb-"));
    assertNull(record.headers().lastHeader("x-user-id"));
  }

  @Test
  void publish_withoutActiveTraceContext_writesNoTraceHeaders() throws Exception {
    @SuppressWarnings("unchecked")
    KafkaTemplate<String, byte[]> kafkaTemplate = mock(KafkaTemplate.class);
    when(kafkaTemplate.send(any(ProducerRecord.class)))
        .thenReturn(CompletableFuture.completedFuture(null));
    var strategy = new HistoricalTracksKafkaJobPublishStrategy(kafkaTemplate, tracing());
    var submission = historicalSubmission();
    byte[] payload = new ObjectMapper().writeValueAsBytes(submission);

    strategy.publish(submission, payload);

    @SuppressWarnings("unchecked")
    var recordCaptor = ArgumentCaptor.forClass(ProducerRecord.class);
    verify(kafkaTemplate).send(recordCaptor.capture());
    ProducerRecord<String, byte[]> record = recordCaptor.getValue();
    assertTrue(Arrays.equals(payload, record.value()));
    assertNull(record.headers().lastHeader("traceparent"));
    assertNull(record.headers().lastHeader("tracestate"));
    assertNull(record.headers().lastHeader("x-user-id"));
    assertEquals(0, record.headers().toArray().length);
  }

  @Test
  void publish_whenKafkaSendFails_throwsPublishFailure() {
    @SuppressWarnings("unchecked")
    KafkaTemplate<String, byte[]> kafkaTemplate = mock(KafkaTemplate.class);
    when(kafkaTemplate.send(any(ProducerRecord.class))).thenThrow(new KafkaException("boom"));
    var strategy = new HistoricalTracksKafkaJobPublishStrategy(kafkaTemplate, tracing());

    var ex =
        assertThrows(
            IllegalStateException.class,
            () -> strategy.publish(historicalSubmission(), new byte[] {1, 2, 3}));

    assertEquals("Historical Kafka publish failed", ex.getMessage());
  }

  private static HistoricalTracksJobSubmission historicalSubmission() {
    return HistoricalTracksJobSubmission.create(
        UUID.fromString("11111111-1111-1111-1111-111111111111"),
        JobIssuer.user(UUID.fromString("22222222-2222-2222-2222-222222222222")),
        SubjectType.SOURCE,
        UUID.fromString("33333333-3333-3333-3333-333333333333"),
        UUID.fromString("44444444-4444-4444-4444-444444444444"),
        Instant.parse("2026-04-06T10:00:00Z"),
        Instant.parse("2026-04-06T10:00:00Z"),
        Instant.parse("2026-04-06T11:00:00Z"),
        Instant.parse("2026-04-06T10:00:00Z"),
        Instant.parse("2026-04-06T11:00:00Z"),
        HistoricalResolution.MINUTE_1,
        60,
        List.of());
  }

  private static LayerCopyJobSubmission layerCopySubmission() {
    return LayerCopyJobSubmission.create(
        JobIssuer.user(UUID.fromString("550e8400-e29b-41d4-a716-446655446099")),
        UUID.fromString("550e8400-e29b-41d4-a716-446655446011"),
        UUID.fromString("550e8400-e29b-41d4-a716-446655446012"),
        true,
        false);
  }

  private static HistoricalSubmissionTracing tracing() {
    Tracer tracer = mock(Tracer.class);
    SpanBuilder spanBuilder = mock(SpanBuilder.class);
    Span span = mock(Span.class);
    when(tracer.spanBuilder(any(String.class))).thenReturn(spanBuilder);
    when(spanBuilder.startSpan()).thenReturn(span);
    return new HistoricalSubmissionTracing(tracer);
  }
}
