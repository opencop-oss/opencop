// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IamDataSourceGroupMembersRepository
    extends JpaRepository<DataSourceGroupMemberEntity, DataSourceGroupMemberIdEntity> {

  @Query(
      "select m from DataSourceGroupMemberEntity m where m.id.dataSourceGroupId = :dataSourceGroupId")
  List<DataSourceGroupMemberEntity> findAllByDataSourceGroupId(UUID dataSourceGroupId);

  @Modifying
  @Query(
      "delete from DataSourceGroupMemberEntity m"
          + " where m.id.dataSourceGroupId = :dataSourceGroupId and m.id.userId in :userIds")
  int deleteAllByDataSourceGroupIdAndUserIds(UUID dataSourceGroupId, Collection<UUID> userIds);

  @Query(
      "select m.id.dataSourceGroupId from DataSourceGroupMemberEntity m where m.id.userId = :userId")
  Set<UUID> findDataSourceGroupIdsByUserId(UUID userId);

  @Query(
      "select m from DataSourceGroupMemberEntity m join fetch m.group where m.id.userId = :userId")
  List<DataSourceGroupMemberEntity> findAllByUserIdWithGroup(@Param("userId") UUID userId);
}
