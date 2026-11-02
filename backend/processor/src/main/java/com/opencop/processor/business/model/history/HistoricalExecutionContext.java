// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.model.history;

import com.opencop.core.sdk.model.SubjectType;
import com.opencop.core.sdk.model.history.HistoricalFilterRequest;
import com.opencop.core.sdk.model.history.HistoricalResolution;
import com.opencop.core.sdk.model.job.HistoricalTracksJobSubmission;
import com.opencop.core.sdk.model.job.HistoricalTracksSubject;
import com.opencop.core.sdk.model.job.JobIssuer;
import com.opencop.core.sdk.model.job.JobIssuerType;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkWindowPlan;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record HistoricalExecutionContext(
    UUID jobId,
    UUID parentJobId,
    UUID requestId,
    JobIssuer issuer,
    UUID requestedBy,
    Instant requestedAt,
    Instant requestedFrom,
    Instant requestedTo,
    Instant effectiveFrom,
    Instant effectiveTo,
    HistoricalResolution resolution,
    long plannedChunks,
    List<HistoricalFilterRequest> filters,
    SubjectType subjectType,
    UUID subjectId,
    List<UUID> sourceIds,
    HistoricalChunkWindowPlan windowPlan) {

  private static final Comparator<HistoricalTracksSubject> CANONICAL_SUBJECT_ORDER =
      Comparator.comparing((HistoricalTracksSubject subject) -> subject.subjectType().name())
          .thenComparing(subject -> subject.subjectId().toString());

  public HistoricalExecutionContext {
    Objects.requireNonNull(jobId, "jobId");
    Objects.requireNonNull(parentJobId, "parentJobId");
    Objects.requireNonNull(requestId, "requestId");
    Objects.requireNonNull(issuer, "issuer");
    Objects.requireNonNull(requestedBy, "requestedBy");
    Objects.requireNonNull(requestedAt, "requestedAt");
    Objects.requireNonNull(requestedFrom, "requestedFrom");
    Objects.requireNonNull(requestedTo, "requestedTo");
    Objects.requireNonNull(effectiveFrom, "effectiveFrom");
    Objects.requireNonNull(effectiveTo, "effectiveTo");
    Objects.requireNonNull(resolution, "resolution");
    filters = List.copyOf(filters);
    Objects.requireNonNull(sourceIds, "sourceIds");
    sourceIds = List.copyOf(sourceIds);
  }

  public static HistoricalExecutionContext from(HistoricalTracksJobSubmission job) {
    List<HistoricalTracksSubject> canonicalSubjects =
        job.subjects().stream().sorted(CANONICAL_SUBJECT_ORDER).toList();
    return new HistoricalExecutionContext(
        job.jobId(),
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
        parentSubjectType(canonicalSubjects),
        parentSubjectId(canonicalSubjects),
        canonicalSubjects.stream().map(subject -> subject.subjectId()).distinct().toList(),
        null);
  }

  public static HistoricalExecutionContext from(HistoricalSubjectExecutionMeta laneMetadata) {
    return new HistoricalExecutionContext(
        laneMetadata.laneJobId(),
        laneMetadata.parentJobId(),
        laneMetadata.requestId(),
        laneMetadata.issuer(),
        laneMetadata.requestedBy(),
        laneMetadata.requestedAt(),
        laneMetadata.requestedFrom(),
        laneMetadata.requestedTo(),
        laneMetadata.effectiveFrom(),
        laneMetadata.effectiveTo(),
        laneMetadata.resolution(),
        laneMetadata.plannedChunks(),
        laneMetadata.filters(),
        laneMetadata.subjectType(),
        laneMetadata.subjectId(),
        List.of(laneMetadata.subjectId()),
        laneMetadata.windowPlan());
  }

  public boolean userInitiated() {
    return issuer.type() == JobIssuerType.USER;
  }

  private static SubjectType parentSubjectType(List<HistoricalTracksSubject> canonicalSubjects) {
    if (canonicalSubjects.size() == 1) {
      return canonicalSubjects.getFirst().subjectType();
    }
    return canonicalSubjects.stream()
        .map(HistoricalTracksSubject::subjectType)
        .distinct()
        .min(Comparator.comparing(Enum::name))
        .orElseThrow();
  }

  private static UUID parentSubjectId(List<HistoricalTracksSubject> canonicalSubjects) {
    if (canonicalSubjects.size() == 1) {
      return canonicalSubjects.getFirst().subjectId();
    }
    String canonicalSubjectSet =
        canonicalSubjects.stream()
            .map(subject -> subject.subjectType().name() + ":" + subject.subjectId())
            .reduce((left, right) -> left + "|" + right)
            .orElseThrow();
    return UUID.nameUUIDFromBytes(canonicalSubjectSet.getBytes(StandardCharsets.UTF_8));
  }
}
