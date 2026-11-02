// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.scim.relationship;

import com.opencop.core.api.general.technical.gateway.scim.user.InternalUserEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface UserGroupRepository
    extends JpaRepository<InternalUserGroupRelationshipEntity, UUID> {

  boolean existsByUserIdAndGroupId(UUID userId, UUID groupId);

  Optional<InternalUserGroupRelationshipEntity> findByUserIdAndGroupId(UUID userId, UUID groupId);

  List<InternalUserGroupRelationshipEntity> findByUserId(UUID userId);

  List<InternalUserGroupRelationshipEntity> findByGroupId(UUID groupId);

  @Query("SELECT u FROM InternalUserEntity u JOIN u.groups g WHERE g.id = :groupId")
  List<InternalUserEntity> findUsersByGroupId(UUID groupId);

  @Modifying
  @Query("DELETE FROM InternalUserGroupRelationshipEntity r WHERE r.group.id = :groupId")
  void deleteByGroupId(UUID groupId);
}
