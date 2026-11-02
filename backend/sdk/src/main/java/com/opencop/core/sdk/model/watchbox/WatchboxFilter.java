// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.watchbox;

import java.util.List;

/**
 * Watchbox event-property filter. This JSON shape is implicitly version 1; top-level "version" is
 * reserved and unused in v1, and absence means v1.
 */
public record WatchboxFilter(WatchboxCombine combine, List<WatchboxFilterPredicate> predicates) {
  public WatchboxFilter {
    predicates = predicates == null ? List.of() : List.copyOf(predicates);
  }

  public WatchboxFilter(List<WatchboxFilterPredicate> predicates) {
    this(WatchboxCombine.ALL, predicates);
  }

  public static WatchboxFilter empty() {
    return new WatchboxFilter(WatchboxCombine.ALL, List.of());
  }
}
