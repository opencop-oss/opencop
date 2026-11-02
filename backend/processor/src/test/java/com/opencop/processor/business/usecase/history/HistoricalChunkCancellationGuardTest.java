// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.usecase.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.opencop.core.sdk.model.SubjectType;
import com.opencop.core.sdk.model.history.HistoricalJobStage;
import com.opencop.core.sdk.model.history.HistoricalResolution;
import com.opencop.core.sdk.model.job.HistoricalTracksJobSubmission;
import com.opencop.core.sdk.model.job.JobIssuer;
import com.opencop.processor.business.model.history.HistoricalExecutionContext;
import com.opencop.processor.business.model.history.HistoricalSubjectExecutionMeta;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkWindowPlan;
import com.opencop.processor.ports.technical.gateway.history.HistoricalJobStateGateway;
import com.opencop.processor.technical.gateway.history.model.job.HistoricalJobState;
import com.opencop.processor.technical.gateway.history.model.job.HistoricalJobStatus;
import com.opencop.processor.technical.observability.history.HistoricalObservability;
import com.opencop.processor.technical.observability.history.HistoricalObservability.CancellationReason;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class HistoricalChunkCancellationGuardTest {

  private static final Instant NOW = Instant.parse("2026-04-06T10:00:00Z");
  private static final Duration TTL = Duration.ofHours(24);

  private final HistoricalJobStateGateway jobStateGateway = mock(HistoricalJobStateGateway.class);
  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final HistoricalObservability historicalObservability =
      new HistoricalObservability(meterRegistry);
  private final Tracer tracer = mock(Tracer.class);
  private final SpanBuilder cancelledSpanBuilder = mock(SpanBuilder.class);
  private final Span cancelledSpan = mock(Span.class);
  private final Scope cancelledScope = mock(Scope.class);
  private final HistoricalChunkCancellationGuard guard =
      new HistoricalChunkCancellationGuard(jobStateGateway, historicalObservability, tracer);

  @Test
  void throwIfCancellationRequested_whenStateIsMissing_doesNothing() {
    var job = job();
    var cancellationHandlerCalled = new AtomicBoolean(false);
    when(jobStateGateway.findByJobId(job.jobId())).thenReturn(Optional.empty());

    guard.throwIfCancellationRequested(
        HistoricalExecutionContext.from(job), 3, () -> cancellationHandlerCalled.set(true));

    assertTrue(!cancellationHandlerCalled.get());
    verify(tracer, never()).spanBuilder("history.chunk.cancelled");
  }

  @Test
  void throwIfCancellationRequested_whenStateIsNotCancellationRequested_doesNothing() {
    var job = job();
    var cancellationHandlerCalled = new AtomicBoolean(false);
    when(jobStateGateway.findByJobId(job.jobId()))
        .thenReturn(Optional.of(state(job, HistoricalJobStage.EXECUTION_RUNNING)));

    guard.throwIfCancellationRequested(
        HistoricalExecutionContext.from(job), 3, () -> cancellationHandlerCalled.set(true));

    assertTrue(!cancellationHandlerCalled.get());
    verify(tracer, never()).spanBuilder("history.chunk.cancelled");
  }

  @Test
  void metadataThrowIfCancellationRequested_whenCancellationRequested_recordsTelemetryAndThrows() {
    var job = job();
    var cancellationHandlerCalled = new AtomicBoolean(false);
    when(jobStateGateway.findByJobId(job.jobId()))
        .thenReturn(Optional.of(state(job, HistoricalJobStage.CANCELLATION_REQUESTED)));
    wireCancelledSpan();

    var exception =
        assertThrows(
            HistoricalJobCancelledException.class,
            () ->
                guard.throwIfCancellationRequested(
                    HistoricalExecutionContext.from(job),
                    2,
                    () -> cancellationHandlerCalled.set(true)));

    assertEquals(job.jobId(), exception.jobId());
    assertEquals("Historical job cancelled", exception.getMessage());
    assertTrue(cancellationHandlerCalled.get());
    assertEquals(
        2.0d,
        meterRegistry
            .find("opencop_history_chunk_cancelled_total")
            .tags("reason", "job_cancelled")
            .counter()
            .count());
    verify(tracer).spanBuilder("history.chunk.cancelled");
    verify(cancelledSpan).setAttribute("historical.skipped_chunks", 2L);
    verify(cancelledSpan).end();
  }

  @Test
  void throwIfCancellationRequested_whenCancellationMetricFails_logsDebugAndStillThrows() {
    var job = job();
    var observability = mock(HistoricalObservability.class);
    var guard = new HistoricalChunkCancellationGuard(jobStateGateway, observability, tracer);
    when(jobStateGateway.findByJobId(job.jobId()))
        .thenReturn(Optional.of(state(job, HistoricalJobStage.CANCELLATION_REQUESTED)));
    doThrow(new IllegalArgumentException("reason"))
        .when(observability)
        .recordChunkCancellation(CancellationReason.JOB_CANCELLED, 3);

    var appender = attachCancellationGuardAppender();
    try {
      assertThrows(
          HistoricalJobCancelledException.class,
          () ->
              guard.throwIfCancellationRequested(
                  HistoricalExecutionContext.from(job), 3, () -> {}));
    } finally {
      detachCancellationGuardAppender(appender);
    }

    var messages =
        appender.appender().list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    assertTrue(
        messages.stream()
            .anyMatch(message -> message.contains("history.chunk.cancelled telemetry failed")));
  }

  @Test
  void throwIfCancellationRequested_whenCancellationSpanAttributeFails_logsDebugAndStillThrows() {
    var job = job();
    when(jobStateGateway.findByJobId(job.jobId()))
        .thenReturn(Optional.of(state(job, HistoricalJobStage.CANCELLATION_REQUESTED)));
    wireCancelledSpan();
    doThrow(new IllegalStateException("span failed"))
        .when(cancelledSpan)
        .setAttribute("historical.skipped_chunks", 2L);

    var appender = attachCancellationGuardAppender();
    try {
      assertThrows(
          HistoricalJobCancelledException.class,
          () ->
              guard.throwIfCancellationRequested(
                  HistoricalExecutionContext.from(job), 2, () -> {}));
    } finally {
      detachCancellationGuardAppender(appender);
    }

    var messages =
        appender.appender().list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    assertTrue(messages.stream().anyMatch(message -> message.contains(job.jobId().toString())));
    assertTrue(
        messages.stream()
            .anyMatch(message -> message.contains("history.chunk.cancelled telemetry failed")));
  }

  @Test
  void throwIfCancellationRequested_whenCancellationRequested_recordsTelemetryAndThrows() {
    var metadata = laneMetadata();
    var cancellationHandlerCalled = new AtomicBoolean(false);
    when(jobStateGateway.findByJobId(metadata.laneJobId()))
        .thenReturn(Optional.of(state(metadata, HistoricalJobStage.CANCELLATION_REQUESTED)));
    wireCancelledSpan();

    var exception =
        assertThrows(
            HistoricalJobCancelledException.class,
            () ->
                guard.throwIfCancellationRequested(
                    HistoricalExecutionContext.from(metadata),
                    2,
                    () -> cancellationHandlerCalled.set(true)));

    assertEquals(metadata.laneJobId(), exception.jobId());
    assertEquals("Historical job cancelled", exception.getMessage());
    assertTrue(cancellationHandlerCalled.get());
    verify(tracer).spanBuilder("history.chunk.cancelled");
    verify(cancelledSpan).setAttribute("historical.skipped_chunks", 2L);
    verify(cancelledSpan).end();
  }

  private void wireCancelledSpan() {
    when(tracer.spanBuilder("history.chunk.cancelled")).thenReturn(cancelledSpanBuilder);
    when(cancelledSpanBuilder.startSpan()).thenReturn(cancelledSpan);
    when(cancelledSpan.makeCurrent()).thenReturn(cancelledScope);
  }

  private static HistoricalTracksJobSubmission job() {
    return HistoricalTracksJobSubmission.create(
        UUID.randomUUID(),
        JobIssuer.user(UUID.fromString("550e8400-e29b-41d4-a716-446655440120")),
        SubjectType.SOURCE,
        UUID.fromString("550e8400-e29b-41d4-a716-446655440121"),
        UUID.fromString("550e8400-e29b-41d4-a716-446655440120"),
        Instant.parse("2026-04-07T10:00:00Z"),
        Instant.parse("2026-04-06T10:02:17Z"),
        Instant.parse("2026-04-06T12:13:02Z"),
        Instant.parse("2026-04-06T10:02:00Z"),
        Instant.parse("2026-04-06T12:14:00Z"),
        HistoricalResolution.MINUTE_1,
        132,
        List.of());
  }

  private static HistoricalJobState state(
      HistoricalTracksJobSubmission job, HistoricalJobStage stage) {
    return new HistoricalJobState(
        job.jobId(),
        job.requestId(),
        "HISTORICAL_TRACKS",
        job.subjects().getFirst().subjectType(),
        job.subjects().getFirst().subjectId(),
        job.requestedBy(),
        job.requestedAt(),
        Instant.parse("2026-04-06T10:02:17Z"),
        Instant.parse("2026-04-06T12:13:02Z"),
        Instant.parse("2026-04-06T10:02:00Z"),
        Instant.parse("2026-04-06T12:14:00Z"),
        job.resolution(),
        job.plannedChunks(),
        HistoricalJobStatus.RUNNING,
        stage,
        NOW,
        NOW,
        NOW.plus(TTL),
        null,
        "worker-test",
        0L,
        0L,
        "");
  }

  private static HistoricalJobState state(
      HistoricalSubjectExecutionMeta metadata, HistoricalJobStage stage) {
    return new HistoricalJobState(
        metadata.laneJobId(),
        metadata.requestId(),
        "HISTORICAL_TRACKS",
        metadata.subjectType(),
        metadata.subjectId(),
        metadata.requestedBy(),
        metadata.requestedAt(),
        Instant.parse("2026-04-06T10:02:17Z"),
        Instant.parse("2026-04-06T12:13:02Z"),
        Instant.parse("2026-04-06T10:02:00Z"),
        Instant.parse("2026-04-06T12:14:00Z"),
        metadata.resolution(),
        metadata.plannedChunks(),
        HistoricalJobStatus.RUNNING,
        stage,
        NOW,
        NOW,
        NOW.plus(TTL),
        null,
        "worker-test",
        0L,
        0L,
        "");
  }

  private static HistoricalSubjectExecutionMeta laneMetadata() {
    return new HistoricalSubjectExecutionMeta(
        UUID.fromString("550e8400-e29b-41d4-a716-446655440200"),
        UUID.fromString("550e8400-e29b-41d4-a716-446655440201"),
        UUID.fromString("550e8400-e29b-41d4-a716-446655440202"),
        JobIssuer.user(UUID.fromString("550e8400-e29b-41d4-a716-446655440120")),
        UUID.fromString("550e8400-e29b-41d4-a716-446655440120"),
        Instant.parse("2026-04-07T10:00:00Z"),
        Instant.parse("2026-04-06T10:02:17Z"),
        Instant.parse("2026-04-06T12:13:02Z"),
        Instant.parse("2026-04-06T10:02:00Z"),
        Instant.parse("2026-04-06T12:14:00Z"),
        HistoricalResolution.MINUTE_1,
        132,
        List.of(),
        SubjectType.SOURCE,
        UUID.fromString("550e8400-e29b-41d4-a716-446655440121"),
        new HistoricalChunkWindowPlan(
            Instant.parse("2026-04-06T10:02:00Z"),
            Instant.parse("2026-04-06T12:14:00Z"),
            1,
            HistoricalResolution.MINUTE_1,
            List.of()));
  }

  private static CancellationGuardLogCapture attachCancellationGuardAppender() {
    Logger logger = (Logger) LoggerFactory.getLogger(HistoricalChunkCancellationGuard.class);
    Level previousLevel = logger.getLevel();
    logger.setLevel(Level.DEBUG);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    return new CancellationGuardLogCapture(appender, previousLevel);
  }

  private static void detachCancellationGuardAppender(CancellationGuardLogCapture capture) {
    Logger logger = (Logger) LoggerFactory.getLogger(HistoricalChunkCancellationGuard.class);
    logger.detachAppender(capture.appender());
    capture.appender().stop();
    logger.setLevel(capture.previousLevel());
  }

  private record CancellationGuardLogCapture(
      ListAppender<ILoggingEvent> appender, Level previousLevel) {}
}
