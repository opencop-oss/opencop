// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.model.history.chunk;

import com.opencop.core.sdk.model.history.HistoricalResolution;
import java.time.Instant;
import java.util.List;

public record HistoricalChunkWindowPlan(
    Instant effectiveFrom,
    Instant effectiveTo,
    long chunks,
    HistoricalResolution resolution,
    List<HistoricalChunkPlanEntry> entries) {

  public HistoricalChunkWindowPlan {
    entries = List.copyOf(entries);
  }
}
