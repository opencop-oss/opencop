// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.gateway.layer;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface MergeLayerValidationGateway {

  Map<UUID, Long> countActiveFeatures(List<UUID> layerIds);

  List<PairCollisionCount> countPairwiseCollisions(List<UUID> orderedLayerIds);

  record PairCollisionCount(UUID firstLayerId, UUID secondLayerId, long count) {}
}
