// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.iam.layergroup;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface IamLayerGroupUserGroupRepository
    extends JpaRepository<LayerGroupUserGroupEntity, LayerGroupUserGroupIdEntity> {

  List<LayerGroupUserGroupEntity> findAllByLayerGroupId(UUID layerGroupId);

  @Query(
      "select e.id.layerGroupId from LayerGroupUserGroupEntity e"
          + " where e.id.userGroupId in :userGroupIds")
  Set<UUID> findLayerGroupIdsByUserGroupIdIn(Collection<UUID> userGroupIds);

  @Query(
      "select e from LayerGroupUserGroupEntity e join fetch e.layerGroup"
          + " where e.id.userGroupId in :userGroupIds")
  List<LayerGroupUserGroupEntity> findAllByUserGroupIdInWithLayerGroup(
      Collection<UUID> userGroupIds);

  List<LayerGroupUserGroupEntity> findAllByIdLayerGroupIdAndIdUserGroupIdIn(
      UUID layerGroupId, Collection<UUID> userGroupIds);

  void deleteAllByIdLayerGroupIdAndIdUserGroupIdIn(
      UUID layerGroupId, Collection<UUID> userGroupIds);
}
