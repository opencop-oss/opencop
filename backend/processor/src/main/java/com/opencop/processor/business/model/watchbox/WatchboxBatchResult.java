// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.model.watchbox;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public record WatchboxBatchResult(
    List<WatchboxTransition> emissions, Map<WatchboxBatchOutcome, Long> counts) {

  public WatchboxBatchResult {
    emissions = List.copyOf(emissions);
    var copy = new EnumMap<WatchboxBatchOutcome, Long>(WatchboxBatchOutcome.class);
    copy.putAll(counts);
    counts = Collections.unmodifiableMap(copy);
  }

  /** A counter never touched by the batch reads as zero, identical to one incremented to zero. */
  public long count(WatchboxBatchOutcome outcome) {
    return counts.getOrDefault(outcome, 0L);
  }
}
