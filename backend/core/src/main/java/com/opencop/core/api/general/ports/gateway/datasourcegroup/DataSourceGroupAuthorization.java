// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.gateway.datasourcegroup;

import com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup.DataSourceGroupRole;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.util.Optional;
import java.util.UUID;

public interface DataSourceGroupAuthorization {

  Optional<DataSourceGroupRole> getEffectiveContainerRole(
      UUID dataSourceGroupId, OpenCopPrincipal principal);
}
