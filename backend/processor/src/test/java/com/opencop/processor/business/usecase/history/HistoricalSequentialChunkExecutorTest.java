// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.usecase.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.sdk.model.SubjectType;
import com.opencop.core.sdk.model.history.HistoricalResolution;
import com.opencop.core.sdk.model.job.JobIssuer;
import com.opencop.processor.business.model.history.HistoricalExecutionContext;
import com.opencop.processor.business.model.history.HistoricalSubjectExecutionMeta;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkCacheStatus;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkPlan;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkPlanEntry;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkWindowPlan;
import com.opencop.processor.business.policy.history.HistoricalChunkFailurePolicy;
import com.opencop.processor.technical.observability.history.HistoricalObservability.Cached;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class HistoricalSequentialChunkExecutorTest {

  private final HistoricalStateReuseExecutor stateReuseExecutor =
      mock(HistoricalStateReuseExecutor.class);
  private final HistoricalStateReuseExecutor.Session stateReuseSession =
      mock(HistoricalStateReuseExecutor.Session.class);
  private final HistoricalChunkExecutionTelemetry executionTelemetry =
      mock(HistoricalChunkExecutionTelemetry.class);
  private final HistoricalChunkCancellationGuard cancellationGuard =
      mock(HistoricalChunkCancellationGuard.class);
  private final HistoricalChunkDeliveryProgressReporter deliveryProgressReporter =
      mock(HistoricalChunkDeliveryProgressReporter.class);
  private final HistoricalChunkComputeKeepalive computeKeepalive =
      mock(HistoricalChunkComputeKeepalive.class);

  @AfterEach
  void clearInterruptedFlag() {
    Thread.interrupted();
  }

  @Test
  void execute_recordsProgressAndReturnsComputedAndFailedSummary() {
    var executor = executor(Integer.MAX_VALUE, 1.0);
    var metadata = laneMetadata();
    var computed = entry(0, HistoricalChunkCacheStatus.MISSING);
    var reused = entry(1, HistoricalChunkCacheStatus.CACHED);
    var failed = entry(2, HistoricalChunkCacheStatus.MISSING);
    var executionContext = HistoricalExecutionContext.from(metadata);
    var plan =
        HistoricalChunkPlan.forSubject(metadata, laneWindowPlan(List.of(computed, reused, failed)));
    wireLaneSession(metadata, plan);
    when(stateReuseSession.executeChunk(executionContext, plan, computed))
        .thenReturn(HistoricalChunkExecutionResult.COMPUTED);
    when(stateReuseSession.executeChunk(executionContext, plan, reused))
        .thenReturn(HistoricalChunkExecutionResult.REUSED_FROM_CACHE);
    when(stateReuseSession.executeChunk(executionContext, plan, failed))
        .thenReturn(HistoricalChunkExecutionResult.FAILED);

    HistoricalChunkExecutionSummary summary = executor.execute(metadata, plan);

    assertEquals(new HistoricalChunkExecutionSummary(1, 1), summary);
    ArgumentCaptor<Long> computedChunks = ArgumentCaptor.forClass(Long.class);
    ArgumentCaptor<Long> failedChunks = ArgumentCaptor.forClass(Long.class);
    verify(deliveryProgressReporter, times(3))
        .recordProgress(eq(plan), eq(metadata), computedChunks.capture(), failedChunks.capture());
    assertEquals(List.of(1L, 1L, 1L), computedChunks.getAllValues());
    assertEquals(List.of(0L, 0L, 1L), failedChunks.getAllValues());
    verify(executionTelemetry)
        .execute(eq(metadata), eq(plan), eq(computed), eq(Cached.FALSE), any());
    verify(executionTelemetry).execute(eq(metadata), eq(plan), eq(reused), eq(Cached.TRUE), any());
  }

  @Test
  void execute_whenCancellationRequestedBeforeFirstChunk_stopsWithoutExecutingChunk() {
    var executor = executor(Integer.MAX_VALUE, 1.0);
    var metadata = laneMetadata();
    var chunk = entry(0, HistoricalChunkCacheStatus.MISSING);
    var plan = HistoricalChunkPlan.forSubject(metadata, laneWindowPlan(List.of(chunk)));
    var cancellation =
        new HistoricalJobCancelledException(metadata.laneJobId(), "Historical job cancelled");
    wireLaneSession(metadata, plan);
    doThrow(cancellation)
        .when(cancellationGuard)
        .throwIfCancellationRequested(eq(HistoricalExecutionContext.from(metadata)), eq(1), any());

    HistoricalJobCancelledException thrown =
        assertThrows(HistoricalJobCancelledException.class, () -> executor.execute(metadata, plan));

    assertEquals(metadata.laneJobId(), thrown.jobId());
    verify(stateReuseSession, never())
        .executeChunk(any(HistoricalExecutionContext.class), any(), any());
    verify(deliveryProgressReporter, never()).recordProgress(any(), any(), anyLong(), anyLong());
  }

  @Test
  void execute_whenCancellationRequestedBetweenChunks_stopsBeforeNextChunk() {
    var executor = executor(Integer.MAX_VALUE, 1.0);
    var metadata = laneMetadata();
    var first = entry(0, HistoricalChunkCacheStatus.MISSING);
    var second = entry(1, HistoricalChunkCacheStatus.MISSING);
    var executionContext = HistoricalExecutionContext.from(metadata);
    var plan = HistoricalChunkPlan.forSubject(metadata, laneWindowPlan(List.of(first, second)));
    var cancellation =
        new HistoricalJobCancelledException(metadata.laneJobId(), "Historical job cancelled");
    wireLaneSession(metadata, plan);
    when(stateReuseSession.executeChunk(executionContext, plan, first))
        .thenReturn(HistoricalChunkExecutionResult.COMPUTED);
    doNothing()
        .doThrow(cancellation)
        .when(cancellationGuard)
        .throwIfCancellationRequested(eq(executionContext), anyInt(), any());

    HistoricalJobCancelledException thrown =
        assertThrows(HistoricalJobCancelledException.class, () -> executor.execute(metadata, plan));

    assertEquals(metadata.laneJobId(), thrown.jobId());
    verify(stateReuseSession).executeChunk(executionContext, plan, first);
    verify(stateReuseSession, never()).executeChunk(executionContext, plan, second);
    verify(deliveryProgressReporter).recordProgress(plan, metadata, 1, 0);
  }

  @Test
  void execute_whenFailureThresholdTrips_throwsBeforePublishingAbortedProgress() {
    var executor = executor(1, 1.0);
    var metadata = laneMetadata();
    var first = entry(0, HistoricalChunkCacheStatus.MISSING);
    var second = entry(1, HistoricalChunkCacheStatus.MISSING);
    var executionContext = HistoricalExecutionContext.from(metadata);
    var plan = HistoricalChunkPlan.forSubject(metadata, laneWindowPlan(List.of(first, second)));
    wireLaneSession(metadata, plan);
    when(stateReuseSession.executeChunk(executionContext, plan, first))
        .thenReturn(HistoricalChunkExecutionResult.FAILED);
    when(stateReuseSession.executeChunk(executionContext, plan, second))
        .thenReturn(HistoricalChunkExecutionResult.FAILED);

    IllegalStateException thrown =
        assertThrows(IllegalStateException.class, () -> executor.execute(metadata, plan));

    assertTrue(thrown.getMessage().contains("failure ratio exceeded threshold"));
    verify(deliveryProgressReporter).recordProgress(plan, metadata, 0, 1);
  }

  @Test
  void execute_whenThreadIsInterrupted_throwsBeforeExecutingChunk() {
    var executor = executor(Integer.MAX_VALUE, 1.0);
    var metadata = laneMetadata();
    var chunk = entry(0, HistoricalChunkCacheStatus.MISSING);
    var plan = HistoricalChunkPlan.forSubject(metadata, laneWindowPlan(List.of(chunk)));
    wireLaneSession(metadata, plan);
    Thread.currentThread().interrupt();

    IllegalStateException thrown =
        assertThrows(IllegalStateException.class, () -> executor.execute(metadata, plan));

    assertEquals("Historical job interrupted during chunk execution", thrown.getMessage());
    verify(stateReuseSession, never())
        .executeChunk(any(HistoricalExecutionContext.class), any(), any());
  }

  @Test
  void execute_usesDistinctStateReuseSessionPerSubjectLaneInvocation() {
    var executor = executor(Integer.MAX_VALUE, 1.0);
    var firstSession = mock(HistoricalStateReuseExecutor.Session.class);
    var secondSession = mock(HistoricalStateReuseExecutor.Session.class);

    var firstMetadata = laneMetadata();
    var secondMetadata =
        new HistoricalSubjectExecutionMeta(
            UUID.fromString("550e8400-e29b-41d4-a716-446655440210"),
            UUID.fromString("550e8400-e29b-41d4-a716-446655440211"),
            UUID.fromString("550e8400-e29b-41d4-a716-446655440212"),
            JobIssuer.user(UUID.fromString("550e8400-e29b-41d4-a716-446655440120")),
            UUID.fromString("550e8400-e29b-41d4-a716-446655440213"),
            Instant.parse("2026-04-07T10:00:00Z"),
            Instant.parse("2026-04-06T10:02:17Z"),
            Instant.parse("2026-04-06T12:13:02Z"),
            Instant.parse("2026-04-06T10:00:00Z"),
            Instant.parse("2026-04-06T10:02:00Z"),
            HistoricalResolution.MINUTE_1,
            2,
            List.of(),
            SubjectType.SOURCE,
            UUID.fromString("550e8400-e29b-41d4-a716-446655440214"),
            laneWindowPlan(List.of(entry(1, HistoricalChunkCacheStatus.MISSING))));
    var firstChunk = entry(0, HistoricalChunkCacheStatus.MISSING);
    var secondChunk = entry(1, HistoricalChunkCacheStatus.MISSING);
    var firstPlan =
        HistoricalChunkPlan.forSubject(firstMetadata, laneWindowPlan(List.of(firstChunk)));
    var secondPlan =
        HistoricalChunkPlan.forSubject(secondMetadata, laneWindowPlan(List.of(secondChunk)));

    when(stateReuseExecutor.newSession()).thenReturn(firstSession, secondSession);
    when(computeKeepalive.execute(any(), any(), any(), any(), any()))
        .thenAnswer(
            invocation -> {
              var operation =
                  (HistoricalChunkExecutionTelemetry.ChunkOperation) invocation.getArgument(4);
              return operation.execute();
            });

    when(executionTelemetry.execute(eq(firstMetadata), eq(firstPlan), any(), any(), any()))
        .thenAnswer(
            invocation ->
                ((HistoricalChunkExecutionTelemetry.ChunkOperation) invocation.getArgument(4))
                    .execute());
    when(executionTelemetry.execute(eq(secondMetadata), eq(secondPlan), any(), any(), any()))
        .thenAnswer(
            invocation ->
                ((HistoricalChunkExecutionTelemetry.ChunkOperation) invocation.getArgument(4))
                    .execute());
    when(firstSession.executeChunk(
            HistoricalExecutionContext.from(firstMetadata), firstPlan, firstChunk))
        .thenReturn(HistoricalChunkExecutionResult.COMPUTED);
    when(secondSession.executeChunk(
            HistoricalExecutionContext.from(secondMetadata), secondPlan, secondChunk))
        .thenReturn(HistoricalChunkExecutionResult.FAILED);

    var firstSummary = executor.execute(firstMetadata, firstPlan);
    var secondSummary = executor.execute(secondMetadata, secondPlan);

    assertEquals(new HistoricalChunkExecutionSummary(1, 0), firstSummary);
    assertEquals(new HistoricalChunkExecutionSummary(0, 1), secondSummary);
    verify(stateReuseExecutor, times(2)).newSession();
    assertNotSame(firstSession, secondSession);

    verify(firstSession)
        .executeChunk(HistoricalExecutionContext.from(firstMetadata), firstPlan, firstChunk);
    verify(firstSession, never())
        .executeChunk(HistoricalExecutionContext.from(secondMetadata), secondPlan, secondChunk);
    verify(secondSession)
        .executeChunk(HistoricalExecutionContext.from(secondMetadata), secondPlan, secondChunk);
    verify(secondSession, never())
        .executeChunk(HistoricalExecutionContext.from(firstMetadata), firstPlan, firstChunk);
  }

  private HistoricalSequentialChunkExecutor executor(
      int maxConsecutiveChunkFailures, double maxFailureRatio) {
    return new HistoricalSequentialChunkExecutor(
        stateReuseExecutor,
        executionTelemetry,
        new HistoricalChunkFailurePolicy(maxConsecutiveChunkFailures, maxFailureRatio),
        cancellationGuard,
        deliveryProgressReporter,
        computeKeepalive);
  }

  private void wireLaneSession(HistoricalSubjectExecutionMeta metadata, HistoricalChunkPlan plan) {
    when(stateReuseExecutor.newSession()).thenReturn(stateReuseSession);
    when(computeKeepalive.execute(any(), eq(metadata), any(), any(), any()))
        .thenAnswer(
            invocation -> {
              var operation =
                  (HistoricalChunkExecutionTelemetry.ChunkOperation) invocation.getArgument(4);
              return operation.execute();
            });
    when(executionTelemetry.execute(eq(metadata), eq(plan), any(), any(), any()))
        .thenAnswer(
            invocation -> {
              var operation =
                  (HistoricalChunkExecutionTelemetry.ChunkOperation) invocation.getArgument(4);
              return operation.execute();
            });
  }

  private static HistoricalChunkPlanEntry entry(int index, HistoricalChunkCacheStatus cacheStatus) {
    Instant from = Instant.parse("2026-04-06T10:00:00Z").plusSeconds(60L * index);
    return new HistoricalChunkPlanEntry(
        index,
        "chunk-" + index,
        "payload-" + index,
        "state-" + index,
        cacheStatus,
        from,
        from.plusSeconds(60));
  }

  private static HistoricalSubjectExecutionMeta laneMetadata() {
    Instant effectiveFrom = Instant.parse("2026-04-06T10:00:00Z");
    return new HistoricalSubjectExecutionMeta(
        UUID.fromString("550e8400-e29b-41d4-a716-446655440200"),
        UUID.fromString("550e8400-e29b-41d4-a716-446655440201"),
        UUID.fromString("550e8400-e29b-41d4-a716-446655440202"),
        JobIssuer.user(UUID.fromString("550e8400-e29b-41d4-a716-446655440120")),
        UUID.fromString("550e8400-e29b-41d4-a716-446655440203"),
        Instant.parse("2026-04-07T10:00:00Z"),
        Instant.parse("2026-04-06T10:02:17Z"),
        Instant.parse("2026-04-06T12:13:02Z"),
        effectiveFrom,
        effectiveFrom.plusSeconds(180),
        HistoricalResolution.MINUTE_1,
        3,
        List.of(),
        SubjectType.SOURCE,
        UUID.fromString("550e8400-e29b-41d4-a716-446655440204"),
        laneWindowPlan(
            List.of(
                entry(0, HistoricalChunkCacheStatus.MISSING),
                entry(1, HistoricalChunkCacheStatus.CACHED),
                entry(2, HistoricalChunkCacheStatus.MISSING))));
  }

  private static HistoricalChunkWindowPlan laneWindowPlan(List<HistoricalChunkPlanEntry> entries) {
    return new HistoricalChunkWindowPlan(
        entries.getFirst().from(),
        entries.getLast().to(),
        entries.size(),
        HistoricalResolution.MINUTE_1,
        entries);
  }
}
