// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.gateway.datasourcegroup;

import com.opencop.core.sdk.technical.gateway.db.PersistenceException;
import com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup.DataSourceGroupRole;
import java.util.Map;
import java.util.UUID;

@FunctionalInterface
public interface DataSourceGroupMemberPatchGuard {
  void check(
      Map<UUID, DataSourceGroupRole> currentUsers,
      Map<UUID, DataSourceGroupRole> projectedUsers,
      Map<UUID, DataSourceGroupRole> currentUserGroups)
      throws PersistenceException;
}
