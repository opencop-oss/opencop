// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.watchbox.business.model;

import com.opencop.core.sdk.model.watchbox.WatchboxTransitionKind;
import java.util.Set;
import java.util.UUID;

/** A null watchboxId selects all visible boxes; empty kinds selects every transition kind. */
public record WatchboxDetectionFilter(UUID watchboxId, Set<WatchboxTransitionKind> kinds) {
  public WatchboxDetectionFilter {
    kinds = Set.copyOf(kinds);
  }
}
