// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.model.watchbox;

import java.util.Map;

/** Result of locking the current Watchbox generations and their existing transition state. */
public sealed interface WatchboxStateLockResult {

  record Current(Map<WatchboxStateKey, WatchboxStateRow> rows) implements WatchboxStateLockResult {
    public Current {
      rows = Map.copyOf(rows);
    }
  }

  record GenerationChanged() implements WatchboxStateLockResult {}
}
