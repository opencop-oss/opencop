// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.business.policy;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.opencop.core.api.event.business.model.HistoricalPreflightPlan;
import com.opencop.core.api.event.business.usecase.event.HistoricalErrorCode;
import com.opencop.core.sdk.model.SubjectType;
import com.opencop.core.sdk.model.history.HistoricalFilterRequest;
import com.opencop.core.sdk.model.history.HistoricalResolution;
import com.opencop.core.sdk.model.history.HistoricalSubjectRequest;
import com.opencop.core.sdk.model.history.HistoricalTracksRequest;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class HistoricalTracksRequestPolicyTest {

  private static final UUID SOURCE_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440121");
  private static final UUID LAYER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440122");
  private static final Instant WINDOW_START = Instant.parse("2026-04-01T00:00:00Z");
  private static final Instant WINDOW_END_AT_RANGE_LIMIT = Instant.parse("2026-05-01T00:00:00Z");
  private static final Instant WINDOW_END_AFTER_RANGE_LIMIT = Instant.parse("2026-05-01T00:00:01Z");
  private static final long CHUNK_COUNT_AT_LIMIT = 720;
  private static final long CHUNK_COUNT_AFTER_LIMIT = 721;

  private final HistoricalTracksRequestPolicy policy = new HistoricalTracksRequestPolicy();

  @Test
  void validateShape_whenStartIsNotBeforeEnd_throwsBadRequest() {
    var request =
        new HistoricalTracksRequest(
            Instant.parse("2026-04-06T12:00:00Z"),
            Instant.parse("2026-04-06T12:00:00Z"),
            List.of(),
            List.of(sourceSubject()));

    var ex = assertThrows(GlobalOpenCopException.class, () -> policy.validateShape(request));

    assertError(ex, HttpStatus.BAD_REQUEST, HistoricalErrorCode.HISTORICAL_RANGE_INVALID);
  }

  @Test
  void validateShape_whenSourcesIsEmpty_throwsBadRequest() {
    var request =
        new HistoricalTracksRequest(
            Instant.parse("2026-04-06T10:00:00Z"),
            Instant.parse("2026-04-06T11:00:00Z"),
            List.of(),
            List.of());

    var ex = assertThrows(GlobalOpenCopException.class, () -> policy.validateShape(request));

    assertError(ex, HttpStatus.BAD_REQUEST, HistoricalErrorCode.HISTORICAL_SOURCES_REQUIRED);
  }

  @Test
  void validateShape_whenSourcesContainsNullEntry_throwsBadRequest() {
    var subjects = new ArrayList<HistoricalSubjectRequest>();
    subjects.add(sourceSubject());
    subjects.add(null);
    var request =
        new HistoricalTracksRequest(
            Instant.parse("2026-04-06T10:00:00Z"),
            Instant.parse("2026-04-06T11:00:00Z"),
            List.of(),
            subjects);

    var ex = assertThrows(GlobalOpenCopException.class, () -> policy.validateShape(request));

    assertError(ex, HttpStatus.BAD_REQUEST, HistoricalErrorCode.HISTORICAL_SOURCES_CONTAINS_NULL);
  }

  @Test
  void validateShape_whenFiltersContainsNullEntry_throwsBadRequest() {
    var filters = new ArrayList<HistoricalFilterRequest>();
    filters.add(null);
    var request =
        new HistoricalTracksRequest(
            Instant.parse("2026-04-06T10:00:00Z"),
            Instant.parse("2026-04-06T11:00:00Z"),
            filters,
            List.of(sourceSubject()));

    var ex = assertThrows(GlobalOpenCopException.class, () -> policy.validateShape(request));

    assertError(ex, HttpStatus.BAD_REQUEST, HistoricalErrorCode.HISTORICAL_FILTERS_CONTAINS_NULL);
  }

  @Test
  void validateShape_whenSourcesContainsDuplicates_throwsBadRequest() {
    var request =
        new HistoricalTracksRequest(
            Instant.parse("2026-04-06T10:00:00Z"),
            Instant.parse("2026-04-06T11:00:00Z"),
            List.of(),
            List.of(sourceSubject(), sourceSubject()));

    var ex = assertThrows(GlobalOpenCopException.class, () -> policy.validateShape(request));

    assertError(ex, HttpStatus.BAD_REQUEST, HistoricalErrorCode.HISTORICAL_SOURCES_DUPLICATE);
  }

  @Test
  void validateShape_whenRequestIsWellFormed_acceptsEmptyFilters() {
    assertDoesNotThrow(() -> policy.validateShape(validRequest()));
  }

  @Test
  void rejectUnsupportedFeatures_whenLayerSubjectWithEmptyFilters_doesNotThrow() {
    var request =
        new HistoricalTracksRequest(
            Instant.parse("2026-04-06T10:00:00Z"),
            Instant.parse("2026-04-06T11:00:00Z"),
            List.of(),
            List.of(new HistoricalSubjectRequest(SubjectType.LAYER, LAYER_ID)));

    assertDoesNotThrow(() -> policy.rejectUnsupportedFeatures(request));
  }

  @Test
  void rejectUnsupportedFeatures_whenFiltersAreNonEmpty_throwsUnprocessableEntity() {
    var request =
        new HistoricalTracksRequest(
            Instant.parse("2026-04-06T10:00:00Z"),
            Instant.parse("2026-04-06T11:00:00Z"),
            List.of(new HistoricalFilterRequest("status", Map.of("value", "active"))),
            List.of(sourceSubject()));

    var ex =
        assertThrows(GlobalOpenCopException.class, () -> policy.rejectUnsupportedFeatures(request));

    assertError(
        ex, HttpStatus.UNPROCESSABLE_ENTITY, HistoricalErrorCode.HISTORICAL_FILTERS_NOT_SUPPORTED);
  }

  @Test
  void
      rejectUnsupportedFeatures_whenMixedSubjectsAndFiltersAreNonEmpty_throwsUnprocessableEntity() {
    var request =
        new HistoricalTracksRequest(
            Instant.parse("2026-04-06T10:00:00Z"),
            Instant.parse("2026-04-06T11:00:00Z"),
            List.of(new HistoricalFilterRequest("status", Map.of("value", "active"))),
            List.of(
                sourceSubject(),
                new HistoricalSubjectRequest(
                    SubjectType.LAYER, UUID.fromString("550e8400-e29b-41d4-a716-446655440124"))));

    var ex =
        assertThrows(GlobalOpenCopException.class, () -> policy.rejectUnsupportedFeatures(request));

    assertError(
        ex, HttpStatus.UNPROCESSABLE_ENTITY, HistoricalErrorCode.HISTORICAL_FILTERS_NOT_SUPPORTED);
  }

  @Test
  void validateGuardrails_whenRequestRangeExceedsThirtyDays_throwsUnprocessableEntity() {
    var request =
        new HistoricalTracksRequest(
            Instant.parse("2026-04-01T00:00:00Z"),
            Instant.parse("2026-05-02T00:00:01Z"),
            List.of(),
            List.of(sourceSubject()));

    var ex =
        assertThrows(
            GlobalOpenCopException.class, () -> policy.validateGuardrails(request, validPlan()));

    assertError(
        ex, HttpStatus.UNPROCESSABLE_ENTITY, HistoricalErrorCode.HISTORICAL_RANGE_TOO_LARGE);
  }

  @Test
  void validateGuardrails_whenChunkCountWouldExceedLimit_throwsUnprocessableEntity() {
    var plan =
        new HistoricalPreflightPlan(
            HistoricalResolution.HOUR_1,
            Instant.parse("2026-04-01T00:00:00Z"),
            Instant.parse("2026-05-01T01:00:00Z"),
            721);

    var ex =
        assertThrows(
            GlobalOpenCopException.class, () -> policy.validateGuardrails(validRequest(), plan));

    assertError(
        ex, HttpStatus.UNPROCESSABLE_ENTITY, HistoricalErrorCode.HISTORICAL_TOO_MANY_CHUNKS);
  }

  @Test
  void validateWindowGuardrails_whenRangeAndChunkCountAreAtLimits_doesNotThrow() {
    assertDoesNotThrow(
        () ->
            policy.validateWindowGuardrails(
                WINDOW_START, WINDOW_END_AT_RANGE_LIMIT, CHUNK_COUNT_AT_LIMIT));
  }

  @Test
  void validateWindowGuardrails_whenRangeExceedsThirtyDays_throwsUnprocessableEntity() {
    var ex =
        assertThrows(
            GlobalOpenCopException.class,
            () ->
                policy.validateWindowGuardrails(
                    WINDOW_START, WINDOW_END_AFTER_RANGE_LIMIT, CHUNK_COUNT_AT_LIMIT));

    assertError(
        ex, HttpStatus.UNPROCESSABLE_ENTITY, HistoricalErrorCode.HISTORICAL_RANGE_TOO_LARGE);
  }

  @Test
  void validateWindowGuardrails_whenChunkCountExceedsLimit_throwsUnprocessableEntity() {
    var ex =
        assertThrows(
            GlobalOpenCopException.class,
            () ->
                policy.validateWindowGuardrails(
                    WINDOW_START, WINDOW_END_AT_RANGE_LIMIT, CHUNK_COUNT_AFTER_LIMIT));

    assertError(
        ex, HttpStatus.UNPROCESSABLE_ENTITY, HistoricalErrorCode.HISTORICAL_TOO_MANY_CHUNKS);
  }

  private static HistoricalTracksRequest validRequest() {
    return new HistoricalTracksRequest(
        Instant.parse("2026-04-06T10:00:00Z"),
        Instant.parse("2026-04-06T11:00:00Z"),
        List.of(),
        List.of(sourceSubject()));
  }

  private static HistoricalPreflightPlan validPlan() {
    return new HistoricalPreflightPlan(
        HistoricalResolution.MINUTE_1,
        Instant.parse("2026-04-06T10:00:00Z"),
        Instant.parse("2026-04-06T11:00:00Z"),
        60);
  }

  private static HistoricalSubjectRequest sourceSubject() {
    return new HistoricalSubjectRequest(SubjectType.SOURCE, SOURCE_ID);
  }

  private static void assertError(
      GlobalOpenCopException ex, HttpStatus status, HistoricalErrorCode code) {
    assertEquals(status, ex.getHttpStatus());
    assertEquals(code.name(), ex.getCode());
  }
}
