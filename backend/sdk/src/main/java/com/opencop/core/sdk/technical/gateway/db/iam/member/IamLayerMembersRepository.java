// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.iam.member;

import com.opencop.core.sdk.technical.gateway.db.iam.LayerRole;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface IamLayerMembersRepository
    extends JpaRepository<LayerMemberEntity, LayerMemberIdEntity> {

  @Query(
      "select tm from LayerMemberEntity tm where tm.layer.id = :layerId and tm.id.userId = :userId")
  Optional<LayerMemberEntity> findByLayerIdAndUserId(UUID layerId, UUID userId);

  @Query("select tm from LayerMemberEntity tm where tm.layer.id = :layerId")
  List<LayerMemberEntity> findPageByLayerId(UUID layerId);

  @Query(
      "select tm from LayerMemberEntity tm where tm.layer.id = :layerId and tm.id.userId in :userIds")
  List<LayerMemberEntity> findAllByLayerIdAndUserIds(UUID layerId, Collection<UUID> userIds);

  @Query("select tm from LayerMemberEntity tm where tm.id.userId = :userId")
  List<LayerMemberEntity> findAllLayersByUserId(UUID userId);

  @Query("select tm from LayerMemberEntity tm where tm.layer.id in :layerIds")
  List<LayerMemberEntity> findAllByLayerIds(Collection<UUID> layerIds);

  @Modifying
  @Query(
      "delete from LayerMemberEntity tm where tm.layer.id = :layerId and tm.id.userId in :userIds")
  int deleteAllByLayerIdAndUserIds(UUID layerId, Collection<UUID> userIds);

  @Query(
      "select tm.id.userId from LayerMemberEntity tm where tm.layer.id = :layerId and tm.role = :role")
  Set<UUID> findUserIdsByLayerIdAndRole(UUID layerId, LayerRole role);
}
