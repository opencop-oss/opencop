// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.history.model.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.opencop.core.sdk.model.SubjectType;
import com.opencop.core.sdk.model.history.HistoricalFilterRequest;
import com.opencop.core.sdk.model.history.HistoricalJobStage;
import com.opencop.core.sdk.model.history.HistoricalResolution;
import com.opencop.core.sdk.model.job.HistoricalTracksJobSubmission;
import com.opencop.core.sdk.model.job.HistoricalTracksSubject;
import com.opencop.core.sdk.model.job.JobIssuer;
import com.opencop.processor.business.model.history.HistoricalSubjectExecutionMeta;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkWindowPlan;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HistoricalJobStateTest {

  private static final Instant NOW = Instant.parse("2026-04-07T12:00:00Z");
  private static final Duration TTL = Duration.ofHours(24);

  @Test
  void received_buildsInitialStateFromSubmission() {
    var job = job();

    HistoricalJobState state = HistoricalJobState.received(job, NOW, TTL);

    assertEquals(job.jobId(), state.jobId());
    assertEquals(job.requestId(), state.requestId());
    assertEquals("HISTORICAL_TRACKS", state.jobType());
    assertEquals(job.subjects().getFirst().subjectType(), state.subjectType());
    assertEquals(job.subjects().getFirst().subjectId(), state.subjectId());
    assertEquals(job.requestedBy(), state.requestedBy());
    assertEquals(job.plannedChunks(), state.plannedChunks());
    assertEquals(HistoricalJobStatus.RECEIVED, state.status());
    assertEquals(HistoricalJobStage.INGRESS_RECEIVED, state.stage());
    assertEquals(NOW, state.updatedAt());
    assertEquals(NOW.plus(TTL), state.expiresAt());
    assertEquals("", state.queueRole());
    assertFalse(state.isTerminal());
  }

  @Test
  void received_buildsInitialStateFromMetadataWithStablePayloadFields() {
    var laneMetadata = laneMetadata();

    HistoricalJobState state = HistoricalJobState.received(laneMetadata, NOW, TTL);

    assertEquals(laneMetadata.laneJobId(), state.jobId());
    assertEquals(laneMetadata.requestId(), state.requestId());
    assertEquals("HISTORICAL_TRACKS", state.jobType());
    assertEquals(laneMetadata.subjectType(), state.subjectType());
    assertEquals(laneMetadata.subjectId(), state.subjectId());
    assertEquals(laneMetadata.requestedBy(), state.requestedBy());
    assertEquals(laneMetadata.plannedChunks(), state.plannedChunks());
    assertEquals(HistoricalJobStatus.RECEIVED, state.status());
    assertEquals(HistoricalJobStage.INGRESS_RECEIVED, state.stage());
    assertEquals(NOW, state.updatedAt());
    assertEquals(NOW.plus(TTL), state.expiresAt());
    assertEquals("", state.queueRole());
    assertFalse(state.isTerminal());
  }

  @Test
  void received_whenMixedSubjectOrderChanges_usesStableAggregateSubjectIdentity() {
    var firstOrder = mixedJob(true);
    var reversedOrder = mixedJob(false);

    HistoricalJobState firstState = HistoricalJobState.received(firstOrder, NOW, TTL);
    HistoricalJobState reversedState = HistoricalJobState.received(reversedOrder, NOW, TTL);

    assertEquals(firstState.subjectType(), reversedState.subjectType());
    assertEquals(firstState.subjectId(), reversedState.subjectId());
    assertTrue(
        firstOrder.subjects().stream()
            .noneMatch(subject -> subject.subjectId().equals(firstState.subjectId())));
  }

  @Test
  void lifecycleTransitionsUpdateStatusStageTimestampsAndQueueRole() {
    var received = HistoricalJobState.received(job(), NOW, TTL);
    var queued = received.queued(NOW.plusSeconds(1), TTL);
    var running = queued.running(NOW.plusSeconds(2), TTL, "worker-1");
    var planning = running.planning(NOW.plusSeconds(3), TTL);
    var planned = planning.planned(NOW.plusSeconds(4), TTL);
    var deliveryPending = planned.deliveryPending(NOW.plusSeconds(5), TTL);
    var completed = deliveryPending.completed(NOW.plusSeconds(6), TTL);

    assertEquals(HistoricalJobStatus.QUEUED, queued.status());
    assertEquals(HistoricalJobStage.INGRESS_QUEUED, queued.stage());
    assertEquals(HistoricalJobState.QUEUE_ROLE_DISPATCH_READY, queued.queueRole());
    assertEquals(HistoricalJobStatus.RUNNING, running.status());
    assertEquals(HistoricalJobStage.EXECUTION_RUNNING, running.stage());
    assertEquals("worker-1", running.workerId());
    assertEquals(NOW.plusSeconds(2), running.startedAt());
    assertEquals("", running.queueRole());
    assertEquals(HistoricalJobStage.PLANNING, planning.stage());
    assertEquals(HistoricalJobStage.PLANNED, planned.stage());
    assertEquals(HistoricalJobStage.DELIVERY_PENDING, deliveryPending.stage());
    assertEquals(HistoricalJobStatus.COMPLETED, completed.status());
    assertEquals(HistoricalJobStage.EXECUTION_COMPLETED, completed.stage());
    assertTrue(completed.isTerminal());
  }

  @Test
  void failedCancellationRequestedAndCancelledTransitionsPreserveExpectedFields() {
    var running =
        HistoricalJobState.received(job(), NOW, TTL).queued(NOW, TTL).running(NOW, TTL, "worker-1");

    var failed =
        running.failed(
            NOW.plusSeconds(1), TTL, HistoricalJobStage.EXECUTION_FAILED_STORAGE, "storage down");
    var cancellationRequested =
        running.cancellationRequested(NOW.plusSeconds(2), TTL, "replacement accepted");
    var cancelled = cancellationRequested.cancelled(NOW.plusSeconds(3), "superseded");

    assertEquals(HistoricalJobStatus.FAILED, failed.status());
    assertEquals(HistoricalJobStage.EXECUTION_FAILED_STORAGE, failed.stage());
    assertEquals("storage down", failed.failureReason());
    assertEquals("worker-1", failed.workerId());
    assertTrue(failed.isTerminal());
    assertEquals(HistoricalJobStatus.RUNNING, cancellationRequested.status());
    assertEquals(HistoricalJobStage.CANCELLATION_REQUESTED, cancellationRequested.stage());
    assertEquals("replacement accepted", cancellationRequested.failureReason());
    assertFalse(cancellationRequested.isTerminal());
    assertEquals(HistoricalJobStatus.CANCELLED, cancelled.status());
    assertEquals(HistoricalJobStage.CANCELLED, cancelled.stage());
    assertEquals("superseded", cancelled.failureReason());
    assertTrue(cancelled.isTerminal());
  }

  @Test
  void queuedRejectsUnsupportedQueueRole() {
    var state = HistoricalJobState.received(job(), NOW, TTL);

    IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class, () -> state.queued(NOW, TTL, "BAD_ROLE"));

    assertEquals("Unsupported queue role: BAD_ROLE", thrown.getMessage());
  }

  @Test
  void redisHashRoundTripPreservesState() {
    var state =
        HistoricalJobState.received(job(), NOW, TTL)
            .queued(NOW.plusSeconds(1), TTL, HistoricalJobState.QUEUE_ROLE_QUEUED_REPLACEMENT);

    HistoricalJobState restored = HistoricalJobState.fromRedisHash(redisHash(state.toRedisHash()));

    assertEquals(state, restored);
  }

  @Test
  void redisHashReadDefaultsLegacyOptionalFields() {
    var state = HistoricalJobState.received(job(), NOW, TTL).queued(NOW, TTL);
    Map<Object, Object> hash = redisHash(state.toRedisHash());
    hash.remove("queueRole");
    hash.remove("userSequence");
    hash.remove("requestSequence");

    HistoricalJobState restored = HistoricalJobState.fromRedisHash(hash);

    assertEquals("", restored.queueRole());
    assertEquals(0L, restored.userSequence());
    assertEquals(0L, restored.requestSequence());
  }

  @Test
  void redisHashReadConvertsBlankNullableFieldsToNull() {
    var state = HistoricalJobState.received(job(), NOW, TTL);

    HistoricalJobState restored = HistoricalJobState.fromRedisHash(redisHash(state.toRedisHash()));

    assertNull(restored.startedAt());
    assertNull(restored.failureReason());
    assertNull(restored.workerId());
  }

  @Test
  void redisHashReadFallsBackForUnknownPersistedStage() {
    var state = HistoricalJobState.received(job(), NOW, TTL);
    Map<Object, Object> hash = redisHash(state.toRedisHash());
    hash.put("stage", "EXECUTION_FAILED");

    HistoricalJobState restored = HistoricalJobState.fromRedisHash(hash);

    assertEquals(HistoricalJobStage.EXECUTION_FAILED_UNKNOWN, restored.stage());
  }

  @Test
  void redisHashReadRejectsMissingRequiredField() {
    var state = HistoricalJobState.received(job(), NOW, TTL);
    Map<Object, Object> hash = redisHash(state.toRedisHash());
    hash.remove("jobId");

    IllegalStateException thrown =
        assertThrows(IllegalStateException.class, () -> HistoricalJobState.fromRedisHash(hash));

    assertEquals("Missing historical job field jobId", thrown.getMessage());
  }

  private static Map<Object, Object> redisHash(Map<String, String> hash) {
    return new HashMap<>(hash);
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

  private static HistoricalSubjectExecutionMeta laneMetadata() {
    return new HistoricalSubjectExecutionMeta(
        UUID.fromString("550e8400-e29b-41d4-a716-446655440221"),
        UUID.fromString("550e8400-e29b-41d4-a716-446655440222"),
        UUID.fromString("550e8400-e29b-41d4-a716-446655440223"),
        JobIssuer.user(UUID.fromString("550e8400-e29b-41d4-a716-446655440120")),
        UUID.fromString("550e8400-e29b-41d4-a716-446655440120"),
        Instant.parse("2026-04-07T10:00:00Z"),
        Instant.parse("2026-04-06T10:02:17Z"),
        Instant.parse("2026-04-06T12:13:02Z"),
        Instant.parse("2026-04-06T10:02:00Z"),
        Instant.parse("2026-04-06T12:14:00Z"),
        HistoricalResolution.MINUTE_1,
        132,
        List.of(new HistoricalFilterRequest("source", Map.of("include", "all"))),
        SubjectType.SOURCE,
        UUID.fromString("550e8400-e29b-41d4-a716-446655440121"),
        new HistoricalChunkWindowPlan(
            Instant.parse("2026-04-06T10:02:00Z"),
            Instant.parse("2026-04-06T12:14:00Z"),
            132,
            HistoricalResolution.MINUTE_1,
            List.of()));
  }

  private static HistoricalTracksJobSubmission mixedJob(boolean sourceFirst) {
    var source =
        new HistoricalTracksSubject(
            UUID.fromString("550e8400-e29b-41d4-a716-446655440211"),
            SubjectType.SOURCE,
            UUID.fromString("550e8400-e29b-41d4-a716-446655440212"));
    var layer =
        new HistoricalTracksSubject(
            UUID.fromString("550e8400-e29b-41d4-a716-446655440213"),
            SubjectType.LAYER,
            UUID.fromString("550e8400-e29b-41d4-a716-446655440214"));
    return HistoricalTracksJobSubmission.create(
        UUID.fromString("550e8400-e29b-41d4-a716-446655440215"),
        JobIssuer.user(UUID.fromString("550e8400-e29b-41d4-a716-446655440120")),
        sourceFirst ? List.of(source, layer) : List.of(layer, source),
        UUID.fromString("550e8400-e29b-41d4-a716-446655440120"),
        Instant.parse("2026-04-07T10:00:00Z"),
        Instant.parse("2026-04-06T10:02:17Z"),
        Instant.parse("2026-04-06T12:13:02Z"),
        Instant.parse("2026-04-06T10:02:00Z"),
        Instant.parse("2026-04-06T12:14:00Z"),
        HistoricalResolution.MINUTE_1,
        132,
        List.of(new HistoricalFilterRequest("source", Map.of("include", "all"))));
  }
}
