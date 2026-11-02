// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface IamDataSourceGroupUserGroupRepository
    extends JpaRepository<DataSourceGroupUserGroupEntity, DataSourceGroupUserGroupIdEntity> {

  @Query(
      "select e from DataSourceGroupUserGroupEntity e"
          + " where e.id.dataSourceGroupId = :dataSourceGroupId")
  List<DataSourceGroupUserGroupEntity> findAllByDataSourceGroupId(UUID dataSourceGroupId);

  @Query(
      "select e.id.dataSourceGroupId from DataSourceGroupUserGroupEntity e"
          + " where e.id.userGroupId in :userGroupIds")
  Set<UUID> findDataSourceGroupIdsByUserGroupIdIn(Collection<UUID> userGroupIds);

  @Query(
      "select e from DataSourceGroupUserGroupEntity e join fetch e.dataSourceGroup"
          + " where e.id.userGroupId in :userGroupIds")
  List<DataSourceGroupUserGroupEntity> findAllByUserGroupIdInWithDataSourceGroup(
      Collection<UUID> userGroupIds);

  List<DataSourceGroupUserGroupEntity> findAllByIdDataSourceGroupIdAndIdUserGroupIdIn(
      UUID dataSourceGroupId, Collection<UUID> userGroupIds);

  void deleteAllByIdDataSourceGroupIdAndIdUserGroupIdIn(
      UUID dataSourceGroupId, Collection<UUID> userGroupIds);
}
