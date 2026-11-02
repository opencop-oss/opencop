// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.usecase.datasourcegroup;

import com.opencop.core.api.general.technical.model.datasourcegroup.DataSourceGroupDataSourceAssignRequest;
import com.opencop.core.api.general.technical.model.datasourcegroup.DataSourceGroupDataSourceResponse;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.util.List;
import java.util.UUID;

public interface DataSourceGroupDataSourceManagement {

  List<DataSourceGroupDataSourceResponse> getDataSources(
      UUID dataSourceGroupId, OpenCopPrincipal principal);

  List<DataSourceGroupDataSourceResponse> assignDataSources(
      UUID dataSourceGroupId,
      DataSourceGroupDataSourceAssignRequest request,
      OpenCopPrincipal principal);

  void removeDataSource(UUID dataSourceGroupId, UUID dataSourceId, OpenCopPrincipal principal);
}
