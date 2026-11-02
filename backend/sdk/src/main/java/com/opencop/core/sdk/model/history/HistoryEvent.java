// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.history;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.UUID;

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "type",
    visible = true)
@JsonSubTypes({
  @JsonSubTypes.Type(value = HistoryJobPlanEvent.class, name = "history.job.plan"),
  @JsonSubTypes.Type(value = HistoryChunkStatusEvent.class, name = "history.chunk.status"),
  @JsonSubTypes.Type(value = HistoryJobProgressEvent.class, name = "history.job.progress"),
  @JsonSubTypes.Type(value = HistoryJobCompletedEvent.class, name = "history.job.completed"),
  @JsonSubTypes.Type(value = HistoryJobFailedEvent.class, name = "history.job.failed")
})
public sealed interface HistoryEvent
    permits HistoryJobPlanEvent,
        HistoryChunkStatusEvent,
        HistoryJobProgressEvent,
        HistoryJobCompletedEvent,
        HistoryJobFailedEvent {

  UUID targetUserId();

  UUID requestId();

  UUID jobId();

  @JsonProperty("type")
  HistoryEventType type();
}
