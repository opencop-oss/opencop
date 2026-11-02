// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.history;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.opencop.core.sdk.model.SubjectType;
import java.util.UUID;

public record HistoryJobFailedEvent(
    UUID targetUserId,
    UUID requestId,
    UUID jobId,
    SubjectType subjectType,
    UUID subjectId,
    String stage,
    String reason)
    implements HistoryEvent {

  public static final HistoryEventType TYPE = HistoryEventType.HISTORY_JOB_FAILED;

  @Override
  @JsonProperty("type")
  public HistoryEventType type() {
    return TYPE;
  }
}
