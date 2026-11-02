// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.usecase.datasourcegroup;

import com.opencop.core.api.general.technical.model.datasourcegroup.DataSourceGroupLayerAssignRequest;
import com.opencop.core.api.general.technical.model.datasourcegroup.DataSourceGroupLayerResponse;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.util.List;
import java.util.UUID;

public interface DataSourceGroupLayerEmbeddingManagement {

  List<DataSourceGroupLayerResponse> getDataSourceGroupsForLayer(
      UUID layerId, OpenCopPrincipal principal);

  List<DataSourceGroupLayerResponse> assignDataSourceGroupsToLayer(
      UUID layerId, DataSourceGroupLayerAssignRequest request, OpenCopPrincipal principal);

  void removeDataSourceGroupFromLayer(
      UUID layerId, UUID dataSourceGroupId, OpenCopPrincipal principal);

  List<DataSourceGroupLayerResponse> getLayersForDataSourceGroup(
      UUID dataSourceGroupId, OpenCopPrincipal principal);
}
