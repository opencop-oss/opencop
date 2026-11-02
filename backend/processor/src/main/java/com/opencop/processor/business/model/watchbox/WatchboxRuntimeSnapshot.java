// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.model.watchbox;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record WatchboxRuntimeSnapshot(Map<UUID, List<WatchboxRuntimeTarget>> targetsBySource) {

  private static final WatchboxRuntimeSnapshot EMPTY = new WatchboxRuntimeSnapshot(Map.of());

  public WatchboxRuntimeSnapshot {
    Objects.requireNonNull(targetsBySource, "targetsBySource");
    var immutable = new HashMap<UUID, List<WatchboxRuntimeTarget>>();
    targetsBySource.forEach(
        (sourceId, targets) ->
            immutable.put(Objects.requireNonNull(sourceId, "sourceId"), List.copyOf(targets)));
    targetsBySource = Map.copyOf(immutable);
  }

  public static WatchboxRuntimeSnapshot empty() {
    return EMPTY;
  }

  public List<WatchboxRuntimeTarget> targetsFor(UUID sourceId) {
    return targetsBySource.getOrDefault(sourceId, List.of());
  }

  public boolean isEmpty() {
    return targetsBySource.isEmpty();
  }
}
