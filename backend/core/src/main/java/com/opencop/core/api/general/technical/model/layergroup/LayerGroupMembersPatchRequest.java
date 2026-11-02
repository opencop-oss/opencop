// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.model.layergroup;

import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.LayerGroupRole;
import java.util.Set;
import java.util.UUID;

public record LayerGroupMembersPatchRequest(Set<MemberAdd> add, Set<UUID> remove) {
  public LayerGroupMembersPatchRequest {
    add = add != null ? add : Set.of();
    remove = remove != null ? remove : Set.of();
  }

  public record MemberAdd(UUID userId, LayerGroupRole role) {}
}
