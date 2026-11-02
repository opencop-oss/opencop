// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.history;

import java.time.Duration;

public enum HistoricalResolution {
  MINUTE_1(Duration.ofMinutes(1)),
  HOUR_1(Duration.ofHours(1)),
  DAY_1(Duration.ofDays(1));

  private final Duration chunkSize;

  HistoricalResolution(Duration chunkSize) {
    this.chunkSize = chunkSize;
  }

  public Duration chunkSize() {
    return chunkSize;
  }
}
