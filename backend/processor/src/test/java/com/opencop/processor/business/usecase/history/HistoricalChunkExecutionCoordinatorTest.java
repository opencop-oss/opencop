// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.usecase.history;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class HistoricalChunkExecutionCoordinatorTest {

  private final HistoricalChunkDeliveryProgressReporter deliveryProgressReporter =
      mock(HistoricalChunkDeliveryProgressReporter.class);
  private final HistoricalSequentialChunkExecutor sequentialChunkExecutor =
      mock(HistoricalSequentialChunkExecutor.class);
  private final HistoricalChunkExecutionCoordinator coordinator =
      new HistoricalChunkExecutionCoordinator(deliveryProgressReporter, sequentialChunkExecutor);

  @Test
  void execute_alwaysDelegatesToSequentialExecutor() {
    var metadata = laneMetadata();
    var plan = HistoricalChunkPlan.forSubject(metadata, metadata.windowPlan());
    when(sequentialChunkExecutor.execute(metadata, plan))
        .thenReturn(new HistoricalChunkExecutionSummary(2, 1));

    coordinator.execute(metadata, plan);

    InOrder inOrder = inOrder(deliveryProgressReporter, sequentialChunkExecutor);
    inOrder.verify(deliveryProgressReporter).start(plan, metadata);
    inOrder.verify(sequentialChunkExecutor).execute(metadata, plan);
    inOrder.verify(deliveryProgressReporter).finish(metadata, 2, 1);
  }

  @Test
  void execute_whenPlanHasMixedCacheStatuses_stillUsesSequentialExecutor() {
    var metadata =
        laneMetadataWithEntries(
            List.of(
                metadataEntry(0, HistoricalChunkCacheStatus.MISSING),
                metadataEntry(1, HistoricalChunkCacheStatus.CACHED),
                metadataEntry(2, HistoricalChunkCacheStatus.MISSING)));
    var plan = HistoricalChunkPlan.forSubject(metadata, metadata.windowPlan());
    when(sequentialChunkExecutor.execute(metadata, plan))
        .thenReturn(new HistoricalChunkExecutionSummary(2, 0));

    coordinator.execute(metadata, plan);

    verify(sequentialChunkExecutor).execute(metadata, plan);
    verify(deliveryProgressReporter).finish(metadata, 2, 0);
  }

  @Test
  void execute_whenSequentialExecutorThrows_doesNotFinishDelivery() {
    var metadata = laneMetadata();
    var plan = HistoricalChunkPlan.forSubject(metadata, metadata.windowPlan());
    var failure = new IllegalStateException("simulated failure");
    when(sequentialChunkExecutor.execute(metadata, plan)).thenThrow(failure);

    IllegalStateException thrown =
        assertThrows(IllegalStateException.class, () -> coordinator.execute(metadata, plan));

    assertSame(failure, thrown);
    verify(deliveryProgressReporter).start(plan, metadata);
    verify(sequentialChunkExecutor).execute(metadata, plan);
    verify(deliveryProgressReporter, never()).finish(any(), anyLong(), anyLong());
  }

  private static HistoricalSubjectExecutionMeta laneMetadata() {
    return laneMetadataWithEntries(
        List.of(
            metadataEntry(0, HistoricalChunkCacheStatus.MISSING),
            metadataEntry(1, HistoricalChunkCacheStatus.MISSING),
            metadataEntry(2, HistoricalChunkCacheStatus.MISSING)));
  }

  private static HistoricalSubjectExecutionMeta laneMetadataWithEntries(
      List<HistoricalChunkPlanEntry> entries) {
    Instant effectiveFrom = entries.getFirst().from();
    Instant effectiveTo = entries.getLast().to();
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
        effectiveTo,
        HistoricalResolution.MINUTE_1,
        entries.size(),
        List.of(),
        SubjectType.SOURCE,
        UUID.fromString("550e8400-e29b-41d4-a716-446655440204"),
        new HistoricalChunkWindowPlan(
            effectiveFrom, effectiveTo, entries.size(), HistoricalResolution.MINUTE_1, entries));
  }

  private static HistoricalChunkPlanEntry metadataEntry(
      int index, HistoricalChunkCacheStatus cacheStatus) {
    Instant from = Instant.parse("2026-04-06T10:00:00Z").plusSeconds(60L * index);
    return new HistoricalChunkPlanEntry(
        index,
        "lane-chunk-" + index,
        "lane-payload-" + index,
        "lane-state-" + index,
        cacheStatus,
        from,
        from.plusSeconds(60));
  }
}
