// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.business.policy;

import static com.opencop.core.api.event.business.usecase.event.HistoricalErrorCode.HISTORICAL_FILTERS_CONTAINS_NULL;
import static com.opencop.core.api.event.business.usecase.event.HistoricalErrorCode.HISTORICAL_FILTERS_NOT_SUPPORTED;
import static com.opencop.core.api.event.business.usecase.event.HistoricalErrorCode.HISTORICAL_RANGE_INVALID;
import static com.opencop.core.api.event.business.usecase.event.HistoricalErrorCode.HISTORICAL_RANGE_TOO_LARGE;
import static com.opencop.core.api.event.business.usecase.event.HistoricalErrorCode.HISTORICAL_SOURCES_CONTAINS_NULL;
import static com.opencop.core.api.event.business.usecase.event.HistoricalErrorCode.HISTORICAL_SOURCES_DUPLICATE;
import static com.opencop.core.api.event.business.usecase.event.HistoricalErrorCode.HISTORICAL_SOURCES_REQUIRED;
import static com.opencop.core.api.event.business.usecase.event.HistoricalErrorCode.HISTORICAL_TOO_MANY_CHUNKS;

import com.opencop.core.api.event.business.model.HistoricalPreflightPlan;
import com.opencop.core.sdk.model.history.HistoricalTracksRequest;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class HistoricalTracksRequestPolicy {

  static final int MAX_RANGE_DAYS = 30;
  static final long MAX_CHUNKS_PER_JOB = 720;
  private static final String SUBJECT_KEY_SEPARATOR = ":";
  private static final String REQUEST_REQUIRED_MESSAGE = "request";

  // 400 for shape violations (null / empty / duplicate request fields).
  // 422 for well-formed requests that exceed a quota or semantic rule lives in validateGuardrails.
  public void validateShape(HistoricalTracksRequest request) {
    Objects.requireNonNull(request, REQUEST_REQUIRED_MESSAGE);
    if (!request.start().isBefore(request.end())) {
      throw new GlobalOpenCopException(
          HISTORICAL_RANGE_INVALID.name(), "start must be before end", HttpStatus.BAD_REQUEST);
    }
    if (request.sources().isEmpty()) {
      throw new GlobalOpenCopException(
          HISTORICAL_SOURCES_REQUIRED.name(), "sources must not be empty", HttpStatus.BAD_REQUEST);
    }

    Set<String> seenSubjects = new HashSet<>();
    for (var subject : request.sources()) {
      if (subject == null) {
        throw new GlobalOpenCopException(
            HISTORICAL_SOURCES_CONTAINS_NULL.name(),
            "sources must not contain null entries",
            HttpStatus.BAD_REQUEST);
      }
      var subjectKey = subject.subjectType().name() + SUBJECT_KEY_SEPARATOR + subject.subjectId();
      if (!seenSubjects.add(subjectKey)) {
        throw new GlobalOpenCopException(
            HISTORICAL_SOURCES_DUPLICATE.name(),
            "sources must not contain duplicate subjects",
            HttpStatus.BAD_REQUEST);
      }
    }

    if (request.filters() != null) {
      for (var filter : request.filters()) {
        if (filter == null) {
          throw new GlobalOpenCopException(
              HISTORICAL_FILTERS_CONTAINS_NULL.name(),
              "filters must not contain null entries",
              HttpStatus.BAD_REQUEST);
        }
      }
    }
  }

  public void rejectUnsupportedFeatures(HistoricalTracksRequest request) {
    if (request.filters() != null && !request.filters().isEmpty()) {
      throw new GlobalOpenCopException(
          HISTORICAL_FILTERS_NOT_SUPPORTED.name(),
          "Filters are not supported in this release",
          HttpStatus.UNPROCESSABLE_ENTITY);
    }
  }

  // 422 for well-formed requests that exceed a quota or semantic rule.
  // 400 for malformed-shape rejection lives in validateShape.
  public void validateGuardrails(HistoricalTracksRequest request, HistoricalPreflightPlan plan) {
    validateWindowGuardrails(request.start(), request.end(), plan.chunkCount());
  }

  public void validateWindowGuardrails(Instant start, Instant end, long chunkCount) {
    if (chunkCount > MAX_CHUNKS_PER_JOB) {
      throw new GlobalOpenCopException(
          HISTORICAL_TOO_MANY_CHUNKS.name(),
          "Historical request exceeds the maximum allowed chunk count of 720",
          HttpStatus.UNPROCESSABLE_ENTITY);
    }

    if (Duration.between(start, end).compareTo(Duration.ofDays(MAX_RANGE_DAYS)) > 0) {
      throw new GlobalOpenCopException(
          HISTORICAL_RANGE_TOO_LARGE.name(),
          "Historical request exceeds the maximum allowed range of 30 days",
          HttpStatus.UNPROCESSABLE_ENTITY);
    }
  }
}
