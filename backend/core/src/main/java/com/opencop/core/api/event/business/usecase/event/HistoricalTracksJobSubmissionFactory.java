// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.business.usecase.event;

import com.opencop.core.api.event.business.model.HistoricalPreflightPlan;
import com.opencop.core.sdk.model.SubjectType;
import com.opencop.core.sdk.model.history.HistoricalFilterRequest;
import com.opencop.core.sdk.model.history.HistoricalSubjectRequest;
import com.opencop.core.sdk.model.history.HistoricalTracksRequest;
import com.opencop.core.sdk.model.job.HistoricalTracksJobSubmission;
import com.opencop.core.sdk.model.job.HistoricalTracksSubject;
import com.opencop.core.sdk.model.job.JobIssuer;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class HistoricalTracksJobSubmissionFactory {

  public HistoricalTracksJobSubmission create(
      OpenCopPrincipal principal,
      HistoricalTracksRequest request,
      HistoricalPreflightPlan plan,
      List<HistoricalSubjectRequest> subjects,
      UUID requestId,
      Instant requestedAt) {
    var orderedSubjects =
        subjects.stream()
            .map(
                subject ->
                    new HistoricalTracksSubject(
                        deterministicChildJobId(
                            requestId, subject.subjectType(), subject.subjectId()),
                        subject.subjectType(),
                        subject.subjectId()))
            .toList();

    return HistoricalTracksJobSubmission.create(
        requestId,
        JobIssuer.user(principal.internalUserId()),
        orderedSubjects,
        principal.internalUserId(),
        requestedAt,
        request.start(),
        request.end(),
        plan.effectiveFrom(),
        plan.effectiveTo(),
        plan.resolution(),
        plan.chunkCount(),
        normalizedFilters(request.filters()));
  }

  private List<HistoricalFilterRequest> normalizedFilters(List<HistoricalFilterRequest> filters) {
    return filters == null ? List.of() : filters;
  }

  private UUID deterministicChildJobId(UUID requestId, SubjectType subjectType, UUID subjectId) {
    return UUID.nameUUIDFromBytes(
        (requestId + ":" + subjectType + ":" + subjectId).getBytes(StandardCharsets.UTF_8));
  }
}
