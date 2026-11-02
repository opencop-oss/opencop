// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.iam.group;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IamGroupMembersRepository
    extends JpaRepository<GroupMemberEntity, GroupMemberIdEntity> {

  @Query("select m from GroupMemberEntity m where m.group.id = :groupId")
  List<GroupMemberEntity> findAllByGroupId(UUID groupId);

  @Modifying
  @Query("delete from GroupMemberEntity m where m.group.id = :groupId and m.id.userId in :userIds")
  int deleteAllByGroupIdAndUserIds(UUID groupId, Collection<UUID> userIds);

  @Query("select m.id.groupId from GroupMemberEntity m where m.id.userId = :userId")
  Set<UUID> findGroupIdsByUserId(UUID userId);

  @Query(
      "select m.id.userId from GroupMemberEntity m"
          + " where m.id.groupId = :groupId and m.id.userId in :userIds")
  Set<UUID> findExistingUserIds(UUID groupId, Collection<UUID> userIds);

  @Query(
      "select case when count(member) > 0 then true else false end"
          + " from GroupMemberEntity member"
          + " where member.id.userId = :userId and member.id.groupId in :groupIds")
  boolean existsByUserIdAndGroupIds(
      @Param("userId") UUID userId, @Param("groupIds") Collection<UUID> groupIds);
}
