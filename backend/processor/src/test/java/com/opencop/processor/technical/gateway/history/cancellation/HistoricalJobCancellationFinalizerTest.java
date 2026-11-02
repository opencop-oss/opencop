// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.history.cancellation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.sdk.model.SubjectType;
import com.opencop.core.sdk.model.history.HistoricalJobCancellationReasons;
import com.opencop.core.sdk.model.history.HistoricalJobStage;
import com.opencop.core.sdk.model.history.HistoricalResolution;
import com.opencop.core.sdk.model.job.HistoricalTracksJobSubmission;
import com.opencop.core.sdk.model.job.JobIssuer;
import com.opencop.processor.ports.technical.gateway.history.HistoricalJobStateGateway;
import com.opencop.processor.ports.technical.gateway.history.HistoryEventPublisher;
import com.opencop.processor.technical.gateway.history.HistoryJobLifecycleEventRecorder;
import com.opencop.processor.technical.gateway.history.model.job.HistoricalJobState;
import com.opencop.processor.technical.observability.history.HistoricalObservability;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class HistoricalJobCancellationFinalizerTest {

  private static final Instant NOW = Instant.parse("2026-04-07T12:00:00Z");
  private static final Duration TTL = Duration.ofHours(24);

  private final HistoricalJobStateGateway jobStateGateway = mock(HistoricalJobStateGateway.class);
  private final HistoryEventPublisher historyEventPublisher = mock(HistoryEventPublisher.class);
  private final AtomicInteger lifecycleEvents = new AtomicInteger();
  private final HistoricalJobCancellationFinalizer finalizer =
      new HistoricalJobCancellationFinalizer(
          jobStateGateway,
          historyEventPublisher,
          new HistoricalObservability(new SimpleMeterRegistry()),
          new HistoryJobLifecycleEventRecorder() {
            @Override
            public void record(String eventName) {
              lifecycleEvents.incrementAndGet();
            }
          });

  @Test
  void finalizeRequestedCancellation_whenStateIsMissing_returnsFalseWithoutSideEffects() {
    var job = job();
    when(jobStateGateway.findByJobId(job.jobId())).thenReturn(Optional.empty());

    boolean finalized = finalizer.finalizeRequestedCancellation(job);

    assertFalse(finalized);
    assertEquals(0, lifecycleEvents.get());
    verify(jobStateGateway, never()).markCancelled(eq(job.jobId()), anyString());
    verify(historyEventPublisher, never())
        .publishFailed(eq(job), eq(HistoricalJobStage.CANCELLED), anyString());
  }

  @Test
  void finalizeRequestedCancellation_whenReasonExists_marksCancelledAndPublishesFailure() {
    var job = job();
    when(jobStateGateway.findByJobId(job.jobId()))
        .thenReturn(Optional.of(cancellationRequested(job, "requested")));
    when(jobStateGateway.markCancelled(job.jobId(), "requested")).thenReturn(true);

    boolean finalized = finalizer.finalizeRequestedCancellation(job);

    assertTrue(finalized);
    assertEquals(1, lifecycleEvents.get());
    verify(jobStateGateway).markCancelled(job.jobId(), "requested");
    verify(historyEventPublisher).publishFailed(job, HistoricalJobStage.CANCELLED, "requested");
  }

  @Test
  void finalizeRequestedCancellation_whenReplacementContextExists_usesSupersededFallback() {
    var job = job();
    var replacementRequestId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    String reason = HistoricalJobCancellationReasons.supersededByRequest(replacementRequestId);
    when(jobStateGateway.findByJobId(job.jobId()))
        .thenReturn(Optional.of(cancellationRequested(job, null)));
    when(jobStateGateway.markCancelled(job.jobId(), reason)).thenReturn(true);

    boolean finalized = finalizer.finalizeRequestedCancellation(job, replacementRequestId);

    assertTrue(finalized);
    assertEquals(1, lifecycleEvents.get());
    verify(jobStateGateway).markCancelled(job.jobId(), reason);
    verify(historyEventPublisher).publishFailed(job, HistoricalJobStage.CANCELLED, reason);
  }

  @Test
  void finalizeThrownCancellation_whenMarkCancelledFails_publishesFailureWithoutLifecycleEvent() {
    var job = job();
    var cause = new RuntimeException("cancelled");
    when(jobStateGateway.markCancelled(job.jobId(), "cancelled")).thenReturn(false);

    finalizer.finalizeThrownCancellation(job, "cancelled", cause);

    assertEquals(0, lifecycleEvents.get());
    verify(historyEventPublisher).publishFailed(job, HistoricalJobStage.CANCELLED, "cancelled");
  }

  private static HistoricalJobState cancellationRequested(
      HistoricalTracksJobSubmission job, String reason) {
    return HistoricalJobState.received(job, NOW, TTL).cancellationRequested(NOW, TTL, reason);
  }

  private static HistoricalTracksJobSubmission job() {
    return HistoricalTracksJobSubmission.create(
        UUID.randomUUID(),
        JobIssuer.user(UUID.fromString("550e8400-e29b-41d4-a716-446655440120")),
        SubjectType.SOURCE,
        UUID.randomUUID(),
        UUID.fromString("550e8400-e29b-41d4-a716-446655440120"),
        Instant.parse("2026-04-07T10:00:00Z"),
        Instant.parse("2026-04-06T10:02:17Z"),
        Instant.parse("2026-04-06T12:00:00Z"),
        Instant.parse("2026-04-06T10:02:00Z"),
        Instant.parse("2026-04-06T12:00:00Z"),
        HistoricalResolution.MINUTE_1,
        118,
        List.of());
  }
}
