// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.usecase.history;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.opencop.core.sdk.model.SubjectType;
import com.opencop.core.sdk.model.history.HistoricalResolution;
import com.opencop.core.sdk.model.job.HistoricalTracksJobSubmission;
import com.opencop.core.sdk.model.job.JobIssuer;
import com.opencop.processor.business.model.history.HistoricalExecutionContext;
import com.opencop.processor.business.model.history.HistoricalSubjectExecutionMeta;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkCacheStatus;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkPlanEntry;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkWindowPlan;
import com.opencop.processor.business.policy.history.HistoricalCacheIdentity;
import com.opencop.processor.ports.technical.gateway.history.HistoricalChunkPayloadCacheGateway;
import com.opencop.processor.ports.technical.gateway.history.HistoricalChunkPersistenceGateway;
import com.opencop.processor.ports.technical.gateway.history.HistoricalChunkReadyNotifier;
import com.opencop.processor.ports.technical.gateway.history.HistoricalChunkStateGateway;
import com.opencop.processor.ports.technical.gateway.history.HistoricalJobStateGateway;
import com.opencop.processor.technical.gateway.history.model.chunk.HistoricalChunkState;
import com.opencop.processor.technical.gateway.history.model.chunk.HistoricalChunkStateStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HistoricalChunkAvailabilityHandlerTest {

  private final HistoricalChunkPersistenceGateway payloadPersistence =
      mock(HistoricalChunkPersistenceGateway.class);
  private final HistoricalChunkPayloadCacheGateway payloadCacheGateway =
      mock(HistoricalChunkPayloadCacheGateway.class);
  private final HistoricalChunkStateGateway chunkStateGateway =
      mock(HistoricalChunkStateGateway.class);
  private final HistoricalJobStateGateway jobStateGateway = mock(HistoricalJobStateGateway.class);
  private final HistoricalChunkReadyNotifier chunkReadyNotifier =
      mock(HistoricalChunkReadyNotifier.class);
  private final HistoricalChunkAvailabilityHandler handler =
      new HistoricalChunkAvailabilityHandler(
          payloadPersistence,
          payloadCacheGateway,
          chunkStateGateway,
          jobStateGateway,
          chunkReadyNotifier,
          new HistoricalCacheIdentity(new tools.jackson.databind.ObjectMapper()));

  @Test
  void metadataReuseCachedChunkIfAvailable_marksSinglePartChunkAvailable() {
    var job = job();
    var chunk = chunk();
    when(chunkStateGateway.findByChunkStateKey(chunk.chunkStateKey())).thenReturn(Optional.empty());
    when(payloadCacheGateway.findAll(List.of(chunk.payloadKey())))
        .thenReturn(Map.of(chunk.payloadKey(), Optional.of(new byte[] {1})));

    var executionContext = HistoricalExecutionContext.from(job);
    boolean reused = handler.reuseCachedChunkIfAvailable(executionContext, chunk);

    assertTrue(reused);
    verify(chunkStateGateway)
        .transition(chunk.chunkStateKey(), HistoricalChunkStateStatus.AVAILABLE_FOR_DELIVERY);
    verify(jobStateGateway).refreshActiveTtl(job.jobId());
    verify(chunkReadyNotifier).notifyChunkReady(executionContext, chunk, true, 1);
    verifyNoInteractions(payloadPersistence);
  }

  @Test
  void reuseCachedChunkIfAvailable_checksEveryStoredPartBeforeMarkingAvailable() {
    var job = job();
    var chunk = chunk();
    when(chunkStateGateway.findByChunkStateKey(chunk.chunkStateKey()))
        .thenReturn(Optional.of(chunkState(chunk, 3)));
    when(payloadCacheGateway.findAll(
            List.of(
                chunk.payloadKey(),
                chunk.payloadKey() + ":part:1",
                chunk.payloadKey() + ":part:2")))
        .thenReturn(
            Map.of(
                chunk.payloadKey(),
                Optional.of(new byte[] {1}),
                chunk.payloadKey() + ":part:1",
                Optional.of(new byte[] {2}),
                chunk.payloadKey() + ":part:2",
                Optional.of(new byte[] {3})));

    var executionContext = HistoricalExecutionContext.from(job);
    boolean reused = handler.reuseCachedChunkIfAvailable(executionContext, chunk);

    assertTrue(reused);
    verify(chunkReadyNotifier).notifyChunkReady(executionContext, chunk, true, 3);
  }

  @Test
  void metadataReuseCachedChunkIfAvailable_marksPayloadMissingForRecoveryWhenAnyPartIsMissing() {
    var job = job();
    var chunk = chunk();
    when(chunkStateGateway.findByChunkStateKey(chunk.chunkStateKey()))
        .thenReturn(Optional.of(chunkState(chunk, 3)));
    when(payloadCacheGateway.findAll(
            List.of(
                chunk.payloadKey(),
                chunk.payloadKey() + ":part:1",
                chunk.payloadKey() + ":part:2")))
        .thenReturn(
            Map.of(
                chunk.payloadKey(),
                Optional.of(new byte[] {1}),
                chunk.payloadKey() + ":part:1",
                Optional.empty(),
                chunk.payloadKey() + ":part:2",
                Optional.of(new byte[] {3})));

    boolean reused =
        handler.reuseCachedChunkIfAvailable(HistoricalExecutionContext.from(job), chunk);

    assertFalse(reused);
    verify(payloadPersistence).markPayloadMissingForRecovery(chunk, true);
    verify(chunkStateGateway, never())
        .transition(chunk.chunkStateKey(), HistoricalChunkStateStatus.AVAILABLE_FOR_DELIVERY);
    verify(jobStateGateway, never()).refreshActiveTtl(job.jobId());
    verifyNoInteractions(chunkReadyNotifier);
  }

  @Test
  void reuseCachedChunkIfAvailable_marksSinglePartChunkAvailable() {
    var laneMetadata = laneMetadata();
    var chunk = chunk();
    when(chunkStateGateway.findByChunkStateKey(chunk.chunkStateKey())).thenReturn(Optional.empty());
    when(payloadCacheGateway.findAll(List.of(chunk.payloadKey())))
        .thenReturn(Map.of(chunk.payloadKey(), Optional.of(new byte[] {1})));

    var executionContext = HistoricalExecutionContext.from(laneMetadata);
    boolean reused = handler.reuseCachedChunkIfAvailable(executionContext, chunk);

    assertTrue(reused);
    verify(chunkStateGateway)
        .transition(chunk.chunkStateKey(), HistoricalChunkStateStatus.AVAILABLE_FOR_DELIVERY);
    verify(jobStateGateway).refreshActiveTtl(laneMetadata.laneJobId());
    verify(chunkReadyNotifier).notifyChunkReady(executionContext, chunk, true, 1);
    verifyNoInteractions(payloadPersistence);
  }

  @Test
  void reuseCachedChunkIfAvailable_marksPayloadMissingForRecoveryWhenAnyPartIsMissing() {
    var laneMetadata = laneMetadata();
    var chunk = chunk();
    when(chunkStateGateway.findByChunkStateKey(chunk.chunkStateKey()))
        .thenReturn(Optional.of(chunkState(chunk, 3)));
    when(payloadCacheGateway.findAll(
            List.of(
                chunk.payloadKey(),
                chunk.payloadKey() + ":part:1",
                chunk.payloadKey() + ":part:2")))
        .thenReturn(
            Map.of(
                chunk.payloadKey(),
                Optional.of(new byte[] {1}),
                chunk.payloadKey() + ":part:1",
                Optional.empty(),
                chunk.payloadKey() + ":part:2",
                Optional.of(new byte[] {3})));

    boolean reused =
        handler.reuseCachedChunkIfAvailable(HistoricalExecutionContext.from(laneMetadata), chunk);

    assertFalse(reused);
    verify(payloadPersistence).markPayloadMissingForRecovery(chunk, true);
    verify(chunkStateGateway, never())
        .transition(chunk.chunkStateKey(), HistoricalChunkStateStatus.AVAILABLE_FOR_DELIVERY);
    verify(jobStateGateway, never()).refreshActiveTtl(laneMetadata.laneJobId());
    verifyNoInteractions(chunkReadyNotifier);
  }

  @Test
  void metadataNotifyComputedChunkReady_notifiesNonCachedChunk() {
    var laneMetadata = laneMetadata();
    var chunk = chunk();

    var executionContext = HistoricalExecutionContext.from(laneMetadata);
    handler.notifyComputedChunkReady(executionContext, chunk, 2);

    verify(chunkReadyNotifier).notifyChunkReady(executionContext, chunk, false, 2);
    verifyNoInteractions(
        payloadPersistence, payloadCacheGateway, chunkStateGateway, jobStateGateway);
  }

  @Test
  void notifyComputedChunkReady_notifiesNonCachedChunk() {
    var job = job();
    var chunk = chunk();

    var executionContext = HistoricalExecutionContext.from(job);
    handler.notifyComputedChunkReady(executionContext, chunk, 2);

    verify(chunkReadyNotifier).notifyChunkReady(executionContext, chunk, false, 2);
    verifyNoInteractions(
        payloadPersistence, payloadCacheGateway, chunkStateGateway, jobStateGateway);
  }

  @Test
  void markChunkFailed_transitionsChunkToFailed() {
    var chunk = chunk();

    handler.markChunkFailed(chunk);

    verify(chunkStateGateway).transition(chunk.chunkStateKey(), HistoricalChunkStateStatus.FAILED);
    verifyNoInteractions(
        payloadPersistence, payloadCacheGateway, jobStateGateway, chunkReadyNotifier);
  }

  private static HistoricalChunkState chunkState(HistoricalChunkPlanEntry chunk, int partCount) {
    return HistoricalChunkState.initialize(
        UUID.fromString("550e8400-e29b-41d4-a716-446655440100"),
        chunk.chunkIndex(),
        chunk.chunkKey(),
        chunk.payloadKey(),
        chunk.chunkStateKey(),
        partCount,
        HistoricalChunkStateStatus.CACHED,
        chunk.from(),
        chunk.to(),
        Instant.parse("2026-04-06T09:00:00Z"),
        Duration.ofHours(24));
  }

  private static HistoricalChunkPlanEntry chunk() {
    return new HistoricalChunkPlanEntry(
        2,
        "chunk-cached",
        "payload-cached",
        "state-cached",
        HistoricalChunkCacheStatus.CACHED,
        Instant.parse("2026-04-06T10:04:00Z"),
        Instant.parse("2026-04-06T10:05:00Z"));
  }

  private static HistoricalTracksJobSubmission job() {
    return HistoricalTracksJobSubmission.create(
        UUID.fromString("550e8400-e29b-41d4-a716-446655440100"),
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

  private static HistoricalSubjectExecutionMeta laneMetadata() {
    return new HistoricalSubjectExecutionMeta(
        UUID.fromString("550e8400-e29b-41d4-a716-446655440400"),
        job().jobId(),
        UUID.fromString("550e8400-e29b-41d4-a716-446655440120"),
        JobIssuer.user(UUID.fromString("550e8400-e29b-41d4-a716-446655440120")),
        UUID.fromString("550e8400-e29b-41d4-a716-446655440137"),
        Instant.parse("2026-04-07T10:00:00Z"),
        Instant.parse("2026-04-06T10:02:17Z"),
        Instant.parse("2026-04-06T12:13:02Z"),
        Instant.parse("2026-04-06T10:02:00Z"),
        Instant.parse("2026-04-06T12:14:00Z"),
        HistoricalResolution.MINUTE_1,
        0L,
        List.of(),
        SubjectType.LAYER,
        UUID.fromString("550e8400-e29b-41d4-a716-446655440121"),
        new HistoricalChunkWindowPlan(
            Instant.parse("2026-04-06T10:02:00Z"),
            Instant.parse("2026-04-06T12:14:00Z"),
            1,
            HistoricalResolution.MINUTE_1,
            List.of()));
  }
}
