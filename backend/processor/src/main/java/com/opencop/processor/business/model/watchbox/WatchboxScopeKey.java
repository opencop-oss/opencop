// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.model.watchbox;

import java.util.Objects;
import java.util.UUID;

public record WatchboxScopeKey(UUID watchboxId, long configEpoch, UUID sourceId) {
  public WatchboxScopeKey {
    Objects.requireNonNull(watchboxId, "watchboxId");
    Objects.requireNonNull(sourceId, "sourceId");
  }
}
