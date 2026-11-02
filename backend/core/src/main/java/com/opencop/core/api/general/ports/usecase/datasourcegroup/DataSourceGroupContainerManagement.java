// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.usecase.datasourcegroup;

import com.opencop.core.api.general.technical.model.datasourcegroup.DataSourceGroupCreateRequest;
import com.opencop.core.api.general.technical.model.datasourcegroup.DataSourceGroupPatchRequest;
import com.opencop.core.api.general.technical.model.datasourcegroup.DataSourceGroupResponse;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.util.UUID;

public interface DataSourceGroupContainerManagement {

  DataSourceGroupResponse create(DataSourceGroupCreateRequest request, OpenCopPrincipal principal);

  DataSourceGroupResponse get(UUID dataSourceGroupId, OpenCopPrincipal principal);

  DataSourceGroupResponse patch(
      UUID dataSourceGroupId, DataSourceGroupPatchRequest request, OpenCopPrincipal principal);

  void delete(UUID dataSourceGroupId, OpenCopPrincipal principal);
}
