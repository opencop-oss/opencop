// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.gateway.layergroup;

import com.opencop.core.api.general.technical.model.layergroup.LayerGroupUserGroupsPatchRequest;
import com.opencop.core.sdk.technical.gateway.db.PersistenceException;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.LayerGroupUserGroupEntity;
import java.util.List;
import java.util.UUID;

public interface LayerGroupUserGroupService {

  List<LayerGroupUserGroupEntity> listUserGroupsForLayerGroup(UUID layerGroupId);

  void applyUserGroupPatch(
      UUID layerGroupId,
      List<LayerGroupUserGroupsPatchRequest.UserGroupAdd> additions,
      List<UUID> removals,
      UserGroupPatchGuard guard)
      throws PersistenceException;
}
