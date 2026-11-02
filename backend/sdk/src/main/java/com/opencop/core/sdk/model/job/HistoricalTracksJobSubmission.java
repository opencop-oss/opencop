// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.job;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.opencop.core.sdk.model.SubjectType;
import com.opencop.core.sdk.model.history.HistoricalFilterRequest;
import com.opencop.core.sdk.model.history.HistoricalResolution;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record HistoricalTracksJobSubmission(
    UUID jobId,
    UUID requestId,
    JobIssuer issuer,
    List<HistoricalTracksSubject> subjects,
    UUID requestedBy,
    Instant requestedAt,
    Instant requestedFrom,
    Instant requestedTo,
    Instant effectiveFrom,
    Instant effectiveTo,
    HistoricalResolution resolution,
    long plannedChunks,
    List<HistoricalFilterRequest> filters)
    implements JobSubmission {

  public static final String TOPIC = "opencop.history.jobs";
  private static final String USER_KEY_PREFIX = "USER:";

  public HistoricalTracksJobSubmission {
    Objects.requireNonNull(jobId, "jobId");
    Objects.requireNonNull(requestId, "requestId");
    Objects.requireNonNull(issuer, "issuer");
    Objects.requireNonNull(subjects, "subjects");
    if (subjects.isEmpty()) {
      throw new IllegalArgumentException("subjects must contain at least one subject");
    }
    Objects.requireNonNull(requestedBy, "requestedBy");
    Objects.requireNonNull(requestedAt, "requestedAt");
    Objects.requireNonNull(requestedFrom, "requestedFrom");
    Objects.requireNonNull(requestedTo, "requestedTo");
    Objects.requireNonNull(effectiveFrom, "effectiveFrom");
    Objects.requireNonNull(effectiveTo, "effectiveTo");
    Objects.requireNonNull(resolution, "resolution");
    Objects.requireNonNull(filters, "filters");
  }

  @JsonCreator
  public static HistoricalTracksJobSubmission fromJson(
      @JsonProperty("jobId") UUID jobId,
      @JsonProperty("requestId") UUID requestId,
      @JsonProperty("issuer") JobIssuer issuer,
      @JsonProperty("subjects") List<HistoricalTracksSubject> subjects,
      @JsonProperty("subjectType") SubjectType subjectType,
      @JsonProperty("subjectId") UUID subjectId,
      @JsonProperty("requestedBy") UUID requestedBy,
      @JsonProperty("requestedAt") Instant requestedAt,
      @JsonProperty("requestedFrom") Instant requestedFrom,
      @JsonProperty("requestedTo") Instant requestedTo,
      @JsonProperty("effectiveFrom") Instant effectiveFrom,
      @JsonProperty("effectiveTo") Instant effectiveTo,
      @JsonProperty("resolution") HistoricalResolution resolution,
      @JsonProperty("plannedChunks") long plannedChunks,
      @JsonProperty("filters") List<HistoricalFilterRequest> filters) {
    return new HistoricalTracksJobSubmission(
        jobId,
        requestId,
        issuer,
        normalizeSubjects(jobId, subjects, subjectType, subjectId),
        requestedBy,
        requestedAt,
        requestedFrom,
        requestedTo,
        effectiveFrom,
        effectiveTo,
        resolution,
        plannedChunks,
        List.copyOf(Optional.ofNullable(filters).orElse(List.of())));
  }

  private static List<HistoricalTracksSubject> normalizeSubjects(
      UUID jobId, List<HistoricalTracksSubject> subjects, SubjectType subjectType, UUID subjectId) {
    if (subjects != null && !subjects.isEmpty()) {
      return List.copyOf(subjects);
    }
    if (subjectType == null || subjectId == null) {
      throw new IllegalArgumentException("subjects or subjectType+subjectId required");
    }
    return List.of(
        new HistoricalTracksSubject(
            deterministicChildJobId(jobId, subjectType, subjectId), subjectType, subjectId));
  }

  private static UUID deterministicChildJobId(
      UUID parentJobId, SubjectType subjectType, UUID subjectId) {
    return Optional.ofNullable(parentJobId)
        .map(
            id ->
                UUID.nameUUIDFromBytes(
                    (id + ":" + subjectType + ":" + subjectId).getBytes(StandardCharsets.UTF_8)))
        .orElse(UUID.randomUUID());
  }

  public static HistoricalTracksJobSubmission create(
      UUID requestId,
      JobIssuer issuer,
      SubjectType subjectType,
      UUID subjectId,
      UUID requestedBy,
      Instant requestedAt,
      Instant requestedFrom,
      Instant requestedTo,
      Instant effectiveFrom,
      Instant effectiveTo,
      HistoricalResolution resolution,
      long plannedChunks,
      List<HistoricalFilterRequest> filters) {
    return new HistoricalTracksJobSubmission(
        UUID.randomUUID(),
        requestId,
        issuer,
        List.of(
            new HistoricalTracksSubject(
                deterministicChildJobId(requestId, subjectType, subjectId),
                subjectType,
                subjectId)),
        requestedBy,
        requestedAt,
        requestedFrom,
        requestedTo,
        effectiveFrom,
        effectiveTo,
        resolution,
        plannedChunks,
        List.copyOf(filters));
  }

  public static HistoricalTracksJobSubmission create(
      UUID requestId,
      JobIssuer issuer,
      List<HistoricalTracksSubject> subjects,
      UUID requestedBy,
      Instant requestedAt,
      Instant requestedFrom,
      Instant requestedTo,
      Instant effectiveFrom,
      Instant effectiveTo,
      HistoricalResolution resolution,
      long plannedChunks,
      List<HistoricalFilterRequest> filters) {
    return new HistoricalTracksJobSubmission(
        UUID.randomUUID(),
        requestId,
        issuer,
        List.copyOf(subjects),
        requestedBy,
        requestedAt,
        requestedFrom,
        requestedTo,
        effectiveFrom,
        effectiveTo,
        resolution,
        plannedChunks,
        List.copyOf(filters));
  }

  @Override
  public JobType jobType() {
    return JobType.HISTORICAL_TRACKS;
  }

  @Override
  public String topic() {
    return TOPIC;
  }

  @Override
  public String key() {
    return USER_KEY_PREFIX + requestedBy;
  }
}
