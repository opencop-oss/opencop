// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.history;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record HistoricalTracksRequest(
    Instant start,
    Instant end,
    List<HistoricalFilterRequest> filters,
    List<HistoricalSubjectRequest> sources) {

  public HistoricalTracksRequest {
    Objects.requireNonNull(start, "start");
    Objects.requireNonNull(end, "end");
    Objects.requireNonNull(sources, "sources");
  }
}
