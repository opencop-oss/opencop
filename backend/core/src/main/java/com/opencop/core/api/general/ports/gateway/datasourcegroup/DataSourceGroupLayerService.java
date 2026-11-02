// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.gateway.datasourcegroup;

import com.opencop.core.api.general.technical.model.datasourcegroup.DataSourceGroupLayerResponse;
import com.opencop.core.sdk.technical.gateway.db.PersistenceException;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface DataSourceGroupLayerService {

  List<DataSourceGroupLayerResponse> getDataSourceGroupsForLayer(UUID layerId)
      throws PersistenceException;

  List<DataSourceGroupLayerResponse> assignDataSourceGroupsToLayer(
      UUID layerId, Set<UUID> dataSourceGroupIds) throws PersistenceException;

  void removeDataSourceGroupFromLayer(UUID layerId, UUID dataSourceGroupId)
      throws PersistenceException;

  List<DataSourceGroupLayerResponse> getLayersForDataSourceGroup(UUID dataSourceGroupId)
      throws PersistenceException;
}
