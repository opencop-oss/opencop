// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.layer;

import com.opencop.core.api.general.business.model.Caller;
import com.opencop.core.api.general.business.model.audit.AuditDeltas;
import com.opencop.core.api.general.business.model.audit.AuditEntry.EntityType;
import com.opencop.core.api.general.business.model.audit.AuditEntry.Operation;
import com.opencop.core.api.general.business.model.audit.AuditEvent;
import com.opencop.core.api.general.ports.gateway.audit.AuditRecorder;
import com.opencop.core.api.general.ports.usecase.layer.ManageLayerMembers;
import com.opencop.core.api.general.technical.service.LayerAuthorizationService;
import com.opencop.core.api.iam.ports.usecase.access.layer.LayerManager;
import com.opencop.core.sdk.business.model.iam.access.Layer;
import com.opencop.core.sdk.business.model.iam.access.LayerMember;
import com.opencop.core.sdk.model.iam.layer.UpdateLayerRoleRequest;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerRole;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Membership changes for one layer: authorization and mutation under a single transaction.
 *
 * <p>The owner list is guarded more tightly than the rest of the membership, so each entry point
 * decides which role it demands from the request itself rather than taking it from the caller.
 */
@Component
public class LayerMembersUsecase implements ManageLayerMembers {

  private final LayerManager<Layer, LayerMember> layerManager;
  private final LayerAuthorizationService layerAuth;
  private final AuditRecorder auditRecorder;
  private final LayerAuditProjection projection;

  public LayerMembersUsecase(
      LayerManager<Layer, LayerMember> layerManager,
      LayerAuthorizationService layerAuth,
      AuditRecorder auditRecorder,
      LayerAuditProjection projection) {
    this.layerManager = layerManager;
    this.layerAuth = layerAuth;
    this.auditRecorder = auditRecorder;
    this.projection = projection;
  }

  @Override
  @Transactional
  public void addMembers(UUID layerId, Map<UUID, LayerRole> request, OpenCopPrincipal caller) {
    if (request == null) {
      throw new GlobalOpenCopException(
          "Layer member add request is required", HttpStatus.BAD_REQUEST);
    }

    if (request.containsValue(LayerRole.OWNER)) {
      this.layerAuth.assertLayerRole(
          layerId, caller, "Only layer owners can modify the owner list", LayerRole.OWNER);
    } else {
      this.layerAuth.assertLayerRole(layerId, caller, LayerRole.OWNER, LayerRole.ADMIN);
    }

    this.layerManager.lock(layerId);
    var before = currentRoles(layerId);
    this.layerManager.addLayerMembers(layerId, request);
    recordApplied(layerId, caller, before);
  }

  @Override
  @Transactional
  public void updateMemberRole(
      UUID layerId, UpdateLayerRoleRequest request, OpenCopPrincipal caller) {
    // Locked before the roles are read, because that read decides which role the caller must hold:
    // an unlocked one lets a concurrent promotion change the answer after it is given.
    this.layerManager.lock(layerId);
    var before = currentRoles(layerId);
    if (changesOwners(before, request)) {
      this.layerAuth.assertLayerRole(
          layerId, caller, "Only layer owners can modify the owner list", LayerRole.OWNER);
    } else {
      this.layerAuth.assertLayerRole(layerId, caller, LayerRole.OWNER, LayerRole.ADMIN);
    }

    this.layerManager.updateLayerMemberRole(layerId, request);
    recordApplied(layerId, caller, before);
  }

  @Override
  @Transactional
  public void removeMembers(UUID layerId, Set<UUID> userIds, OpenCopPrincipal caller) {
    this.layerAuth.assertLayerRole(layerId, caller, LayerRole.OWNER, LayerRole.ADMIN);
    this.layerManager.lock(layerId);
    var before = currentRoles(layerId);
    this.layerManager.removeLayerMembers(layerId, userIds);
    recordApplied(layerId, caller, before);
  }

  /**
   * Runs a membership write under the layer's lock and records what the membership actually became.
   *
   * <p>Reading the roles on both sides of the write is what makes the row true: the writer upserts,
   * resolves its own bucket precedence and keeps the higher of two roles, so neither the request
   * nor a partial view of it says what happened. A write that moved nothing leaves no row.
   *
   * <p>One row per request, whatever the size of the lists in it: sixty members added in one action
   * is one thing the administrator did, not sixty.
   */
  private void recordApplied(UUID layerId, OpenCopPrincipal caller, Map<UUID, LayerRole> before) {
    var delta = AuditDeltas.diff(before, currentRoles(layerId));
    if (delta.isEmpty()) {
      return;
    }

    // Read after the write so a rename in the same transaction is the name the row carries.
    var layer = this.layerManager.get(layerId);
    this.auditRecorder.record(
        new AuditEvent(
            EntityType.LAYER,
            layerId,
            layer == null ? null : layer.getName(),
            Operation.MEMBERS_CHANGED,
            Caller.of(caller),
            this.projection.roleSetChanged(delta),
            AuditEvent.CURRENT_CHANGE_VERSION));
  }

  /**
   * The roles as they stand before a write, so the row records what moved and not what was asked.
   */
  private Map<UUID, LayerRole> currentRoles(UUID layerId) {
    return this.layerManager.listLayerMembers(layerId).stream()
        .collect(Collectors.toMap(LayerMember::getUserId, LayerMember::getRole, (a, b) -> a));
  }

  /**
   * Whether the request promotes anyone to OWNER, or moves a current OWNER down to a lesser role.
   */
  private static boolean changesOwners(
      Map<UUID, LayerRole> before, UpdateLayerRoleRequest request) {
    if (request.owners() != null && !request.owners().isEmpty()) {
      return true;
    }

    var currentOwners =
        before.entrySet().stream()
            .filter(entry -> entry.getValue() == LayerRole.OWNER)
            .map(Map.Entry::getKey)
            .collect(Collectors.toSet());

    return demotes(request.members(), currentOwners)
        || demotes(request.editors(), currentOwners)
        || demotes(request.admins(), currentOwners);
  }

  private static boolean demotes(Set<UUID> target, Set<UUID> currentOwners) {
    return target != null && target.stream().anyMatch(currentOwners::contains);
  }
}
