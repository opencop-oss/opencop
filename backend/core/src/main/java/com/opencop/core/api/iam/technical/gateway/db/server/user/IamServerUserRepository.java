// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.iam.technical.gateway.db.server.user;

import com.opencop.core.api.iam.technical.gateway.db.server.user.entity.ServerUserAccessEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.ServerRole;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface IamServerUserRepository extends JpaRepository<ServerUserAccessEntity, UUID> {

  boolean existsByIdServerId(UUID serverId);

  @Modifying
  @Query("delete from ServerUserAccessEntity a where a.id.serverId = :serverId")
  int deleteAllByServerId(UUID serverId);

  @Query("select a from ServerUserAccessEntity a where a.id.serverId = :serverId")
  Page<ServerUserAccessEntity> findPageByServerId(UUID serverId, Pageable pageable);

  @Query("select a from ServerUserAccessEntity a where a.id.userId = :userId")
  List<ServerUserAccessEntity> findAllServersByUserId(UUID userId);

  //    @Query("select a from ServerUserAccessEntity a where a.id.userId = :userId")
  List<ServerUserAccessEntity> findByIdUserId(UUID userId);

  @Query(
      "select a from ServerUserAccessEntity a where a.id.serverId = :serverId and a.id.userId = :userId")
  List<ServerUserAccessEntity> findByServerAndUserId(UUID serverId, UUID userId);

  @Query(
      "select a.id.userId from ServerUserAccessEntity a where a.id.serverId = :serverId and a.role = :role")
  List<String> findUserIdsByServerAndRole(UUID serverId, ServerRole role);

  @Query(
      "select distinct a.id.userId from ServerUserAccessEntity a where a.id.serverId = :serverId")
  List<UUID> findUserIdsByServer(UUID serverId);

  @Query("select a from ServerUserAccessEntity a where a.id.serverId = :serverId")
  List<ServerUserAccessEntity> findAllByServerId(UUID serverId);

  @Modifying
  @Query(
      "delete from ServerUserAccessEntity a where a.id.serverId = :serverId and a.id.userId = :userId and a.role = :role")
  int deleteOne(UUID serverId, UUID userId, ServerRole role);

  @Modifying
  @Query(
      "delete from ServerUserAccessEntity a where a.id.serverId = :serverId and a.id.userId in :userIds")
  int deleteMany(UUID serverId, Collection<UUID> userIds);
}
