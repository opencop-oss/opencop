// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.business.model;

import com.opencop.core.sdk.model.history.HistoricalResolution;
import java.time.Instant;
import java.util.List;

public record SourceHistogramResponse(
    Instant requestedFrom,
    Instant requestedTo,
    Instant effectiveFrom,
    Instant effectiveTo,
    HistoricalResolution resolution,
    long chunks,
    long bucketWidthMillis,
    List<SourceHistogramBucket> buckets) {

  public SourceHistogramResponse(List<SourceHistogramBucket> buckets) {
    this(null, null, null, null, null, 0, 0, buckets);
  }
}
