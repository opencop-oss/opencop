// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.me.permissions;

import com.opencop.core.api.general.ports.gateway.datasourcegroup.DataSourceGroupService;
import com.opencop.core.api.general.ports.usecase.me.RetrieveUserDataSourceGroups;
import com.opencop.core.api.general.technical.model.datasourcegroup.MyDataSourceGroupResponse;
import java.util.List;
import java.util.UUID;

public class RetrieveUserDataSourceGroupsUsecase implements RetrieveUserDataSourceGroups {

  private final DataSourceGroupService dataSourceGroupService;

  public RetrieveUserDataSourceGroupsUsecase(DataSourceGroupService dataSourceGroupService) {
    this.dataSourceGroupService = dataSourceGroupService;
  }

  @Override
  public List<MyDataSourceGroupResponse> get(UUID userId) {
    return dataSourceGroupService.listMyGroups(userId);
  }
}
