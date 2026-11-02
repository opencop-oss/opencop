// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.layer;

import com.opencop.core.api.general.business.model.Caller;
import com.opencop.core.api.general.business.model.audit.AuditEntry.EntityType;
import com.opencop.core.api.general.business.model.audit.AuditEntry.Operation;
import com.opencop.core.api.general.business.model.audit.AuditEvent;
import com.opencop.core.api.general.ports.gateway.audit.AuditRecorder;
import com.opencop.core.api.general.ports.usecase.layer.CreateLayer;
import com.opencop.core.api.iam.ports.usecase.access.layer.LayerManager;
import com.opencop.core.sdk.business.model.iam.access.Layer;
import com.opencop.core.sdk.business.model.iam.access.LayerMember;
import com.opencop.core.sdk.model.iam.layer.LayerCreateRequest;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerAccessScope;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerRole;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Layer creation: the row, its creator's OWNER grant, and any initial members, as one unit.
 *
 * <p>These were three separate transactions, which could leave a layer with no owner if the second
 * failed.
 */
@Component
public class CreateLayerUsecase implements CreateLayer {

  private static final Logger log = LoggerFactory.getLogger(CreateLayerUsecase.class);

  private final LayerManager<Layer, LayerMember> layerManager;
  private final AuditRecorder auditRecorder;
  private final LayerAuditProjection projection;

  public CreateLayerUsecase(
      LayerManager<Layer, LayerMember> layerManager,
      AuditRecorder auditRecorder,
      LayerAuditProjection projection) {
    this.layerManager = layerManager;
    this.auditRecorder = auditRecorder;
    this.projection = projection;
  }

  @Override
  @Transactional
  public Layer create(
      String name,
      LayerAccessScope accessScope,
      Map<UUID, LayerRole> initialMembers,
      OpenCopPrincipal caller) {
    var resolvedScope = accessScope == null ? LayerAccessScope.PRIVATE : accessScope;
    if (resolvedScope == LayerAccessScope.GLOBAL && !caller.isGlobalAdmin()) {
      throw new GlobalOpenCopException(
          "Only admin users can create global layers", HttpStatus.FORBIDDEN);
    }

    var layer =
        this.layerManager.create(
            new LayerCreateRequest(name, resolvedScope), caller.internalUserId());

    log.info(
        "Created layer '{}' (id={}), adding creator {} as OWNER",
        layer.getName(),
        layer.getId(),
        caller.internalUserId());
    var allMembers = new LinkedHashMap<UUID, LayerRole>();
    allMembers.putAll(
        this.layerManager.addLayerMembers(
            layer.getId(), Map.of(caller.internalUserId(), LayerRole.OWNER)));

    if (initialMembers != null && !initialMembers.isEmpty()) {
      log.info("Adding {} initial users to layer '{}'", initialMembers.size(), layer.getName());
      // Applied roles again: a creator who also appears in the initial list keeps OWNER.
      allMembers.putAll(this.layerManager.addLayerMembers(layer.getId(), initialMembers));
    }

    // One row for the whole creation, including who it started with: three transactions were
    // collapsed into one precisely so this cannot describe a half-built layer.
    this.auditRecorder.record(
        new AuditEvent(
            EntityType.LAYER,
            layer.getId(),
            layer.getName(),
            Operation.CREATE,
            Caller.of(caller),
            this.projection.create(layer, allMembers),
            AuditEvent.CURRENT_CHANGE_VERSION));

    return layer;
  }
}
