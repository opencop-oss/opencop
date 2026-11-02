// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.scim.relationship;

import com.opencop.core.api.general.technical.gateway.scim.group.InternalGroupEntity;
import com.opencop.core.api.general.technical.gateway.scim.group.InternalGroupRepository;
import com.opencop.core.api.general.technical.gateway.scim.user.InternalUserEntity;
import com.opencop.core.api.general.technical.gateway.scim.user.InternalUserRepository;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserGroupService {

  private final String issuer;
  private final UserGroupRepository userGroupRepository;
  private final InternalUserRepository userRepository;
  private final InternalGroupRepository groupRepository;

  public UserGroupService(
      @Value("${opencop.idp.issuer}") String issuer,
      UserGroupRepository userGroupRepository,
      InternalUserRepository userRepository,
      InternalGroupRepository groupRepository) {
    this.issuer = issuer;
    this.userGroupRepository = userGroupRepository;
    this.userRepository = userRepository;
    this.groupRepository = groupRepository;
  }

  @Transactional
  public void addUserToGroup(String userId, UUID groupId) {
    InternalUserEntity user =
        userRepository
            .findByIdpIssuerAndIdpUserId(issuer, userId)
            .orElseThrow(() -> new RuntimeException("User not found"));

    InternalGroupEntity group =
        groupRepository
            .findById(groupId)
            .orElseThrow(() -> new RuntimeException("Group not found"));

    if (userGroupRepository.existsByUserIdAndGroupId(user.getId(), groupId)) {
      throw new RuntimeException("User already assigned to the group");
    }

    InternalUserGroupRelationshipEntity userGroupRelationship =
        new InternalUserGroupRelationshipEntity();
    userGroupRelationship.setUser(user);
    userGroupRelationship.setGroup(group);
    userGroupRepository.save(userGroupRelationship);
  }

  @Transactional
  public void removeUserFromGroup(UUID userId, UUID groupId) {
    InternalUserGroupRelationshipEntity userGroupRelationship =
        userGroupRepository
            .findByUserIdAndGroupId(userId, groupId)
            .orElseThrow(() -> new RuntimeException("User not assigned to this group"));

    userGroupRepository.delete(userGroupRelationship);
  }
}
