// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.service;

import com.opencop.core.api.general.ports.gateway.layergroup.LayerGroupAuthorization;
import com.opencop.core.sdk.technical.gateway.db.iam.group.IamGroupMembersRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.IamLayerGroupMembersRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.IamLayerGroupRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.IamLayerGroupUserGroupRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.LayerGroupAccessScope;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.LayerGroupMemberIdEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.LayerGroupRole;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Shared layer group authorization logic. Checks whether a user is a global admin or has one of the
 * required roles on a given layer group.
 */
@Service
public class LayerGroupAuthorizationService implements LayerGroupAuthorization {

  private final IamLayerGroupMembersRepository membersRepository;
  private final IamGroupMembersRepository groupMembersRepository;
  private final IamLayerGroupUserGroupRepository layerGroupUserGroupRepository;
  private final IamLayerGroupRepository layerGroupRepository;

  public LayerGroupAuthorizationService(
      IamLayerGroupMembersRepository membersRepository,
      IamGroupMembersRepository groupMembersRepository,
      IamLayerGroupUserGroupRepository layerGroupUserGroupRepository,
      IamLayerGroupRepository layerGroupRepository) {
    this.membersRepository = membersRepository;
    this.groupMembersRepository = groupMembersRepository;
    this.layerGroupUserGroupRepository = layerGroupUserGroupRepository;
    this.layerGroupRepository = layerGroupRepository;
  }

  @Override
  public Optional<LayerGroupRole> getEffectiveContainerRole(
      UUID layerGroupId, OpenCopPrincipal principal) {
    var memberId = new LayerGroupMemberIdEntity(layerGroupId, principal.internalUserId());
    var directMember = membersRepository.findById(memberId);
    LayerGroupRole highest = directMember.map(m -> m.getRole()).orElse(null);

    Set<UUID> userGroupIds =
        groupMembersRepository.findGroupIdsByUserId(principal.internalUserId());
    if (!userGroupIds.isEmpty()) {
      var assignments =
          layerGroupUserGroupRepository.findAllByIdLayerGroupIdAndIdUserGroupIdIn(
              layerGroupId, userGroupIds);
      for (var assignment : assignments) {
        LayerGroupRole role = assignment.getRole();
        if (highest == null || role.isHigherThan(highest)) {
          highest = role;
        }
      }
    }

    if (highest == null) {
      var group = layerGroupRepository.findById(layerGroupId).orElse(null);
      if (group != null && group.getAccessScope() == LayerGroupAccessScope.GLOBAL) {
        highest = LayerGroupRole.MEMBER;
      }
    }

    return Optional.ofNullable(highest);
  }

  /**
   * Every layer group this principal may view: direct membership, membership inherited through a
   * user group, plus all GLOBAL groups (which grant {@code MEMBER} to everyone).
   *
   * <p>The batched counterpart to {@link #getEffectiveContainerRole}, for callers that must filter
   * a list of groups rather than authorize one. Runs a constant three queries no matter how many
   * groups are being checked — looping {@code getEffectiveContainerRole} would be an N+1.
   *
   * <p>Returns view-level visibility only. Anything that mutates a group must still enforce the
   * specific container role required by that operation.
   */
  public Set<UUID> viewableLayerGroupIds(OpenCopPrincipal principal) {
    var viewable =
        new HashSet<>(membersRepository.findLayerGroupIdsByUserId(principal.internalUserId()));

    Set<UUID> userGroupIds =
        groupMembersRepository.findGroupIdsByUserId(principal.internalUserId());
    if (!userGroupIds.isEmpty()) {
      viewable.addAll(layerGroupUserGroupRepository.findLayerGroupIdsByUserGroupIdIn(userGroupIds));
    }

    layerGroupRepository.findAllByAccessScope(LayerGroupAccessScope.GLOBAL).stream()
        .map(group -> group.getId())
        .forEach(viewable::add);

    return viewable;
  }
}
