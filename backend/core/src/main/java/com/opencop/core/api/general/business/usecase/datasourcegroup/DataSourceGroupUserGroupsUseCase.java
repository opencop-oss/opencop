// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.datasourcegroup;

import com.opencop.core.api.general.business.policy.DataSourceGroupPolicy;
import com.opencop.core.api.general.ports.gateway.datasourcegroup.DataSourceGroupAuthorization;
import com.opencop.core.api.general.ports.gateway.datasourcegroup.DataSourceGroupUserGroupPatchGuard;
import com.opencop.core.api.general.ports.gateway.datasourcegroup.DataSourceGroupUserGroupService;
import com.opencop.core.api.general.ports.usecase.datasourcegroup.DataSourceGroupUserGroupManagement;
import com.opencop.core.api.general.technical.model.datasourcegroup.DataSourceGroupUserGroupResponse;
import com.opencop.core.api.general.technical.model.datasourcegroup.DataSourceGroupUserGroupsPatchRequest;
import com.opencop.core.sdk.ports.audit.AuthzAccessDeniedException;
import com.opencop.core.sdk.ports.audit.AuthzReasonCode;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.technical.gateway.db.PersistenceException;
import com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup.DataSourceGroupRole;
import com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup.DataSourceGroupUserGroupEntity;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class DataSourceGroupUserGroupsUseCase implements DataSourceGroupUserGroupManagement {

  private final DataSourceGroupAuthorization auth;
  private final DataSourceGroupUserGroupService service;

  public DataSourceGroupUserGroupsUseCase(
      DataSourceGroupAuthorization auth, DataSourceGroupUserGroupService service) {
    this.auth = auth;
    this.service = service;
  }

  @Override
  public List<DataSourceGroupUserGroupResponse> getUserGroups(
      UUID dataSourceGroupId, OpenCopPrincipal principal) {
    DataSourceGroupPolicy.assertContainerMember(
        auth.getEffectiveContainerRole(dataSourceGroupId, principal), principal.isGlobalAdmin());
    List<DataSourceGroupUserGroupEntity> assignments;
    try {
      assignments = service.listUserGroupsForDataSourceGroup(dataSourceGroupId);
    } catch (PersistenceException e) {
      throw new GlobalOpenCopException(e.getMessage(), HttpStatus.NOT_FOUND);
    }
    return assignments.stream()
        .map(
            e ->
                new DataSourceGroupUserGroupResponse(
                    e.getUserGroupId(), e.getUserGroup().getName(), e.getRole(), e.getAssignedAt()))
        .toList();
  }

  @Override
  public void patchUserGroups(
      UUID dataSourceGroupId,
      DataSourceGroupUserGroupsPatchRequest request,
      OpenCopPrincipal principal)
      throws PersistenceException {
    validateRequest(request);
    DataSourceGroupPolicy.assertContainerRole(
        auth.getEffectiveContainerRole(dataSourceGroupId, principal),
        principal.isGlobalAdmin(),
        DataSourceGroupRole.ADMIN,
        DataSourceGroupRole.OWNER);
    assertCanAssignRequestedRoles(dataSourceGroupId, request, principal);

    DataSourceGroupUserGroupPatchGuard guard =
        buildPatchGuard(dataSourceGroupId, principal, principal.isGlobalAdmin());
    service.applyUserGroupPatch(dataSourceGroupId, request.add(), request.remove(), guard);
  }

  private static void validateRequest(DataSourceGroupUserGroupsPatchRequest request)
      throws PersistenceException {
    if (request == null) {
      throw new PersistenceException("Data source group user groups patch request is required");
    }
    for (DataSourceGroupUserGroupsPatchRequest.UserGroupAdd addition : request.add()) {
      if (addition == null) {
        throw new PersistenceException("User group addition is required");
      }
      if (addition.userGroupId() == null) {
        throw new PersistenceException("User group id is required");
      }
      if (addition.role() == null) {
        throw new PersistenceException("User group role is required");
      }
    }
    if (request.remove().stream().anyMatch(Objects::isNull)) {
      throw new PersistenceException("User group removal id is required");
    }
  }

  private void assertCanAssignRequestedRoles(
      UUID dataSourceGroupId,
      DataSourceGroupUserGroupsPatchRequest request,
      OpenCopPrincipal principal) {
    if (!principal.isGlobalAdmin()) {
      Optional<DataSourceGroupRole> effectiveRole =
          auth.getEffectiveContainerRole(dataSourceGroupId, principal);
      for (DataSourceGroupUserGroupsPatchRequest.UserGroupAdd addition : request.add()) {
        if (effectiveRole.isEmpty() || addition.role().isHigherThan(effectiveRole.get())) {
          throw new AuthzAccessDeniedException(
              effectiveRole.isEmpty()
                  ? AuthzReasonCode.MISSING_DATA_SOURCE_GROUP_ROLE
                  : AuthzReasonCode.INSUFFICIENT_DATA_SOURCE_GROUP_ROLE,
              "You are not authorized to assign the requested data source group role");
        }
      }
    }
  }

  private DataSourceGroupUserGroupPatchGuard buildPatchGuard(
      UUID dataSourceGroupId, OpenCopPrincipal principal, boolean isGlobalAdmin) {
    return (currentUsers, currentUserGroups, projectedUserGroups) -> {
      if (!isGlobalAdmin
          && DataSourceGroupPolicy.userGroupPatchAffectsOwnerList(
              currentUsers, currentUserGroups, projectedUserGroups)) {
        DataSourceGroupPolicy.assertContainerRole(
            auth.getEffectiveContainerRole(dataSourceGroupId, principal),
            principal.isGlobalAdmin(),
            DataSourceGroupRole.OWNER);
      }
      DataSourceGroupPolicy.assertRetainsOwner(currentUsers, projectedUserGroups);
    };
  }
}
