// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.watchbox;

import java.util.List;

public record WatchboxFilterMatch(
    boolean matches, List<WatchboxFilterPredicateResult> predicateResults) {
  public WatchboxFilterMatch {
    predicateResults = predicateResults == null ? List.of() : List.copyOf(predicateResults);
  }
}
