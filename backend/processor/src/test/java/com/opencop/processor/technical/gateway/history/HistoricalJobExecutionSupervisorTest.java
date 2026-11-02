// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.opencop.core.sdk.model.SubjectType;
import com.opencop.core.sdk.model.history.HistoricalJobStage;
import com.opencop.core.sdk.model.history.HistoricalResolution;
import com.opencop.core.sdk.model.job.JobIssuer;
import com.opencop.processor.business.model.history.HistoricalSubjectExecutionMeta;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkPlanEntry;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkWindowPlan;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class HistoricalJobExecutionSupervisorTest {

  @Test
  void submit_whenJobCompletes_clearsTrackingAndCancelsScheduledTasks() {
    var fixture = HistoricalJobExecutionSupervisorFixture.withDirectExecutor();
    var laneMetadata = laneMetadata();

    fixture.submit(laneMetadata);

    assertEquals(1, fixture.executions());
    assertEquals(1, fixture.completions());
    assertEquals(0, fixture.cancellations());
    assertFalse(fixture.isTracking(laneMetadata));
    fixture.assertScheduledTasksCancelled();
  }

  @Test
  void submit_whenExecutorRejects_clearsTrackingAndCancelsScheduledTasks() {
    var fixture = HistoricalJobExecutionSupervisorFixture.withRejectingExecutor();
    var laneMetadata = laneMetadata();

    assertThrows(RejectedExecutionException.class, () -> fixture.submit(laneMetadata));

    assertEquals(0, fixture.completions());
    assertFalse(fixture.isTracking(laneMetadata));
    fixture.assertScheduledTasksCancelled();
  }

  @Test
  void cancelBeforeStartIfTracked_cancelsQueuedWorkerAndCompletesOnce() {
    var fixture = HistoricalJobExecutionSupervisorFixture.withManualExecutor();
    var laneMetadata = laneMetadata();

    fixture.submit(laneMetadata);
    assertTrue(fixture.isTracking(laneMetadata));

    fixture.cancelBeforeStart(laneMetadata);
    fixture.runQueuedWorker();

    assertEquals(0, fixture.executions());
    assertEquals(1, fixture.completions());
    assertEquals(1, fixture.cancellations());
    assertFalse(fixture.isTracking(laneMetadata));
    fixture.assertScheduledTasksCancelled();
  }

  @Test
  void scheduledRefresh_refreshesActiveTtlWhileTracked() {
    var fixture = HistoricalJobExecutionSupervisorFixture.withManualExecutor();
    var laneMetadata = laneMetadata();

    fixture.submitNoop(laneMetadata);
    fixture.runRefresh();

    assertEquals(laneMetadata.laneJobId(), fixture.repository.lastRefreshJobId);
    assertEquals(1, fixture.repository.refreshCount);
    assertTrue(fixture.isTracking(laneMetadata));
  }

  @Test
  void timeout_marksJobFailedPublishesFailureNotifiesAndInterruptsWorker() {
    var fixture = HistoricalJobExecutionSupervisorFixture.withSingleThreadExecutor();
    var laneMetadata = laneMetadata();
    var started = new CountDownLatch(1);
    var interrupted = new CountDownLatch(1);
    var completed = new CountDownLatch(1);

    try {
      fixture.submitBlocking(laneMetadata, started, interrupted, completed);
      await(started);

      fixture.runTimeout();
      await(interrupted);
      await(completed);

      assertEquals(HistoricalJobStage.TIMEOUT, fixture.repository.failedStage);
      assertTrue(fixture.repository.failureReason.contains("max runtime"));
      assertEquals(1, fixture.completions());
      assertFalse(fixture.isTracking(laneMetadata));
    } finally {
      fixture.shutdownExecutor();
    }
  }

  @Test
  void timeout_recordsTimeoutMetricWithDuration() {
    var fixture = HistoricalJobExecutionSupervisorFixture.withManualExecutor();
    var laneMetadata = laneMetadata();

    fixture.submitNoop(laneMetadata);
    fixture.runTimeout();

    var timeoutCounter =
        fixture
            .meterRegistry
            .find("opencop_history_job_stage_total")
            .tags("stage", "TIMEOUT", "outcome", "error")
            .counter();
    var timeoutTimer =
        fixture
            .meterRegistry
            .find("opencop_history_job_stage_duration_seconds")
            .tags("stage", "TIMEOUT", "outcome", "error")
            .timer();
    assertNotNull(timeoutCounter);
    assertEquals(1.0d, timeoutCounter.count());
    assertNotNull(timeoutTimer);
    assertTrue(timeoutTimer.count() > 0);
    assertTrue(timeoutTimer.totalTime(TimeUnit.NANOSECONDS) > 0.0d);
  }

  private static HistoricalSubjectExecutionMeta laneMetadata() {
    Instant requestedFrom = Instant.parse("2026-04-06T10:02:17Z");
    Instant requestedTo = Instant.parse("2026-04-06T12:00:00Z");
    Instant effectiveFrom = Instant.parse("2026-04-06T10:02:00Z");
    Instant effectiveTo = Instant.parse("2026-04-06T12:00:00Z");
    return new HistoricalSubjectExecutionMeta(
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        JobIssuer.user(UUID.fromString("550e8400-e29b-41d4-a716-446655440120")),
        UUID.randomUUID(),
        Instant.parse("2026-04-07T10:00:00Z"),
        requestedFrom,
        requestedTo,
        effectiveFrom,
        effectiveTo,
        HistoricalResolution.MINUTE_1,
        118,
        List.of(),
        SubjectType.SOURCE,
        UUID.randomUUID(),
        new HistoricalChunkWindowPlan(
            effectiveFrom,
            effectiveTo,
            118,
            HistoricalResolution.MINUTE_1,
            List.of(
                new HistoricalChunkPlanEntry(
                    0,
                    "chunk-key",
                    UUID.randomUUID().toString(),
                    null,
                    null,
                    effectiveFrom,
                    effectiveFrom.plusSeconds(60)))));
  }

  private static void await(CountDownLatch latch) {
    try {
      assertTrue(latch.await(2, TimeUnit.SECONDS));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError(e);
    }
  }
}
