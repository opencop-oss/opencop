// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.usecase.history;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.opencop.core.sdk.model.SubjectType;
import com.opencop.core.sdk.model.history.HistoricalResolution;
import com.opencop.core.sdk.model.job.JobIssuer;
import com.opencop.core.sdk.technical.gateway.history.HistoryJobDeliveryCoordinator;
import com.opencop.processor.business.model.history.HistoricalExecutionContext;
import com.opencop.processor.business.model.history.HistoricalSubjectExecutionMeta;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkCacheStatus;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkPlan;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkPlanEntry;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkWindowPlan;
import com.opencop.processor.ports.technical.gateway.history.HistoryEventPublisher;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HistoricalChunkDeliveryProgressReporterTest {

  private final HistoryEventPublisher historyEventPublisher = mock(HistoryEventPublisher.class);
  private final HistoryJobDeliveryCoordinator jobDeliveryCoordinator =
      mock(HistoryJobDeliveryCoordinator.class);
  private final HistoricalChunkDeliveryProgressReporter reporter =
      new HistoricalChunkDeliveryProgressReporter(historyEventPublisher, jobDeliveryCoordinator);

  @Test
  void start_initializesDeliveryTrackingForUserJobs() {
    var laneMetadata = laneMetadata();
    var plan = HistoricalChunkPlan.forSubject(laneMetadata, laneWindowPlan(laneMetadata));

    reporter.start(plan, laneMetadata);

    verify(jobDeliveryCoordinator)
        .onExecutionStarted(
            laneMetadata.laneJobId(),
            laneMetadata.requestedBy(),
            laneMetadata.requestId(),
            laneMetadata.subjectType(),
            laneMetadata.subjectId(),
            2,
            1);
    verifyNoInteractions(historyEventPublisher);
  }

  @Test
  void start_ignoresSystemJobs() {
    var laneMetadata = laneMetadata(JobIssuer.system("history-test"));
    var plan = HistoricalChunkPlan.forSubject(laneMetadata, laneWindowPlan(laneMetadata));

    reporter.start(plan, laneMetadata);

    verifyNoInteractions(jobDeliveryCoordinator, historyEventPublisher);
  }

  @Test
  void recordProgress_updatesDeliveryTrackingForUserJobsAndPublishesExecutionProgress() {
    var laneMetadata = laneMetadata();
    var plan = HistoricalChunkPlan.forSubject(laneMetadata, laneWindowPlan(laneMetadata));

    var computedChunks = 4L;
    var failedChunks = 1L;

    reporter.recordProgress(plan, laneMetadata, computedChunks, failedChunks);

    verify(jobDeliveryCoordinator)
        .recordExecutionProgress(laneMetadata.laneJobId(), computedChunks, failedChunks);
    verify(historyEventPublisher)
        .publishExecutionProgress(
            plan, HistoricalExecutionContext.from(laneMetadata), computedChunks, failedChunks);
  }

  @Test
  void recordProgress_publishesExecutionProgressForSystemJobsWithoutDeliveryTracking() {
    var laneMetadata = laneMetadata(JobIssuer.system("history-test"));
    var plan = HistoricalChunkPlan.forSubject(laneMetadata, laneWindowPlan(laneMetadata));

    reporter.recordProgress(plan, laneMetadata, 4, 1);

    verify(jobDeliveryCoordinator, never()).recordExecutionProgress(laneMetadata.laneJobId(), 4, 1);
    verify(historyEventPublisher)
        .publishExecutionProgress(plan, HistoricalExecutionContext.from(laneMetadata), 4, 1);
  }

  @Test
  void finish_completesDeliveryTrackingForUserJobs() {
    var laneMetadata = laneMetadata();

    reporter.finish(laneMetadata, 5, 2);

    verify(jobDeliveryCoordinator).onExecutionFinished(laneMetadata.laneJobId(), 5, 2);
    verifyNoInteractions(historyEventPublisher);
  }

  @Test
  void finish_ignoresSystemJobs() {
    var laneMetadata = laneMetadata(JobIssuer.system("history-test"));

    reporter.finish(laneMetadata, 5, 2);

    verifyNoInteractions(jobDeliveryCoordinator, historyEventPublisher);
  }

  @Test
  void progress_usesMetadataForDeliveryCoordinatorAndEventPublisher() {
    var laneMetadata = laneMetadata();
    var plan = HistoricalChunkPlan.forSubject(laneMetadata, laneWindowPlan(laneMetadata));

    reporter.start(plan, laneMetadata);
    reporter.recordProgress(plan, laneMetadata, 4, 1);
    reporter.finish(laneMetadata, 2, 1);

    verify(jobDeliveryCoordinator)
        .onExecutionStarted(
            laneMetadata.laneJobId(),
            laneMetadata.requestedBy(),
            laneMetadata.requestId(),
            laneMetadata.subjectType(),
            laneMetadata.subjectId(),
            plan.chunks(),
            plan.cachedChunks());
    verify(jobDeliveryCoordinator).recordExecutionProgress(laneMetadata.laneJobId(), 4, 1);
    verify(historyEventPublisher)
        .publishExecutionProgress(plan, HistoricalExecutionContext.from(laneMetadata), 4, 1);
    verify(jobDeliveryCoordinator).onExecutionFinished(laneMetadata.laneJobId(), 2, 1);
  }

  @Test
  void progress_publishesExecutionProgressForSystemLanesWithoutDeliveryTracking() {
    var laneMetadata = laneMetadata(JobIssuer.system("history-test"));
    var plan = HistoricalChunkPlan.forSubject(laneMetadata, laneWindowPlan(laneMetadata));

    reporter.start(plan, laneMetadata);
    reporter.recordProgress(plan, laneMetadata, 4, 1);
    reporter.finish(laneMetadata, 2, 1);

    verifyNoInteractions(jobDeliveryCoordinator);
    verify(historyEventPublisher)
        .publishExecutionProgress(plan, HistoricalExecutionContext.from(laneMetadata), 4, 1);
  }

  @Test
  void userLifecycleMethodsAreNoOpsWhenDeliveryCoordinatorIsMissing() {
    var laneMetadata = laneMetadata();
    var plan = HistoricalChunkPlan.forSubject(laneMetadata, laneWindowPlan(laneMetadata));
    var reporterWithoutDeliveryCoordinator =
        new HistoricalChunkDeliveryProgressReporter(historyEventPublisher, null);

    reporterWithoutDeliveryCoordinator.start(plan, laneMetadata);
    reporterWithoutDeliveryCoordinator.recordProgress(plan, laneMetadata, 2, 1);
    reporterWithoutDeliveryCoordinator.finish(laneMetadata, 2, 1);

    verify(historyEventPublisher)
        .publishExecutionProgress(plan, HistoricalExecutionContext.from(laneMetadata), 2, 1);
  }

  @Test
  void laneLifecycleMethodsAreNoOpsWhenDeliveryCoordinatorIsMissing() {
    var laneMetadata = laneMetadata();
    var plan = HistoricalChunkPlan.forSubject(laneMetadata, laneWindowPlan(laneMetadata));
    var reporterWithoutDeliveryCoordinator =
        new HistoricalChunkDeliveryProgressReporter(historyEventPublisher, null);

    reporterWithoutDeliveryCoordinator.start(plan, laneMetadata);
    reporterWithoutDeliveryCoordinator.recordProgress(plan, laneMetadata, 2, 1);
    reporterWithoutDeliveryCoordinator.finish(laneMetadata, 2, 1);

    verify(historyEventPublisher)
        .publishExecutionProgress(plan, HistoricalExecutionContext.from(laneMetadata), 2, 1);
  }

  private static HistoricalChunkPlanEntry entry(int index, HistoricalChunkCacheStatus cacheStatus) {
    return new HistoricalChunkPlanEntry(
        index,
        "chunk-" + index,
        "payload-" + index,
        "state-" + index,
        cacheStatus,
        Instant.parse("2026-04-06T10:0" + index + ":00Z"),
        Instant.parse("2026-04-06T10:0" + (index + 1) + ":00Z"));
  }

  private static HistoricalSubjectExecutionMeta laneMetadata() {
    return laneMetadata(JobIssuer.user(UUID.fromString("550e8400-e29b-41d4-a716-446655440120")));
  }

  private static HistoricalSubjectExecutionMeta laneMetadata(JobIssuer issuer) {
    return new HistoricalSubjectExecutionMeta(
        UUID.fromString("550e8400-e29b-41d4-a716-446655440200"),
        UUID.fromString("550e8400-e29b-41d4-a716-446655440201"),
        UUID.fromString("550e8400-e29b-41d4-a716-446655440212"),
        issuer,
        UUID.fromString("550e8400-e29b-41d4-a716-446655440203"),
        Instant.parse("2026-04-07T10:00:00Z"),
        Instant.parse("2026-04-06T10:02:17Z"),
        Instant.parse("2026-04-06T12:13:02Z"),
        Instant.parse("2026-04-06T10:02:00Z"),
        Instant.parse("2026-04-06T12:14:00Z"),
        HistoricalResolution.MINUTE_1,
        132,
        List.of(),
        SubjectType.LAYER,
        UUID.fromString("550e8400-e29b-41d4-a716-446655440204"),
        laneWindowPlan());
  }

  private static HistoricalChunkWindowPlan laneWindowPlan(HistoricalSubjectExecutionMeta metadata) {
    return new HistoricalChunkWindowPlan(
        metadata.effectiveFrom(),
        metadata.effectiveTo(),
        2,
        metadata.resolution(),
        List.of(
            entry(metadata.effectiveFrom(), 0, HistoricalChunkCacheStatus.CACHED),
            entry(
                metadata.effectiveFrom().plusSeconds(60), 1, HistoricalChunkCacheStatus.MISSING)));
  }

  private static HistoricalChunkWindowPlan laneWindowPlan() {
    return laneWindowPlan(
        Instant.parse("2026-04-06T10:02:00Z"),
        Instant.parse("2026-04-06T12:14:00Z"),
        List.of(
            entry(Instant.parse("2026-04-06T10:02:00Z"), 0, HistoricalChunkCacheStatus.CACHED),
            entry(Instant.parse("2026-04-06T10:03:00Z"), 1, HistoricalChunkCacheStatus.MISSING)));
  }

  private static HistoricalChunkWindowPlan laneWindowPlan(
      Instant effectiveFrom, Instant effectiveTo, List<HistoricalChunkPlanEntry> entries) {
    return new HistoricalChunkWindowPlan(
        effectiveFrom, effectiveTo, entries.size(), HistoricalResolution.MINUTE_1, entries);
  }

  private static HistoricalChunkPlanEntry entry(
      Instant from, int index, HistoricalChunkCacheStatus cacheStatus) {
    return new HistoricalChunkPlanEntry(
        index,
        "lane-chunk-" + index,
        "lane-payload-" + index,
        "lane-state-" + index,
        cacheStatus,
        from,
        from.plusSeconds(60L));
  }
}
