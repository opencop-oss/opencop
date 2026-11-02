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
import com.opencop.core.api.general.ports.gateway.layergroup.MemberPatchGuard;
import com.opencop.core.api.general.ports.usecase.layergroup.LayerGroupMemberManagement;
import com.opencop.core.api.general.technical.model.layergroup.LayerGroupMemberResponse;
import com.opencop.core.api.general.technical.model.layergroup.LayerGroupMembersPatchRequest;
import com.opencop.core.api.general.technical.model.layergroup.MyLayerGroupResponse;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.technical.gateway.db.PersistenceException;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.LayerGroupRole;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class LayerGroupMembersUseCase implements LayerGroupMemberManagement {

  private final LayerGroupAuthorization auth;
  private final LayerGroupService layerGroupService;
  private final AuditRecorder auditRecorder;
  private final LayerGroupAuditProjection projection;

  public LayerGroupMembersUseCase(
      LayerGroupAuthorization auth,
      LayerGroupService layerGroupService,
      AuditRecorder auditRecorder,
      LayerGroupAuditProjection projection) {
    this.auth = auth;
    this.layerGroupService = layerGroupService;
    this.auditRecorder = auditRecorder;
    this.projection = projection;
  }

  @Override
  public List<LayerGroupMemberResponse> getMembers(UUID layerGroupId, OpenCopPrincipal principal) {
    LayerGroupPolicy.assertContainerMember(
        auth.getEffectiveContainerRole(layerGroupId, principal), principal.isGlobalAdmin());
    try {
      return layerGroupService.getMembers(layerGroupId);
    } catch (PersistenceException e) {
      throw new GlobalOpenCopException(e.getMessage(), HttpStatus.NOT_FOUND);
    }
  }

  @Override
  @Transactional
  public void patchMembers(
      UUID layerGroupId, LayerGroupMembersPatchRequest request, OpenCopPrincipal principal) {
    LayerGroupPolicy.assertContainerRole(
        auth.getEffectiveContainerRole(layerGroupId, principal),
        principal.isGlobalAdmin(),
        LayerGroupRole.ADMIN,
        LayerGroupRole.OWNER);
    MemberPatchGuard guard = buildGuard(layerGroupId, principal, principal.isGlobalAdmin());
    try {
      layerGroupService.lockGroup(layerGroupId);
      var before = memberRoles(layerGroupId);
      layerGroupService.patchMembers(layerGroupId, request, guard);
      var delta = AuditDeltas.diff(before, memberRoles(layerGroupId));
      if (delta.isEmpty()) {
        return;
      }

      auditRecorder.record(
          new AuditEvent(
              EntityType.LAYER_GROUP,
              layerGroupId,
              groupName(layerGroupId),
              Operation.MEMBERS_CHANGED,
              Caller.of(principal),
              projection.roleSetChanged(delta),
              AuditEvent.CURRENT_CHANGE_VERSION));
    } catch (PersistenceException e) {
      throw new GlobalOpenCopException(e.getMessage(), HttpStatus.BAD_REQUEST);
    }
  }

  private Map<UUID, LayerGroupRole> memberRoles(UUID layerGroupId) throws PersistenceException {
    var roles = new LinkedHashMap<UUID, LayerGroupRole>();
    layerGroupService
        .getMembers(layerGroupId)
        .forEach(member -> roles.put(member.userId(), member.role()));
    return roles;
  }

  private MemberPatchGuard buildGuard(
      UUID layerGroupId, OpenCopPrincipal principal, boolean isGlobalAdmin) {
    return (currentUsers, projectedUsers, currentUserGroups) -> {
      if (!isGlobalAdmin
          && LayerGroupPolicy.memberPatchAffectsOwnerList(
              currentUsers, projectedUsers, currentUserGroups)) {
        LayerGroupPolicy.assertContainerRole(
            auth.getEffectiveContainerRole(layerGroupId, principal),
            principal.isGlobalAdmin(),
            LayerGroupRole.OWNER);
      }
      LayerGroupPolicy.assertRetainsOwner(projectedUsers, currentUserGroups);
    };
  }

  @Override
  public List<MyLayerGroupResponse> listMyLayerGroups(OpenCopPrincipal principal) {
    return layerGroupService.listMyLayerGroups(principal.internalUserId());
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
