// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.usecase.datasourcegroup;

import com.opencop.core.api.general.technical.model.datasourcegroup.DataSourceGroupUserGroupResponse;
import com.opencop.core.api.general.technical.model.datasourcegroup.DataSourceGroupUserGroupsPatchRequest;
import com.opencop.core.sdk.technical.gateway.db.PersistenceException;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.util.List;
import java.util.UUID;

public interface DataSourceGroupUserGroupManagement {

  List<DataSourceGroupUserGroupResponse> getUserGroups(
      UUID dataSourceGroupId, OpenCopPrincipal principal);

  void patchUserGroups(
      UUID dataSourceGroupId,
      DataSourceGroupUserGroupsPatchRequest request,
      OpenCopPrincipal principal)
      throws PersistenceException;
}
