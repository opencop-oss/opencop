// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.usecase.layergroup;

import com.opencop.core.api.general.technical.model.layergroup.LayerGroupMemberResponse;
import com.opencop.core.api.general.technical.model.layergroup.LayerGroupMembersPatchRequest;
import com.opencop.core.api.general.technical.model.layergroup.MyLayerGroupResponse;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.util.List;
import java.util.UUID;

public interface LayerGroupMemberManagement {

  List<LayerGroupMemberResponse> getMembers(UUID layerGroupId, OpenCopPrincipal principal);

  void patchMembers(
      UUID layerGroupId, LayerGroupMembersPatchRequest request, OpenCopPrincipal principal);

  List<MyLayerGroupResponse> listMyLayerGroups(OpenCopPrincipal principal);
}
