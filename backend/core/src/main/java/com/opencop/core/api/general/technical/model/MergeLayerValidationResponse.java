// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.model;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Advisory validation report for layer merge admission.
 *
 * <p>The collision summary reflects helper rows observed when validation runs. Source layer locking
 * is operational rather than a frozen processor ingest snapshot; async materialization reads latest
 * active data when the merge job runs.
 */
public record MergeLayerValidationResponse(
    List<UUID> layerIds, Map<UUID, Long> activeFeatureCounts, CollisionSummary collisionSummary) {

  public record CollisionSummary(
      long totalCollisions, List<PairwiseCollisionCount> pairwiseCounts) {}

  public record PairwiseCollisionCount(UUID firstLayerId, UUID secondLayerId, long count) {}
}
