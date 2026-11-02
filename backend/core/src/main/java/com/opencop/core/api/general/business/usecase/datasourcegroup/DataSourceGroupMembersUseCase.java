// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.datasourcegroup;

import com.opencop.core.api.general.business.policy.DataSourceGroupPolicy;
import com.opencop.core.api.general.ports.gateway.datasourcegroup.DataSourceGroupAuthorization;
import com.opencop.core.api.general.ports.gateway.datasourcegroup.DataSourceGroupMemberPatchGuard;
import com.opencop.core.api.general.ports.gateway.datasourcegroup.DataSourceGroupService;
import com.opencop.core.api.general.ports.usecase.datasourcegroup.DataSourceGroupMemberManagement;
import com.opencop.core.api.general.technical.model.datasourcegroup.DataSourceGroupMemberResponse;
import com.opencop.core.api.general.technical.model.datasourcegroup.DataSourceGroupMembersPatchRequest;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.technical.gateway.db.PersistenceException;
import com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup.DataSourceGroupRole;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class DataSourceGroupMembersUseCase implements DataSourceGroupMemberManagement {

  private final DataSourceGroupAuthorization auth;
  private final DataSourceGroupService dataSourceGroupService;

  public DataSourceGroupMembersUseCase(
      DataSourceGroupAuthorization auth, DataSourceGroupService dataSourceGroupService) {
    this.auth = auth;
    this.dataSourceGroupService = dataSourceGroupService;
  }

  @Override
  public List<DataSourceGroupMemberResponse> getMembers(
      UUID dataSourceGroupId, OpenCopPrincipal principal) {
    DataSourceGroupPolicy.assertContainerMember(
        auth.getEffectiveContainerRole(dataSourceGroupId, principal), principal.isGlobalAdmin());
    try {
      return dataSourceGroupService.getMembers(dataSourceGroupId);
    } catch (PersistenceException e) {
      throw new GlobalOpenCopException(e.getMessage(), HttpStatus.NOT_FOUND);
    }
  }

  @Override
  public void patchMembers(
      UUID dataSourceGroupId,
      DataSourceGroupMembersPatchRequest request,
      OpenCopPrincipal principal) {
    validateRequest(request);
    DataSourceGroupPolicy.assertContainerRole(
        auth.getEffectiveContainerRole(dataSourceGroupId, principal),
        principal.isGlobalAdmin(),
        DataSourceGroupRole.ADMIN,
        DataSourceGroupRole.OWNER);
    DataSourceGroupMemberPatchGuard guard =
        buildGuard(dataSourceGroupId, principal, principal.isGlobalAdmin());
    try {
      dataSourceGroupService.patchMembers(dataSourceGroupId, request, guard);
    } catch (PersistenceException e) {
      throw new GlobalOpenCopException(e.getMessage(), HttpStatus.BAD_REQUEST);
    }
  }

  private static void validateRequest(DataSourceGroupMembersPatchRequest request) {
    if (request == null) {
      throw new GlobalOpenCopException(
          "Data source group members patch request is required", HttpStatus.BAD_REQUEST);
    }
    for (DataSourceGroupMembersPatchRequest.MemberAdd addition : request.add()) {
      if (addition == null) {
        throw new GlobalOpenCopException("Member addition is required", HttpStatus.BAD_REQUEST);
      }
      if (addition.userId() == null) {
        throw new GlobalOpenCopException("Member user id is required", HttpStatus.BAD_REQUEST);
      }
      if (addition.role() == null) {
        throw new GlobalOpenCopException("Member role is required", HttpStatus.BAD_REQUEST);
      }
    }
    if (request.remove().stream().anyMatch(Objects::isNull)) {
      throw new GlobalOpenCopException("Member removal id is required", HttpStatus.BAD_REQUEST);
    }
  }

  private DataSourceGroupMemberPatchGuard buildGuard(
      UUID dataSourceGroupId, OpenCopPrincipal principal, boolean isGlobalAdmin) {
    return (currentUsers, projectedUsers, currentUserGroups) -> {
      if (!isGlobalAdmin
          && DataSourceGroupPolicy.memberPatchAffectsOwnerList(
              currentUsers, projectedUsers, currentUserGroups)) {
        DataSourceGroupPolicy.assertContainerRole(
            auth.getEffectiveContainerRole(dataSourceGroupId, principal),
            principal.isGlobalAdmin(),
            DataSourceGroupRole.OWNER);
      }
      DataSourceGroupPolicy.assertRetainsOwner(projectedUsers, currentUserGroups);
    };
  }
}
