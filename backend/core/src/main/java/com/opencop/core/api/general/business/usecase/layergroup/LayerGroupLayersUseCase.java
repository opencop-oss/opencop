// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.layergroup;

import com.opencop.core.api.general.business.model.Caller;
import com.opencop.core.api.general.business.model.audit.AuditDeltas;
import com.opencop.core.api.general.business.model.audit.AuditEntry.EntityType;
import com.opencop.core.api.general.business.model.audit.AuditEntry.Operation;
import com.opencop.core.api.general.business.model.audit.AuditEvent;
import com.opencop.core.api.general.business.policy.LayerGroupPolicy;
import com.opencop.core.api.general.ports.gateway.audit.AuditRecorder;
import com.opencop.core.api.general.ports.gateway.layergroup.LayerGroupAuthorization;
import com.opencop.core.api.general.ports.gateway.layergroup.LayerGroupLayerService;
import com.opencop.core.api.general.ports.gateway.layergroup.LayerGroupService;
import com.opencop.core.api.general.ports.usecase.layergroup.LayerGroupLayerManagement;
import com.opencop.core.api.general.technical.model.layergroup.LayerGroupLayerResponse;
import com.opencop.core.api.general.technical.model.layergroup.LayerGroupLayersAssignRequest;
import com.opencop.core.api.iam.business.usecase.access.layer.EffectiveLayerAccessService;
import com.opencop.core.api.iam.ports.gateway.repository.LayersRepository;
import com.opencop.core.api.iam.ports.gateway.repository.RepositoryException;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.technical.gateway.db.PersistenceException;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerAccessScope;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerRole;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.LayerGroupAccessScope;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.LayerGroupRole;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class LayerGroupLayersUseCase implements LayerGroupLayerManagement {

  private static final String PRIVATE_LAYER_IN_GLOBAL_GROUP_MSG =
      "Cannot assign a non-global layer to a global layer group";

  private final LayerGroupAuthorization auth;
  private final LayerGroupLayerService layerGroupLayerService;
  private final EffectiveLayerAccessService effectiveAccess;
  private final LayerGroupService layerGroupService;
  private final LayersRepository layersRepository;
  private final AuditRecorder auditRecorder;
  private final LayerGroupAuditProjection projection;

  public LayerGroupLayersUseCase(
      LayerGroupAuthorization auth,
      LayerGroupLayerService layerGroupLayerService,
      EffectiveLayerAccessService effectiveAccess,
      LayerGroupService layerGroupService,
      LayersRepository layersRepository,
      AuditRecorder auditRecorder,
      LayerGroupAuditProjection projection) {
    this.auth = auth;
    this.layerGroupLayerService = layerGroupLayerService;
    this.effectiveAccess = effectiveAccess;
    this.layerGroupService = layerGroupService;
    this.layersRepository = layersRepository;
    this.auditRecorder = auditRecorder;
    this.projection = projection;
  }

  @Override
  public List<LayerGroupLayerResponse> getLayers(UUID layerGroupId, OpenCopPrincipal principal) {
    LayerGroupPolicy.assertContainerMember(
        auth.getEffectiveContainerRole(layerGroupId, principal), principal.isGlobalAdmin());
    try {
      return layerGroupLayerService.getLayersForLayerGroup(layerGroupId);
    } catch (PersistenceException e) {
      throw new GlobalOpenCopException(e.getMessage(), HttpStatus.NOT_FOUND);
    }
  }

  @Override
  @Transactional
  public List<LayerGroupLayerResponse> assignLayers(
      UUID layerGroupId, LayerGroupLayersAssignRequest request, OpenCopPrincipal principal) {
    LayerGroupPolicy.assertContainerRole(
        auth.getEffectiveContainerRole(layerGroupId, principal),
        principal.isGlobalAdmin(),
        LayerGroupRole.MAINTAINER,
        LayerGroupRole.ADMIN,
        LayerGroupRole.OWNER);
    if (request == null || request.layerIds().isEmpty()) {
      throw new GlobalOpenCopException(
          "At least one layer must be specified", HttpStatus.BAD_REQUEST);
    }
    if (request.layerIds().stream().anyMatch(Objects::isNull)) {
      throw new GlobalOpenCopException("Layer ids must not contain null", HttpStatus.BAD_REQUEST);
    }
    LayerRole requestedRole = request.role() != null ? request.role() : LayerRole.MEMBER;
    // Validate every layer up front so the batch is all-or-nothing: a single failing layer
    // rejects the whole request before anything is written.
    try {
      var group = layerGroupService.getGroup(layerGroupId);
      boolean globalGroup = group.accessScope() == LayerGroupAccessScope.GLOBAL;
      // Both the global-group rule and the non-admin scope gate need the layer's own scope.
      boolean needsLayer = globalGroup || !principal.isGlobalAdmin();
      for (UUID layerId : request.layerIds()) {
        var layer = needsLayer ? layersRepository.get(layerId) : null;
        if (globalGroup && layer.getAccessScope() != LayerAccessScope.GLOBAL) {
          throw new GlobalOpenCopException(PRIVATE_LAYER_IN_GLOBAL_GROUP_MSG, HttpStatus.CONFLICT);
        }
        if (!principal.isGlobalAdmin()) {
          LayerGroupPolicy.assertLayerIsPrivate(layer.getAccessScope(), layerId);
          var effective = effectiveAccess.getEffectiveRole(layerId, principal.internalUserId());
          LayerGroupPolicy.assertCanAssignLayer(effective, layerId);
          LayerGroupPolicy.assertCanAssignRole(effective, requestedRole, layerId);
        }
      }
    } catch (PersistenceException | RepositoryException e) {
      throw new GlobalOpenCopException(e.getMessage(), HttpStatus.BAD_REQUEST);
    }
    try {
      layerGroupService.lockGroup(layerGroupId);
      var before = layerRoles(layerGroupId);
      var assigned =
          layerGroupLayerService.assignLayersToLayerGroup(
              layerGroupId, request.layerIds(), requestedRole);
      recordLayersChange(layerGroupId, principal, before);

      return assigned;
    } catch (PersistenceException e) {
      throw new GlobalOpenCopException(e.getMessage(), HttpStatus.BAD_REQUEST);
    }
  }

  @Override
  @Transactional
  public void removeLayer(UUID layerGroupId, UUID layerId, OpenCopPrincipal principal) {
    LayerGroupPolicy.assertContainerRole(
        auth.getEffectiveContainerRole(layerGroupId, principal),
        principal.isGlobalAdmin(),
        LayerGroupRole.MAINTAINER,
        LayerGroupRole.ADMIN,
        LayerGroupRole.OWNER);
    try {
      layerGroupService.lockGroup(layerGroupId);
      var before = layerRoles(layerGroupId);
      layerGroupLayerService.removeLayerFromLayerGroup(layerGroupId, layerId);
      recordLayersChange(layerGroupId, principal, before);
    } catch (PersistenceException e) {
      throw new GlobalOpenCopException(e.getMessage(), HttpStatus.NOT_FOUND);
    }
  }

  private Map<UUID, LayerRole> layerRoles(UUID layerGroupId) throws PersistenceException {
    var roles = new LinkedHashMap<UUID, LayerRole>();
    layerGroupLayerService
        .getLayersForLayerGroup(layerGroupId)
        .forEach(layer -> roles.put(layer.layerId(), layer.role()));
    return roles;
  }

  private void recordLayersChange(
      UUID layerGroupId, OpenCopPrincipal principal, Map<UUID, LayerRole> before)
      throws PersistenceException {
    var delta = AuditDeltas.diff(before, layerRoles(layerGroupId));
    if (delta.isEmpty()) {
      return;
    }
    auditRecorder.record(
        new AuditEvent(
            EntityType.LAYER_GROUP,
            layerGroupId,
            groupName(layerGroupId),
            Operation.LAYERS_CHANGED,
            Caller.of(principal),
            projection.roleSetChanged(delta),
            AuditEvent.CURRENT_CHANGE_VERSION));
  }

  /**
   * The group's name for a history row, or null when it cannot be read.
   *
   * <p>Decoration, not contract: a layer group has no updated_by of its own so the name is worth
   * fetching, but failing to read it must never fail the change being recorded.
   */
  private String groupName(UUID layerGroupId) {
    try {
      var group = layerGroupService.getGroup(layerGroupId);
      return group == null ? null : group.name();
    } catch (PersistenceException unreadable) {
      return null;
    }
  }
}
