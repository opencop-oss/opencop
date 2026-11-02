// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface IamDataSourceGroupDataSourceRepository
    extends JpaRepository<DataSourceGroupDataSourceEntity, DataSourceGroupDataSourceIdEntity> {

  @Query(
      "select m from DataSourceGroupDataSourceEntity m" + " where m.group.id = :dataSourceGroupId")
  List<DataSourceGroupDataSourceEntity> findAllByDataSourceGroupId(UUID dataSourceGroupId);

  @Query(
      "select m from DataSourceGroupDataSourceEntity m"
          + " where m.id.dataSourceId = :dataSourceId")
  List<DataSourceGroupDataSourceEntity> findAllByDataSourceId(UUID dataSourceId);

  @Query(
      "select m from DataSourceGroupDataSourceEntity m"
          + " where m.group.id in :dataSourceGroupIds")
  List<DataSourceGroupDataSourceEntity> findAllByDataSourceGroupIdIn(
      Collection<UUID> dataSourceGroupIds);

  @Modifying
  @Query(
      "delete from DataSourceGroupDataSourceEntity m"
          + " where m.id.dataSourceGroupId = :dataSourceGroupId and m.id.dataSourceId = :dataSourceId")
  void deleteByDataSourceGroupIdAndDataSourceId(UUID dataSourceGroupId, UUID dataSourceId);
}
