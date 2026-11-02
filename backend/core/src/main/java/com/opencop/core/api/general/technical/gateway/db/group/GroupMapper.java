// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.group;

import com.opencop.core.api.general.technical.gateway.scim.user.InternalUserEntity;
import com.opencop.core.api.general.technical.model.group.GroupMemberResponse;
import com.opencop.core.api.general.technical.model.group.GroupResponse;
import com.opencop.core.api.general.technical.model.group.GroupSearchResult;
import com.opencop.core.sdk.model.iam.group.GroupManagementSource;
import com.opencop.core.sdk.technical.gateway.db.iam.group.GroupEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.group.GroupMemberEntity;

public final class GroupMapper {

  private GroupMapper() {}

  public static GroupResponse toResponse(GroupEntity e) {
    return new GroupResponse(
        e.getId(),
        e.getName(),
        e.getDescription(),
        e.getCreatedAt(),
        GroupManagementSource.ofIdpIssuer(e.getIdpIssuer()));
  }

  public static GroupSearchResult toSearchResult(GroupEntity e) {
    return new GroupSearchResult(
        e.getId(), e.getName(), GroupManagementSource.ofIdpIssuer(e.getIdpIssuer()));
  }

  public static GroupMemberResponse toMemberResponse(GroupMemberEntity e, InternalUserEntity user) {
    return new GroupMemberResponse(
        e.getUserId(),
        user != null ? user.getUsername() : null,
        user != null ? user.getFirstName() : null,
        user != null ? user.getLastName() : null,
        user != null ? user.getEmail() : null,
        e.getJoinedAt());
  }
}
