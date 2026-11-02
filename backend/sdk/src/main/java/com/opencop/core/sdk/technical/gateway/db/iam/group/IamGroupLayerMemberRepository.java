// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.iam.group;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface IamGroupLayerMemberRepository
    extends JpaRepository<GroupLayerMemberEntity, GroupLayerMemberIdEntity> {

  @Query(
      "select m from GroupLayerMemberEntity m join fetch m.layer join fetch m.group where m.layer.id = :layerId")
  List<GroupLayerMemberEntity> findAllByLayerId(UUID layerId);

  @Query(
      "select m from GroupLayerMemberEntity m join fetch m.layer join fetch m.group where m.group.id = :groupId")
  List<GroupLayerMemberEntity> findAllByGroupId(UUID groupId);

  @Query("select m from GroupLayerMemberEntity m join fetch m.layer where m.group.id in :groupIds")
  List<GroupLayerMemberEntity> findAllByGroupIdIn(Collection<UUID> groupIds);

  @Query(
      "select m from GroupLayerMemberEntity m"
          + " where m.group.id in :groupIds and m.layer.id = :layerId")
  List<GroupLayerMemberEntity> findAllByGroupIdsAndLayerId(Collection<UUID> groupIds, UUID layerId);

  @Modifying
  @Query(
      "delete from GroupLayerMemberEntity m"
          + " where m.group.id = :groupId and m.layer.id = :layerId")
  void deleteByGroupIdAndLayerId(UUID groupId, UUID layerId);
}
