// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.opencop.core.sdk.model.SubjectType;
import com.opencop.core.sdk.model.history.HistoricalResolution;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.exc.ValueInstantiationException;

class HistoricalTracksJobSubmissionTest {

  private static final UUID REQUEST_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
  private static final JobIssuer ISSUER =
      JobIssuer.user(UUID.fromString("550e8400-e29b-41d4-a716-446655449999"));
  private static final Instant REQUESTED_AT = Instant.parse("2026-05-25T00:00:00Z");
  private static final Instant REQUESTED_FROM = Instant.parse("2026-05-24T00:00:00Z");
  private static final Instant REQUESTED_TO = Instant.parse("2026-05-25T00:00:00Z");
  private static final Instant EFFECTIVE_FROM = Instant.parse("2026-05-24T00:00:00Z");
  private static final Instant EFFECTIVE_TO = Instant.parse("2026-05-25T00:00:00Z");
  private static final UUID LEGACY_REQUEST_ID =
      UUID.fromString("550e8400-e29b-41d4-a716-446655440200");
  private static final UUID LEGACY_SUBJECT_ID =
      UUID.fromString("550e8400-e29b-41d4-a716-446655440300");
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void key_sameRequestedByDifferentSubjectIds_returnsSameUserKey() {
    UUID requestedBy = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");
    var firstSubmission =
        buildSubmission(
            UUID.fromString("550e8400-e29b-41d4-a716-446655440010"),
            requestedBy,
            SubjectType.SOURCE);
    var secondSubmission =
        buildSubmission(
            UUID.fromString("550e8400-e29b-41d4-a716-446655440011"),
            requestedBy,
            SubjectType.LAYER);

    assertEquals("USER:550e8400-e29b-41d4-a716-446655440001", firstSubmission.key());
    assertEquals("USER:550e8400-e29b-41d4-a716-446655440001", secondSubmission.key());
  }

  @Test
  void key_differentRequestedBy_returnsDifferentKeys() {
    UUID subjectId = UUID.fromString("550e8400-e29b-41d4-a716-446655440020");
    SubjectType subjectType = SubjectType.SOURCE;
    var firstSubmission =
        buildSubmission(
            subjectId, UUID.fromString("550e8400-e29b-41d4-a716-446655440001"), subjectType);
    var secondSubmission =
        buildSubmission(
            subjectId, UUID.fromString("550e8400-e29b-41d4-a716-446655440002"), subjectType);

    assertNotEquals(firstSubmission.key(), secondSubmission.key());
  }

  @Test
  void key_excludesSubjectTypeAndSubjectId() {
    UUID subjectId = UUID.fromString("550e8400-e29b-41d4-a716-446655440003");
    var submission =
        buildSubmission(
            subjectId, UUID.fromString("550e8400-e29b-41d4-a716-446655440001"), SubjectType.SOURCE);

    String key = submission.key();

    assertFalse(key.contains("SOURCE"));
    assertFalse(key.contains("550e8400-e29b-41d4-a716-446655440003"));
  }

  @Test
  void roundTripsThroughJobSubmission_withOrderedMixedLayerAndSourceSubjects() throws Exception {
    var submission =
        HistoricalTracksJobSubmission.create(
            REQUEST_ID,
            ISSUER,
            List.of(
                new HistoricalTracksSubject(
                    UUID.fromString("a5c4adce-6f74-4cc6-8ce7-5adf6c4f0001"),
                    SubjectType.LAYER,
                    UUID.fromString("550e8400-e29b-41d4-a716-446655440010")),
                new HistoricalTracksSubject(
                    UUID.fromString("550e8400-e29b-41d4-a716-446655440011"),
                    SubjectType.LAYER,
                    UUID.fromString("550e8400-e29b-41d4-a716-446655440012")),
                new HistoricalTracksSubject(
                    UUID.fromString("550e8400-e29b-41d4-a716-446655440013"),
                    SubjectType.LAYER,
                    UUID.fromString("550e8400-e29b-41d4-a716-446655440014")),
                new HistoricalTracksSubject(
                    UUID.fromString("550e8400-e29b-41d4-a716-446655440015"),
                    SubjectType.SOURCE,
                    UUID.fromString("550e8400-e29b-41d4-a716-446655440016")),
                new HistoricalTracksSubject(
                    UUID.fromString("550e8400-e29b-41d4-a716-446655440017"),
                    SubjectType.SOURCE,
                    UUID.fromString("550e8400-e29b-41d4-a716-446655440018"))),
            UUID.fromString("550e8400-e29b-41d4-a716-446655440099"),
            REQUESTED_AT,
            REQUESTED_FROM,
            REQUESTED_TO,
            EFFECTIVE_FROM,
            EFFECTIVE_TO,
            HistoricalResolution.MINUTE_1,
            1L,
            List.of());

    JobSubmission deserialized =
        objectMapper.readValue(objectMapper.writeValueAsBytes(submission), JobSubmission.class);
    var historicalSubmission = assertInstanceOf(HistoricalTracksJobSubmission.class, deserialized);
    var subjects = historicalSubmission.subjects();

    assertEquals(submission.subjects(), subjects);
    assertEquals(submission.subjects().size(), 5);
    assertEquals(SubjectType.LAYER, subjects.get(0).subjectType());
    assertEquals(SubjectType.LAYER, subjects.get(2).subjectType());
    assertEquals(SubjectType.SOURCE, subjects.get(4).subjectType());
  }

