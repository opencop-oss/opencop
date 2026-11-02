// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.history;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.opencop.core.sdk.model.SubjectType;
import java.time.Instant;
import java.util.UUID;

public record HistoryJobPlanEvent(
    UUID targetUserId,
    UUID requestId,
    UUID jobId,
    SubjectType subjectType,
    UUID subjectId,
    UUID requestedBy,
    Instant requestedFrom,
    Instant requestedTo,
    Instant effectiveFrom,
    Instant effectiveTo,
    HistoricalResolution resolution,
    Instant requestedAt,
    long chunks,
    long cachedChunks,
    long chunksToCompute)
    implements HistoryEvent {

  public static final HistoryEventType TYPE = HistoryEventType.HISTORY_JOB_PLAN;

  @Override
  @JsonProperty("type")
  public HistoryEventType type() {
    return TYPE;
  }
}
