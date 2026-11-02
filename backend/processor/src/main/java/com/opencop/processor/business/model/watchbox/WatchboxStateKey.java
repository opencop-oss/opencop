// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.model.watchbox;

import java.util.Comparator;
import java.util.Objects;
import java.util.UUID;

public record WatchboxStateKey(UUID watchboxId, long configEpoch, UUID sourceId, String objectId)
    implements Comparable<WatchboxStateKey> {

  // Total order so every DB read, lock, and write touches state rows in one global key order;
  // cross-batch deadlocks then reduce to the insert races the engine's retry loop absorbs.
  private static final Comparator<WatchboxStateKey> ORDER =
      Comparator.comparing(WatchboxStateKey::watchboxId)
          .thenComparingLong(WatchboxStateKey::configEpoch)
          .thenComparing(WatchboxStateKey::sourceId)
          .thenComparing(WatchboxStateKey::objectId);

  public WatchboxStateKey {
    Objects.requireNonNull(watchboxId, "watchboxId");
    Objects.requireNonNull(sourceId, "sourceId");
    if (objectId == null || objectId.isBlank()) {
      throw new IllegalArgumentException("objectId must be non-blank");
    }
  }

  @Override
  public int compareTo(WatchboxStateKey other) {
    return ORDER.compare(this, other);
  }

  public WatchboxScopeKey scope() {
    return new WatchboxScopeKey(watchboxId, configEpoch, sourceId);
  }
}
