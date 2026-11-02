// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.history.model.job;

import com.opencop.core.sdk.model.SubjectType;
import com.opencop.core.sdk.model.job.HistoricalTracksJobSubmission;
import com.opencop.core.sdk.model.job.HistoricalTracksSubject;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public record HistoricalJobSubjectIdentity(SubjectType subjectType, UUID subjectId) {

  private static final Comparator<HistoricalTracksSubject> CANONICAL_SUBJECT_ORDER =
      Comparator.comparing((HistoricalTracksSubject subject) -> subject.subjectType().name())
          .thenComparing(subject -> subject.subjectId().toString());

  public static HistoricalJobSubjectIdentity fromParentJob(HistoricalTracksJobSubmission job) {
    List<HistoricalTracksSubject> canonicalSubjects =
        job.subjects().stream().sorted(CANONICAL_SUBJECT_ORDER).toList();
    if (canonicalSubjects.size() == 1) {
      HistoricalTracksSubject subject = canonicalSubjects.iterator().next();
      return new HistoricalJobSubjectIdentity(subject.subjectType(), subject.subjectId());
    }
    return new HistoricalJobSubjectIdentity(
        aggregateSubjectType(canonicalSubjects), aggregateSubjectId(canonicalSubjects));
  }

  private static SubjectType aggregateSubjectType(List<HistoricalTracksSubject> canonicalSubjects) {
    return canonicalSubjects.stream()
        .map(HistoricalTracksSubject::subjectType)
        .distinct()
        .min(Comparator.comparing(Enum::name))
        .orElseThrow();
  }

  private static UUID aggregateSubjectId(List<HistoricalTracksSubject> canonicalSubjects) {
    String canonicalSubjectSet =
        canonicalSubjects.stream()
            .map(subject -> subject.subjectType().name() + ":" + subject.subjectId())
            .reduce((left, right) -> left + "|" + right)
            .orElseThrow();
    return UUID.nameUUIDFromBytes(canonicalSubjectSet.getBytes(StandardCharsets.UTF_8));
  }
}
