// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.usecase.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.opencop.core.sdk.model.SubjectType;
import com.opencop.core.sdk.model.history.HistoricalFilterRequest;
import com.opencop.core.sdk.model.history.HistoricalResolution;
import com.opencop.core.sdk.model.job.HistoricalTracksJobSubmission;
import com.opencop.core.sdk.model.job.HistoricalTracksSubject;
import com.opencop.core.sdk.model.job.JobIssuer;
import com.opencop.processor.business.model.history.HistoricalSubjectExecutionMeta;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkPlan;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkWindowPlan;
import com.opencop.processor.business.policy.history.HistoricalCacheIdentity;
import com.opencop.processor.business.policy.history.HistoricalChunkPlanner;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class HistoricalChunkPlannerTest {

  private final HistoricalChunkPlanner planner = new HistoricalChunkPlanner();
  private final HistoricalCacheIdentity cacheIdentity =
      new HistoricalCacheIdentity(new ObjectMapper());

  @Test
  void planWindow_enumeratesChunksDeterministically() {
    var job =
        HistoricalTracksJobSubmission.create(
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

    var windowPlan = planner.planWindow(job);
    var plan = HistoricalChunkPlan.forSubject(metadataFor(job, windowPlan), windowPlan);

    assertEquals(132, plan.chunks());
    assertEquals(132, plan.entries().size());
    assertEquals(0, plan.entries().getFirst().chunkIndex());
    assertEquals("2026-04-06T10:02:00Z", plan.entries().getFirst().from().toString());
    assertEquals("2026-04-06T10:03:00Z", plan.entries().getFirst().to().toString());
    assertEquals(131, plan.entries().getLast().chunkIndex());
    assertEquals("2026-04-06T12:13:00Z", plan.entries().getLast().from().toString());
    assertEquals(
        "opencop:history:chunk:MINUTE_1:v1:2026-04-06T10:02:00Z",
        plan.entries().getFirst().chunkKey());
    assertEquals(0, plan.cachedChunks());
    assertEquals(0, plan.chunksToCompute());
  }

  @Test
  void planWindow_whenSubmissionMetadataDisagrees_failsExplicitly() {
    var job =
        HistoricalTracksJobSubmission.create(
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
            131,
            List.of());

    assertThrows(IllegalStateException.class, () -> planner.planWindow(job));
  }

  @Test
  void planWindow_isSharedAcrossSubjectCombinations() {
    var sourceJob = multiSubjectJob(SubjectType.SOURCE);
    var layerJob = multiSubjectJob(SubjectType.LAYER);

    HistoricalChunkWindowPlan sourceWindow = planner.planWindow(sourceJob);
    HistoricalChunkWindowPlan layerWindow = planner.planWindow(layerJob);

    assertEquals(sourceWindow.effectiveFrom(), layerWindow.effectiveFrom());
    assertEquals(sourceWindow.effectiveTo(), layerWindow.effectiveTo());
    assertEquals(sourceWindow.resolution(), layerWindow.resolution());
    assertEquals(sourceWindow.chunks(), layerWindow.chunks());
    assertEquals(sourceWindow.entries(), layerWindow.entries());
    assertEquals(
        "opencop:history:chunk:MINUTE_1:v1:2026-04-06T10:02:00Z",
        sourceWindow.entries().getFirst().chunkKey());
    assertEquals(
        "opencop:history:chunk:MINUTE_1:v1:2026-04-06T12:13:00Z",
        sourceWindow.entries().getLast().chunkKey());
  }

  @Test
  void cacheIdentity_isDeterministicForEquivalentFilters() {
    var filtersA =
        List.of(
            new HistoricalFilterRequest(
                "bbox", Map.of("maxLon", 10, "minLon", 1, "minLat", 2, "maxLat", 9)),
            new HistoricalFilterRequest("speed", Map.of("max", 50, "min", 10)));
    var filtersB =
        List.of(
            new HistoricalFilterRequest("speed", Map.of("min", 10, "max", 50)),
            new HistoricalFilterRequest(
                "bbox", Map.of("minLat", 2, "maxLat", 9, "minLon", 1, "maxLon", 10)));

    var jobA = jobWithFilters(filtersA);
    var jobB = jobWithFilters(filtersB);
    var chunkFrom = Instant.parse("2026-04-06T10:02:00Z");

    assertEquals(payloadKey(jobA, chunkFrom), payloadKey(jobB, chunkFrom));
    assertEquals(chunkStateKey(jobA, chunkFrom), chunkStateKey(jobB, chunkFrom));
  }

  @Test
  void plan_distinguishesLayerAndSourceCacheIdentityWithSameWindowPlan() {
    var sharedSubjectId = UUID.fromString("550e8400-e29b-41d4-a716-446655440121");

    var sourceJob = jobWithSubject(SubjectType.SOURCE, sharedSubjectId);
    var layerJob = jobWithSubject(SubjectType.LAYER, sharedSubjectId);

    var sourceWindowPlan = planner.planWindow(sourceJob);
    var layerWindowPlan = planner.planWindow(layerJob);
    var sourcePlan =
        HistoricalChunkPlan.forSubject(metadataFor(sourceJob, sourceWindowPlan), sourceWindowPlan);
    var layerPlan =
        HistoricalChunkPlan.forSubject(metadataFor(layerJob, layerWindowPlan), layerWindowPlan);

    assertEquals(
        sourcePlan.entries().getFirst().chunkKey(), layerPlan.entries().getFirst().chunkKey());
    assertNotEquals(
        payloadKey(sourceJob, sourcePlan.entries().getFirst().from()),
        payloadKey(layerJob, sourcePlan.entries().getFirst().from()));
    assertNotEquals(
        chunkStateKey(sourceJob, sourcePlan.entries().getFirst().from()),
        chunkStateKey(layerJob, sourcePlan.entries().getFirst().from()));
    assertEquals(
        "opencop:history:chunk:MINUTE_1:v1:2026-04-06T10:02:00Z",
        sourcePlan.entries().getFirst().chunkKey());
    assertEquals(
        "opencop:history:chunk:MINUTE_1:v1:2026-04-06T10:02:00Z",
        layerPlan.entries().getFirst().chunkKey());
  }

  private HistoricalTracksJobSubmission jobWithFilters(List<HistoricalFilterRequest> filters) {
    return jobWithSubject(
        SubjectType.SOURCE, UUID.fromString("550e8400-e29b-41d4-a716-446655440121"), filters);
  }

  private HistoricalTracksJobSubmission multiSubjectJob(SubjectType subjectType) {
    UUID sourceSubjectId = UUID.fromString("550e8400-e29b-41d4-a716-446655440121");
    UUID layerSubjectId = UUID.fromString("550e8400-e29b-41d4-a716-446655440123");
    return HistoricalTracksJobSubmission.create(
        UUID.fromString("550e8400-e29b-41d4-a716-446655440122"),
        JobIssuer.user(UUID.fromString("550e8400-e29b-41d4-a716-446655440120")),
        List.of(
            new HistoricalTracksSubject(UUID.randomUUID(), subjectType, sourceSubjectId),
            new HistoricalTracksSubject(UUID.randomUUID(), SubjectType.LAYER, layerSubjectId)),
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

  private HistoricalTracksJobSubmission jobWithSubject(SubjectType subjectType, UUID subjectId) {
    return jobWithSubject(subjectType, subjectId, List.of());
  }

  private HistoricalTracksJobSubmission jobWithSubject(
      SubjectType subjectType, UUID subjectId, List<HistoricalFilterRequest> filters) {
    return HistoricalTracksJobSubmission.create(
        UUID.fromString("550e8400-e29b-41d4-a716-446655440122"),
        JobIssuer.user(UUID.fromString("550e8400-e29b-41d4-a716-446655440120")),
        subjectType,
        subjectId,
        UUID.fromString("550e8400-e29b-41d4-a716-446655440120"),
        Instant.parse("2026-04-07T10:00:00Z"),
        Instant.parse("2026-04-06T10:02:17Z"),
        Instant.parse("2026-04-06T12:13:02Z"),
        Instant.parse("2026-04-06T10:02:00Z"),
        Instant.parse("2026-04-06T12:14:00Z"),
        HistoricalResolution.MINUTE_1,
        132,
        filters);
  }

  private String payloadKey(HistoricalTracksJobSubmission job, Instant chunkFrom) {
    return cacheIdentity.payloadKey(metadataFor(job, windowPlan(job)), chunkFrom);
  }

  private String chunkStateKey(HistoricalTracksJobSubmission job, Instant chunkFrom) {
    return cacheIdentity.chunkStateKey(metadataFor(job, windowPlan(job)), chunkFrom);
  }

  private static HistoricalChunkWindowPlan windowPlan(HistoricalTracksJobSubmission job) {
    return new HistoricalChunkWindowPlan(
        job.effectiveFrom(), job.effectiveTo(), job.plannedChunks(), job.resolution(), List.of());
  }

  private static HistoricalSubjectExecutionMeta metadataFor(
      HistoricalTracksJobSubmission job, HistoricalChunkWindowPlan windowPlan) {
    var subject = job.subjects().getFirst();
    return new HistoricalSubjectExecutionMeta(
        UUID.randomUUID(),
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
        windowPlan);
  }
}
