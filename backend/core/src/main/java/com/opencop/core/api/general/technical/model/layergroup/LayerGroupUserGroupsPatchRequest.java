// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.model.layergroup;

import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.LayerGroupRole;
import java.util.List;
import java.util.UUID;

public record LayerGroupUserGroupsPatchRequest(List<UserGroupAdd> add, List<UUID> remove) {

  public LayerGroupUserGroupsPatchRequest {
    add = add != null ? add : List.of();
    remove = remove != null ? remove : List.of();
  }

  public record UserGroupAdd(UUID userGroupId, LayerGroupRole role) {}
}
