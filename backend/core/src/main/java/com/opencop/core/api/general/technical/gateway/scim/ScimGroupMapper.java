// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.scim;

import com.opencop.core.api.general.technical.gateway.scim.group.InternalGroupEntity;
import com.opencop.core.api.general.technical.gateway.scim.user.InternalUserEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.group.GroupEntity;
import com.unboundid.scim2.common.types.GroupResource;
import com.unboundid.scim2.common.types.Member;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class ScimGroupMapper {

  private ScimGroupMapper() {}

  /** DB -> SCIM (library DTO) */
  public static GroupResource toScim(InternalGroupEntity entity) {
    GroupResource group = new GroupResource();

    group.setId(entity.getId().toString());
    group.setExternalId(entity.getIdpGroupId());
    group.setDisplayName(entity.getName());

    return group;
  }

  public static GroupResource toScim(GroupEntity entity) {
    GroupResource group = new GroupResource();

    group.setId(entity.getId().toString());
    group.setExternalId(entity.getIdpGroupId());
    group.setDisplayName(entity.getName());

    return group;
  }

  /** SCIM -> DB (apply changes to internal entity) */
  public static void applyReplace(GroupResource scimGroup, InternalGroupEntity entity) {
    entity.setName(scimGroup.getDisplayName());
  }

  public static void applyReplace(GroupResource scimGroup, GroupEntity entity) {
    entity.setName(scimGroup.getDisplayName());
  }

  // Map users to SCIM members if needed
  private static List<Member> mapUsersToMembers(Set<InternalUserEntity> users) {
    List<Member> members = new ArrayList<>();
    for (InternalUserEntity user : users) {
      Member member = new Member();
      member.setValue(user.getIdpUserId());
      member.setType("User");
      member.setRef(URI.create("/scim/v1/provisioning/Users/" + user.getId()));
      member.setDisplay(user.getDisplayName());
      members.add(member);
    }
    return members;
  }
}
