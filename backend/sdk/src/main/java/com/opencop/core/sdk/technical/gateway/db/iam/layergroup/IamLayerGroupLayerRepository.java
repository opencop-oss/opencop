// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.iam.layergroup;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IamLayerGroupLayerRepository
    extends JpaRepository<LayerGroupLayerEntity, LayerGroupLayerIdEntity> {

  @Query(
      "select m from LayerGroupLayerEntity m join fetch m.layer join fetch m.group where m.layer.id = :layerId")
  List<LayerGroupLayerEntity> findAllByLayerId(UUID layerId);

  @Query(
      "select m from LayerGroupLayerEntity m join fetch m.layer join fetch m.group where m.group.id = :layerGroupId")
  List<LayerGroupLayerEntity> findAllByLayerGroupId(UUID layerGroupId);

  @Query(
      "select m from LayerGroupLayerEntity m join fetch m.layer where m.group.id in :layerGroupIds")
  List<LayerGroupLayerEntity> findAllByLayerGroupIdIn(Collection<UUID> layerGroupIds);

  @Query(
      "select m from LayerGroupLayerEntity m"
          + " where m.group.id in :layerGroupIds and m.layer.id = :layerId")
  List<LayerGroupLayerEntity> findAllByLayerGroupIdsAndLayerId(
      Collection<UUID> layerGroupIds, UUID layerId);

  @Modifying
  @Query(
      "delete from LayerGroupLayerEntity m"
          + " where m.group.id = :layerGroupId and m.layer.id = :layerId")
  void deleteByLayerGroupIdAndLayerId(UUID layerGroupId, UUID layerId);

  @Query(
      value =
          "select count(*) from iam.layer_group_layer lgl"
              + " join iam.layer l on l.id = lgl.layer_id"
              + " where lgl.layer_group_id = :layerGroupId and l.access_scope != 'GLOBAL'",
      nativeQuery = true)
  long countNonGlobalLayers(@Param("layerGroupId") UUID layerGroupId);

  @Query(
      value =
          "select count(*) from iam.layer_group_layer lgl"
              + " join iam.layer_group lg on lg.id = lgl.layer_group_id"
              + " where lgl.layer_id = :layerId and lg.access_scope = 'GLOBAL'",
      nativeQuery = true)
  long countGlobalLayerGroupsByLayerId(@Param("layerId") UUID layerId);
}
