// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.layergroup;

import com.opencop.core.api.general.ports.gateway.layergroup.LayerGroupUserGroupService;
import com.opencop.core.api.general.ports.gateway.layergroup.UserGroupPatchGuard;
import com.opencop.core.api.general.technical.model.layergroup.LayerGroupUserGroupsPatchRequest;
import com.opencop.core.sdk.technical.gateway.db.PersistenceException;
import com.opencop.core.sdk.technical.gateway.db.iam.group.GroupEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.group.IamGroupRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.IamLayerGroupMembersRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.IamLayerGroupRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.IamLayerGroupUserGroupRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.LayerGroupEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.LayerGroupMemberEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.LayerGroupRole;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.LayerGroupUserGroupEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.LayerGroupUserGroupIdEntity;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppLayerGroupUserGroupService implements LayerGroupUserGroupService {

  private final IamLayerGroupUserGroupRepository userGroupRepo;
  private final IamLayerGroupRepository layerGroupRepo;
  private final IamGroupRepository groupRepo;
  private final IamLayerGroupMembersRepository membersRepo;

  public AppLayerGroupUserGroupService(
      IamLayerGroupUserGroupRepository userGroupRepo,
      IamLayerGroupRepository layerGroupRepo,
      IamGroupRepository groupRepo,
      IamLayerGroupMembersRepository membersRepo) {
    this.userGroupRepo = userGroupRepo;
    this.layerGroupRepo = layerGroupRepo;
    this.groupRepo = groupRepo;
    this.membersRepo = membersRepo;
  }

  @Override
  @Transactional(readOnly = true)
  public List<LayerGroupUserGroupEntity> listUserGroupsForLayerGroup(UUID layerGroupId) {
    return userGroupRepo.findAllByLayerGroupId(layerGroupId);
  }

  @Override
  @Transactional
  public void applyUserGroupPatch(
      UUID layerGroupId,
      List<LayerGroupUserGroupsPatchRequest.UserGroupAdd> additions,
      List<UUID> removals,
      UserGroupPatchGuard guard)
      throws PersistenceException {

    LayerGroupEntity layerGroup =
        layerGroupRepo
            .findByIdForUpdate(layerGroupId)
            .orElseThrow(
                () -> new PersistenceException("Could not find layer group: " + layerGroupId));

    List<LayerGroupUserGroupEntity> currentAssignments =
        userGroupRepo.findAllByLayerGroupId(layerGroupId);
    Map<UUID, LayerGroupRole> currentUserGroupsMap =
        currentAssignments.stream()
            .collect(
                Collectors.toMap(
                    LayerGroupUserGroupEntity::getUserGroupId, LayerGroupUserGroupEntity::getRole));

    if (guard != null) {
      Map<UUID, LayerGroupRole> currentUsersMap =
          membersRepo.findAllByLayerGroupId(layerGroupId).stream()
              .collect(
                  Collectors.toMap(
                      LayerGroupMemberEntity::getUserId, LayerGroupMemberEntity::getRole));

      Map<UUID, LayerGroupRole> projectedUserGroups = new HashMap<>(currentUserGroupsMap);
      removals.forEach(projectedUserGroups::remove);
      additions.forEach(a -> projectedUserGroups.put(a.userGroupId(), a.role()));
      guard.check(currentUsersMap, currentUserGroupsMap, projectedUserGroups);
    }

    Set<UUID> toDelete = new HashSet<>(removals);
    additions.forEach(a -> toDelete.remove(a.userGroupId()));

    if (!toDelete.isEmpty()) {
      userGroupRepo.deleteAllByIdLayerGroupIdAndIdUserGroupIdIn(layerGroupId, toDelete);
    }

    for (LayerGroupUserGroupsPatchRequest.UserGroupAdd addition : additions) {
      GroupEntity userGroup =
          groupRepo
              .findById(addition.userGroupId())
              .orElseThrow(
                  () ->
                      new PersistenceException(
                          "Could not find user group: " + addition.userGroupId()));

      Optional<LayerGroupUserGroupEntity> existing =
          userGroupRepo.findById(
              new LayerGroupUserGroupIdEntity(layerGroupId, addition.userGroupId()));
      if (existing.isPresent()) {
        existing.get().setRole(addition.role());
        userGroupRepo.save(existing.get());
      } else {
        userGroupRepo.save(new LayerGroupUserGroupEntity(layerGroup, userGroup, addition.role()));
      }
    }
  }
}
