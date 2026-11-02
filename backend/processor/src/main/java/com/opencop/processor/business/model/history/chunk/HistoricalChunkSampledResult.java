// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.model.history.chunk;

import com.opencop.core.sdk.model.history.HistoricalResolution;
import com.opencop.processor.business.model.history.HistoricalSampledPlaybackPoint;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record HistoricalChunkSampledResult(
    int chunkIndex,
    String chunkKey,
    Instant from,
    Instant to,
    HistoricalResolution resolution,
    int pointCount,
    int objectCount,
    List<HistoricalSampledPlaybackPoint> points) {

  public HistoricalChunkSampledResult {
    Objects.requireNonNull(chunkKey, "chunkKey");
    Objects.requireNonNull(from, "from");
    Objects.requireNonNull(to, "to");
    Objects.requireNonNull(resolution, "resolution");
    Objects.requireNonNull(points, "points");
    points = List.copyOf(points);
  }
}
