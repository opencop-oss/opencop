// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.model.history.chunk;

import java.time.Instant;

public record HistoricalChunkPlanEntry(
    int chunkIndex,
    String chunkKey,
    String payloadKey,
    String chunkStateKey,
    HistoricalChunkCacheStatus cacheStatus,
    Instant from,
    Instant to) {

  public HistoricalChunkPlanEntry withCacheAnalysis(
      String payloadKey, String chunkStateKey, HistoricalChunkCacheStatus cacheStatus) {
    return new HistoricalChunkPlanEntry(
        chunkIndex, chunkKey, payloadKey, chunkStateKey, cacheStatus, from, to);
  }

  public HistoricalChunkPlanEntry withoutCacheAnalysis() {
    return new HistoricalChunkPlanEntry(chunkIndex, chunkKey, null, null, null, from, to);
  }
}
