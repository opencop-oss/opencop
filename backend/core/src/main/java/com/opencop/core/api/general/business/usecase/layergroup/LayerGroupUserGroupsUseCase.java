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
import com.opencop.core.api.general.ports.gateway.layergroup.LayerGroupService;
import com.opencop.core.api.general.ports.gateway.layergroup.LayerGroupUserGroupService;
import com.opencop.core.api.general.ports.gateway.layergroup.UserGroupPatchGuard;
import com.opencop.core.api.general.ports.usecase.layergroup.LayerGroupUserGroupManagement;
import com.opencop.core.api.general.technical.model.layergroup.LayerGroupUserGroupResponse;
import com.opencop.core.api.general.technical.model.layergroup.LayerGroupUserGroupsPatchRequest;
import com.opencop.core.sdk.model.iam.group.GroupManagementSource;
import com.opencop.core.sdk.ports.audit.AuthzAccessDeniedException;
import com.opencop.core.sdk.ports.audit.AuthzReasonCode;
import com.opencop.core.sdk.technical.gateway.db.PersistenceException;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.LayerGroupRole;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.LayerGroupUserGroupEntity;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class LayerGroupUserGroupsUseCase implements LayerGroupUserGroupManagement {

  private final LayerGroupAuthorization auth;
  private final LayerGroupUserGroupService service;
  private final LayerGroupService layerGroupService;
  private final AuditRecorder auditRecorder;
  private final LayerGroupAuditProjection projection;

  public LayerGroupUserGroupsUseCase(
      LayerGroupAuthorization auth,
      LayerGroupUserGroupService service,
      LayerGroupService layerGroupService,
      AuditRecorder auditRecorder,
      LayerGroupAuditProjection projection) {
    this.auth = auth;
    this.service = service;
    this.layerGroupService = layerGroupService;
    this.auditRecorder = auditRecorder;
    this.projection = projection;
  }

  @Override
  public List<LayerGroupUserGroupResponse> getUserGroups(
      UUID layerGroupId, OpenCopPrincipal principal) {
    LayerGroupPolicy.assertContainerMember(
        auth.getEffectiveContainerRole(layerGroupId, principal), principal.isGlobalAdmin());
    List<LayerGroupUserGroupEntity> assignments = service.listUserGroupsForLayerGroup(layerGroupId);
    return assignments.stream()
        .map(
            e ->
                new LayerGroupUserGroupResponse(
                    e.getUserGroupId(),
                    e.getUserGroup().getName(),
                    e.getRole(),
                    e.getAssignedAt(),
                    GroupManagementSource.ofIdpIssuer(e.getUserGroup().getIdpIssuer())))
        .toList();
  }

  @Override
  // rollbackFor: PersistenceException is checked, and this method lets it escape. Without this
  // Spring would commit the partial patch the service had already applied.
  @Transactional(rollbackFor = PersistenceException.class)
  public void patchUserGroups(
      UUID layerGroupId, LayerGroupUserGroupsPatchRequest request, OpenCopPrincipal principal)
      throws PersistenceException {
    for (LayerGroupUserGroupsPatchRequest.UserGroupAdd addition : request.add()) {
      if (addition.userGroupId() == null) {
        throw new PersistenceException("User group id is required");
      }
      if (addition.role() == null) {
        throw new PersistenceException("User group role is required");
      }
    }

    LayerGroupPolicy.assertContainerRole(
        auth.getEffectiveContainerRole(layerGroupId, principal),
        principal.isGlobalAdmin(),
        LayerGroupRole.ADMIN,
        LayerGroupRole.OWNER);

    UserGroupPatchGuard guard = buildPatchGuard(layerGroupId, principal, principal.isGlobalAdmin());

    if (!principal.isGlobalAdmin()) {
      Optional<LayerGroupRole> effectiveRole =
          auth.getEffectiveContainerRole(layerGroupId, principal);
      for (LayerGroupUserGroupsPatchRequest.UserGroupAdd addition : request.add()) {
        if (effectiveRole.isEmpty() || addition.role().isHigherThan(effectiveRole.get())) {
          throw new AuthzAccessDeniedException(
              effectiveRole.isEmpty()
                  ? AuthzReasonCode.MISSING_LAYER_GROUP_ROLE
                  : AuthzReasonCode.INSUFFICIENT_LAYER_GROUP_ROLE,
              "Cannot assign role "
                  + addition.role()
                  + ": actor's effective role is "
                  + effectiveRole.map(LayerGroupRole::name).orElse("NONE"));
        }
      }
    }

    layerGroupService.lockGroup(layerGroupId);
    var before = userGroupRoles(layerGroupId);
    service.applyUserGroupPatch(layerGroupId, request.add(), request.remove(), guard);
    var delta = AuditDeltas.diff(before, userGroupRoles(layerGroupId));
    if (delta.isEmpty()) {
      return;
    }

    auditRecorder.record(
        new AuditEvent(
            EntityType.LAYER_GROUP,
            layerGroupId,
            groupName(layerGroupId),
            Operation.USER_GROUPS_CHANGED,
            Caller.of(principal),
            projection.roleSetChanged(delta),
            AuditEvent.CURRENT_CHANGE_VERSION));
  }

  private Map<UUID, LayerGroupRole> userGroupRoles(UUID layerGroupId) {
    var roles = new LinkedHashMap<UUID, LayerGroupRole>();
    service
        .listUserGroupsForLayerGroup(layerGroupId)
        .forEach(assignment -> roles.put(assignment.getUserGroupId(), assignment.getRole()));
    return roles;
  }

  private UserGroupPatchGuard buildPatchGuard(
      UUID layerGroupId, OpenCopPrincipal principal, boolean isGlobalAdmin) {
    return (currentUsers, currentUserGroups, projectedUserGroups) -> {
      if (!isGlobalAdmin
          && LayerGroupPolicy.userGroupPatchAffectsOwnerList(
              currentUsers, currentUserGroups, projectedUserGroups)) {
        LayerGroupPolicy.assertContainerRole(
            auth.getEffectiveContainerRole(layerGroupId, principal),
            principal.isGlobalAdmin(),
            LayerGroupRole.OWNER);
      }
      LayerGroupPolicy.assertRetainsOwner(currentUsers, projectedUserGroups);
    };
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
