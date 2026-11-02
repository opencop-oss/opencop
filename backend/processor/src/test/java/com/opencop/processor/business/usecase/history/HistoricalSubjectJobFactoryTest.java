// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.usecase.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.opencop.core.sdk.model.SubjectType;
import com.opencop.core.sdk.model.history.HistoricalFilterRequest;
import com.opencop.core.sdk.model.history.HistoricalResolution;
import com.opencop.core.sdk.model.job.HistoricalTracksJobSubmission;
import com.opencop.core.sdk.model.job.HistoricalTracksSubject;
import com.opencop.core.sdk.model.job.JobIssuer;
import com.opencop.processor.business.model.history.HistoricalSubjectExecutionMeta;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkPlanEntry;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkWindowPlan;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HistoricalSubjectJobFactoryTest {

  private static final UUID REQUEST_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440100");
  private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440101");
  private static final UUID REQUESTED_BY = UUID.fromString("550e8400-e29b-41d4-a716-446655440102");
  private static final Instant REQUESTED_AT = Instant.parse("2026-04-06T10:00:00Z");
  private static final Instant REQUESTED_FROM = Instant.parse("2026-04-06T10:05:00Z");
  private static final Instant REQUESTED_TO = Instant.parse("2026-04-06T11:00:00Z");
  private static final Instant EFFECTIVE_FROM = Instant.parse("2026-04-06T10:05:00Z");
  private static final Instant EFFECTIVE_TO = Instant.parse("2026-04-06T11:00:00Z");
  private static final long PLANNED_CHUNKS = 42L;

  private final HistoricalSubjectJobFactory factory = new HistoricalSubjectJobFactory();

  @Test
  void createExpandsOneMetadataRecordPerSubject() {
    HistoricalTracksJobSubmission parent = parentSubmission();
    HistoricalChunkWindowPlan sharedWindowPlan = sharedWindowPlan(parent);

    List<HistoricalSubjectExecutionMeta> metadata = factory.create(parent, sharedWindowPlan);

    assertEquals(2, metadata.size());
    for (int index = 0; index < 2; index++) {
      HistoricalSubjectExecutionMeta subjectMeta = metadata.get(index);
      HistoricalTracksSubject sourceSubject = parent.subjects().get(index);
      assertEquals(sourceSubject.jobId(), subjectMeta.laneJobId());
      assertEquals(parent.jobId(), subjectMeta.parentJobId());
      assertEquals(parent.requestId(), subjectMeta.requestId());
      assertEquals(parent.issuer(), subjectMeta.issuer());
      assertEquals(parent.requestedBy(), subjectMeta.requestedBy());
      assertEquals(parent.requestedAt(), subjectMeta.requestedAt());
      assertEquals(parent.requestedFrom(), subjectMeta.requestedFrom());
      assertEquals(parent.requestedTo(), subjectMeta.requestedTo());
      assertEquals(parent.effectiveFrom(), subjectMeta.effectiveFrom());
      assertEquals(parent.effectiveTo(), subjectMeta.effectiveTo());
      assertEquals(parent.plannedChunks(), subjectMeta.plannedChunks());
      assertEquals(parent.resolution(), subjectMeta.resolution());
      assertEquals(parent.filters(), subjectMeta.filters());
      assertSame(sharedWindowPlan, subjectMeta.windowPlan());
      assertEquals(sourceSubject.subjectType(), subjectMeta.subjectType());
      assertEquals(sourceSubject.subjectId(), subjectMeta.subjectId());
    }
  }

  @Test
  void metadataHasNoSubjectsCollectionAccessor() {
    assertThrows(
        NoSuchMethodException.class,
        () -> HistoricalSubjectExecutionMeta.class.getMethod("subjects"));
  }

  @Test
  void metadataReusesParentMetadataAndLaneJobIdFromSubject() {
    HistoricalTracksJobSubmission parent = parentSubmission();
    HistoricalChunkWindowPlan sharedWindowPlan = sharedWindowPlan(parent);

    List<HistoricalSubjectExecutionMeta> metadata = factory.create(parent, sharedWindowPlan);

    HistoricalSubjectExecutionMeta sourceSubjectMeta = metadata.get(0);
    HistoricalTracksSubject sourceSubject = parent.subjects().get(0);
    assertEquals(sourceSubject.subjectType(), sourceSubjectMeta.subjectType());
    assertEquals(sourceSubject.jobId(), sourceSubjectMeta.laneJobId());
    assertEquals(parent.requestId(), sourceSubjectMeta.requestId());
    assertEquals(parent.issuer(), sourceSubjectMeta.issuer());
    assertEquals(parent.resolution(), sourceSubjectMeta.resolution());
    assertSame(sharedWindowPlan, sourceSubjectMeta.windowPlan());
    assertEquals(parent.filters(), sourceSubjectMeta.filters());
  }

  @Test
  void createRejectsMissingWindowPlan() {
    HistoricalTracksJobSubmission parent = parentSubmission();

    NullPointerException missingWindowPlan =
        assertThrows(NullPointerException.class, () -> factory.create(parent, null));
    assertEquals("windowPlan", missingWindowPlan.getMessage());
  }

  private static HistoricalTracksJobSubmission parentSubmission() {
    List<HistoricalFilterRequest> filters =
        List.of(new HistoricalFilterRequest("status", Map.of("value", "active")));
    return new HistoricalTracksJobSubmission(
        UUID.randomUUID(),
        REQUEST_ID,
        JobIssuer.user(USER_ID),
        List.of(
            new HistoricalTracksSubject(
                UUID.fromString("550e8400-e29b-41d4-a716-446655440110"),
                SubjectType.SOURCE,
                UUID.fromString("550e8400-e29b-41d4-a716-446655440111")),
            new HistoricalTracksSubject(
                UUID.fromString("550e8400-e29b-41d4-a716-446655440120"),
                SubjectType.LAYER,
                UUID.fromString("550e8400-e29b-41d4-a716-446655440121"))),
        REQUESTED_BY,
        REQUESTED_AT,
        REQUESTED_FROM,
        REQUESTED_TO,
        EFFECTIVE_FROM,
        EFFECTIVE_TO,
        HistoricalResolution.MINUTE_1,
        PLANNED_CHUNKS,
        filters);
  }

  private static HistoricalChunkWindowPlan sharedWindowPlan(
      HistoricalTracksJobSubmission parentSubmission) {
    return new HistoricalChunkWindowPlan(
        parentSubmission.effectiveFrom(),
        parentSubmission.effectiveTo(),
        parentSubmission.plannedChunks(),
        parentSubmission.resolution(),
        List.of(
            new HistoricalChunkPlanEntry(
                0,
                "chunk-key",
                UUID.randomUUID().toString(),
                null,
                null,
                parentSubmission.effectiveFrom(),
                parentSubmission.effectiveFrom().plusSeconds(60))));
  }
}
