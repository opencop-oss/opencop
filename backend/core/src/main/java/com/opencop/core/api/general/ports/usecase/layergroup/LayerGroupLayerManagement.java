// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.usecase.layergroup;

import com.opencop.core.api.general.technical.model.layergroup.LayerGroupLayerResponse;
import com.opencop.core.api.general.technical.model.layergroup.LayerGroupLayersAssignRequest;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.util.List;
import java.util.UUID;

public interface LayerGroupLayerManagement {

  List<LayerGroupLayerResponse> getLayers(UUID layerGroupId, OpenCopPrincipal principal);

  List<LayerGroupLayerResponse> assignLayers(
      UUID layerGroupId, LayerGroupLayersAssignRequest request, OpenCopPrincipal principal);

  void removeLayer(UUID layerGroupId, UUID layerId, OpenCopPrincipal principal);
}
