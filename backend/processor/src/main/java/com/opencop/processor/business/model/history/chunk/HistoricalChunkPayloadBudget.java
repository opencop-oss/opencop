// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.model.history.chunk;

public record HistoricalChunkPayloadBudget(int maxPartBytes, int maxChunkBytes) {

  public HistoricalChunkPayloadBudget {
    if (maxPartBytes <= 0) {
      throw new IllegalArgumentException("maxPartBytes must be positive, got " + maxPartBytes);
    }
    if (maxChunkBytes <= 0) {
      throw new IllegalArgumentException("maxChunkBytes must be positive, got " + maxChunkBytes);
    }
  }
}
