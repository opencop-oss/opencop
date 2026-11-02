// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.datasourcegroup;

import com.opencop.core.api.general.technical.gateway.scim.user.InternalUserEntity;
import com.opencop.core.api.general.technical.model.datasourcegroup.DataSourceGroupMemberResponse;
import com.opencop.core.api.general.technical.model.datasourcegroup.DataSourceGroupResponse;
import com.opencop.core.api.general.technical.model.datasourcegroup.DataSourceSummary;
import com.opencop.core.api.general.technical.model.datasourcegroup.MyDataSourceGroupResponse;
import com.opencop.core.sdk.model.datasource.DataSource;
import com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup.DataSourceGroupDataSourceEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup.DataSourceGroupEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup.DataSourceGroupMemberEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup.DataSourceGroupRole;
import com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup.DataSourceGroupUserGroupEntity;
import java.util.List;

public final class DataSourceGroupMapper {

  private DataSourceGroupMapper() {}

  public static DataSourceGroupResponse toResponse(DataSourceGroupEntity e) {
    return new DataSourceGroupResponse(
        e.getId(), e.getName(), e.getDescription(), e.getCreatedAt());
  }

  public static MyDataSourceGroupResponse toMyResponse(DataSourceGroupMemberEntity e) {
    return toMyResponse(e.getGroup(), e.getRole());
  }

  public static MyDataSourceGroupResponse toMyResponse(
      DataSourceGroupEntity group, DataSourceGroupRole role) {
    return new MyDataSourceGroupResponse(
        group.getId(),
        group.getName(),
        group.getDescription(),
        role,
        group.getCreatedAt(),
        List.of());
  }

  public static MyDataSourceGroupResponse toMyResponse(DataSourceGroupUserGroupEntity e) {
    return toMyResponse(e.getDataSourceGroup(), e.getRole());
  }

  public static MyDataSourceGroupResponse withDataSources(
      MyDataSourceGroupResponse r, List<DataSourceSummary> dataSources) {
    return new MyDataSourceGroupResponse(
        r.id(), r.name(), r.description(), r.containerRole(), r.createdAt(), dataSources);
  }

  public static DataSourceSummary toDataSourceSummary(
      DataSourceGroupDataSourceEntity e, DataSource dataSource) {
    return new DataSourceSummary(
        e.getDataSourceId(), dataSource != null ? dataSource.name() : null, e.getRole());
  }

  public static DataSourceGroupMemberResponse toMemberResponse(
      DataSourceGroupMemberEntity e, InternalUserEntity user) {
    return new DataSourceGroupMemberResponse(
        e.getUserId(),
        user != null ? user.getUsername() : null,
        user != null ? user.getFirstName() : null,
        user != null ? user.getLastName() : null,
        user != null ? user.getEmail() : null,
        e.getRole(),
        e.getJoinedAt());
  }
}
