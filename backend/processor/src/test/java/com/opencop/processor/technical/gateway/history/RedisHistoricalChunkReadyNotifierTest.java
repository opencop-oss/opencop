// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.sdk.model.SubjectType;
import com.opencop.core.sdk.model.history.HistoricalFilterRequest;
import com.opencop.core.sdk.model.history.HistoricalResolution;
import com.opencop.core.sdk.model.history.HistoryChunkStatus;
import com.opencop.core.sdk.model.history.HistoryChunkStatusEvent;
import com.opencop.core.sdk.model.history.HistoryEvent;
import com.opencop.core.sdk.model.history.HistoryEventType;
import com.opencop.core.sdk.model.job.HistoricalTracksJobSubmission;
import com.opencop.core.sdk.model.job.HistoricalTracksSubject;
import com.opencop.core.sdk.model.job.JobIssuer;
import com.opencop.core.sdk.ports.gateway.cache.CachePublisher;
import com.opencop.processor.business.model.history.HistoricalExecutionContext;
import com.opencop.processor.business.model.history.HistoricalSubjectExecutionMeta;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkCacheStatus;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkPlanEntry;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkWindowPlan;
import com.opencop.processor.business.policy.history.HistoricalCacheIdentity;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.context.Context;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RedisHistoricalChunkReadyNotifierTest {

  private final RecordingPublisher historyEventsPublisher = new RecordingPublisher();
  private final RedisHistoricalChunkReadyNotifier notifier =
      new RedisHistoricalChunkReadyNotifier(
          historyEventsPublisher,
          TracerProvider.noop().get(RedisHistoricalChunkReadyNotifier.class.getName()));

  @Test
  void notifyChunkReady_whenIssuerIsUser_publishesChunkStatusEvent() {
    var job = job(JobIssuer.user(UUID.fromString("550e8400-e29b-41d4-a716-446655446099")));
    var chunk = chunk();

    notifier.notifyChunkReady(HistoricalExecutionContext.from(job), chunk, true, 3);

    HistoryChunkStatusEvent event = historyEventsPublisher.event;
    assertEquals(HistoryEventType.HISTORY_CHUNK_STATUS, event.type());
    assertEquals(job.requestedBy(), event.targetUserId());
    assertEquals(job.requestId(), event.requestId());
    assertEquals(job.jobId(), event.jobId());
    assertEquals(job.subjects().getFirst().subjectType(), event.subjectType());
    assertEquals(job.subjects().getFirst().subjectId(), event.subjectId());
    assertEquals(chunk.chunkKey(), event.chunkKey());
    assertEquals(chunk.chunkIndex(), event.chunkIndex());
    assertEquals(chunk.payloadKey(), event.payloadKey());
    assertEquals(chunk.from(), event.chunkFrom());
    assertEquals(chunk.to(), event.chunkTo());
    assertEquals(job.resolution(), event.resolution());
    assertEquals(HistoricalCacheIdentity.SAMPLING_VERSION, event.samplingVersion());
    assertEquals(true, event.cached());
    assertEquals(3, event.partCount());
    assertEquals(HistoryChunkStatus.AVAILABLE_FOR_DELIVERY, event.status());
  }

  @Test
  void notifyChunkReady_whenIssuerIsSystem_skipsHistoryEvent() {
    var job = job(JobIssuer.system("history-test"));

    notifier.notifyChunkReady(HistoricalExecutionContext.from(job), chunk(), false, 1);

    assertNull(historyEventsPublisher.event);
  }

  @Test
  void notifyChunkReady_usesPublishSpanContextWithoutAmbientSpan() {
    Tracer tracer = mock(Tracer.class);
    SpanBuilder spanBuilder = mock(SpanBuilder.class);
    Span publishSpan = mockedCurrentSpan("cccccccccccccccccccccccccccccccc", "00f067aa0ba902b7");
    when(tracer.spanBuilder("history.redis.chunk_ready.publish")).thenReturn(spanBuilder);
    when(spanBuilder.startSpan()).thenReturn(publishSpan);
    var tracedNotifier = new RedisHistoricalChunkReadyNotifier(historyEventsPublisher, tracer);
    var job = job(JobIssuer.user(UUID.fromString("550e8400-e29b-41d4-a716-446655446099")));
    var chunk = chunk();

    tracedNotifier.notifyChunkReady(HistoricalExecutionContext.from(job), chunk, false, 1);

    var event = historyEventsPublisher.event;
    assertEquals(HistoryEventType.HISTORY_CHUNK_STATUS, event.type());
    assertEquals(1, event.traceContext().size());
    assertEquals(
        "00-cccccccccccccccccccccccccccccccc-",
        event.traceContext().get("traceparent").substring(0, 36));
    assertFalse(event.traceContext().containsKey("x-user-id"));

    verify(tracer).spanBuilder("history.redis.chunk_ready.publish");
    var attributesCaptor =
        org.mockito.ArgumentCaptor.forClass(io.opentelemetry.api.common.Attributes.class);
    verify(publishSpan).setAllAttributes(attributesCaptor.capture());
    var attributes = attributesCaptor.getValue();
    assertEquals(job.requestId().toString(), attributes.get(AttributeKey.stringKey("requestId")));
    assertEquals(job.jobId().toString(), attributes.get(AttributeKey.stringKey("jobId")));
    assertEquals("0", attributes.get(AttributeKey.stringKey("chunkIndex")));
    assertEquals(
        job.subjects().getFirst().subjectType().name(),
        attributes.get(AttributeKey.stringKey("subjectType")));
    assertEquals(
        HistoricalCacheIdentity.SAMPLING_VERSION,
        attributes.get(AttributeKey.stringKey("samplerVersion")));
    assertNull(attributes.get(AttributeKey.stringKey("payloadKey")));
    assertNull(attributes.get(AttributeKey.stringKey("requestedBy")));
  }

  @Test
  void notifyChunkReady_whenPublisherThrows_recordsErrorAndEndsPublishSpan() {
    Tracer tracer = mock(Tracer.class);
    SpanBuilder spanBuilder = mock(SpanBuilder.class);
    Span publishSpan = mock(Span.class);
    when(tracer.spanBuilder("history.redis.chunk_ready.publish")).thenReturn(spanBuilder);
    when(spanBuilder.startSpan()).thenReturn(publishSpan);
    RuntimeException publishFailure = new RuntimeException("redis publish failed");
    var throwingPublisher = new ThrowingPublisher(publishFailure);
    var tracedNotifier = new RedisHistoricalChunkReadyNotifier(throwingPublisher, tracer);
    var job = job(JobIssuer.user(UUID.fromString("550e8400-e29b-41d4-a716-446655446099")));

    tracedNotifier.notifyChunkReady(HistoricalExecutionContext.from(job), chunk(), false, 1);

    verify(publishSpan).recordException(same(publishFailure));
    verify(publishSpan).setStatus(io.opentelemetry.api.trace.StatusCode.ERROR, "error");
    verify(publishSpan).end();
  }

  @Test
  void notifyChunkReady_whenIssuerIsUser_publishesChunkStatusEventUsingMetadataIdentity() {
    var laneMetadata =
        laneMetadata(JobIssuer.user(UUID.fromString("550e8400-e29b-41d4-a716-446655446099")));
    var chunk = chunk();

    notifier.notifyChunkReady(HistoricalExecutionContext.from(laneMetadata), chunk, true, 2);

    HistoryChunkStatusEvent event = historyEventsPublisher.event;
    assertEquals(HistoryEventType.HISTORY_CHUNK_STATUS, event.type());
    assertEquals(laneMetadata.requestedBy(), event.targetUserId());
    assertEquals(laneMetadata.requestId(), event.requestId());
    assertEquals(laneMetadata.laneJobId(), event.jobId());
    assertEquals(laneMetadata.subjectType(), event.subjectType());
    assertEquals(laneMetadata.subjectId(), event.subjectId());
    assertEquals(chunk.chunkKey(), event.chunkKey());
    assertEquals(chunk.chunkIndex(), event.chunkIndex());
    assertEquals(chunk.payloadKey(), event.payloadKey());
    assertEquals(chunk.from(), event.chunkFrom());
    assertEquals(chunk.to(), event.chunkTo());
    assertEquals(laneMetadata.resolution(), event.resolution());
    assertEquals(HistoricalCacheIdentity.SAMPLING_VERSION, event.samplingVersion());
    assertEquals(true, event.cached());
    assertEquals(2, event.partCount());
    assertEquals(HistoryChunkStatus.AVAILABLE_FOR_DELIVERY, event.status());
  }

  @Test
  void notifyChunkReady_whenMixedSubjectOrderChanges_usesStableAggregateSubjectIdentity() {
    var firstOrder = mixedJob(true);
    var reversedOrder = mixedJob(false);

    notifier.notifyChunkReady(HistoricalExecutionContext.from(firstOrder), chunk(), false, 1);
    var firstEvent = historyEventsPublisher.event;

    notifier.notifyChunkReady(HistoricalExecutionContext.from(reversedOrder), chunk(), false, 1);
    var reversedEvent = historyEventsPublisher.event;

    assertEquals(firstEvent.subjectType(), reversedEvent.subjectType());
    assertEquals(firstEvent.subjectId(), reversedEvent.subjectId());
    assertTrue(
        firstOrder.subjects().stream()
            .noneMatch(subject -> subject.subjectId().equals(firstEvent.subjectId())));
  }

  private static HistoricalTracksJobSubmission job(JobIssuer issuer) {
    return HistoricalTracksJobSubmission.create(
        UUID.randomUUID(),
        issuer,
        SubjectType.SOURCE,
        UUID.fromString("550e8400-e29b-41d4-a716-446655446001"),
        UUID.fromString("550e8400-e29b-41d4-a716-446655446099"),
        Instant.parse("2026-04-07T10:00:00Z"),
        Instant.parse("2026-04-06T10:00:00Z"),
        Instant.parse("2026-04-06T11:00:00Z"),
        Instant.parse("2026-04-06T10:00:00Z"),
        Instant.parse("2026-04-06T11:00:00Z"),
        HistoricalResolution.HOUR_1,
        1,
        List.of());
  }

  private static HistoricalTracksJobSubmission mixedJob(boolean sourceFirst) {
    var source =
        new HistoricalTracksSubject(
            UUID.fromString("550e8400-e29b-41d4-a716-446655446311"),
            SubjectType.SOURCE,
            UUID.fromString("550e8400-e29b-41d4-a716-446655446013"));
    var layer =
        new HistoricalTracksSubject(
            UUID.fromString("550e8400-e29b-41d4-a716-446655446312"),
            SubjectType.LAYER,
            UUID.fromString("550e8400-e29b-41d4-a716-446655446014"));
    return HistoricalTracksJobSubmission.create(
        UUID.fromString("550e8400-e29b-41d4-a716-446655446015"),
        JobIssuer.user(UUID.fromString("550e8400-e29b-41d4-a716-446655446099")),
        sourceFirst ? List.of(source, layer) : List.of(layer, source),
        UUID.fromString("550e8400-e29b-41d4-a716-446655446099"),
        Instant.parse("2026-04-07T10:00:00Z"),
        Instant.parse("2026-04-06T10:00:00Z"),
        Instant.parse("2026-04-06T11:00:00Z"),
        Instant.parse("2026-04-06T10:00:00Z"),
        Instant.parse("2026-04-06T11:00:00Z"),
        HistoricalResolution.HOUR_1,
        1,
        List.of());
  }

  private static HistoricalChunkPlanEntry chunk() {
    return new HistoricalChunkPlanEntry(
        0,
        "opencop:history:chunk:SOURCE:test:HOUR_1:v1:2026-04-06T10:00:00Z",
        "opencop:history:payload:SOURCE:test:HOUR_1:v1:v1_latest_in_bucket:filters:2026-04-06T10:00:00Z",
        "opencop:history:chunk-state:SOURCE:test:HOUR_1:v1:v1_latest_in_bucket:filters:2026-04-06T10:00:00Z",
        HistoricalChunkCacheStatus.CACHED,
        Instant.parse("2026-04-06T10:00:00Z"),
        Instant.parse("2026-04-06T11:00:00Z"));
  }

  private static HistoricalSubjectExecutionMeta laneMetadata(JobIssuer issuer) {
    return new HistoricalSubjectExecutionMeta(
        UUID.fromString("550e8400-e29b-41d4-a716-446655446201"),
        UUID.fromString("550e8400-e29b-41d4-a716-446655446202"),
        UUID.fromString("550e8400-e29b-41d4-a716-446655446203"),
        issuer,
        UUID.fromString("550e8400-e29b-41d4-a716-446655446099"),
        Instant.parse("2026-04-07T10:00:00Z"),
        Instant.parse("2026-04-06T10:00:00Z"),
        Instant.parse("2026-04-06T11:00:00Z"),
        Instant.parse("2026-04-06T10:00:00Z"),
        Instant.parse("2026-04-06T11:00:00Z"),
        HistoricalResolution.HOUR_1,
        1,
        List.of(new HistoricalFilterRequest("source", Map.of())),
        SubjectType.SOURCE,
        UUID.fromString("550e8400-e29b-41d4-a716-446655446001"),
        new HistoricalChunkWindowPlan(
            Instant.parse("2026-04-06T10:00:00Z"),
            Instant.parse("2026-04-06T11:00:00Z"),
            1,
            HistoricalResolution.HOUR_1,
            List.of()));
  }

  private static final class RecordingPublisher implements CachePublisher<HistoryEvent> {

    private HistoryChunkStatusEvent event;

    @Override
    public void publish(HistoryEvent event) {
      this.event = (HistoryChunkStatusEvent) event;
    }
  }

  private static final class ThrowingPublisher implements CachePublisher<HistoryEvent> {
    private final RuntimeException failure;

    private ThrowingPublisher(RuntimeException failure) {
      this.failure = failure;
    }

    @Override
    public void publish(HistoryEvent event) {
      throw failure;
    }
  }

  private static Span mockedCurrentSpan(String traceId, String spanId) {
    Span span = mock(Span.class);
    Span realSpan =
        Span.wrap(
            SpanContext.create(traceId, spanId, TraceFlags.getSampled(), TraceState.getDefault()));
    when(span.getSpanContext()).thenReturn(realSpan.getSpanContext());
    when(span.storeInContext(any(Context.class)))
        .thenAnswer(invocation -> invocation.<Context>getArgument(0).with(realSpan));
    when(span.makeCurrent())
        .thenAnswer(invocation -> span.storeInContext(Context.current()).makeCurrent());
    return span;
  }
}
