// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.usecase.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
import com.opencop.processor.technical.observability.history.HistoricalObservability.Cached;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class HistoricalChunkOperationExecutorTest {

  private final HistoricalChunkExecutionTelemetry executionTelemetry =
      mock(HistoricalChunkExecutionTelemetry.class);
  private final HistoricalChunkAvailabilityHandler availabilityHandler =
      mock(HistoricalChunkAvailabilityHandler.class);
  private final HistoricalChunkComputationService computationService =
      mock(HistoricalChunkComputationService.class);
  private final HistoricalChunkOperationExecutor executor =
      new HistoricalChunkOperationExecutor(
          executionTelemetry, availabilityHandler, computationService);

  @AfterEach
  void clearInterruptedFlag() {
    Thread.interrupted();
  }

  @Test
  void execute_whenChunkIsMissing_computesChunkAndReturnsComputed() {
    var laneMetadata = laneMetadata();
    var executionContext = HistoricalExecutionContext.from(laneMetadata);
    var chunk = entry(0, HistoricalChunkCacheStatus.MISSING);
    var plan = HistoricalChunkPlan.forSubject(laneMetadata, laneMetadata.windowPlan());
    executeOperationThroughLaneTelemetry(laneMetadata, plan, chunk, Cached.FALSE);

    HistoricalChunkExecutionResult result = executor.execute(plan, laneMetadata, chunk);

    assertEquals(HistoricalChunkExecutionResult.COMPUTED, result);
    verify(computationService).computeMissingChunk(executionContext, plan, chunk);
    verifyNoInteractions(availabilityHandler);
  }

  @Test
  void execute_whenCachedChunkIsAvailable_reusesCacheWithoutComputing() {
    var laneMetadata = laneMetadata();
    var executionContext = HistoricalExecutionContext.from(laneMetadata);
    var chunk = entry(0, HistoricalChunkCacheStatus.CACHED);
    var plan = HistoricalChunkPlan.forSubject(laneMetadata, laneMetadata.windowPlan());
    when(availabilityHandler.reuseCachedChunkIfAvailable(executionContext, chunk)).thenReturn(true);
    executeOperationThroughLaneTelemetry(laneMetadata, plan, chunk, Cached.TRUE);

    HistoricalChunkExecutionResult result = executor.execute(plan, laneMetadata, chunk);

    assertEquals(HistoricalChunkExecutionResult.REUSED_FROM_CACHE, result);
    verify(availabilityHandler).reuseCachedChunkIfAvailable(executionContext, chunk);
    verify(computationService, never())
        .computeMissingChunk(any(HistoricalExecutionContext.class), any(), any());
  }

  @Test
  void execute_whenCachedPayloadIsMissing_recomputesChunkAndReturnsComputed() {
    var laneMetadata = laneMetadata();
    var executionContext = HistoricalExecutionContext.from(laneMetadata);
    var chunk = entry(0, HistoricalChunkCacheStatus.CACHED);
    var plan = HistoricalChunkPlan.forSubject(laneMetadata, laneMetadata.windowPlan());
    when(availabilityHandler.reuseCachedChunkIfAvailable(executionContext, chunk))
        .thenReturn(false);
    executeOperationThroughLaneTelemetry(laneMetadata, plan, chunk, Cached.TRUE);

    HistoricalChunkExecutionResult result = executor.execute(plan, laneMetadata, chunk);

    assertEquals(HistoricalChunkExecutionResult.COMPUTED, result);
    verify(availabilityHandler).reuseCachedChunkIfAvailable(executionContext, chunk);
    verify(computationService).computeMissingChunk(executionContext, plan, chunk);
  }

  @Test
  void execute_whenCachedChunkIsAvailable_reusesCachedChunkForMetadataSubjectIdentity() {
    var laneMetadata = laneMetadata();
    var executionContext = HistoricalExecutionContext.from(laneMetadata);
    var chunk = entry(0, HistoricalChunkCacheStatus.CACHED);
    var plan = HistoricalChunkPlan.forSubject(laneMetadata, laneMetadata.windowPlan());
    when(availabilityHandler.reuseCachedChunkIfAvailable(executionContext, chunk)).thenReturn(true);
    executeOperationThroughLaneTelemetry(laneMetadata, plan, chunk, Cached.TRUE);

    HistoricalChunkExecutionResult result = executor.execute(plan, laneMetadata, chunk);

    assertEquals(HistoricalChunkExecutionResult.REUSED_FROM_CACHE, result);
    verify(availabilityHandler).reuseCachedChunkIfAvailable(executionContext, chunk);
    verify(computationService, never())
        .computeMissingChunk(any(HistoricalExecutionContext.class), any(), any());
  }

  @Test
  void execute_whenCachedPayloadIsMissing_recomputesChunkForMetadataSubjectIdentity() {
    var laneMetadata = laneMetadata();
    var executionContext = HistoricalExecutionContext.from(laneMetadata);
    var chunk = entry(0, HistoricalChunkCacheStatus.CACHED);
    var plan = HistoricalChunkPlan.forSubject(laneMetadata, laneMetadata.windowPlan());
    when(availabilityHandler.reuseCachedChunkIfAvailable(executionContext, chunk))
        .thenReturn(false);
    executeOperationThroughLaneTelemetry(laneMetadata, plan, chunk, Cached.TRUE);

    HistoricalChunkExecutionResult result = executor.execute(plan, laneMetadata, chunk);

    assertEquals(HistoricalChunkExecutionResult.COMPUTED, result);
    verify(availabilityHandler).reuseCachedChunkIfAvailable(executionContext, chunk);
    verify(computationService).computeMissingChunk(executionContext, plan, chunk);
  }

  @Test
  void execute_whenTelemetryConvertsComputationFailure_returnsFailed() {
    var laneMetadata = laneMetadata();
    var executionContext = HistoricalExecutionContext.from(laneMetadata);
    var chunk = entry(0, HistoricalChunkCacheStatus.MISSING);
    var plan = HistoricalChunkPlan.forSubject(laneMetadata, laneMetadata.windowPlan());
    var failure = new IllegalStateException("simulated failure");
    when(executionTelemetry.execute(eq(laneMetadata), eq(plan), eq(chunk), eq(Cached.FALSE), any()))
        .thenAnswer(
            invocation -> {
              var operation =
                  (HistoricalChunkExecutionTelemetry.ChunkOperation) invocation.getArgument(4);
              try {
                operation.execute();
              } catch (RuntimeException error) {
                return HistoricalChunkExecutionResult.FAILED;
              }
              return HistoricalChunkExecutionResult.COMPUTED;
            });
    org.mockito.Mockito.doThrow(failure)
        .when(computationService)
        .computeMissingChunk(executionContext, plan, chunk);

    HistoricalChunkExecutionResult result = executor.execute(plan, laneMetadata, chunk);

    assertEquals(HistoricalChunkExecutionResult.FAILED, result);
  }

  @Test
  void execute_whenThreadIsInterrupted_throwsBeforeTelemetryOrComputation() {
    var laneMetadata = laneMetadata();
    var chunk = entry(0, HistoricalChunkCacheStatus.MISSING);
    var plan = HistoricalChunkPlan.forSubject(laneMetadata, laneMetadata.windowPlan());
    Thread.currentThread().interrupt();

    IllegalStateException thrown =
        assertThrows(
            IllegalStateException.class, () -> executor.execute(plan, laneMetadata, chunk));

    assertEquals("Historical job interrupted during chunk execution", thrown.getMessage());
    verifyNoInteractions(executionTelemetry, availabilityHandler, computationService);
  }

  private void executeOperationThroughLaneTelemetry(
      HistoricalSubjectExecutionMeta metadata,
      HistoricalChunkPlan plan,
      HistoricalChunkPlanEntry chunk,
      Cached cached) {
    when(executionTelemetry.execute(eq(metadata), eq(plan), eq(chunk), eq(cached), any()))
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
    return new HistoricalSubjectExecutionMeta(
        UUID.fromString("550e8400-e29b-41d4-a716-446655440199"),
        UUID.fromString("550e8400-e29b-41d4-a716-446655440130"),
        UUID.fromString("550e8400-e29b-41d4-a716-446655440120"),
        JobIssuer.user(UUID.fromString("550e8400-e29b-41d4-a716-446655440120")),
        UUID.fromString("550e8400-e29b-41d4-a716-446655440120"),
        Instant.parse("2026-04-07T10:00:00Z"),
        Instant.parse("2026-04-06T10:02:17Z"),
        Instant.parse("2026-04-06T12:13:02Z"),
        Instant.parse("2026-04-06T10:02:00Z"),
        Instant.parse("2026-04-06T12:14:00Z"),
        HistoricalResolution.MINUTE_1,
        0L,
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
}
