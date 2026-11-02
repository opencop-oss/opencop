// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.usecase.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.opencop.core.sdk.model.SubjectType;
import com.opencop.core.sdk.model.history.HistoricalResolution;
import com.opencop.core.sdk.model.job.JobIssuer;
import com.opencop.processor.business.model.history.HistoricalSubjectExecutionMeta;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkCacheStatus;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkPlan;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkPlanEntry;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkWindowPlan;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class HistoricalChunkComputeKeepaliveTest {

  private final ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
  private final ScheduledFuture<?> scheduledFuture = mock(ScheduledFuture.class);
  private final HistoricalChunkDeliveryProgressReporter deliveryProgressReporter =
      mock(HistoricalChunkDeliveryProgressReporter.class);
  private final HistoricalChunkComputeKeepalive keepalive =
      new HistoricalChunkComputeKeepalive(scheduler, deliveryProgressReporter);

  @Test
  void execute_schedulesProgressKeepaliveDuringChunkOperationAndCancelsAfterward() {
    var laneMetadata = laneMetadata();
    var plan = HistoricalChunkPlan.forSubject(laneMetadata, laneWindowPlan());
    ArgumentCaptor<Runnable> keepaliveTask = ArgumentCaptor.forClass(Runnable.class);
    doReturn(scheduledFuture)
        .when(scheduler)
        .scheduleAtFixedRate(
            keepaliveTask.capture(),
            eq(HistoricalChunkComputeKeepalive.KEEPALIVE_INTERVAL.toMillis()),
            eq(HistoricalChunkComputeKeepalive.KEEPALIVE_INTERVAL.toMillis()),
            eq(TimeUnit.MILLISECONDS));

    HistoricalChunkExecutionResult result =
        keepalive.execute(
            plan, laneMetadata, () -> 2L, () -> 1L, () -> HistoricalChunkExecutionResult.COMPUTED);

    assertEquals(HistoricalChunkExecutionResult.COMPUTED, result);
    keepaliveTask.getValue().run();
    verify(deliveryProgressReporter).recordProgress(plan, laneMetadata, 2, 1);
    verify(scheduledFuture).cancel(false);
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
        UUID.fromString("550e8400-e29b-41d4-a716-446655440200"),
        UUID.fromString("550e8400-e29b-41d4-a716-446655440201"),
        UUID.fromString("550e8400-e29b-41d4-a716-446655440212"),
        JobIssuer.user(UUID.fromString("550e8400-e29b-41d4-a716-446655440120")),
        UUID.fromString("550e8400-e29b-41d4-a716-446655440203"),
        Instant.parse("2026-04-07T10:00:00Z"),
        Instant.parse("2026-04-06T10:02:17Z"),
        Instant.parse("2026-04-06T12:13:02Z"),
        Instant.parse("2026-04-06T10:02:00Z"),
        Instant.parse("2026-04-06T12:14:00Z"),
        HistoricalResolution.MINUTE_1,
        2,
        List.of(),
        SubjectType.SOURCE,
        UUID.fromString("550e8400-e29b-41d4-a716-446655440204"),
        new HistoricalChunkWindowPlan(
            Instant.parse("2026-04-06T10:02:00Z"),
            Instant.parse("2026-04-06T12:14:00Z"),
            2,
            HistoricalResolution.MINUTE_1,
            List.of(
                entryForMetadata(
                    Instant.parse("2026-04-06T10:02:00Z"), 0, HistoricalChunkCacheStatus.MISSING),
                entryForMetadata(
                    Instant.parse("2026-04-06T10:03:00Z"), 1, HistoricalChunkCacheStatus.CACHED))));
  }

  private static HistoricalChunkWindowPlan laneWindowPlan() {
    return new HistoricalChunkWindowPlan(
        Instant.parse("2026-04-06T10:02:00Z"),
        Instant.parse("2026-04-06T12:14:00Z"),
        2,
        HistoricalResolution.MINUTE_1,
        List.of(
            entryForMetadata(
                Instant.parse("2026-04-06T10:02:00Z"), 0, HistoricalChunkCacheStatus.CACHED),
            entryForMetadata(
                Instant.parse("2026-04-06T10:03:00Z"), 1, HistoricalChunkCacheStatus.CACHED)));
  }

  private static HistoricalChunkPlanEntry entryForMetadata(
      Instant from, int index, HistoricalChunkCacheStatus cacheStatus) {
    return new HistoricalChunkPlanEntry(
        index,
        "metadata-chunk-" + index,
        "metadata-payload-" + index,
        "metadata-state-" + index,
        cacheStatus,
        from,
        from.plusSeconds(60L));
  }
}
