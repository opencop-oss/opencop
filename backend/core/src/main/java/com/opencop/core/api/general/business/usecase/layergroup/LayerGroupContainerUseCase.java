// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.layergroup;

import com.opencop.core.api.general.business.model.Caller;
import com.opencop.core.api.general.business.model.audit.AuditEntry.EntityType;
import com.opencop.core.api.general.business.model.audit.AuditEntry.Operation;
import com.opencop.core.api.general.business.model.audit.AuditEvent;
import com.opencop.core.api.general.business.policy.LayerGroupPolicy;
import com.opencop.core.api.general.ports.gateway.audit.AuditRecorder;
import com.opencop.core.api.general.ports.gateway.layergroup.LayerGroupAuthorization;
import com.opencop.core.api.general.ports.gateway.layergroup.LayerGroupLayerService;
import com.opencop.core.api.general.ports.gateway.layergroup.LayerGroupService;
import com.opencop.core.api.general.ports.gateway.layergroup.LayerGroupUserGroupService;
import com.opencop.core.api.general.ports.usecase.layergroup.LayerGroupContainerManagement;
import com.opencop.core.api.general.technical.model.layergroup.LayerGroupCreateRequest;
import com.opencop.core.api.general.technical.model.layergroup.LayerGroupMembersPatchRequest;
import com.opencop.core.api.general.technical.model.layergroup.LayerGroupPatchRequest;
import com.opencop.core.api.general.technical.model.layergroup.LayerGroupResponse;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.technical.gateway.db.PersistenceException;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.LayerGroupAccessScope;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.LayerGroupRole;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class LayerGroupContainerUseCase implements LayerGroupContainerManagement {

  private static final String ADMIN_ONLY_SCOPE_MSG =
      "Only admin users can change layer group access scope";
  private static final String PRIVATE_LAYERS_CONFLICT_MSG =
      "Cannot set layer group access scope to GLOBAL while non-global layers are assigned";

  private final LayerGroupAuthorization auth;
  private final LayerGroupService layerGroupService;
  private final LayerGroupLayerService layerGroupLayerService;
  private final LayerGroupUserGroupService layerGroupUserGroupService;
  private final AuditRecorder auditRecorder;
  private final LayerGroupAuditProjection projection;

  public LayerGroupContainerUseCase(
      LayerGroupAuthorization auth,
      LayerGroupService layerGroupService,
      LayerGroupLayerService layerGroupLayerService,
      LayerGroupUserGroupService layerGroupUserGroupService,
      AuditRecorder auditRecorder,
      LayerGroupAuditProjection projection) {
    this.auth = auth;
    this.layerGroupService = layerGroupService;
    this.layerGroupLayerService = layerGroupLayerService;
    this.layerGroupUserGroupService = layerGroupUserGroupService;
    this.auditRecorder = auditRecorder;
    this.projection = projection;
  }

  @Override
  @Transactional
  public LayerGroupResponse create(LayerGroupCreateRequest request, OpenCopPrincipal principal) {
    if (request.accessScope() == LayerGroupAccessScope.GLOBAL && !principal.isGlobalAdmin()) {
      throw new GlobalOpenCopException(ADMIN_ONLY_SCOPE_MSG, HttpStatus.FORBIDDEN);
    }
    try {
      LayerGroupResponse created = layerGroupService.createGroup(request);
      layerGroupService.patchMembers(
          created.id(),
          new LayerGroupMembersPatchRequest(
              Set.of(
                  new LayerGroupMembersPatchRequest.MemberAdd(
                      principal.internalUserId(), LayerGroupRole.OWNER)),
              Set.of()),
          null);

      // One row for the whole creation: the group and its owner are written in one transaction,
      // and a row describing only half of that would be misleading.
      auditRecorder.record(
          new AuditEvent(
              EntityType.LAYER_GROUP,
              created.id(),
              created.name(),
              Operation.CREATE,
              Caller.of(principal),
              projection.create(created, principal.internalUserId()),
              AuditEvent.CURRENT_CHANGE_VERSION));

      return created;
    } catch (PersistenceException e) {
      throw new GlobalOpenCopException(e.getMessage(), HttpStatus.BAD_REQUEST);
    }
  }

  @Override
  public LayerGroupResponse get(UUID layerGroupId, OpenCopPrincipal principal) {
    LayerGroupPolicy.assertContainerMember(
        auth.getEffectiveContainerRole(layerGroupId, principal), principal.isGlobalAdmin());
    try {
      return layerGroupService.getGroup(layerGroupId);
    } catch (PersistenceException e) {
      throw new GlobalOpenCopException(e.getMessage(), HttpStatus.NOT_FOUND);
    }
  }

  @Override
  @Transactional
  public LayerGroupResponse patch(
      UUID layerGroupId, LayerGroupPatchRequest request, OpenCopPrincipal principal) {
    LayerGroupPolicy.assertContainerRole(
        auth.getEffectiveContainerRole(layerGroupId, principal),
        principal.isGlobalAdmin(),
        LayerGroupRole.ADMIN,
        LayerGroupRole.OWNER);
    try {
      layerGroupService.lockGroup(layerGroupId);
    } catch (PersistenceException e) {
      throw new GlobalOpenCopException(e.getMessage(), HttpStatus.NOT_FOUND);
    }
    if (request != null && request.accessScope() != null) {
      try {
        LayerGroupResponse current = layerGroupService.getGroup(layerGroupId);
        boolean scopeChanges = request.accessScope() != current.accessScope();
        if (scopeChanges && !principal.isGlobalAdmin()) {
          throw new GlobalOpenCopException(ADMIN_ONLY_SCOPE_MSG, HttpStatus.FORBIDDEN);
        }
        if (current.accessScope() == LayerGroupAccessScope.PRIVATE
            && request.accessScope() == LayerGroupAccessScope.GLOBAL) {
          long nonGlobal = layerGroupLayerService.countNonGlobalLayers(layerGroupId);
          if (nonGlobal > 0) {
            throw new GlobalOpenCopException(PRIVATE_LAYERS_CONFLICT_MSG, HttpStatus.CONFLICT);
          }
        }
      } catch (PersistenceException e) {
        throw new GlobalOpenCopException(e.getMessage(), HttpStatus.BAD_REQUEST);
      }
    }
    try {
      var before = layerGroupService.getGroup(layerGroupId);
      var patched = layerGroupService.patchGroup(layerGroupId, request);
      // Diffed against what was stored, not what was asked for: the writer trims the name, so a
      // re-saved " Foo " would otherwise record a rename that never happened.
      var change = projection.patch(before, patched);

      change.ifPresent(
          moved ->
              auditRecorder.record(
                  new AuditEvent(
                      EntityType.LAYER_GROUP,
                      layerGroupId,
                      patched.name(),
                      Operation.PATCH,
                      Caller.of(principal),
                      moved,
                      AuditEvent.CURRENT_CHANGE_VERSION)));

      return patched;
    } catch (PersistenceException e) {
      throw new GlobalOpenCopException(e.getMessage(), HttpStatus.BAD_REQUEST);
    }
  }

  @Override
  @Transactional
  public void delete(UUID layerGroupId, OpenCopPrincipal principal) {
    LayerGroupPolicy.assertContainerRole(
        auth.getEffectiveContainerRole(layerGroupId, principal),
        principal.isGlobalAdmin(),
        LayerGroupRole.OWNER);
    try {
      layerGroupService.lockGroup(layerGroupId);
      // Read what the cascade is about to take, while it still exists. After the delete there is
      // nothing left to count, and the drawer that would show this history is gone too — the rows
      // survive only in the ledger and are reachable through the API.
      var before = layerGroupService.getGroup(layerGroupId);
      var members = layerGroupService.getMembers(layerGroupId).size();
      var layers = layerGroupLayerService.getLayersForLayerGroup(layerGroupId).size();
      var userGroups = layerGroupUserGroupService.listUserGroupsForLayerGroup(layerGroupId).size();

      layerGroupService.deleteGroup(layerGroupId);

      auditRecorder.record(
          new AuditEvent(
              EntityType.LAYER_GROUP,
              layerGroupId,
              before == null ? null : before.name(),
              Operation.DELETE,
              Caller.of(principal),
              projection.delete(before, members, layers, userGroups),
              AuditEvent.CURRENT_CHANGE_VERSION));
    } catch (PersistenceException e) {
      throw new GlobalOpenCopException(e.getMessage(), HttpStatus.NOT_FOUND);
    }
  }
}
