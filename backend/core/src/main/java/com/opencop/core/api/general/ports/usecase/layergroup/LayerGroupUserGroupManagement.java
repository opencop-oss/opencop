// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.usecase.layergroup;

import com.opencop.core.api.general.technical.model.layergroup.LayerGroupUserGroupResponse;
import com.opencop.core.api.general.technical.model.layergroup.LayerGroupUserGroupsPatchRequest;
import com.opencop.core.sdk.technical.gateway.db.PersistenceException;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.util.List;
import java.util.UUID;

public interface LayerGroupUserGroupManagement {

  List<LayerGroupUserGroupResponse> getUserGroups(UUID layerGroupId, OpenCopPrincipal principal);

  void patchUserGroups(
      UUID layerGroupId, LayerGroupUserGroupsPatchRequest request, OpenCopPrincipal principal)
      throws PersistenceException;
}
