// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.history;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record HistoricalTracksAck(
    UUID requestId, HistoricalTracksAckStatus status, List<HistoricalSubjectAck> subjects) {

  public HistoricalTracksAck {
    Objects.requireNonNull(requestId, "requestId");
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(subjects, "subjects");
  }
}
