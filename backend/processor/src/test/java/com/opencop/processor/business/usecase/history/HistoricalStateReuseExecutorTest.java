// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.usecase.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.opencop.core.sdk.model.SubjectType;
import com.opencop.core.sdk.model.history.HistoricalJobStage;
import com.opencop.core.sdk.model.history.HistoricalResolution;
import com.opencop.core.sdk.model.job.HistoricalTracksJobSubmission;
import com.opencop.core.sdk.model.job.JobIssuer;
import com.opencop.processor.business.model.history.HistoricalExecutionContext;
import com.opencop.processor.business.model.history.HistoricalReadPage;
import com.opencop.processor.business.model.history.HistoricalSourceEvent;
import com.opencop.processor.business.model.history.HistoricalSubjectExecutionMeta;
import com.opencop.processor.business.model.history.SourceObjectKey;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkCacheStatus;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkPayloadBuildResult;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkPayloadBuilder;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkPlan;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkPlanEntry;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkSampledResult;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkWindowPlan;
import com.opencop.processor.business.policy.history.HistoricalCacheIdentity;
import com.opencop.processor.business.policy.history.V1LatestInBucketSampler;
import com.opencop.processor.ports.technical.gateway.history.HistoricalChunkPayloadCacheGateway;
import com.opencop.processor.ports.technical.gateway.history.HistoricalChunkPersistenceGateway;
import com.opencop.processor.ports.technical.gateway.history.HistoricalChunkReadyNotifier;
import com.opencop.processor.ports.technical.gateway.history.HistoricalChunkStateGateway;
import com.opencop.processor.ports.technical.gateway.history.HistoricalChunkStateReader;
import com.opencop.processor.ports.technical.gateway.history.HistoricalJobStateGateway;
import com.opencop.processor.technical.gateway.history.model.chunk.HistoricalChunkState;
import com.opencop.processor.technical.gateway.history.model.chunk.HistoricalChunkStateStatus;
import com.opencop.processor.technical.gateway.history.model.job.HistoricalJobState;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class HistoricalStateReuseExecutorTest {
  private static final int MANY_ADJACENT_CHUNK_COUNT = 31;
  private static final int MANY_ADJACENT_DELTA_READ_COUNT = MANY_ADJACENT_CHUNK_COUNT - 1;
  private static final Instant MANY_ADJACENT_START = Instant.parse("2026-04-06T10:00:00Z");

  private final RecordingReadGateway readGateway = new RecordingReadGateway();
  private final RecordingPayloadBuilder payloadBuilder = new RecordingPayloadBuilder();
  private final RecordingPayloadPersistence payloadPersistence = new RecordingPayloadPersistence();
  private final RecordingChunkStateGateway chunkStateGateway = new RecordingChunkStateGateway();
  private final RecordingChunkReadyNotifier chunkReadyNotifier = new RecordingChunkReadyNotifier();
  private final HistoricalChunkAvailabilityHandler availabilityHandler =
      new HistoricalChunkAvailabilityHandler(
          payloadPersistence,
          new NoopPayloadCacheGateway(),
          chunkStateGateway,
          new NoopJobStateGateway(),
          chunkReadyNotifier,
          new HistoricalCacheIdentity(new tools.jackson.databind.ObjectMapper()));
  private final HistoricalStateReuseExecutor executor =
      new HistoricalStateReuseExecutor(
          readGateway,
          new V1LatestInBucketSampler(),
          payloadBuilder,
          payloadPersistence,
          chunkStateGateway,
          availabilityHandler);

  @Test
  void session_reusesActiveStateAcrossAdjacentChunksAndPrunesInactiveObjects() {
    var job = job();
    var chunkA = chunk(0, "chunk-a", "payload-a", "state-a", "2026-04-06T10:00:00Z");
    var chunkB = chunk(1, "chunk-b", "payload-b", "state-b", "2026-04-06T10:01:00Z");
    var chunkC = chunk(2, "chunk-c", "payload-c", "state-c", "2026-04-06T10:02:00Z");
    var plan = plan(job, List.of(chunkA, chunkB, chunkC));
    readGateway.snapshotPages =
        List.of(
            new HistoricalReadPage(
                List.of(
                    event("obj-1", "2026-04-06T10:00:30Z"), event("obj-2", "2026-04-06T10:00:40Z")),
                null,
                false));
    readGateway.deltaPagesByChunk.put(
        "chunk-b",
        List.of(
            new HistoricalReadPage(List.of(event("obj-1", "2026-04-06T10:01:20Z")), null, false)));
    readGateway.deltaPagesByChunk.put(
        "chunk-c",
        List.of(
            new HistoricalReadPage(List.of(event("obj-3", "2026-04-06T10:02:20Z")), null, false)));

    var session = executor.newSession();

    assertEquals(
        HistoricalChunkExecutionResult.COMPUTED,
        session.executeChunk(HistoricalExecutionContext.from(job), plan, chunkA));
    assertEquals(
        HistoricalChunkExecutionResult.COMPUTED,
        session.executeChunk(HistoricalExecutionContext.from(job), plan, chunkB));
    assertEquals(
        HistoricalChunkExecutionResult.COMPUTED,
        session.executeChunk(HistoricalExecutionContext.from(job), plan, chunkC));

    assertEquals(1, readGateway.snapshotCalls);
    assertEquals(2, readGateway.stateDeltaCalls);
    assertEquals(1, readGateway.initialStateCalls);
    assertEquals(3, payloadBuilder.buildCalls);
    assertEquals(3, payloadPersistence.persistCalls);
    assertEquals(3, chunkReadyNotifier.notifications.size());
    assertEquals(2, payloadBuilder.lastSampledChunk.objectCount());
    assertEquals(1, readGateway.initialStateCalls);
    var lastChunkObjectIds =
        payloadBuilder.lastSampledChunk.points().stream()
            .map(point -> point.objectId())
            .collect(Collectors.toSet());
    assertEquals(Set.of("obj-1", "obj-3"), lastChunkObjectIds);
  }

  @Test
  void session_passesPreviousChunkEndToDeltaReads() {
    var job = job();
    var chunkA = chunk(0, "chunk-a", "payload-a", "state-a", "2026-04-06T10:00:00Z");
    var chunkB = chunk(1, "chunk-b", "payload-b", "state-b", "2026-04-06T10:01:00Z");
    var plan = plan(job, List.of(chunkA, chunkB));
    readGateway.snapshotPages =
        List.of(
            new HistoricalReadPage(List.of(event("obj-1", "2026-04-06T10:00:30Z")), null, false));
    readGateway.deltaPagesByChunk.put(
        "chunk-b",
        List.of(
            new HistoricalReadPage(List.of(event("obj-1", "2026-04-06T10:01:20Z")), null, false)));
    var session = executor.newSession();

    session.executeChunk(HistoricalExecutionContext.from(job), plan, chunkA);
    session.executeChunk(HistoricalExecutionContext.from(job), plan, chunkB);

    assertEquals(List.of(chunkA.to()), readGateway.deltaFromValues);
  }

  @Test
  void session_carriesForwardActiveStateWhenLaterChunkHasNoDeltaForObject() {
    var job = job();
    var chunkA = chunk(0, "chunk-a", "payload-a", "state-a", "2026-04-06T10:00:00Z");
    var chunkB = chunk(1, "chunk-b", "payload-b", "state-b", "2026-04-06T10:01:00Z");
    var chunkC = chunk(2, "chunk-c", "payload-c", "state-c", "2026-04-06T10:02:00Z");
    var plan = plan(job, List.of(chunkA, chunkB, chunkC));
    readGateway.snapshotPages =
        List.of(
            new HistoricalReadPage(
                List.of(event("carried-forward", "2026-04-06T10:00:30Z", "2026-04-06T10:05:00Z")),
                null,
                false));
    readGateway.deltaPagesByChunk.put(
        "chunk-b",
        List.of(
            new HistoricalReadPage(
                List.of(event("chunk-b-only", "2026-04-06T10:01:20Z", "2026-04-06T10:02:00Z")),
                null,
                false)));
    readGateway.deltaPagesByChunk.put(
        "chunk-c", List.of(new HistoricalReadPage(List.of(), null, false)));

    var session = executor.newSession();

    assertEquals(
        HistoricalChunkExecutionResult.COMPUTED,
        session.executeChunk(HistoricalExecutionContext.from(job), plan, chunkA));
    assertEquals(
        HistoricalChunkExecutionResult.COMPUTED,
        session.executeChunk(HistoricalExecutionContext.from(job), plan, chunkB));
    assertEquals(
        HistoricalChunkExecutionResult.COMPUTED,
        session.executeChunk(HistoricalExecutionContext.from(job), plan, chunkC));

    assertEquals(1, readGateway.snapshotCalls);
    assertEquals(2, readGateway.stateDeltaCalls);
    var lastChunkObjectIds =
        payloadBuilder.lastSampledChunk.points().stream()
            .map(point -> point.objectId())
            .collect(Collectors.toSet());
    assertEquals(Set.of("carried-forward"), lastChunkObjectIds);
  }

  @Test
  void session_readsOneSnapshotThenDeltasAcrossManyAdjacentMissingChunks() {
    var job = job();
    var chunks = adjacentMissingChunks(MANY_ADJACENT_CHUNK_COUNT, MANY_ADJACENT_START);
    var plan = plan(job, chunks);
    readGateway.snapshotPages =
        List.of(
            new HistoricalReadPage(
                List.of(
                    event("snapshot-stale-before-later-chunk", "2026-04-06T10:00:30Z"),
                    event("snapshot-live-through-second-chunk", "2026-04-06T10:01:30Z")),
                null,
                false));
    for (int index = 1; index < chunks.size(); index++) {
      readGateway.deltaPagesByChunk.put(
          chunks.get(index).chunkKey(),
          List.of(
              new HistoricalReadPage(
                  List.of(event("delta-object-" + index, chunks.get(index).from().toString())),
                  null,
                  false)));
    }
    var session = executor.newSession();

    for (HistoricalChunkPlanEntry chunk : chunks) {
      assertEquals(
          HistoricalChunkExecutionResult.COMPUTED,
          session.executeChunk(HistoricalExecutionContext.from(job), plan, chunk));
    }

    assertEquals(1, readGateway.snapshotCalls);
    assertEquals(MANY_ADJACENT_DELTA_READ_COUNT, readGateway.stateDeltaCalls);
    assertEquals(1, readGateway.initialStateCalls);
    assertEquals(MANY_ADJACENT_CHUNK_COUNT, payloadBuilder.buildCalls);
    assertEquals(previousChunkEnds(chunks), readGateway.deltaFromValues);
    var lastChunkObjectIds =
        payloadBuilder.lastSampledChunk.points().stream()
            .map(point -> point.objectId())
            .collect(Collectors.toSet());
    assertFalse(lastChunkObjectIds.contains("snapshot-stale-before-later-chunk"));
  }

  @Test
  void session_marksChunkFailedAndWrapsRuntimeFailure() {
    var job = job();
    var chunk = chunk(0, "chunk-fails", "payload-fails", "state-fails", "2026-04-06T10:00:00Z");
    var plan = plan(job, List.of(chunk));
    var failure = new IllegalStateException("simulated read failure");
    readGateway.failure = failure;

    IllegalStateException thrown =
        assertThrows(
            IllegalStateException.class,
            () ->
                executor
                    .newSession()
                    .executeChunk(HistoricalExecutionContext.from(job), plan, chunk));

    assertSame(failure, thrown.getCause());
    assertEquals(
        List.of(HistoricalChunkStateStatus.COMPUTING, HistoricalChunkStateStatus.FAILED),
        chunkStateGateway.transitions);
  }

  @Test
  void streaming_strategy_appliesAllPagedDeltaRowsInOrderDuringStreamingRead() {
    var job = job();
    var chunkA = chunk(0, "chunk-a", "payload-a", "state-a", "2026-04-06T10:00:00Z");
    var chunkB = chunk(1, "chunk-b", "payload-b", "state-b", "2026-04-06T10:01:00Z");
    readGateway.snapshotPages = List.of(new HistoricalReadPage(List.of(), null, false));
    readGateway.deltaPagesByChunk.put(
        chunkB.chunkKey(),
        List.of(
            new HistoricalReadPage(
                List.of(
                    event("delta-object-first", "2026-04-06T10:01:20Z"),
                    event("delta-object-repeat", "2026-04-06T10:01:30Z")),
                null,
                true),
            new HistoricalReadPage(
                List.of(
                    event("delta-object-repeat", "2026-04-06T10:01:40Z"),
                    event("delta-object-last", "2026-04-06T10:01:50Z")),
                null,
                false)));

    var consumedObjectIds = new ArrayList<String>();
    Map<SourceObjectKey, HistoricalSourceEvent> stateByObject = new LinkedHashMap<>();
    readGateway.readStateDelta(
        HistoricalExecutionContext.from(job),
        chunkB,
        chunkA.to(),
        event -> {
          consumedObjectIds.add(event.objectId());
          stateByObject.put(new SourceObjectKey(event.sourceId(), event.objectId()), event);
        });

    assertEquals(
        List.of(
            "delta-object-first",
            "delta-object-repeat",
            "delta-object-repeat",
            "delta-object-last"),
        consumedObjectIds);
    assertEquals(
        1,
        readGateway.stateDeltaCalls,
        "The gateway should invoke both pages and stop after the last page");
    assertEquals(
        Set.of("delta-object-first", "delta-object-repeat", "delta-object-last"),
        stateByObject.keySet().stream().map(SourceObjectKey::objectId).collect(Collectors.toSet()));
    assertEquals(
        Instant.parse("2026-04-06T10:01:40Z"),
        stateByObject
            .get(new SourceObjectKey(TEST_SOURCE_ID, "delta-object-repeat"))
            .staleAt()
            .minusSeconds(60));
  }

  @Test
  void streaming_executor_replacesRepeatObjectRowsAndKeepsExpectedObjectIdsInChunkSample() {
    var job = job();
    var chunkA = chunk(0, "chunk-a", "payload-a", "state-a", "2026-04-06T10:00:00Z");
    var chunkB = chunk(1, "chunk-b", "payload-b", "state-b", "2026-04-06T10:01:00Z");
    var chunkC = chunk(2, "chunk-c", "payload-c", "state-c", "2026-04-06T10:02:00Z");
    var plan = plan(job, List.of(chunkA, chunkB, chunkC));

    readGateway.snapshotPages =
        List.of(
            new HistoricalReadPage(
                List.of(
                    event("repeat", "2026-04-06T10:00:30Z"),
                    event("stale", "2026-04-06T10:00:40Z")),
                null,
                false));
    readGateway.deltaPagesByChunk.put(
        "chunk-b",
        List.of(
            new HistoricalReadPage(
                List.of(
                    event("repeat", "2026-04-06T10:01:20Z"),
                    event("new-b", "2026-04-06T10:01:40Z")),
                null,
                false)));
    readGateway.deltaPagesByChunk.put(
        "chunk-c",
        List.of(
            new HistoricalReadPage(
                List.of(
                    event("repeat", "2026-04-06T10:02:20Z"),
                    event("new-c", "2026-04-06T10:02:30Z")),
                null,
                false)));

    var session = executor.newSession();

    assertEquals(
        HistoricalChunkExecutionResult.COMPUTED,
        session.executeChunk(HistoricalExecutionContext.from(job), plan, chunkA));
    assertEquals(
        HistoricalChunkExecutionResult.COMPUTED,
        session.executeChunk(HistoricalExecutionContext.from(job), plan, chunkB));
    assertEquals(
        HistoricalChunkExecutionResult.COMPUTED,
        session.executeChunk(HistoricalExecutionContext.from(job), plan, chunkC));

    var expectedObjectIds = Set.of("new-c", "new-b", "repeat");
    var actualObjectIds =
        payloadBuilder.lastSampledChunk.points().stream()
            .map(point -> point.objectId())
            .collect(Collectors.toSet());
    assertEquals(expectedObjectIds, actualObjectIds);
    assertEquals(
        Instant.parse("2026-04-06T10:02:20Z"),
        payloadBuilder.lastSampledChunk.points().stream()
            .filter(point -> "repeat".equals(point.objectId()))
            .findFirst()
            .orElseThrow()
            .playbackAt());
  }

  @Test
  void executor_wrapsInterruptedExceptionWhenThreadIsInterruptedBeforeDeltaCallback() {
    var job = job();
    var chunkA = chunk(0, "chunk-a", "payload-a", "state-a", "2026-04-06T10:00:00Z");
    var chunkB = chunk(1, "chunk-b", "payload-b", "state-b", "2026-04-06T10:01:00Z");
    var plan = plan(job, List.of(chunkA, chunkB));

    readGateway.snapshotPages =
        List.of(
            new HistoricalReadPage(
                List.of(event("snapshot", "2026-04-06T10:00:30Z")), null, false));
    readGateway.deltaPagesByChunk.put(
        "chunk-b",
        List.of(
            new HistoricalReadPage(List.of(event("delta", "2026-04-06T10:01:20Z")), null, false)));

    var session = executor.newSession();
    assertEquals(
        HistoricalChunkExecutionResult.COMPUTED,
        session.executeChunk(HistoricalExecutionContext.from(job), plan, chunkA));

    Thread.currentThread().interrupt();
    try {
      var thrown =
          assertThrows(
              IllegalStateException.class,
              () -> session.executeChunk(HistoricalExecutionContext.from(job), plan, chunkB));
      assertEquals(
          HistoricalChunkInterruption.interruptedMessage(), thrown.getCause().getMessage());
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  void executor_wrapsInterruptedExceptionWhenThreadIsInterruptedBeforeFirstSnapshotCallback() {
    var job = job();
    var chunkA = chunk(0, "chunk-a", "payload-a", "state-a", "2026-04-06T10:00:00Z");
    var plan = plan(job, List.of(chunkA));

    readGateway.snapshotPages =
        List.of(
            new HistoricalReadPage(
                List.of(
                    event("snapshot-first", "2026-04-06T10:00:30Z"),
                    event("snapshot-second", "2026-04-06T10:00:40Z")),
                null,
                false));

    try {
      Thread.currentThread().interrupt();

      IllegalStateException thrown =
          assertThrows(
              IllegalStateException.class,
              () ->
                  executor
                      .newSession()
                      .executeChunk(HistoricalExecutionContext.from(job), plan, chunkA));
      assertEquals(
          HistoricalChunkInterruption.interruptedMessage(), thrown.getCause().getMessage());
      assertEquals(1, readGateway.initialStateCalls);
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  void session_executeChunk_readsLaneSnapshotThenLaneDeltaAndPersistsLanePayload() {
    var laneMetadata = laneMetadata();
    var chunkA = chunk(0, "lane-chunk-a", "lane-payload-a", "lane-state-a", "2026-04-06T10:00:00Z");
    var chunkB = chunk(1, "lane-chunk-b", "lane-payload-b", "lane-state-b", "2026-04-06T10:01:00Z");
    var plan =
        HistoricalChunkPlan.forSubject(laneMetadata, laneWindowPlan(List.of(chunkA, chunkB)));
    readGateway.snapshotPages =
        List.of(
            new HistoricalReadPage(List.of(event("obj-1", "2026-04-06T10:00:30Z")), null, false));
    readGateway.deltaPagesByChunk.put(
        chunkB.chunkKey(),
        List.of(
            new HistoricalReadPage(List.of(event("obj-2", "2026-04-06T10:01:20Z")), null, false)));

    var session = executor.newSession();

    assertEquals(
        HistoricalChunkExecutionResult.COMPUTED,
        session.executeChunk(HistoricalExecutionContext.from(laneMetadata), plan, chunkA));
    assertEquals(
        HistoricalChunkExecutionResult.COMPUTED,
        session.executeChunk(HistoricalExecutionContext.from(laneMetadata), plan, chunkB));

    assertEquals(1, readGateway.initialStateCalls);
    assertEquals(1, readGateway.stateDeltaCalls);
    assertEquals(List.of(chunkA.to()), readGateway.deltaFromValues);
    assertEquals(2, payloadPersistence.persistCalls);
    assertEquals(HistoricalExecutionContext.from(laneMetadata), payloadPersistence.lastContext);
    assertEquals(2, chunkReadyNotifier.notificationCalls);
  }

  @Test
  void session_afterCachedChunk_nextMissingChunkUsesReadInitialState() {
    var laneMetadata = laneMetadata();
    var firstMissing =
        chunk(0, "lane-chunk-0", "lane-payload-0", "lane-state-0", "2026-04-06T10:00:00Z");
    var cached =
        cachedChunk(1, "lane-chunk-1", "lane-payload-1", "lane-state-1", "2026-04-06T10:01:00Z");
    var resumedMissing =
        chunk(2, "lane-chunk-2", "lane-payload-2", "lane-state-2", "2026-04-06T10:02:00Z");
    var plan =
        HistoricalChunkPlan.forSubject(
            laneMetadata, laneWindowPlan(List.of(firstMissing, cached, resumedMissing)));
    readGateway.snapshotPages =
        List.of(
            new HistoricalReadPage(List.of(event("obj-1", "2026-04-06T10:00:30Z")), null, false),
            new HistoricalReadPage(List.of(event("obj-2", "2026-04-06T10:02:30Z")), null, false));

    var session = executor.newSession();
    var ctx = HistoricalExecutionContext.from(laneMetadata);
    assertEquals(
        HistoricalChunkExecutionResult.COMPUTED, session.executeChunk(ctx, plan, firstMissing));
    assertEquals(
        HistoricalChunkExecutionResult.REUSED_FROM_CACHE, session.executeChunk(ctx, plan, cached));
    assertEquals(
        HistoricalChunkExecutionResult.COMPUTED, session.executeChunk(ctx, plan, resumedMissing));

    assertEquals(2, readGateway.initialStateCalls);
    assertEquals(0, readGateway.stateDeltaCalls);
  }

  private static HistoricalChunkPlanEntry chunk(
      int index, String chunkKey, String payloadKey, String stateKey, String from) {
    Instant chunkFrom = Instant.parse(from);
    return new HistoricalChunkPlanEntry(
        index,
        chunkKey,
        payloadKey,
        stateKey,
        HistoricalChunkCacheStatus.MISSING,
        chunkFrom,
        chunkFrom.plusSeconds(60));
  }

  private static HistoricalChunkPlanEntry cachedChunk(
      int index, String chunkKey, String payloadKey, String stateKey, String from) {
    Instant chunkFrom = Instant.parse(from);
    return new HistoricalChunkPlanEntry(
        index,
        chunkKey,
        payloadKey,
        stateKey,
        HistoricalChunkCacheStatus.CACHED,
        chunkFrom,
        chunkFrom.plusSeconds(60));
  }

  private static List<HistoricalChunkPlanEntry> adjacentMissingChunks(int count, Instant start) {
    var chunks = new ArrayList<HistoricalChunkPlanEntry>();
    for (int index = 0; index < count; index++) {
      Instant chunkFrom = start.plusSeconds(index * 60L);
      chunks.add(
          new HistoricalChunkPlanEntry(
              index,
              "chunk-" + index,
              "payload-" + index,
              "state-" + index,
              HistoricalChunkCacheStatus.MISSING,
              chunkFrom,
              chunkFrom.plusSeconds(60)));
    }
    return chunks;
  }

  private static List<Instant> previousChunkEnds(List<HistoricalChunkPlanEntry> chunks) {
    var previousEnds = new ArrayList<Instant>();
    for (int index = 1; index < chunks.size(); index++) {
      previousEnds.add(chunks.get(index - 1).to());
    }
    return previousEnds;
  }

  private static HistoricalTracksJobSubmission job() {
    return HistoricalTracksJobSubmission.create(
        UUID.randomUUID(),
        JobIssuer.user(UUID.fromString("550e8400-e29b-41d4-a716-446655440120")),
        SubjectType.SOURCE,
        UUID.fromString("550e8400-e29b-41d4-a716-446655440121"),
        UUID.fromString("550e8400-e29b-41d4-a716-446655440120"),
        Instant.parse("2026-04-07T10:00:00Z"),
        Instant.parse("2026-04-06T10:00:00Z"),
        Instant.parse("2026-04-06T10:03:00Z"),
        Instant.parse("2026-04-06T10:00:00Z"),
        Instant.parse("2026-04-06T10:03:00Z"),
        HistoricalResolution.MINUTE_1,
        3,
        List.of());
  }

  private static HistoricalChunkPlan plan(
      HistoricalTracksJobSubmission job, List<HistoricalChunkPlanEntry> entries) {
    return HistoricalChunkPlan.forSubject(laneMetadata(job, entries), laneWindowPlan(entries));
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
        Instant.parse("2026-04-06T10:00:00Z"),
        Instant.parse("2026-04-06T10:03:00Z"),
        effectiveFrom,
        effectiveFrom.plusSeconds(120),
        HistoricalResolution.MINUTE_1,
        2,
        List.of(),
        SubjectType.SOURCE,
        UUID.fromString("550e8400-e29b-41d4-a716-446655440204"),
        new HistoricalChunkWindowPlan(
            effectiveFrom,
            effectiveFrom.plusSeconds(120),
            2,
            HistoricalResolution.MINUTE_1,
            List.of()));
  }

  private static HistoricalSubjectExecutionMeta laneMetadata(
      HistoricalTracksJobSubmission job, List<HistoricalChunkPlanEntry> entries) {
    var subject = job.subjects().getFirst();
    return new HistoricalSubjectExecutionMeta(
        subject.jobId(),
        job.jobId(),
        job.requestId(),
        job.issuer(),
        job.requestedBy(),
        job.requestedAt(),
        job.requestedFrom(),
        job.requestedTo(),
        job.effectiveFrom(),
        job.effectiveTo(),
        job.resolution(),
        job.plannedChunks(),
        job.filters(),
        subject.subjectType(),
        subject.subjectId(),
        laneWindowPlan(entries));
  }

  private static HistoricalChunkWindowPlan laneWindowPlan(List<HistoricalChunkPlanEntry> entries) {
    return new HistoricalChunkWindowPlan(
        entries.getFirst().from(),
        entries.getLast().to(),
        entries.size(),
        HistoricalResolution.MINUTE_1,
        entries);
  }

  private static final UUID TEST_SOURCE_ID =
      UUID.fromString("550e8400-e29b-41d4-a716-4466554400aa");

  private static HistoricalSourceEvent event(String objectId, String playbackAt) {
    return event(objectId, playbackAt, Instant.parse(playbackAt).plusSeconds(60).toString());
  }

  private static HistoricalSourceEvent event(String objectId, String playbackAt, String staleAt) {
    Instant playback = Instant.parse(playbackAt);
    Instant stale = Instant.parse(staleAt);
    return new HistoricalSourceEvent(
        UUID.randomUUID(),
        TEST_SOURCE_ID,
        objectId,
        playback,
        playback,
        playback,
        stale,
        """
        {
          "type": "Feature",
          "geometry": { "type": "Point", "coordinates": [-73.5, 45.5] },
          "properties": {
            "opencop": {
              "id": "550e8400-e29b-41d4-a716-446655440001",
              "objectId": "%s",
              "source": "550e8400-e29b-41d4-a716-4466554400aa",
              "displayName": "Object %s",
              "timestampz": { "time": %s, "start": %s, "stale": %s },
              "element": { "type": "a-f-G-U-C", "speed": 12.5, "remarks": "alpha" }
            }
          }
        }
        """
            .formatted(
                objectId,
                objectId,
                playback.toEpochMilli(),
                playback.toEpochMilli(),
                stale.toEpochMilli()));
  }

  private record NotificationRecord(String chunkKey, boolean cached, int partCount) {}

  private static final class RecordingReadGateway implements HistoricalChunkStateReader {
    private RuntimeException failure;
    private int snapshotCalls;
    private int stateDeltaCalls;
    private int initialStateCalls;
    private List<HistoricalReadPage> snapshotPages = List.of();
    private final Map<String, List<HistoricalReadPage>> deltaPagesByChunk = new HashMap<>();
    private final List<Instant> deltaFromValues = new ArrayList<>();

    @Override
    public void readInitialState(
        HistoricalExecutionContext executionContext,
        HistoricalChunkPlanEntry chunk,
        Consumer<HistoricalSourceEvent> rowConsumer) {
      initialStateCalls++;
      if (failure != null) {
        throw failure;
      }
      HistoricalReadPage page = snapshotPages.get(snapshotCalls++);
      page.rows().forEach(rowConsumer);
    }

    @Override
    public void readStateDelta(
        HistoricalExecutionContext executionContext,
        HistoricalChunkPlanEntry chunk,
        Instant previousChunkEndExclusive,
        Consumer<HistoricalSourceEvent> rowConsumer) {
      stateDeltaCalls++;
      deltaFromValues.add(previousChunkEndExclusive);
      List<HistoricalReadPage> pages = deltaPagesByChunk.getOrDefault(chunk.chunkKey(), List.of());
      for (HistoricalReadPage page : pages) {
        page.rows().forEach(rowConsumer);
      }
    }
  }

  private static final class RecordingPayloadBuilder extends HistoricalChunkPayloadBuilder {
    private int buildCalls;
    private HistoricalChunkSampledResult lastSampledChunk;

    private RecordingPayloadBuilder() {
      super(
          new tools.jackson.databind.ObjectMapper(),
          new HistoricalCacheIdentity(new tools.jackson.databind.ObjectMapper()),
          131_072,
          524_288);
    }

    @Override
    public HistoricalChunkPayloadBuildResult buildPayload(
        HistoricalChunkSampledResult sampledChunk, String basePayloadKey) {
      buildCalls++;
      lastSampledChunk = sampledChunk;
      return super.buildPayload(sampledChunk, basePayloadKey);
    }
  }

  private static final class RecordingPayloadPersistence
      implements HistoricalChunkPersistenceGateway {
    private int persistCalls;
    private HistoricalExecutionContext lastContext;

    @Override
    public void persistComputedChunk(
        HistoricalExecutionContext executionContext,
        HistoricalChunkPlanEntry chunk,
        HistoricalChunkPayloadBuildResult payload) {
      persistCalls++;
      lastContext = executionContext;
    }

    @Override
    public void markPayloadMissingForRecovery(
        HistoricalChunkPlanEntry chunk, boolean recoveryPossible) {}
  }

  private static final class NoopPayloadCacheGateway implements HistoricalChunkPayloadCacheGateway {

    @Override
    public Map<String, Optional<Duration>> remainingPayloadTtl(Set<String> payloadKeys) {
      return Map.of();
    }

    @Override
    public void put(String payloadKey, byte[] payloadBytes, Duration ttl) {}

    @Override
    public Optional<byte[]> find(String payloadKey) {
      return Optional.empty();
    }

    @Override
    public Map<String, Optional<byte[]>> findAll(List<String> payloadKeys) {
      return Map.of();
    }
  }

  private static final class RecordingChunkStateGateway implements HistoricalChunkStateGateway {
    private final List<HistoricalChunkStateStatus> transitions = new ArrayList<>();

    @Override
    public void initialize(HistoricalChunkPlan plan) {}

    @Override
    public Optional<HistoricalChunkState> findByChunkStateKey(String chunkStateKey) {
      return Optional.empty();
    }

    @Override
    public void transition(String chunkStateKey, HistoricalChunkStateStatus status) {
      transitions.add(status);
    }

    @Override
    public void updatePartCount(String chunkStateKey, int partCount) {}
  }

  private static final class NoopJobStateGateway implements HistoricalJobStateGateway {

    @Override
    public void createReceived(HistoricalTracksJobSubmission job) {}

    @Override
    public boolean markQueued(UUID jobId, String queueRole) {
      return true;
    }

    @Override
    public boolean markRunning(UUID jobId, String workerId) {
      return true;
    }

    @Override
    public boolean markPlanning(UUID jobId) {
      return true;
    }

    @Override
    public boolean markPlanned(UUID jobId) {
      return true;
    }

    @Override
    public boolean markCompleted(UUID jobId) {
      return true;
    }

    @Override
    public boolean markDeliveredCompleted(UUID jobId) {
      return true;
    }

    @Override
    public boolean markDeliveryPending(UUID jobId) {
      return true;
    }

    @Override
    public boolean markDeliveryFailed(UUID jobId, String failureReason) {
      return true;
    }

    @Override
    public boolean markFailed(UUID jobId, HistoricalJobStage stage, String failureReason) {
      return true;
    }

    @Override
    public boolean markCancellationRequested(UUID jobId, String reason) {
      return true;
    }

    @Override
    public boolean markCancelled(UUID jobId, String reason) {
      return true;
    }

    @Override
    public boolean refreshActiveTtl(UUID jobId) {
      return true;
    }

    @Override
    public Optional<HistoricalJobState> findByJobId(UUID jobId) {
      return Optional.empty();
    }
  }

  private static final class RecordingChunkReadyNotifier implements HistoricalChunkReadyNotifier {
    private final List<NotificationRecord> notifications = new ArrayList<>();
    private int notificationCalls;

    @Override
    public void notifyChunkReady(
        HistoricalExecutionContext executionContext,
        HistoricalChunkPlanEntry chunk,
        boolean cached,
        int partCount) {
      notificationCalls++;
      notifications.add(new NotificationRecord(chunk.chunkKey(), cached, partCount));
    }
  }
}
