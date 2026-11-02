// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.gateway.datasourcegroup;

import com.opencop.core.api.general.technical.model.datasourcegroup.DataSourceGroupUserGroupsPatchRequest;
import com.opencop.core.sdk.technical.gateway.db.PersistenceException;
import com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup.DataSourceGroupUserGroupEntity;
import java.util.List;
import java.util.UUID;

public interface DataSourceGroupUserGroupService {

  List<DataSourceGroupUserGroupEntity> listUserGroupsForDataSourceGroup(UUID dataSourceGroupId)
      throws PersistenceException;

  void applyUserGroupPatch(
      UUID dataSourceGroupId,
      List<DataSourceGroupUserGroupsPatchRequest.UserGroupAdd> additions,
      List<UUID> removals,
      DataSourceGroupUserGroupPatchGuard guard)
      throws PersistenceException;
}
