// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.model.datasourcegroup;

import com.opencop.core.sdk.technical.gateway.db.iam.ServerRole;
import java.util.Set;
import java.util.UUID;

public record DataSourceGroupDataSourceAssignRequest(Set<UUID> dataSourceIds, ServerRole role) {
  public DataSourceGroupDataSourceAssignRequest {
    dataSourceIds = dataSourceIds != null ? dataSourceIds : Set.of();
    role = role != null ? role : ServerRole.VIEWER;
  }
}
