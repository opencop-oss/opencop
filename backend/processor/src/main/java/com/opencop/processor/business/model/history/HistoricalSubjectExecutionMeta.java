// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.model.history;

import com.opencop.core.sdk.model.SubjectType;
import com.opencop.core.sdk.model.history.HistoricalFilterRequest;
import com.opencop.core.sdk.model.history.HistoricalResolution;
import com.opencop.core.sdk.model.job.JobIssuer;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkWindowPlan;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record HistoricalSubjectExecutionMeta(
    UUID laneJobId,
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
    HistoricalChunkWindowPlan windowPlan) {

  public HistoricalSubjectExecutionMeta {
    Objects.requireNonNull(laneJobId, "laneJobId");
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
    Objects.requireNonNull(subjectType, "subjectType");
    Objects.requireNonNull(subjectId, "subjectId");
    Objects.requireNonNull(windowPlan, "windowPlan");
  }
}
