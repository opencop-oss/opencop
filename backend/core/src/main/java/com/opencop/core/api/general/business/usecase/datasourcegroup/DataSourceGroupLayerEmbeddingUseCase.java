// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.datasourcegroup;

import com.opencop.core.api.general.business.policy.DataSourceGroupPolicy;
import com.opencop.core.api.general.business.policy.ResourceLifecycleGuard;
import com.opencop.core.api.general.ports.gateway.datasourcegroup.DataSourceGroupAuthorization;
import com.opencop.core.api.general.ports.gateway.datasourcegroup.DataSourceGroupLayerService;
import com.opencop.core.api.general.ports.usecase.datasourcegroup.DataSourceGroupLayerEmbeddingManagement;
import com.opencop.core.api.general.technical.model.datasourcegroup.DataSourceGroupLayerAssignRequest;
import com.opencop.core.api.general.technical.model.datasourcegroup.DataSourceGroupLayerResponse;
import com.opencop.core.api.iam.business.usecase.access.layer.EffectiveLayerAccessService;
import com.opencop.core.sdk.ports.audit.AuthzAccessDeniedException;
import com.opencop.core.sdk.ports.audit.AuthzReasonCode;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.technical.gateway.db.PersistenceException;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerRole;
import com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup.DataSourceGroupRole;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class DataSourceGroupLayerEmbeddingUseCase
    implements DataSourceGroupLayerEmbeddingManagement {

  private static final String EMBED_OPERATION = "embed data source group in layer";
  private static final String REMOVE_EMBED_OPERATION = "remove data source group from layer";

  private final DataSourceGroupLayerService service;
  private final EffectiveLayerAccessService effectiveLayerAccess;
  private final DataSourceGroupAuthorization dataSourceGroupAuth;
  private final ResourceLifecycleGuard lifecycleGuard;

  public DataSourceGroupLayerEmbeddingUseCase(
      DataSourceGroupLayerService service,
      EffectiveLayerAccessService effectiveLayerAccess,
      DataSourceGroupAuthorization dataSourceGroupAuth,
      ResourceLifecycleGuard lifecycleGuard) {
    this.service = service;
    this.effectiveLayerAccess = effectiveLayerAccess;
    this.dataSourceGroupAuth = dataSourceGroupAuth;
    this.lifecycleGuard = lifecycleGuard;
  }

  @Override
  public List<DataSourceGroupLayerResponse> getDataSourceGroupsForLayer(
      UUID layerId, OpenCopPrincipal principal) {
    assertLayerRole(
        layerId, principal, LayerRole.MEMBER, LayerRole.EDITOR, LayerRole.ADMIN, LayerRole.OWNER);
    try {
      return service.getDataSourceGroupsForLayer(layerId);
    } catch (PersistenceException e) {
      throw new GlobalOpenCopException(e.getMessage(), HttpStatus.NOT_FOUND);
    }
  }

  @Override
  public List<DataSourceGroupLayerResponse> assignDataSourceGroupsToLayer(
      UUID layerId, DataSourceGroupLayerAssignRequest request, OpenCopPrincipal principal) {
    if (request == null
        || request.dataSourceGroupIds() == null
        || request.dataSourceGroupIds().isEmpty()) {
      throw new GlobalOpenCopException(
          "At least one data source group must be specified", HttpStatus.BAD_REQUEST);
    }
    if (request.dataSourceGroupIds().stream().anyMatch(Objects::isNull)) {
      throw new GlobalOpenCopException(
          "Data source group ids must not contain null", HttpStatus.BAD_REQUEST);
    }

    lifecycleGuard.assertLayerActive(layerId, EMBED_OPERATION);
    assertLayerRole(layerId, principal, LayerRole.ADMIN, LayerRole.OWNER);
    for (UUID dataSourceGroupId : request.dataSourceGroupIds()) {
      assertDataSourceGroupManagementRole(dataSourceGroupId, principal);
    }

    try {
      return service.assignDataSourceGroupsToLayer(layerId, request.dataSourceGroupIds());
    } catch (PersistenceException e) {
      throw new GlobalOpenCopException(e.getMessage(), HttpStatus.BAD_REQUEST);
    }
  }

  @Override
  public void removeDataSourceGroupFromLayer(
      UUID layerId, UUID dataSourceGroupId, OpenCopPrincipal principal) {
    lifecycleGuard.assertLayerActive(layerId, REMOVE_EMBED_OPERATION);
    assertLayerRole(layerId, principal, LayerRole.ADMIN, LayerRole.OWNER);
    assertDataSourceGroupManagementRole(dataSourceGroupId, principal);

    try {
      service.removeDataSourceGroupFromLayer(layerId, dataSourceGroupId);
    } catch (PersistenceException e) {
      throw new GlobalOpenCopException(e.getMessage(), HttpStatus.NOT_FOUND);
    }
  }

  @Override
  public List<DataSourceGroupLayerResponse> getLayersForDataSourceGroup(
      UUID dataSourceGroupId, OpenCopPrincipal principal) {
    DataSourceGroupPolicy.assertContainerMember(
        dataSourceGroupAuth.getEffectiveContainerRole(dataSourceGroupId, principal),
        principal.isGlobalAdmin());
    try {
      var layers = service.getLayersForDataSourceGroup(dataSourceGroupId);
      if (principal.isGlobalAdmin()) {
        return layers;
      }
      return layers.stream()
          .filter(
              layer ->
                  effectiveLayerAccess
                      .getEffectiveRole(layer.layerId(), principal.internalUserId())
                      .isPresent())
          .toList();
    } catch (PersistenceException e) {
      throw new GlobalOpenCopException(e.getMessage(), HttpStatus.NOT_FOUND);
    }
  }

  private void assertLayerRole(
      UUID layerId, OpenCopPrincipal principal, LayerRole... allowedRoles) {
    if (principal.isGlobalAdmin()) {
      return;
    }
    Optional<LayerRole> effectiveRole =
        effectiveLayerAccess.getEffectiveRole(layerId, principal.internalUserId());
    if (effectiveRole.isPresent() && List.of(allowedRoles).contains(effectiveRole.get())) {
      return;
    }
    throw new AuthzAccessDeniedException(
        effectiveRole.isEmpty()
            ? AuthzReasonCode.MISSING_SOURCE_OR_LAYER_ROLE
            : AuthzReasonCode.INSUFFICIENT_SOURCE_OR_LAYER_ROLE,
        "You are not authorized for the specified change to this layer");
  }

  private void assertDataSourceGroupManagementRole(
      UUID dataSourceGroupId, OpenCopPrincipal principal) {
    DataSourceGroupPolicy.assertContainerRole(
        dataSourceGroupAuth.getEffectiveContainerRole(dataSourceGroupId, principal),
        principal.isGlobalAdmin(),
        DataSourceGroupRole.MAINTAINER,
        DataSourceGroupRole.ADMIN,
        DataSourceGroupRole.OWNER);
  }
}
