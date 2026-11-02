// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.history;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.opencop.core.sdk.model.SubjectType;
import java.util.UUID;

public record HistoryJobProgressEvent(
    UUID targetUserId,
    UUID requestId,
    UUID jobId,
    SubjectType subjectType,
    UUID subjectId,
    long totalChunks,
    long cachedChunks,
    long computedChunks,
    long deliveredChunks,
    long failedChunks,
    long remainingChunks,
    int percentComplete)
    implements HistoryEvent {

  public static final HistoryEventType TYPE = HistoryEventType.HISTORY_JOB_PROGRESS;

  @Override
  @JsonProperty("type")
  public HistoryEventType type() {
    return TYPE;
  }
}
