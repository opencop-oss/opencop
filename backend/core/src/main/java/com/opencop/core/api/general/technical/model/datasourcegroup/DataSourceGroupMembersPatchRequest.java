// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.model.datasourcegroup;

import com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup.DataSourceGroupRole;
import java.util.Set;
import java.util.UUID;

public record DataSourceGroupMembersPatchRequest(Set<MemberAdd> add, Set<UUID> remove) {
  public DataSourceGroupMembersPatchRequest {
    add = add != null ? add : Set.of();
    remove = remove != null ? remove : Set.of();
  }

  public record MemberAdd(UUID userId, DataSourceGroupRole role) {}
}
