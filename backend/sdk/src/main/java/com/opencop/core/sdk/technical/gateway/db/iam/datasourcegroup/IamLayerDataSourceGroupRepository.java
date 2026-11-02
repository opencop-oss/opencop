// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface IamLayerDataSourceGroupRepository
    extends JpaRepository<LayerDataSourceGroupEntity, LayerDataSourceGroupIdEntity> {

  @Query(
      "select e from LayerDataSourceGroupEntity e"
          + " join fetch e.layer join fetch e.dataSourceGroup"
          + " where e.layer.id = :layerId")
  List<LayerDataSourceGroupEntity> findAllByLayerId(UUID layerId);

  @Query(
      "select e from LayerDataSourceGroupEntity e"
          + " join fetch e.layer join fetch e.dataSourceGroup"
          + " where e.dataSourceGroup.id = :dataSourceGroupId")
  List<LayerDataSourceGroupEntity> findAllByDataSourceGroupId(UUID dataSourceGroupId);

  @Modifying
  @Query(
      "delete from LayerDataSourceGroupEntity e"
          + " where e.layer.id = :layerId and e.dataSourceGroup.id = :dataSourceGroupId")
  void deleteByLayerIdAndDataSourceGroupId(UUID layerId, UUID dataSourceGroupId);
}
