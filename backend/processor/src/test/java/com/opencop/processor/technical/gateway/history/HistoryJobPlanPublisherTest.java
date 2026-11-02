// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.opencop.core.sdk.model.SubjectType;
import com.opencop.core.sdk.model.history.HistoricalFilterRequest;
import com.opencop.core.sdk.model.history.HistoricalJobStage;
import com.opencop.core.sdk.model.history.HistoricalResolution;
import com.opencop.core.sdk.model.history.HistoryEvent;
import com.opencop.core.sdk.model.history.HistoryEventType;
import com.opencop.core.sdk.model.history.HistoryJobCompletedEvent;
import com.opencop.core.sdk.model.history.HistoryJobFailedEvent;
import com.opencop.core.sdk.model.history.HistoryJobPlanEvent;
import com.opencop.core.sdk.model.history.HistoryJobProgressEvent;
import com.opencop.core.sdk.model.job.HistoricalTracksJobSubmission;
import com.opencop.core.sdk.model.job.HistoricalTracksSubject;
import com.opencop.core.sdk.model.job.JobIssuer;
import com.opencop.core.sdk.ports.gateway.cache.CachePublisher;
import com.opencop.processor.business.model.history.HistoricalExecutionContext;
import com.opencop.processor.business.model.history.HistoricalSubjectExecutionMeta;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkCacheStatus;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkPlan;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkPlanEntry;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkWindowPlan;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HistoryJobPlanPublisherTest {

  private final RecordingPublisher historyEventsPublisher = new RecordingPublisher();
  private final HistoryJobPlanPublisher publisher =
      new HistoryJobPlanPublisher(historyEventsPublisher);

  @Test
  void publish_whenIssuerIsUser_publishesPlanningEvent() {
    var job = job(JobIssuer.user(UUID.fromString("550e8400-e29b-41d4-a716-446655446099")));
    var plan = plan(job);

    publisher.publishPlan(plan, HistoricalExecutionContext.from(job));

    HistoryJobPlanEvent event =
        assertInstanceOf(HistoryJobPlanEvent.class, historyEventsPublisher.event);
    assertEquals(HistoryEventType.HISTORY_JOB_PLAN, event.type());
    assertEquals(job.requestedBy(), event.targetUserId());
    assertEquals(job.jobId(), event.jobId());
    assertEquals(job.requestId(), event.requestId());
    assertEquals(job.resolution(), event.resolution());
    assertEquals(plan.chunks(), event.chunks());
    assertEquals(plan.cachedChunks(), event.cachedChunks());
    assertEquals(plan.chunksToCompute(), event.chunksToCompute());
  }

  @Test
  void publishPlan_whenIssuerIsUser_publishesPlanningEventUsingMetadataIdentity() {
    var laneMetadata =
        laneMetadata(JobIssuer.user(UUID.fromString("550e8400-e29b-41d4-a716-446655446099")));
    var plan = plan(laneMetadata, 2, 1);

    publisher.publishPlan(plan, HistoricalExecutionContext.from(laneMetadata));

    HistoryJobPlanEvent event =
        assertInstanceOf(HistoryJobPlanEvent.class, historyEventsPublisher.event);
    assertEquals(HistoryEventType.HISTORY_JOB_PLAN, event.type());
    assertEquals(laneMetadata.requestedBy(), event.targetUserId());
    assertEquals(laneMetadata.laneJobId(), event.jobId());
    assertEquals(laneMetadata.requestId(), event.requestId());
    assertEquals(laneMetadata.subjectType(), event.subjectType());
    assertEquals(laneMetadata.subjectId(), event.subjectId());
    assertEquals(laneMetadata.resolution(), event.resolution());
    assertEquals(plan.chunks(), event.chunks());
    assertEquals(plan.cachedChunks(), event.cachedChunks());
    assertEquals(plan.chunksToCompute(), event.chunksToCompute());
  }

  @Test
  void publishExecutionProgress_whenIssuerIsUser_publishesComputeProgressAsProgressEvent() {
    var job = job(JobIssuer.user(UUID.fromString("550e8400-e29b-41d4-a716-446655446099")));
    var plan = plan(job, 2, 0);

    publisher.publishExecutionProgress(plan, HistoricalExecutionContext.from(job), 1, 0);

    var progress = assertInstanceOf(HistoryJobProgressEvent.class, historyEventsPublisher.event);
    assertEquals(HistoryEventType.HISTORY_JOB_PROGRESS, progress.type());
    assertEquals(1, progress.computedChunks());
    assertEquals(0, progress.deliveredChunks());
    assertEquals(0, progress.failedChunks());
    assertEquals(2, progress.remainingChunks());
    assertEquals(0, progress.percentComplete());
  }

  @Test
  void publishExecutionProgress_whenIssuerIsSystem_doesNotPublishProgress() {
    var userJob = job(JobIssuer.user(UUID.fromString("550e8400-e29b-41d4-a716-446655446099")));
    var systemJob = job(JobIssuer.system("history-test"));
    var userPublisher = new RecordingPublisher();
    var systemPublisher = new RecordingPublisher();

    var userProgressPublisher = new HistoryJobPlanPublisher(userPublisher);
    var systemProgressPublisher = new HistoryJobPlanPublisher(systemPublisher);

    userProgressPublisher.publishExecutionProgress(
        plan(userJob, 2, 0), HistoricalExecutionContext.from(userJob), 1, 0);
    assertInstanceOf(HistoryJobProgressEvent.class, userPublisher.event);

    systemProgressPublisher.publishExecutionProgress(
        plan(systemJob, 2, 0), HistoricalExecutionContext.from(systemJob), 1, 0);
    assertNull(systemPublisher.event);
  }

  @Test
  void publishProgressCompletedAndFailed_whenIssuerIsUser_publishLifecycleEvents() {
    var job = job(JobIssuer.user(UUID.fromString("550e8400-e29b-41d4-a716-446655446099")));
    var plan = plan(job);

    publisher.publishProgress(plan, job, 1, 1, 0);
    var progress = assertInstanceOf(HistoryJobProgressEvent.class, historyEventsPublisher.event);
    assertEquals(HistoryEventType.HISTORY_JOB_PROGRESS, progress.type());
    assertEquals(1, progress.computedChunks());
    assertEquals(1, progress.deliveredChunks());
    assertEquals(0, progress.failedChunks());
    assertEquals(0, progress.remainingChunks());
    assertEquals(100, progress.percentComplete());

    publisher.publishCompleted(plan, job, 1, 1, 0);
    var completed = assertInstanceOf(HistoryJobCompletedEvent.class, historyEventsPublisher.event);
    assertEquals(HistoryEventType.HISTORY_JOB_COMPLETED, completed.type());
    assertEquals(1, completed.deliveredChunks());

    publisher.publishFailed(job, HistoricalJobStage.EXECUTION_FAILED_UNKNOWN, "boom");
    var failed = assertInstanceOf(HistoryJobFailedEvent.class, historyEventsPublisher.event);
    assertEquals(HistoryEventType.HISTORY_JOB_FAILED, failed.type());
    assertEquals(HistoricalJobStage.EXECUTION_FAILED_UNKNOWN.name(), failed.stage());
    assertEquals("boom", failed.reason());
  }

  @Test
  void publishFailed_whenMixedSubjectOrderChanges_usesStableAggregateSubjectIdentity() {
    var firstOrder = mixedJob(true);
    var reversedOrder = mixedJob(false);

    publisher.publishFailed(firstOrder, HistoricalJobStage.EXECUTION_FAILED_UNKNOWN, "boom");
    var firstEvent = assertInstanceOf(HistoryJobFailedEvent.class, historyEventsPublisher.event);

    publisher.publishFailed(reversedOrder, HistoricalJobStage.EXECUTION_FAILED_UNKNOWN, "boom");
    var reversedEvent = assertInstanceOf(HistoryJobFailedEvent.class, historyEventsPublisher.event);

    assertEquals(firstEvent.subjectType(), reversedEvent.subjectType());
    assertEquals(firstEvent.subjectId(), reversedEvent.subjectId());
    assertEquals(firstOrder.requestId(), firstEvent.requestId());
    assertEquals(reversedOrder.requestId(), reversedEvent.requestId());
    assertEquals(firstOrder.jobId(), firstEvent.jobId());
    assertEquals(reversedOrder.jobId(), reversedEvent.jobId());
    assertTrue(
        firstOrder.subjects().stream()
            .noneMatch(subject -> subject.subjectId().equals(firstEvent.subjectId())));
  }

  @Test
  void publish_whenIssuerIsSystem_skipsHistoryEvent() {
    var job = job(JobIssuer.system("history-test"));
    var plan = plan(job);

    publisher.publishPlan(plan, HistoricalExecutionContext.from(job));

    assertNull(historyEventsPublisher.event);
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
            UUID.fromString("550e8400-e29b-41d4-a716-446655446111"),
            SubjectType.SOURCE,
            UUID.fromString("550e8400-e29b-41d4-a716-446655446011"));
    var layer =
        new HistoricalTracksSubject(
            UUID.fromString("550e8400-e29b-41d4-a716-446655446112"),
            SubjectType.LAYER,
            UUID.fromString("550e8400-e29b-41d4-a716-446655446012"));
    return HistoricalTracksJobSubmission.create(
        UUID.fromString("550e8400-e29b-41d4-a716-446655446010"),
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

  private static HistoricalChunkPlan plan(HistoricalTracksJobSubmission job) {
    return plan(job, 1, 1);
  }

  private static HistoricalChunkPlan plan(
      HistoricalSubjectExecutionMeta laneMetadata, int chunks, int cachedChunks) {
    var entries =
        chunks == 1
            ? List.of(chunk(0, "10:00:00Z", "11:00:00Z", cacheStatus(cachedChunks, 0)))
            : List.of(
                chunk(0, "10:00:00Z", "11:00:00Z", cacheStatus(cachedChunks, 0)),
                chunk(1, "11:00:00Z", "12:00:00Z", cacheStatus(cachedChunks, 1)));

    return new HistoricalChunkPlan(
        HistoricalChunkPlan.PLANNING_VERSION,
        laneMetadata.requestId(),
        laneMetadata.laneJobId(),
        laneMetadata.subjectType(),
        laneMetadata.subjectId(),
        laneMetadata.requestedBy(),
        laneMetadata.requestedFrom(),
        laneMetadata.requestedTo(),
        laneMetadata.effectiveFrom(),
        laneMetadata.effectiveTo(),
        laneMetadata.resolution(),
        laneMetadata.requestedAt(),
        chunks,
        cachedChunks,
        0,
        entries);
  }

  private static HistoricalChunkPlan plan(
      HistoricalTracksJobSubmission job, int chunks, int cachedChunks) {
    var entries =
        chunks == 1
            ? List.of(chunk(0, "10:00:00Z", "11:00:00Z", cacheStatus(cachedChunks, 0)))
            : List.of(
                chunk(0, "10:00:00Z", "11:00:00Z", cacheStatus(cachedChunks, 0)),
                chunk(1, "11:00:00Z", "12:00:00Z", cacheStatus(cachedChunks, 1)));

    return new HistoricalChunkPlan(
        HistoricalChunkPlan.PLANNING_VERSION,
        job.requestId(),
        job.jobId(),
        job.subjects().getFirst().subjectType(),
        job.subjects().getFirst().subjectId(),
        job.requestedBy(),
        job.requestedFrom(),
        job.requestedTo(),
        job.effectiveFrom(),
        job.effectiveTo(),
        job.resolution(),
        job.requestedAt(),
        chunks,
        cachedChunks,
        0,
        entries);
  }

  private static HistoricalSubjectExecutionMeta laneMetadata(JobIssuer issuer) {
    return new HistoricalSubjectExecutionMeta(
        UUID.fromString("550e8400-e29b-41d4-a716-446655446301"),
        UUID.fromString("550e8400-e29b-41d4-a716-446655446302"),
        UUID.fromString("550e8400-e29b-41d4-a716-446655446303"),
        issuer,
        UUID.fromString("550e8400-e29b-41d4-a716-446655446099"),
        Instant.parse("2026-04-07T10:00:00Z"),
        Instant.parse("2026-04-06T10:00:00Z"),
        Instant.parse("2026-04-06T11:00:00Z"),
        Instant.parse("2026-04-06T10:00:00Z"),
        Instant.parse("2026-04-06T11:00:00Z"),
        HistoricalResolution.HOUR_1,
        2,
        List.of(new HistoricalFilterRequest("source", Map.of())),
        SubjectType.SOURCE,
        UUID.fromString("550e8400-e29b-41d4-a716-446655446001"),
        new HistoricalChunkWindowPlan(
            Instant.parse("2026-04-06T10:00:00Z"),
            Instant.parse("2026-04-06T12:00:00Z"),
            2,
            HistoricalResolution.HOUR_1,
            List.of()));
  }

  private static HistoricalChunkCacheStatus cacheStatus(int cachedChunks, int chunkIndex) {
    return chunkIndex < cachedChunks
        ? HistoricalChunkCacheStatus.CACHED
        : HistoricalChunkCacheStatus.MISSING;
  }

  private static HistoricalChunkPlanEntry chunk(
      int index, String startMinute, String endMinute, HistoricalChunkCacheStatus cacheStatus) {
    return new HistoricalChunkPlanEntry(
        index,
        "opencop:history:chunk:SOURCE:550e8400-e29b-41d4-a716-44665544600"
            + index
            + ":HOUR_1:v1:2026-04-06T"
            + startMinute,
        "opencop:history:payload:SOURCE:550e8400-e29b-41d4-a716-44665544600"
            + index
            + ":HOUR_1:v1:v1_latest_in_bucket:filters:2026-04-06T"
            + startMinute,
        "opencop:history:chunk-state:SOURCE:550e8400-e29b-41d4-a716-44665544600"
            + index
            + ":HOUR_1:v1:v1_latest_in_bucket:filters:2026-04-06T"
            + startMinute,
        cacheStatus,
        Instant.parse("2026-04-06T" + startMinute),
        Instant.parse("2026-04-06T" + endMinute));
  }

  private static final class RecordingPublisher implements CachePublisher<HistoryEvent> {

    private HistoryEvent event;

    @Override
    public void publish(HistoryEvent event) {
      this.event = event;
    }
  }
}
