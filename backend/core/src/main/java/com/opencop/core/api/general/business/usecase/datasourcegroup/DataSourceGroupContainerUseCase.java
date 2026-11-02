// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.datasourcegroup;

import com.opencop.core.api.general.business.policy.DataSourceGroupPolicy;
import com.opencop.core.api.general.ports.gateway.datasourcegroup.DataSourceGroupAuthorization;
import com.opencop.core.api.general.ports.gateway.datasourcegroup.DataSourceGroupService;
import com.opencop.core.api.general.ports.usecase.datasourcegroup.DataSourceGroupContainerManagement;
import com.opencop.core.api.general.technical.model.datasourcegroup.DataSourceGroupCreateRequest;
import com.opencop.core.api.general.technical.model.datasourcegroup.DataSourceGroupMembersPatchRequest;
import com.opencop.core.api.general.technical.model.datasourcegroup.DataSourceGroupPatchRequest;
import com.opencop.core.api.general.technical.model.datasourcegroup.DataSourceGroupResponse;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.technical.gateway.db.NotFoundException;
import com.opencop.core.sdk.technical.gateway.db.PersistenceException;
import com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup.DataSourceGroupRole;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class DataSourceGroupContainerUseCase implements DataSourceGroupContainerManagement {

  private final DataSourceGroupAuthorization auth;
  private final DataSourceGroupService dataSourceGroupService;
  private final TransactionTemplate transactionTemplate;

  public DataSourceGroupContainerUseCase(
      DataSourceGroupAuthorization auth,
      DataSourceGroupService dataSourceGroupService,
      TransactionTemplate transactionTemplate) {
    this.auth = auth;
    this.dataSourceGroupService = dataSourceGroupService;
    this.transactionTemplate = transactionTemplate;
  }

  @Override
  public DataSourceGroupResponse create(
      DataSourceGroupCreateRequest request, OpenCopPrincipal principal) {
    if (request == null) {
      throw new GlobalOpenCopException(
          "Data source group request is required", HttpStatus.BAD_REQUEST);
    }
    return transactionTemplate.execute(_ -> createWithOwner(request, principal.internalUserId()));
  }

  private DataSourceGroupResponse createWithOwner(
      DataSourceGroupCreateRequest request, UUID ownerId) {
    try {
      DataSourceGroupResponse created = dataSourceGroupService.createGroup(request);
      dataSourceGroupService.patchMembers(
          created.id(),
          new DataSourceGroupMembersPatchRequest(
              Set.of(
                  new DataSourceGroupMembersPatchRequest.MemberAdd(
                      ownerId, DataSourceGroupRole.OWNER)),
              Set.of()),
          null);
      return created;
    } catch (PersistenceException e) {
      throw new GlobalOpenCopException(e.getMessage(), HttpStatus.BAD_REQUEST);
    }
  }

  @Override
  public DataSourceGroupResponse get(UUID dataSourceGroupId, OpenCopPrincipal principal) {
    DataSourceGroupPolicy.assertContainerMember(
        auth.getEffectiveContainerRole(dataSourceGroupId, principal), principal.isGlobalAdmin());
    try {
      return dataSourceGroupService.getGroup(dataSourceGroupId);
    } catch (PersistenceException e) {
      throw new GlobalOpenCopException(e.getMessage(), HttpStatus.NOT_FOUND);
    }
  }

  @Override
  public DataSourceGroupResponse patch(
      UUID dataSourceGroupId, DataSourceGroupPatchRequest request, OpenCopPrincipal principal) {
    if (request == null) {
      throw new GlobalOpenCopException(
          "Data source group patch request is required", HttpStatus.BAD_REQUEST);
    }
    DataSourceGroupPolicy.assertContainerRole(
        auth.getEffectiveContainerRole(dataSourceGroupId, principal),
        principal.isGlobalAdmin(),
        DataSourceGroupRole.ADMIN,
        DataSourceGroupRole.OWNER);
    try {
      return dataSourceGroupService.patchGroup(dataSourceGroupId, request);
    } catch (NotFoundException e) {
      throw new GlobalOpenCopException(e.getMessage(), HttpStatus.NOT_FOUND);
    } catch (PersistenceException e) {
      throw new GlobalOpenCopException(e.getMessage(), HttpStatus.BAD_REQUEST);
    }
  }

  @Override
  public void delete(UUID dataSourceGroupId, OpenCopPrincipal principal) {
    DataSourceGroupPolicy.assertContainerRole(
        auth.getEffectiveContainerRole(dataSourceGroupId, principal),
        principal.isGlobalAdmin(),
        DataSourceGroupRole.OWNER);
    try {
      dataSourceGroupService.deleteGroup(dataSourceGroupId);
    } catch (PersistenceException e) {
      throw new GlobalOpenCopException(e.getMessage(), HttpStatus.NOT_FOUND);
    }
  }
}
