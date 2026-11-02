// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.usecase.layergroup;

import com.opencop.core.api.general.technical.model.layergroup.LayerGroupCreateRequest;
import com.opencop.core.api.general.technical.model.layergroup.LayerGroupPatchRequest;
import com.opencop.core.api.general.technical.model.layergroup.LayerGroupResponse;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.util.UUID;

public interface LayerGroupContainerManagement {

  LayerGroupResponse create(LayerGroupCreateRequest request, OpenCopPrincipal principal);

  LayerGroupResponse get(UUID layerGroupId, OpenCopPrincipal principal);

  LayerGroupResponse patch(
      UUID layerGroupId, LayerGroupPatchRequest request, OpenCopPrincipal principal);

  void delete(UUID layerGroupId, OpenCopPrincipal principal);
}
