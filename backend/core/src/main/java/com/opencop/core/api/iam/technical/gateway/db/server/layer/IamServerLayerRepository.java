// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.iam.technical.gateway.db.server.layer;

import com.opencop.core.api.iam.technical.gateway.db.server.layer.entity.ServerLayerAccessEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.ServerRole;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface IamServerLayerRepository extends JpaRepository<ServerLayerAccessEntity, UUID> {

  boolean existsByIdServerId(UUID serverId);

  @Modifying
  @Query("delete from ServerLayerAccessEntity a where a.id.serverId = :serverId")
  int deleteAllByServerId(UUID serverId);

  @Query("select a from ServerLayerAccessEntity a where a.id.serverId = :serverId")
  Page<ServerLayerAccessEntity> findPageByServerId(UUID serverId, Pageable pageable);

  @Query(
      "select a from ServerLayerAccessEntity a where a.id.serverId = :serverId and a.layer.id = :layerId")
  List<ServerLayerAccessEntity> findAllByServerAndLayer(UUID serverId, UUID layerId);

  @Query("select a from ServerLayerAccessEntity a where a.id.serverId = :serverId")
  List<ServerLayerAccessEntity> findAllByServerId(UUID serverId);

  @Query(
      "select a.layer.id from ServerLayerAccessEntity a where a.id.serverId = :serverId and a.role = :role")
  List<UUID> findLayerIdsByServerAndRole(UUID serverId, ServerRole role);

  @Query("select distinct a from ServerLayerAccessEntity a where a.id.layerId = :layerId")
  List<ServerLayerAccessEntity> findServerIdsByLayer(UUID layerId);

  @Modifying
  @Query(
      "delete from ServerLayerAccessEntity a where a.id.serverId = :serverId and a.layer.id = :layerId and a.role = :role")
  int deleteOne(UUID serverId, UUID layerId, ServerRole role);

  @Modifying
  @Query(
      "delete from ServerLayerAccessEntity a where a.id.serverId = :serverId and a.layer.id in :layerIds")
  int deleteMany(UUID serverId, Collection<UUID> layerIds);
}
