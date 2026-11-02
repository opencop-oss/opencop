// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.model.history;

import com.opencop.core.sdk.model.history.HistoricalResolution;
import java.util.List;
import java.util.UUID;

public record HistoricalReadContext(
    List<UUID> sourceIds,
    UUID singleSourceId,
    boolean helperTableEnabled,
    HistoricalResolution checkpointResolution,
    boolean checkpointEnabled) {

  public HistoricalReadContext {
    sourceIds = sourceIds == null ? List.of() : List.copyOf(sourceIds);
  }

  public HistoricalReadContext(
      List<UUID> sourceIds, UUID singleSourceId, boolean helperTableEnabled) {
    this(sourceIds, singleSourceId, helperTableEnabled, null, false);
  }

  public static HistoricalReadContext forSourceIds(List<UUID> sourceIds) {
    List<UUID> normalizedSourceIds = sourceIds == null ? List.of() : List.copyOf(sourceIds);
    UUID singleSourceId = normalizedSourceIds.size() == 1 ? normalizedSourceIds.getFirst() : null;
    return new HistoricalReadContext(normalizedSourceIds, singleSourceId, singleSourceId != null);
  }

  public boolean isEmpty() {
    return sourceIds.isEmpty();
  }

  public boolean isSingleSource() {
    return singleSourceId != null;
  }

  public boolean usesCheckpoint() {
    return checkpointEnabled && checkpointResolution != null && isSingleSource();
  }
}
