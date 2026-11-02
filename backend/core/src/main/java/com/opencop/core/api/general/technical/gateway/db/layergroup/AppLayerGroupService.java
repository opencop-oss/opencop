// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.layergroup;

import com.opencop.core.api.general.ports.gateway.layergroup.LayerGroupService;
import com.opencop.core.api.general.ports.gateway.layergroup.MemberPatchGuard;
import com.opencop.core.api.general.technical.gateway.scim.user.InternalUserEntity;
import com.opencop.core.api.general.technical.gateway.scim.user.InternalUserRepository;
import com.opencop.core.api.general.technical.model.layergroup.LayerGroupCreateRequest;
import com.opencop.core.api.general.technical.model.layergroup.LayerGroupMemberResponse;
import com.opencop.core.api.general.technical.model.layergroup.LayerGroupMembersPatchRequest;
import com.opencop.core.api.general.technical.model.layergroup.LayerGroupPatchRequest;
import com.opencop.core.api.general.technical.model.layergroup.LayerGroupResponse;
import com.opencop.core.api.general.technical.model.layergroup.LayerSummary;
import com.opencop.core.api.general.technical.model.layergroup.MyLayerGroupResponse;
import com.opencop.core.sdk.technical.gateway.db.PersistenceException;
import com.opencop.core.sdk.technical.gateway.db.iam.group.IamGroupMembersRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.IamLayerGroupLayerRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.IamLayerGroupMembersRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.IamLayerGroupRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.IamLayerGroupUserGroupRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.LayerGroupAccessScope;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.LayerGroupEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.LayerGroupMemberEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.LayerGroupRole;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.LayerGroupUserGroupEntity;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppLayerGroupService implements LayerGroupService {

  private final IamLayerGroupRepository layerGroupRepo;
  private final IamLayerGroupMembersRepository membersRepo;
  private final InternalUserRepository userRepo;
  private final IamLayerGroupLayerRepository layerGroupLayerRepo;
  private final IamGroupMembersRepository groupMembersRepo;
  private final IamLayerGroupUserGroupRepository layerGroupUserGroupRepo;

  public AppLayerGroupService(
      IamLayerGroupRepository layerGroupRepo,
      IamLayerGroupMembersRepository membersRepo,
      InternalUserRepository userRepo,
      IamLayerGroupLayerRepository layerGroupLayerRepo,
      IamGroupMembersRepository groupMembersRepo,
      IamLayerGroupUserGroupRepository layerGroupUserGroupRepo) {
    this.layerGroupRepo = layerGroupRepo;
    this.membersRepo = membersRepo;
    this.userRepo = userRepo;
    this.layerGroupLayerRepo = layerGroupLayerRepo;
    this.groupMembersRepo = groupMembersRepo;
    this.layerGroupUserGroupRepo = layerGroupUserGroupRepo;
  }

  @Override
  @Transactional(readOnly = true)
  public List<LayerGroupResponse> listGroups() {
    return layerGroupRepo.findAll().stream().map(LayerGroupMapper::toResponse).toList();
  }

  @Override
  @Transactional
  public LayerGroupResponse createGroup(LayerGroupCreateRequest request)
      throws PersistenceException {
    if (request.name() == null || request.name().isBlank()) {
      throw new PersistenceException("Layer group name is required");
    }
    if (layerGroupRepo.existsByName(request.name().trim())) {
      throw new PersistenceException("A layer group with that name already exists");
    }
    try {
      var entity = new LayerGroupEntity();
      entity.setName(request.name().trim());
      entity.setDescription(request.description());
      entity.setAccessScope(
          request.accessScope() != null ? request.accessScope() : LayerGroupAccessScope.PRIVATE);
      var saved = layerGroupRepo.save(entity);
      return LayerGroupMapper.toResponse(saved);
    } catch (DataIntegrityViolationException e) {
      throw new PersistenceException("A layer group with that name already exists");
    }
  }

  @Override
  @Transactional(readOnly = true)
  public LayerGroupResponse getGroup(UUID groupId) throws PersistenceException {
    var entity =
        layerGroupRepo
            .findById(groupId)
            .orElseThrow(() -> new PersistenceException("Could not find layer group: " + groupId));
    return LayerGroupMapper.toResponse(entity);
  }

  @Override
  @Transactional
  public LayerGroupResponse patchGroup(UUID groupId, LayerGroupPatchRequest request)
      throws PersistenceException {
    var entity =
        layerGroupRepo
            .findById(groupId)
            .orElseThrow(() -> new PersistenceException("Could not find layer group: " + groupId));
    if (request.name() != null) {
      if (request.name().isBlank()) {
        throw new PersistenceException("Layer group name cannot be blank");
      }
      String trimmed = request.name().trim();
      if (!trimmed.equals(entity.getName()) && layerGroupRepo.existsByName(trimmed)) {
        throw new PersistenceException("A layer group with that name already exists");
      }
      entity.setName(trimmed);
    }
    if (request.description() != null) {
      entity.setDescription(request.description());
    }
    if (request.accessScope() != null) {
      entity.setAccessScope(request.accessScope());
    }
    try {
      return LayerGroupMapper.toResponse(layerGroupRepo.save(entity));
    } catch (DataIntegrityViolationException e) {
      throw new PersistenceException("A layer group with that name already exists");
    }
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void lockGroup(UUID groupId) throws PersistenceException {
    layerGroupRepo
        .findByIdForUpdate(groupId)
        .orElseThrow(() -> new PersistenceException("Could not find layer group: " + groupId));
  }

  @Override
  @Transactional
  public void deleteGroup(UUID groupId) throws PersistenceException {
    if (!layerGroupRepo.existsById(groupId)) {
      throw new PersistenceException("Could not find layer group: " + groupId);
    }
    layerGroupRepo.deleteById(groupId);
  }

  @Override
  @Transactional(readOnly = true)
  public List<LayerGroupMemberResponse> getMembers(UUID layerGroupId) throws PersistenceException {
    if (!layerGroupRepo.existsById(layerGroupId)) {
      throw new PersistenceException("Could not find layer group: " + layerGroupId);
    }
    List<LayerGroupMemberEntity> members = membersRepo.findAllByLayerGroupId(layerGroupId);
    Set<UUID> userIds =
        members.stream().map(LayerGroupMemberEntity::getUserId).collect(Collectors.toSet());
    Map<UUID, InternalUserEntity> userMap =
        userRepo.findAllById(userIds).stream()
            .collect(Collectors.toMap(InternalUserEntity::getId, Function.identity()));
    return members.stream()
        .map(m -> LayerGroupMapper.toMemberResponse(m, userMap.get(m.getUserId())))
        .toList();
  }

  @Override
  @Transactional
  public void patchMembers(
      UUID layerGroupId, LayerGroupMembersPatchRequest request, MemberPatchGuard guard)
      throws PersistenceException {

    for (LayerGroupMembersPatchRequest.MemberAdd memberAdd : request.add()) {
      if (memberAdd.userId() == null) {
        throw new PersistenceException("Member user id is required");
      }
      if (memberAdd.role() == null) {
        throw new PersistenceException("Member role is required");
      }
    }

    LayerGroupEntity group =
        layerGroupRepo
            .findByIdForUpdate(layerGroupId)
            .orElseThrow(
                () -> new PersistenceException("Could not find layer group: " + layerGroupId));

    if (!request.add().isEmpty()) {
      Set<UUID> addUserIds =
          request.add().stream()
              .map(LayerGroupMembersPatchRequest.MemberAdd::userId)
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

    List<LayerGroupMemberEntity> existingMembers = membersRepo.findAllByLayerGroupId(layerGroupId);
    Map<UUID, LayerGroupMemberEntity> existingByUserId =
        existingMembers.stream()
            .collect(Collectors.toMap(LayerGroupMemberEntity::getUserId, Function.identity()));

    if (guard != null) {
      Map<UUID, LayerGroupRole> currentMap =
          existingMembers.stream()
              .collect(
                  Collectors.toMap(
                      LayerGroupMemberEntity::getUserId, LayerGroupMemberEntity::getRole));
      Map<UUID, LayerGroupRole> currentUserGroups =
          layerGroupUserGroupRepo.findAllByLayerGroupId(layerGroupId).stream()
              .collect(
                  Collectors.toMap(
                      LayerGroupUserGroupEntity::getUserGroupId,
                      LayerGroupUserGroupEntity::getRole));
      Map<UUID, LayerGroupRole> projected = new HashMap<>(currentMap);
      request.add().forEach(a -> projected.put(a.userId(), a.role()));
      request.remove().forEach(projected::remove);
      guard.check(currentMap, projected, currentUserGroups);
    }

    for (LayerGroupMembersPatchRequest.MemberAdd memberAdd : request.add()) {
      LayerGroupMemberEntity existing = existingByUserId.get(memberAdd.userId());
      if (existing != null) {
        existing.setRole(memberAdd.role());
        membersRepo.save(existing);
      } else {
        membersRepo.save(new LayerGroupMemberEntity(group, memberAdd.userId(), memberAdd.role()));
      }
    }

    if (!request.remove().isEmpty()) {
      membersRepo.deleteAllByLayerGroupIdAndUserIds(layerGroupId, request.remove());
    }
  }

  @Override
  @Transactional(readOnly = true)
  public List<MyLayerGroupResponse> listMyLayerGroups(UUID userId) {
    Map<UUID, MyLayerGroupResponse> byGroupId = new HashMap<>();

    membersRepo.findAllByUserIdWithGroup(userId).stream()
        .map(LayerGroupMapper::toMyResponse)
        .forEach(r -> mergeIfHigher(byGroupId, r));

    Set<UUID> userGroupIds = groupMembersRepo.findGroupIdsByUserId(userId);
    if (!userGroupIds.isEmpty()) {
      layerGroupUserGroupRepo.findAllByUserGroupIdInWithLayerGroup(userGroupIds).stream()
          .map(LayerGroupMapper::toMyResponse)
          .forEach(r -> mergeIfHigher(byGroupId, r));
    }

    layerGroupRepo
        .findAllByAccessScope(LayerGroupAccessScope.GLOBAL)
        .forEach(
            globalGroup ->
                byGroupId.computeIfAbsent(
                    globalGroup.getId(),
                    id -> LayerGroupMapper.toMyResponse(globalGroup, LayerGroupRole.MEMBER)));

    if (byGroupId.isEmpty()) {
      return List.of();
    }

    Map<UUID, List<LayerSummary>> layersByGroup =
        layerGroupLayerRepo.findAllByLayerGroupIdIn(byGroupId.keySet()).stream()
            .collect(
                Collectors.groupingBy(
                    e -> e.getId().getLayerGroupId(),
                    Collectors.mapping(LayerGroupMapper::toLayerSummary, Collectors.toList())));

    return byGroupId.entrySet().stream()
        .map(
            entry ->
                LayerGroupMapper.withLayers(
                    entry.getValue(), layersByGroup.getOrDefault(entry.getKey(), List.of())))
        .toList();
  }

  private static void mergeIfHigher(
      Map<UUID, MyLayerGroupResponse> byGroupId, MyLayerGroupResponse candidate) {
    byGroupId.merge(
        candidate.id(),
        candidate,
        (existing, incoming) ->
            incoming.containerRole().isHigherThan(existing.containerRole()) ? incoming : existing);
  }

  @Transactional(readOnly = true)
  public List<LayerGroupMemberEntity> listMembers(UUID layerGroupId) {
    return membersRepo.findAllByLayerGroupId(layerGroupId);
  }
}
