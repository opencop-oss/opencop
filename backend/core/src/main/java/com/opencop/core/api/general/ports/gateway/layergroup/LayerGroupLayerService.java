// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.gateway.layergroup;

import com.opencop.core.api.general.technical.model.layergroup.LayerGroupLayerResponse;
import com.opencop.core.sdk.technical.gateway.db.PersistenceException;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerRole;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface LayerGroupLayerService {

  List<LayerGroupLayerResponse> getLayersForLayerGroup(UUID layerGroupId)
      throws PersistenceException;

  List<LayerGroupLayerResponse> assignLayersToLayerGroup(
      UUID layerGroupId, Set<UUID> layerIds, LayerRole role) throws PersistenceException;

  void removeLayerFromLayerGroup(UUID layerGroupId, UUID layerId) throws PersistenceException;

  long countNonGlobalLayers(UUID layerGroupId);
}
