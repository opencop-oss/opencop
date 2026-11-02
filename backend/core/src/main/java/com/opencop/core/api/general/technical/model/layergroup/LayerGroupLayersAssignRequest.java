// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.model.layergroup;

import com.opencop.core.sdk.technical.gateway.db.iam.LayerRole;
import java.util.Set;
import java.util.UUID;

/**
 * Batch request to assign one or more layers to a layer group, applying a single role to every
 * selected layer.
 */
public record LayerGroupLayersAssignRequest(Set<UUID> layerIds, LayerRole role) {
  public LayerGroupLayersAssignRequest {
    layerIds = layerIds != null ? layerIds : Set.of();
  }
}
