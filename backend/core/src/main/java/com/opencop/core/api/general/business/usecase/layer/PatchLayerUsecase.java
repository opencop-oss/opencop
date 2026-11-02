// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.layer;

import com.opencop.core.api.general.business.model.Caller;
import com.opencop.core.api.general.business.model.audit.AuditEntry.EntityType;
import com.opencop.core.api.general.business.model.audit.AuditEntry.Operation;
import com.opencop.core.api.general.business.model.audit.AuditEvent;
import com.opencop.core.api.general.business.policy.ResourceLifecyclePolicy;
import com.opencop.core.api.general.ports.gateway.audit.AuditRecorder;
import com.opencop.core.api.general.technical.service.LayerAuthorizationService;
import com.opencop.core.api.iam.ports.usecase.access.layer.LayerManager;
import com.opencop.core.sdk.business.model.iam.access.Layer;
import com.opencop.core.sdk.business.model.iam.access.LayerMember;
import com.opencop.core.sdk.model.iam.layer.LayerPatchRequest;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerAccessScope;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerRole;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.IamLayerGroupLayerRepository;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PatchLayerUsecase {

  private final LayerManager<Layer, LayerMember> layerManager;
  private final LayerAuthorizationService layerAuth;
  private final IamLayerGroupLayerRepository layerGroupLayerRepository;
  private final AuditRecorder auditRecorder;
  private final LayerAuditProjection projection;

  public PatchLayerUsecase(
      LayerManager<Layer, LayerMember> layerManager,
      LayerAuthorizationService layerAuth,
      IamLayerGroupLayerRepository layerGroupLayerRepository,
      AuditRecorder auditRecorder,
      LayerAuditProjection projection) {
    this.layerManager = layerManager;
    this.layerAuth = layerAuth;
    this.layerGroupLayerRepository = layerGroupLayerRepository;
    this.auditRecorder = auditRecorder;
    this.projection = projection;
  }

  @Transactional
  public void patchLayer(UUID layerId, LayerPatchRequest request, OpenCopPrincipal authentication) {
    // Locked before the state is read, so the delta recorded below is against what commits and a
    // concurrent archive cannot be overwritten by this patch.
    this.layerManager.lock(layerId);
    var layer = this.layerManager.get(layerId);
    ResourceLifecyclePolicy.assertLayerActive(layer, "patch layer");
    var changesAccessScope =
        request.accessScope() != null && request.accessScope() != layer.getAccessScope();
    var downgradesGlobalLayerToPrivate =
        layer.getAccessScope() == LayerAccessScope.GLOBAL
            && request.accessScope() == LayerAccessScope.PRIVATE;
    if (changesAccessScope && !authentication.isGlobalAdmin()) {
      throw new GlobalOpenCopException(
          "Only admin users can change layer access scope", HttpStatus.FORBIDDEN);
    }
    layerAuth.assertLayerRole(layerId, authentication, LayerRole.OWNER);
    if (downgradesGlobalLayerToPrivate
        && layerGroupLayerRepository.countGlobalLayerGroupsByLayerId(layerId) > 0) {
      throw new GlobalOpenCopException(
          "Cannot set layer access scope to PRIVATE while assigned to a GLOBAL layer group",
          HttpStatus.CONFLICT);
    }
    var change = this.projection.patch(layer, request);
    if (change.isEmpty()) {
      // A patch that moves nothing still passes its guards, but there is nothing to record.
      return;
    }

    this.layerManager.update(layerId, request, authentication.internalUserId());

    this.auditRecorder.record(
        new AuditEvent(
            EntityType.LAYER,
            layerId,
            request.name() == null ? layer.getName() : request.name(),
            Operation.PATCH,
            Caller.of(authentication),
            change.get(),
            AuditEvent.CURRENT_CHANGE_VERSION));
  }
}
