// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.datasourcegroup;

import com.opencop.core.api.general.ports.gateway.datasourcegroup.DataSourceGroupUserGroupPatchGuard;
import com.opencop.core.api.general.ports.gateway.datasourcegroup.DataSourceGroupUserGroupService;
import com.opencop.core.api.general.technical.model.datasourcegroup.DataSourceGroupUserGroupsPatchRequest;
import com.opencop.core.sdk.technical.gateway.db.NotFoundException;
import com.opencop.core.sdk.technical.gateway.db.PersistenceException;
import com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup.DataSourceGroupEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup.DataSourceGroupMemberEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup.DataSourceGroupRole;
import com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup.DataSourceGroupUserGroupEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup.DataSourceGroupUserGroupIdEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup.IamDataSourceGroupMembersRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup.IamDataSourceGroupRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup.IamDataSourceGroupUserGroupRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.group.GroupEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.group.IamGroupRepository;
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
public class AppDataSourceGroupUserGroupService implements DataSourceGroupUserGroupService {

  private final IamDataSourceGroupUserGroupRepository userGroupRepo;
  private final IamDataSourceGroupRepository dataSourceGroupRepo;
  private final IamGroupRepository groupRepo;
  private final IamDataSourceGroupMembersRepository membersRepo;

  public AppDataSourceGroupUserGroupService(
      IamDataSourceGroupUserGroupRepository userGroupRepo,
      IamDataSourceGroupRepository dataSourceGroupRepo,
      IamGroupRepository groupRepo,
      IamDataSourceGroupMembersRepository membersRepo) {
    this.userGroupRepo = userGroupRepo;
    this.dataSourceGroupRepo = dataSourceGroupRepo;
    this.groupRepo = groupRepo;
    this.membersRepo = membersRepo;
  }

  @Override
  @Transactional(readOnly = true)
  public List<DataSourceGroupUserGroupEntity> listUserGroupsForDataSourceGroup(
      UUID dataSourceGroupId) throws PersistenceException {
    if (!dataSourceGroupRepo.existsById(dataSourceGroupId)) {
      throw dataSourceGroupNotFound(dataSourceGroupId);
    }
    return userGroupRepo.findAllByDataSourceGroupId(dataSourceGroupId);
  }

  @Override
  @Transactional
  public void applyUserGroupPatch(
      UUID dataSourceGroupId,
      List<DataSourceGroupUserGroupsPatchRequest.UserGroupAdd> additions,
      List<UUID> removals,
      DataSourceGroupUserGroupPatchGuard guard)
      throws PersistenceException {
    DataSourceGroupEntity dataSourceGroup =
        dataSourceGroupRepo
            .findByIdForUpdate(dataSourceGroupId)
            .orElseThrow(() -> dataSourceGroupNotFound(dataSourceGroupId));

    List<DataSourceGroupUserGroupEntity> currentAssignments =
        userGroupRepo.findAllByDataSourceGroupId(dataSourceGroupId);
    Map<UUID, DataSourceGroupRole> currentUserGroups = toUserGroupRoleMap(currentAssignments);

    runGuard(dataSourceGroupId, additions, removals, guard, currentUserGroups);
    Map<UUID, GroupEntity> userGroupsById = resolveAddedUserGroups(additions);
    deleteRemovedUserGroups(dataSourceGroupId, additions, removals);
    upsertAddedUserGroups(dataSourceGroup, additions, userGroupsById);
  }

  private Map<UUID, DataSourceGroupRole> toUserGroupRoleMap(
      List<DataSourceGroupUserGroupEntity> assignments) {
    return assignments.stream()
        .collect(
            Collectors.toMap(
                DataSourceGroupUserGroupEntity::getUserGroupId,
                DataSourceGroupUserGroupEntity::getRole));
  }

  private void runGuard(
      UUID dataSourceGroupId,
      List<DataSourceGroupUserGroupsPatchRequest.UserGroupAdd> additions,
      List<UUID> removals,
      DataSourceGroupUserGroupPatchGuard guard,
      Map<UUID, DataSourceGroupRole> currentUserGroups)
      throws PersistenceException {
    if (guard == null) {
      return;
    }

    Map<UUID, DataSourceGroupRole> currentUsers =
        membersRepo.findAllByDataSourceGroupId(dataSourceGroupId).stream()
            .collect(
                Collectors.toMap(
                    DataSourceGroupMemberEntity::getUserId, DataSourceGroupMemberEntity::getRole));
    Map<UUID, DataSourceGroupRole> projectedUserGroups =
        projectedUserGroups(currentUserGroups, additions, removals);
    guard.check(currentUsers, currentUserGroups, projectedUserGroups);
  }

  private Map<UUID, DataSourceGroupRole> projectedUserGroups(
      Map<UUID, DataSourceGroupRole> currentUserGroups,
      List<DataSourceGroupUserGroupsPatchRequest.UserGroupAdd> additions,
      List<UUID> removals) {
    Map<UUID, DataSourceGroupRole> projectedUserGroups = new HashMap<>(currentUserGroups);
    removals.forEach(projectedUserGroups::remove);
    additions.forEach(a -> projectedUserGroups.put(a.userGroupId(), a.role()));
    return projectedUserGroups;
  }

  private Map<UUID, GroupEntity> resolveAddedUserGroups(
      List<DataSourceGroupUserGroupsPatchRequest.UserGroupAdd> additions)
      throws PersistenceException {
    Map<UUID, GroupEntity> userGroupsById = new HashMap<>();
    Set<UUID> addUserGroupIds =
        additions.stream()
            .map(DataSourceGroupUserGroupsPatchRequest.UserGroupAdd::userGroupId)
            .collect(Collectors.toSet());
    for (UUID userGroupId : addUserGroupIds) {
      GroupEntity userGroup =
          groupRepo
              .findById(userGroupId)
              .orElseThrow(
                  () -> new PersistenceException("Could not find user group: " + userGroupId));
      userGroupsById.put(userGroupId, userGroup);
    }
    return userGroupsById;
  }

  private void deleteRemovedUserGroups(
      UUID dataSourceGroupId,
      List<DataSourceGroupUserGroupsPatchRequest.UserGroupAdd> additions,
      List<UUID> removals) {
    Set<UUID> toDelete = new HashSet<>(removals);
    additions.forEach(a -> toDelete.remove(a.userGroupId()));
    if (!toDelete.isEmpty()) {
      userGroupRepo.deleteAllByIdDataSourceGroupIdAndIdUserGroupIdIn(dataSourceGroupId, toDelete);
    }
  }

  private void upsertAddedUserGroups(
      DataSourceGroupEntity dataSourceGroup,
      List<DataSourceGroupUserGroupsPatchRequest.UserGroupAdd> additions,
      Map<UUID, GroupEntity> userGroupsById) {
    for (DataSourceGroupUserGroupsPatchRequest.UserGroupAdd addition : additions) {
      Optional<DataSourceGroupUserGroupEntity> existing =
          userGroupRepo.findById(
              new DataSourceGroupUserGroupIdEntity(
                  dataSourceGroup.getId(), addition.userGroupId()));
      if (existing.isPresent()) {
        existing.get().setRole(addition.role());
        userGroupRepo.save(existing.get());
      } else {
        userGroupRepo.save(
            new DataSourceGroupUserGroupEntity(
                dataSourceGroup, userGroupsById.get(addition.userGroupId()), addition.role()));
      }
    }
  }

  private static NotFoundException dataSourceGroupNotFound(UUID dataSourceGroupId) {
    return new NotFoundException("Could not find data source group: " + dataSourceGroupId);
  }
}
