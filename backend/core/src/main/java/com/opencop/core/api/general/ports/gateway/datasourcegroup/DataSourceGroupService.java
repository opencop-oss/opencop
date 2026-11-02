// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.gateway.datasourcegroup;

import com.opencop.core.api.general.technical.model.datasourcegroup.DataSourceGroupCreateRequest;
import com.opencop.core.api.general.technical.model.datasourcegroup.DataSourceGroupMemberResponse;
import com.opencop.core.api.general.technical.model.datasourcegroup.DataSourceGroupMembersPatchRequest;
import com.opencop.core.api.general.technical.model.datasourcegroup.DataSourceGroupPatchRequest;
import com.opencop.core.api.general.technical.model.datasourcegroup.DataSourceGroupResponse;
import com.opencop.core.api.general.technical.model.datasourcegroup.MyDataSourceGroupResponse;
import com.opencop.core.sdk.technical.gateway.db.PersistenceException;
import java.util.List;
import java.util.UUID;

public interface DataSourceGroupService {

  List<DataSourceGroupResponse> listGroups();

  List<DataSourceGroupResponse> listGroupsForUser(UUID userId);

  List<MyDataSourceGroupResponse> listMyGroups(UUID userId);

  DataSourceGroupResponse createGroup(DataSourceGroupCreateRequest request)
      throws PersistenceException;

  DataSourceGroupResponse getGroup(UUID groupId) throws PersistenceException;

  DataSourceGroupResponse patchGroup(UUID groupId, DataSourceGroupPatchRequest request)
      throws PersistenceException;

  void deleteGroup(UUID groupId) throws PersistenceException;

  List<DataSourceGroupMemberResponse> getMembers(UUID dataSourceGroupId)
      throws PersistenceException;

  void patchMembers(
      UUID dataSourceGroupId,
      DataSourceGroupMembersPatchRequest request,
      DataSourceGroupMemberPatchGuard guard)
      throws PersistenceException;
}