  @Test
  void deserializesLegacySingleSubjectPayloadThroughJobSubmissionTypeInfo() throws Exception {
    String legacyPayload =
        """
            {"type":"HISTORICAL_TRACKS","jobId":"550e8400-e29b-41d4-a716-446655440099","requestId":"550e8400-e29b-41d4-a716-446655440200","issuer":{"type":"USER","id":"550e8400-e29b-41d4-a716-446655440201"},"subjectType":"SOURCE","subjectId":"550e8400-e29b-41d4-a716-446655440300","requestedBy":"550e8400-e29b-41d4-a716-446655440301","requestedAt":"2026-05-25T00:00:00Z","requestedFrom":"2026-05-24T00:00:00Z","requestedTo":"2026-05-25T00:00:00Z","effectiveFrom":"2026-05-24T00:00:00Z","effectiveTo":"2026-05-25T00:00:00Z","resolution":"MINUTE_1","plannedChunks":1,"filters":[]}""";

    JobSubmission deserialized =
        objectMapper.readValue(legacyPayload.getBytes(StandardCharsets.UTF_8), JobSubmission.class);
    var historicalSubmission = assertInstanceOf(HistoricalTracksJobSubmission.class, deserialized);

    assertEquals(1, historicalSubmission.subjects().size());
    assertEquals(SubjectType.SOURCE, historicalSubmission.subjects().getFirst().subjectType());
    assertEquals(LEGACY_SUBJECT_ID, historicalSubmission.subjects().getFirst().subjectId());
    assertEquals(LEGACY_REQUEST_ID, historicalSubmission.requestId());
  }

  @Test
  void rejectsPayloadWithoutAnySubjects() {
    String legacySubjectPayload =
        """
            {"type":"HISTORICAL_TRACKS","jobId":"550e8400-e29b-41d4-a716-446655440099","requestId":"550e8400-e29b-41d4-a716-446655440200","issuer":{"type":"USER","id":"550e8400-e29b-41d4-a716-446655440201"},"subjects":[],"requestedBy":"550e8400-e29b-41d4-a716-446655440301","requestedAt":"2026-05-25T00:00:00Z","requestedFrom":"2026-05-24T00:00:00Z","requestedTo":"2026-05-25T00:00:00Z","effectiveFrom":"2026-05-24T00:00:00Z","effectiveTo":"2026-05-25T00:00:00Z","resolution":"MINUTE_1","plannedChunks":1,"filters":[]}""";

    assertThrows(
        ValueInstantiationException.class,
        () ->
            objectMapper.readValue(
                legacySubjectPayload.getBytes(StandardCharsets.UTF_8), JobSubmission.class));
  }

  private HistoricalTracksJobSubmission buildSubmission(
      UUID subjectId, UUID requestedBy, SubjectType subjectType) {
    var subject =
        new HistoricalTracksSubject(
            UUID.fromString("550e8400-e29b-41d4-a716-446655440005"), subjectType, subjectId);
    return new HistoricalTracksJobSubmission(
        UUID.randomUUID(),
        REQUEST_ID,
        ISSUER,
        List.of(subject),
        requestedBy,
        REQUESTED_AT,
        REQUESTED_FROM,
        REQUESTED_TO,
        EFFECTIVE_FROM,
        EFFECTIVE_TO,
        HistoricalResolution.MINUTE_1,
        1L,
        List.of());
  }
}
