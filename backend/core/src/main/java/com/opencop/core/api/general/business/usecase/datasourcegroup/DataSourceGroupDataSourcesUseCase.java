// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.datasourcegroup;

import com.opencop.core.api.general.business.policy.DataSourceGroupPolicy;
import com.opencop.core.api.general.business.policy.ResourceLifecycleGuard;
import com.opencop.core.api.general.ports.gateway.datasourcegroup.DataSourceGroupAuthorization;
import com.opencop.core.api.general.ports.gateway.datasourcegroup.DataSourceGroupDataSourceService;
import com.opencop.core.api.general.ports.usecase.datasourcegroup.DataSourceGroupDataSourceManagement;
import com.opencop.core.api.general.technical.model.datasourcegroup.DataSourceGroupDataSourceAssignRequest;
import com.opencop.core.api.general.technical.model.datasourcegroup.DataSourceGroupDataSourceResponse;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.ports.gateway.iam.SourceAccessRepository;
import com.opencop.core.sdk.technical.gateway.db.PersistenceException;
import com.opencop.core.sdk.technical.gateway.db.iam.ServerRole;
import com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup.DataSourceGroupRole;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class DataSourceGroupDataSourcesUseCase implements DataSourceGroupDataSourceManagement {

  private static final String ASSIGN_OPERATION = "assign data source to data source group";

  private final DataSourceGroupAuthorization auth;
  private final DataSourceGroupDataSourceService dataSourceService;
  private final SourceAccessRepository sourceAccessRepository;
  private final ResourceLifecycleGuard lifecycleGuard;

  public DataSourceGroupDataSourcesUseCase(
      DataSourceGroupAuthorization auth,
      DataSourceGroupDataSourceService dataSourceService,
      SourceAccessRepository sourceAccessRepository,
      ResourceLifecycleGuard lifecycleGuard) {
    this.auth = auth;
    this.dataSourceService = dataSourceService;
    this.sourceAccessRepository = sourceAccessRepository;
    this.lifecycleGuard = lifecycleGuard;
  }

  @Override
  public List<DataSourceGroupDataSourceResponse> getDataSources(
      UUID dataSourceGroupId, OpenCopPrincipal principal) {
    DataSourceGroupPolicy.assertContainerMember(
        auth.getEffectiveContainerRole(dataSourceGroupId, principal), principal.isGlobalAdmin());
    try {
      return dataSourceService.getDataSources(dataSourceGroupId);
    } catch (PersistenceException e) {
      throw new GlobalOpenCopException(e.getMessage(), HttpStatus.NOT_FOUND);
    }
  }

  @Override
  public List<DataSourceGroupDataSourceResponse> assignDataSources(
      UUID dataSourceGroupId,
      DataSourceGroupDataSourceAssignRequest request,
      OpenCopPrincipal principal) {
    DataSourceGroupPolicy.assertContainerRole(
        auth.getEffectiveContainerRole(dataSourceGroupId, principal),
        principal.isGlobalAdmin(),
        DataSourceGroupRole.MAINTAINER,
        DataSourceGroupRole.ADMIN,
        DataSourceGroupRole.OWNER);
    validateRequest(request);

    for (UUID dataSourceId : request.dataSourceIds()) {
      lifecycleGuard.assertDataSourceActive(dataSourceId, ASSIGN_OPERATION);
      if (!principal.isGlobalAdmin()) {
        Optional<ServerRole> effectiveRole =
            Optional.ofNullable(
                sourceAccessRepository.userRole(principal.internalUserId(), dataSourceId));
        DataSourceGroupPolicy.assertCanAssignSourceRole(effectiveRole, request.role());
      }
    }

    try {
      return dataSourceService.assignDataSources(dataSourceGroupId, request);
    } catch (PersistenceException e) {
      throw new GlobalOpenCopException(e.getMessage(), HttpStatus.BAD_REQUEST);
    }
  }

  @Override
  public void removeDataSource(
      UUID dataSourceGroupId, UUID dataSourceId, OpenCopPrincipal principal) {
    DataSourceGroupPolicy.assertContainerRole(
        auth.getEffectiveContainerRole(dataSourceGroupId, principal),
        principal.isGlobalAdmin(),
        DataSourceGroupRole.MAINTAINER,
        DataSourceGroupRole.ADMIN,
        DataSourceGroupRole.OWNER);
    try {
      dataSourceService.removeDataSource(dataSourceGroupId, dataSourceId);
    } catch (PersistenceException e) {
      throw new GlobalOpenCopException(e.getMessage(), HttpStatus.NOT_FOUND);
    }
  }

  private static void validateRequest(DataSourceGroupDataSourceAssignRequest request) {
    if (request == null || request.dataSourceIds().isEmpty()) {
      throw new GlobalOpenCopException(
          "At least one data source must be specified", HttpStatus.BAD_REQUEST);
    }
    if (request.dataSourceIds().stream().anyMatch(Objects::isNull)) {
      throw new GlobalOpenCopException(
          "Data source ids must not contain null", HttpStatus.BAD_REQUEST);
    }
  }
}
