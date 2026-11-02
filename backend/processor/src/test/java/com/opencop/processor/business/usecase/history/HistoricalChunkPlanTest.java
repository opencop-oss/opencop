// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.usecase.history;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.opencop.core.sdk.model.SubjectType;
import com.opencop.core.sdk.model.history.HistoricalResolution;
import com.opencop.core.sdk.model.job.JobIssuer;
import com.opencop.processor.business.model.history.HistoricalSubjectExecutionMeta;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkPlan;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkPlanEntry;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkWindowPlan;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HistoricalChunkPlanTest {

  @Test
  void forSubjectLaneBuildsSubjectPlanFromWindowPlan() {
    var metadata = metadata();
    var sharedWindowPlan =
        new HistoricalChunkWindowPlan(
            metadata.effectiveFrom(),
            metadata.effectiveTo(),
            2,
            metadata.resolution(),
            List.of(
                entry(
                    0,
                    Instant.parse("2026-04-06T10:02:00Z"),
                    Instant.parse("2026-04-06T10:03:00Z")),
                entry(
                    1,
                    Instant.parse("2026-04-06T10:03:00Z"),
                    Instant.parse("2026-04-06T10:04:00Z"))));

    HistoricalChunkPlan plan = HistoricalChunkPlan.forSubject(metadata, sharedWindowPlan);

    assertTrue(plan.subjectType() == metadata.subjectType());
    assertTrue(plan.subjectId().equals(metadata.subjectId()));
    assertTrue(plan.requestId().equals(metadata.requestId()));
    assertTrue(plan.requestedBy().equals(metadata.requestedBy()));
    assertTrue(plan.chunks() == sharedWindowPlan.chunks());
    assertTrue(plan.resolution() == sharedWindowPlan.resolution());
    assertTrue(plan.entries().equals(sharedWindowPlan.entries()));
    assertTrue(plan.effectiveFrom().equals(sharedWindowPlan.effectiveFrom()));
    assertTrue(plan.effectiveTo().equals(sharedWindowPlan.effectiveTo()));
  }

  private static HistoricalChunkPlanEntry entry(int index, Instant from, Instant to) {
    return new HistoricalChunkPlanEntry(index, "chunk-" + index, null, null, null, from, to);
  }

  private static HistoricalSubjectExecutionMeta metadata() {
    return new HistoricalSubjectExecutionMeta(
        UUID.fromString("550e8400-e29b-41d4-a716-446655440130"),
        UUID.fromString("550e8400-e29b-41d4-a716-446655440131"),
        UUID.fromString("550e8400-e29b-41d4-a716-446655440120"),
        JobIssuer.user(UUID.fromString("550e8400-e29b-41d4-a716-446655440120")),
        UUID.fromString("550e8400-e29b-41d4-a716-446655440120"),
        Instant.parse("2026-04-07T10:00:00Z"),
        Instant.parse("2026-04-06T10:02:17Z"),
        Instant.parse("2026-04-06T12:13:02Z"),
        Instant.parse("2026-04-06T10:02:00Z"),
        Instant.parse("2026-04-06T12:14:00Z"),
        HistoricalResolution.MINUTE_1,
        132,
        List.of(),
        SubjectType.SOURCE,
        UUID.fromString("550e8400-e29b-41d4-a716-446655440131"),
        new HistoricalChunkWindowPlan(
            Instant.parse("2026-04-06T10:02:00Z"),
            Instant.parse("2026-04-06T12:14:00Z"),
            2,
            HistoricalResolution.MINUTE_1,
            List.of()));
  }
}
