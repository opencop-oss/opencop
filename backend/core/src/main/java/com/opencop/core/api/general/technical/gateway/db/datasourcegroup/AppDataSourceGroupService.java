// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.datasourcegroup;

import com.opencop.core.api.general.ports.gateway.datasourcegroup.DataSourceGroupMemberPatchGuard;
import com.opencop.core.api.general.ports.gateway.datasourcegroup.DataSourceGroupService;
import com.opencop.core.api.general.ports.gateway.repository.DataSourceRepository;
import com.opencop.core.api.general.technical.gateway.scim.user.InternalUserEntity;
import com.opencop.core.api.general.technical.gateway.scim.user.InternalUserRepository;
import com.opencop.core.api.general.technical.model.datasourcegroup.DataSourceGroupCreateRequest;
import com.opencop.core.api.general.technical.model.datasourcegroup.DataSourceGroupMemberResponse;
import com.opencop.core.api.general.technical.model.datasourcegroup.DataSourceGroupMembersPatchRequest;
import com.opencop.core.api.general.technical.model.datasourcegroup.DataSourceGroupPatchRequest;
import com.opencop.core.api.general.technical.model.datasourcegroup.DataSourceGroupResponse;
import com.opencop.core.api.general.technical.model.datasourcegroup.DataSourceSummary;
import com.opencop.core.api.general.technical.model.datasourcegroup.MyDataSourceGroupResponse;
import com.opencop.core.sdk.model.datasource.DataSource;
import com.opencop.core.sdk.model.datasource.DataSourceReadFilter;
import com.opencop.core.sdk.model.visibility.RecordVisibility;
import com.opencop.core.sdk.technical.gateway.db.NotFoundException;
import com.opencop.core.sdk.technical.gateway.db.PersistenceException;
import com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup.DataSourceGroupDataSourceEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup.DataSourceGroupEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup.DataSourceGroupMemberEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup.DataSourceGroupRole;
import com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup.DataSourceGroupUserGroupEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup.IamDataSourceGroupDataSourceRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup.IamDataSourceGroupMembersRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup.IamDataSourceGroupRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup.IamDataSourceGroupUserGroupRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.group.IamGroupMembersRepository;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppDataSourceGroupService implements DataSourceGroupService {

  private static final String DATA_SOURCE_GROUP_NOT_FOUND = "Could not find data source group: ";

  private final IamDataSourceGroupRepository dataSourceGroupRepo;
  private final IamDataSourceGroupMembersRepository membersRepo;
  private final InternalUserRepository userRepo;
  private final IamDataSourceGroupUserGroupRepository dataSourceGroupUserGroupRepo;
  private final IamGroupMembersRepository groupMembersRepo;
  private final IamDataSourceGroupDataSourceRepository dataSourceRepo;
  private final DataSourceRepository coreDataSourceRepo;

  public AppDataSourceGroupService(
      IamDataSourceGroupRepository dataSourceGroupRepo,
      IamDataSourceGroupMembersRepository membersRepo,
      InternalUserRepository userRepo,
      IamDataSourceGroupUserGroupRepository dataSourceGroupUserGroupRepo,
      IamGroupMembersRepository groupMembersRepo,
      IamDataSourceGroupDataSourceRepository dataSourceRepo,
      DataSourceRepository coreDataSourceRepo) {
    this.dataSourceGroupRepo = dataSourceGroupRepo;
    this.membersRepo = membersRepo;
    this.userRepo = userRepo;
    this.dataSourceGroupUserGroupRepo = dataSourceGroupUserGroupRepo;
    this.groupMembersRepo = groupMembersRepo;
    this.dataSourceRepo = dataSourceRepo;
    this.coreDataSourceRepo = coreDataSourceRepo;
  }

  @Override
  @Transactional(readOnly = true)
  public List<DataSourceGroupResponse> listGroups() {
    return dataSourceGroupRepo.findAll().stream().map(DataSourceGroupMapper::toResponse).toList();
  }

  @Override
  @Transactional(readOnly = true)
  public List<DataSourceGroupResponse> listGroupsForUser(UUID userId) {
    Map<UUID, DataSourceGroupResponse> groups = new LinkedHashMap<>();
    membersRepo
        .findAllByUserIdWithGroup(userId)
        .forEach(
            member ->
                groups.putIfAbsent(
                    member.getGroup().getId(),
                    DataSourceGroupMapper.toResponse(member.getGroup())));

    var userGroupIds = groupMembersRepo.findGroupIdsByUserId(userId);
    if (!userGroupIds.isEmpty()) {
      dataSourceGroupUserGroupRepo
          .findAllByUserGroupIdInWithDataSourceGroup(userGroupIds)
          .forEach(
              assignment -> {
                var group = assignment.getDataSourceGroup();
                groups.putIfAbsent(group.getId(), DataSourceGroupMapper.toResponse(group));
              });
    }
    return List.copyOf(groups.values());
  }

  @Override
  @Transactional(readOnly = true)
  public List<MyDataSourceGroupResponse> listMyGroups(UUID userId) {
    Map<UUID, MyDataSourceGroupResponse> byGroupId = new LinkedHashMap<>();

    membersRepo.findAllByUserIdWithGroup(userId).stream()
        .map(DataSourceGroupMapper::toMyResponse)
        .forEach(r -> mergeIfHigher(byGroupId, r));

    var userGroupIds = groupMembersRepo.findGroupIdsByUserId(userId);
    if (!userGroupIds.isEmpty()) {
      dataSourceGroupUserGroupRepo.findAllByUserGroupIdInWithDataSourceGroup(userGroupIds).stream()
          .map(DataSourceGroupMapper::toMyResponse)
          .forEach(r -> mergeIfHigher(byGroupId, r));
    }

    if (byGroupId.isEmpty()) {
      return List.of();
    }

    List<DataSourceGroupDataSourceEntity> assignments =
        dataSourceRepo.findAllByDataSourceGroupIdIn(byGroupId.keySet());
    Map<UUID, DataSource> dataSourcesById = dataSourcesById(assignments);
    Map<UUID, List<DataSourceSummary>> dataSourcesByGroup =
        assignments.stream()
            .collect(
                Collectors.groupingBy(
                    e -> e.getGroup().getId(),
                    Collectors.mapping(
                        e ->
                            DataSourceGroupMapper.toDataSourceSummary(
                                e, dataSourcesById.get(e.getDataSourceId())),
                        Collectors.toList())));

    return byGroupId.entrySet().stream()
        .map(
            entry ->
                DataSourceGroupMapper.withDataSources(
                    entry.getValue(), dataSourcesByGroup.getOrDefault(entry.getKey(), List.of())))
        .toList();
  }

  @Override
  @Transactional
  public DataSourceGroupResponse createGroup(DataSourceGroupCreateRequest request)
      throws PersistenceException {
    if (request.name() == null || request.name().isBlank()) {
      throw new PersistenceException("Data source group name is required");
    }
    if (dataSourceGroupRepo.existsByName(request.name().trim())) {
      throw new PersistenceException("A data source group with that name already exists");
    }
    try {
      var entity = new DataSourceGroupEntity();
      entity.setName(request.name().trim());
      entity.setDescription(request.description());
      return DataSourceGroupMapper.toResponse(dataSourceGroupRepo.save(entity));
    } catch (DataIntegrityViolationException _) {
      throw new PersistenceException("A data source group with that name already exists");
    }
  }

  @Override
  @Transactional(readOnly = true)
  public DataSourceGroupResponse getGroup(UUID groupId) throws PersistenceException {
    var entity =
        dataSourceGroupRepo.findById(groupId).orElseThrow(() -> dataSourceGroupNotFound(groupId));
    return DataSourceGroupMapper.toResponse(entity);
  }

  @Override
  @Transactional
  public DataSourceGroupResponse patchGroup(UUID groupId, DataSourceGroupPatchRequest request)
      throws PersistenceException {
    var entity =
        dataSourceGroupRepo.findById(groupId).orElseThrow(() -> dataSourceGroupNotFound(groupId));
    if (request.name() != null) {
      if (request.name().isBlank()) {
        throw new PersistenceException("Data source group name cannot be blank");
      }
      String trimmed = request.name().trim();
      if (!trimmed.equals(entity.getName()) && dataSourceGroupRepo.existsByName(trimmed)) {
        throw new PersistenceException("A data source group with that name already exists");
      }
      entity.setName(trimmed);
    }
    if (request.description() != null) {
      entity.setDescription(request.description());
    }
    try {
      return DataSourceGroupMapper.toResponse(dataSourceGroupRepo.save(entity));
    } catch (DataIntegrityViolationException _) {
      throw new PersistenceException("A data source group with that name already exists");
    }
  }

  @Override
  @Transactional
  public void deleteGroup(UUID groupId) throws PersistenceException {
    if (!dataSourceGroupRepo.existsById(groupId)) {
      throw dataSourceGroupNotFound(groupId);
    }
    dataSourceGroupRepo.deleteById(groupId);
  }

  @Override
  @Transactional(readOnly = true)
  public List<DataSourceGroupMemberResponse> getMembers(UUID dataSourceGroupId)
      throws PersistenceException {
    if (!dataSourceGroupRepo.existsById(dataSourceGroupId)) {
      throw dataSourceGroupNotFound(dataSourceGroupId);
    }
    List<DataSourceGroupMemberEntity> members =
        membersRepo.findAllByDataSourceGroupId(dataSourceGroupId);
    Set<UUID> userIds =
        members.stream().map(DataSourceGroupMemberEntity::getUserId).collect(Collectors.toSet());
    Map<UUID, InternalUserEntity> userMap =
        userRepo.findAllById(userIds).stream()
            .collect(Collectors.toMap(InternalUserEntity::getId, Function.identity()));
    return members.stream()
        .map(m -> DataSourceGroupMapper.toMemberResponse(m, userMap.get(m.getUserId())))
        .toList();
  }

  @Override
  @Transactional
  public void patchMembers(
      UUID dataSourceGroupId,
      DataSourceGroupMembersPatchRequest request,
      DataSourceGroupMemberPatchGuard guard)
      throws PersistenceException {
    for (DataSourceGroupMembersPatchRequest.MemberAdd memberAdd : request.add()) {
      if (memberAdd.userId() == null) {
        throw new PersistenceException("Member user id is required");
      }
      if (memberAdd.role() == null) {
        throw new PersistenceException("Member role is required");
      }
    }

    DataSourceGroupEntity group =
        dataSourceGroupRepo
            .findByIdForUpdate(dataSourceGroupId)
            .orElseThrow(() -> dataSourceGroupNotFound(dataSourceGroupId));

    if (!request.add().isEmpty()) {
      Set<UUID> addUserIds =
          request.add().stream()
              .map(DataSourceGroupMembersPatchRequest.MemberAdd::userId)
              .collect(Collectors.toSet());
      List<InternalUserEntity> foundUsers = userRepo.findAllById(addUserIds);
      if (foundUsers.size() != addUserIds.size()) {
        Set<UUID> foundIds =
            foundUsers.stream().map(InternalUserEntity::getId).collect(Collectors.toSet());
        UUID missing =
            addUserIds.stream().filter(id -> !foundIds.contains(id)).findFirst().orElseThrow();
        throw new PersistenceException("Could not find user: " + missing);
      }
    }

    List<DataSourceGroupMemberEntity> existingMembers =
        membersRepo.findAllByDataSourceGroupId(dataSourceGroupId);
    Map<UUID, DataSourceGroupMemberEntity> existingByUserId =
        existingMembers.stream()
            .collect(Collectors.toMap(DataSourceGroupMemberEntity::getUserId, Function.identity()));

    if (guard != null) {
      Map<UUID, DataSourceGroupRole> currentMap =
          existingMembers.stream()
              .collect(
                  Collectors.toMap(
                      DataSourceGroupMemberEntity::getUserId,
                      DataSourceGroupMemberEntity::getRole));
      Map<UUID, DataSourceGroupRole> currentUserGroups =
          dataSourceGroupUserGroupRepo.findAllByDataSourceGroupId(dataSourceGroupId).stream()
              .collect(
                  Collectors.toMap(
                      DataSourceGroupUserGroupEntity::getUserGroupId,
                      DataSourceGroupUserGroupEntity::getRole));
      Map<UUID, DataSourceGroupRole> projected = new HashMap<>(currentMap);
      request.add().forEach(a -> projected.put(a.userId(), a.role()));
      request.remove().forEach(projected::remove);
      guard.check(currentMap, projected, currentUserGroups);
    }

    for (DataSourceGroupMembersPatchRequest.MemberAdd memberAdd : request.add()) {
      DataSourceGroupMemberEntity existing = existingByUserId.get(memberAdd.userId());
      if (existing != null) {
        existing.setRole(memberAdd.role());
        membersRepo.save(existing);
      } else {
        membersRepo.save(
            new DataSourceGroupMemberEntity(group, memberAdd.userId(), memberAdd.role()));
      }
    }

    if (!request.remove().isEmpty()) {
      membersRepo.deleteAllByDataSourceGroupIdAndUserIds(dataSourceGroupId, request.remove());
    }
  }

  private static NotFoundException dataSourceGroupNotFound(UUID groupId) {
    return new NotFoundException(DATA_SOURCE_GROUP_NOT_FOUND + groupId);
  }

  private Map<UUID, DataSource> dataSourcesById(List<DataSourceGroupDataSourceEntity> assignments) {
    Set<UUID> dataSourceIds =
        assignments.stream()
            .map(DataSourceGroupDataSourceEntity::getDataSourceId)
            .collect(Collectors.toSet());
    if (dataSourceIds.isEmpty()) {
      return Map.of();
    }
    return coreDataSourceRepo
        .findAll(
            new DataSourceReadFilter(null, dataSourceIds, RecordVisibility.ALL, null, null, null))
        .stream()
        .collect(Collectors.toMap(DataSource::id, Function.identity()));
  }

  private static void mergeIfHigher(
      Map<UUID, MyDataSourceGroupResponse> byGroupId, MyDataSourceGroupResponse candidate) {
    byGroupId.merge(
        candidate.id(),
        candidate,
        (existing, incoming) ->
            incoming.containerRole().isHigherThan(existing.containerRole()) ? incoming : existing);
  }
}
