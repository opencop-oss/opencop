// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.model.history.chunk;

import java.util.List;
import java.util.Objects;

public record HistoricalChunkPayloadBuildResult(List<HistoricalChunkPayloadPart> parts) {

  public HistoricalChunkPayloadBuildResult {
    Objects.requireNonNull(parts, "parts");
    parts = List.copyOf(parts);
    if (parts.isEmpty()) {
      throw new IllegalArgumentException("parts must not be empty");
    }
  }
}
