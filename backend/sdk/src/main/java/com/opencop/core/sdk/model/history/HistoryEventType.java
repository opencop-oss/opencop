// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.history;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum HistoryEventType {
  HISTORY_JOB_PLAN("history.job.plan"),
  HISTORY_CHUNK_STATUS("history.chunk.status"),
  HISTORY_CHUNK_DATA("history.chunk.data"),
  HISTORY_JOB_PROGRESS("history.job.progress"),
  HISTORY_JOB_COMPLETED("history.job.completed"),
  HISTORY_JOB_FAILED("history.job.failed");

  private final String type;

  HistoryEventType(String type) {
    this.type = type;
  }

  @JsonValue
  public String getName() {
    return type;
  }

  @JsonCreator
  public static HistoryEventType fromName(String type) {
    for (HistoryEventType value : values()) {
      if (value.type.equals(type)) {
        return value;
      }
    }
    throw new IllegalArgumentException("Unknown history event type: " + type);
  }
}
