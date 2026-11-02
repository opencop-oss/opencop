// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.iam.layergroup;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IamLayerGroupMembersRepository
    extends JpaRepository<LayerGroupMemberEntity, LayerGroupMemberIdEntity> {

  @Query("select m from LayerGroupMemberEntity m where m.group.id = :layerGroupId")
  List<LayerGroupMemberEntity> findAllByLayerGroupId(UUID layerGroupId);

  @Modifying
  @Query(
      "delete from LayerGroupMemberEntity m"
          + " where m.group.id = :layerGroupId and m.id.userId in :userIds")
  int deleteAllByLayerGroupIdAndUserIds(UUID layerGroupId, Collection<UUID> userIds);

  @Query("select m.id.layerGroupId from LayerGroupMemberEntity m where m.id.userId = :userId")
  Set<UUID> findLayerGroupIdsByUserId(UUID userId);

  @Query("select m from LayerGroupMemberEntity m join fetch m.group where m.id.userId = :userId")
  List<LayerGroupMemberEntity> findAllByUserIdWithGroup(@Param("userId") UUID userId);
}
