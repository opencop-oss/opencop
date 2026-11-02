// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.gateway.datasourcegroup;

import com.opencop.core.api.general.technical.model.datasourcegroup.DataSourceGroupDataSourceAssignRequest;
import com.opencop.core.api.general.technical.model.datasourcegroup.DataSourceGroupDataSourceResponse;
import com.opencop.core.sdk.technical.gateway.db.PersistenceException;
import java.util.List;
import java.util.UUID;

public interface DataSourceGroupDataSourceService {

  List<DataSourceGroupDataSourceResponse> getDataSources(UUID dataSourceGroupId)
      throws PersistenceException;

  List<DataSourceGroupDataSourceResponse> assignDataSources(
      UUID dataSourceGroupId, DataSourceGroupDataSourceAssignRequest request)
      throws PersistenceException;

  void removeDataSource(UUID dataSourceGroupId, UUID dataSourceId) throws PersistenceException;
}
