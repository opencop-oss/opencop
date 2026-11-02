// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.business.usecase.event;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.opencop.core.api.event.business.model.HistoricalPreflightPlan;
import com.opencop.core.sdk.model.SubjectType;
import com.opencop.core.sdk.model.history.HistoricalFilterRequest;
import com.opencop.core.sdk.model.history.HistoricalResolution;
import com.opencop.core.sdk.model.history.HistoricalSubjectRequest;
import com.opencop.core.sdk.model.history.HistoricalTracksRequest;
import com.opencop.core.sdk.model.job.JobIssuer;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HistoricalTracksJobSubmissionFactoryTest {

  private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440120");
  private static final UUID REQUEST_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440130");
  private static final UUID SOURCE_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440121");
  private static final UUID LAYER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440122");
  private static final Instant REQUESTED_AT = Instant.parse("2026-04-06T09:59:59Z");

  private final HistoricalTracksJobSubmissionFactory factory =
      new HistoricalTracksJobSubmissionFactory();

  @Test
  void create_mapsRequestPlanPrincipalAndSubjectsToSubmission() {
    var filter = new HistoricalFilterRequest("status", Map.of("value", "active"));

    var submission =
        factory.create(
            principal(),
            request(List.of(filter)),
            plan(),
            List.of(sourceSubject(), layerSubject()),
            REQUEST_ID,
            REQUESTED_AT);

    assertEquals(REQUEST_ID, submission.requestId());
    assertEquals(JobIssuer.user(USER_ID), submission.issuer());
    assertEquals(2, submission.subjects().size());
    assertEquals(
        deterministicChildJobId(REQUEST_ID, SubjectType.SOURCE, SOURCE_ID),
        submission.subjects().getFirst().jobId());
    assertEquals(SubjectType.SOURCE, submission.subjects().getFirst().subjectType());
    assertEquals(SOURCE_ID, submission.subjects().getFirst().subjectId());
    assertEquals(
        deterministicChildJobId(REQUEST_ID, SubjectType.LAYER, LAYER_ID),
        submission.subjects().get(1).jobId());
    assertEquals(SubjectType.LAYER, submission.subjects().get(1).subjectType());
    assertEquals(LAYER_ID, submission.subjects().get(1).subjectId());
    assertEquals(USER_ID, submission.requestedBy());
    assertEquals(REQUESTED_AT, submission.requestedAt());
    assertEquals(Instant.parse("2026-04-06T10:00:00Z"), submission.requestedFrom());
    assertEquals(Instant.parse("2026-04-06T11:00:00Z"), submission.requestedTo());
    assertEquals(Instant.parse("2026-04-06T10:00:00Z"), submission.effectiveFrom());
    assertEquals(Instant.parse("2026-04-06T11:00:00Z"), submission.effectiveTo());
    assertEquals(HistoricalResolution.MINUTE_1, submission.resolution());
    assertEquals(60, submission.plannedChunks());
    assertEquals(List.of(filter), submission.filters());
  }

  @Test
  void create_withSingleSubjectAcceptsSingleElementList() {
    var submission =
        factory.create(
            principal(), request(null), plan(), List.of(layerSubject()), REQUEST_ID, REQUESTED_AT);

    assertEquals(1, submission.subjects().size());
    assertEquals(SubjectType.LAYER, submission.subjects().getFirst().subjectType());
    assertEquals(LAYER_ID, submission.subjects().getFirst().subjectId());
    assertEquals(
        deterministicChildJobId(REQUEST_ID, SubjectType.LAYER, LAYER_ID),
        submission.subjects().getFirst().jobId());
  }

  @Test
  void create_whenRequestFiltersAreNull_normalizesToEmptyFilters() {
    var submission =
        factory.create(
            principal(), request(null), plan(), List.of(sourceSubject()), REQUEST_ID, REQUESTED_AT);

    assertEquals(List.of(), submission.filters());
  }

  private static HistoricalPreflightPlan plan() {
    return new HistoricalPreflightPlan(
        HistoricalResolution.MINUTE_1,
        Instant.parse("2026-04-06T10:00:00Z"),
        Instant.parse("2026-04-06T11:00:00Z"),
        60);
  }

  private static HistoricalSubjectRequest sourceSubject() {
    return new HistoricalSubjectRequest(SubjectType.SOURCE, SOURCE_ID);
  }

  private static HistoricalSubjectRequest layerSubject() {
    return new HistoricalSubjectRequest(SubjectType.LAYER, LAYER_ID);
  }

  private static HistoricalTracksRequest request(List<HistoricalFilterRequest> filters) {
    return request(filters, sourceSubject());
  }

  private static HistoricalTracksRequest request(
      List<HistoricalFilterRequest> filters, HistoricalSubjectRequest requestedSubject) {
    return new HistoricalTracksRequest(
        Instant.parse("2026-04-06T10:00:00Z"),
        Instant.parse("2026-04-06T11:00:00Z"),
        filters,
        List.of(requestedSubject));
  }

  private static OpenCopPrincipal principal() {
    return com.opencop.core.sdk.component.security.TestPrincipals.oidcPrincipal(USER_ID);
  }

  private static UUID deterministicChildJobId(
      UUID requestId, SubjectType subjectType, UUID subjectId) {
    return UUID.nameUUIDFromBytes(
        (requestId + ":" + subjectType + ":" + subjectId).getBytes(StandardCharsets.UTF_8));
  }
}
