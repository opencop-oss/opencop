// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.usecase.datasourcegroup;

import com.opencop.core.api.general.technical.model.datasourcegroup.DataSourceGroupMemberResponse;
import com.opencop.core.api.general.technical.model.datasourcegroup.DataSourceGroupMembersPatchRequest;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.util.List;
import java.util.UUID;

public interface DataSourceGroupMemberManagement {

  List<DataSourceGroupMemberResponse> getMembers(
      UUID dataSourceGroupId, OpenCopPrincipal principal);

  void patchMembers(
      UUID dataSourceGroupId,
      DataSourceGroupMembersPatchRequest request,
      OpenCopPrincipal principal);
}
