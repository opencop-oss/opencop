// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.model.datasourcegroup;

import com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup.DataSourceGroupRole;
import java.util.List;
import java.util.UUID;

public record DataSourceGroupUserGroupsPatchRequest(List<UserGroupAdd> add, List<UUID> remove) {

  public DataSourceGroupUserGroupsPatchRequest {
    add = add != null ? add : List.of();
    remove = remove != null ? remove : List.of();
  }

  public record UserGroupAdd(UUID userGroupId, DataSourceGroupRole role) {}
}
