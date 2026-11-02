// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.layermerge;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record LayerMergeOperation(
    UUID targetLayerId,
    UUID jobId,
    List<UUID> sourceLayerIds,
    LayerMergeOperationPhase phase,
    String failureReason) {

  public LayerMergeOperation {
    Objects.requireNonNull(targetLayerId, "targetLayerId");
    Objects.requireNonNull(jobId, "jobId");
    Objects.requireNonNull(sourceLayerIds, "sourceLayerIds");
    Objects.requireNonNull(phase, "phase");
    if (sourceLayerIds.size() < 2) {
      throw new IllegalArgumentException("sourceLayerIds must contain at least two layers");
    }
    sourceLayerIds = List.copyOf(sourceLayerIds);
  }
}
