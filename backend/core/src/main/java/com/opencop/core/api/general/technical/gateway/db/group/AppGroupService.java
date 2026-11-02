// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.group;

import com.opencop.core.api.general.ports.gateway.group.GroupService;
import com.opencop.core.api.general.technical.gateway.scim.user.InternalUserEntity;
import com.opencop.core.api.general.technical.gateway.scim.user.InternalUserRepository;
import com.opencop.core.api.general.technical.model.group.GroupCreateRequest;
import com.opencop.core.api.general.technical.model.group.GroupMemberResponse;
import com.opencop.core.api.general.technical.model.group.GroupMembersPatchRequest;
import com.opencop.core.api.general.technical.model.group.GroupPatchRequest;
import com.opencop.core.api.general.technical.model.group.GroupResponse;
import com.opencop.core.api.general.technical.model.group.GroupSearchResult;
import com.opencop.core.sdk.model.iam.group.GroupReadFilter;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.technical.gateway.db.PageResponse;
import com.opencop.core.sdk.technical.gateway.db.Pageables;
import com.opencop.core.sdk.technical.gateway.db.PersistenceException;
import com.opencop.core.sdk.technical.gateway.db.iam.group.GroupEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.group.GroupMemberEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.group.IamGroupMembersRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.group.IamGroupRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppGroupService implements GroupService {

  private final IamGroupRepository groupRepo;
  private final IamGroupMembersRepository membersRepo;
  private final InternalUserRepository userRepo;

  public AppGroupService(
      IamGroupRepository groupRepo,
      IamGroupMembersRepository membersRepo,
      InternalUserRepository userRepo) {
    this.groupRepo = groupRepo;
    this.membersRepo = membersRepo;
    this.userRepo = userRepo;
  }

  @Override
  @Transactional(readOnly = true)
  public List<GroupResponse> listGroups() {
    return groupRepo.findAll().stream().map(GroupMapper::toResponse).toList();
  }

  @Override
  @Transactional(readOnly = true)
  public PageResponse<GroupSearchResult> getPage(GroupReadFilter filter, Pageable pageable) {
    // Name is unique only within a scope: uk_user_group_admin_name covers admin-created groups
    // (idp_issuer IS NULL) and uk_user_group_issuer_name covers each SCIM issuer, both since V23.
    // Groups from different scopes can share a name, so the "id" tie-breaker is what keeps offset
    // paging total. Removing it lets a tied page boundary repeat or drop a row.
    Pageable ordered =
        Pageables.withStableOrder(
            GroupSearchSort.validate(pageable),
            Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id")),
            "id");
    Page<GroupEntity> page = groupRepo.findAll(GroupSpecifications.from(filter), ordered);
    return new PageResponse<>(
        page.getContent().stream().map(GroupMapper::toSearchResult).toList(),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages());
  }

  @Override
  @Transactional
  public GroupResponse createGroup(GroupCreateRequest request) throws PersistenceException {
    if (request.name() == null || request.name().isBlank()) {
      throw new PersistenceException("Group name is required");
    }
    if (groupRepo.findByIdpIssuerIsNullAndName(request.name().trim()).isPresent()) {
      throw new PersistenceException("A group with that name already exists");
    }
    try {
      var entity = new GroupEntity();
      entity.setName(request.name().trim());
      entity.setDescription(request.description());
      var saved = groupRepo.save(entity);
      return GroupMapper.toResponse(saved);
    } catch (DataIntegrityViolationException e) {
      throw new PersistenceException("A group with that name already exists");
    }
  }

  @Override
  @Transactional
  public void deleteGroup(UUID groupId) throws PersistenceException {
    var group =
        groupRepo
            .findById(groupId)
            .orElseThrow(() -> new PersistenceException("Could not find group: " + groupId));
    assertNotScimManaged(group, "deleted");
    groupRepo.deleteById(groupId);
  }

  private static void assertNotScimManaged(GroupEntity group, String operation) {
    if (group.getIdpIssuer() != null) {
      throw new GlobalOpenCopException(
          "SCIM-managed groups can only be "
              + operation
              + " through the SCIM provisioning endpoint",
          HttpStatus.CONFLICT);
    }
  }

  @Override
  @Transactional(readOnly = true)
  public List<GroupMemberResponse> getMembers(UUID groupId) throws PersistenceException {
    if (!groupRepo.existsById(groupId)) {
      throw new PersistenceException("Could not find group: " + groupId);
    }
    var members = membersRepo.findAllByGroupId(groupId);
    var userIds = members.stream().map(m -> m.getUserId()).collect(Collectors.toSet());
    Map<UUID, InternalUserEntity> userMap =
        userRepo.findAllById(userIds).stream()
            .collect(Collectors.toMap(InternalUserEntity::getId, Function.identity()));
    return members.stream()
        .map(m -> GroupMapper.toMemberResponse(m, userMap.get(m.getUserId())))
        .toList();
  }

  @Override
  @Transactional
  public void patchMembers(UUID groupId, GroupMembersPatchRequest request)
      throws PersistenceException {
    var group =
        groupRepo
            .findById(groupId)
            .orElseThrow(() -> new PersistenceException("Could not find group: " + groupId));
    assertNotScimManaged(group, "modified");

    Set<UUID> toAdd = request.add() != null ? request.add() : Set.of();
    Set<UUID> toRemove = request.remove() != null ? request.remove() : Set.of();

    try {
      if (!toAdd.isEmpty()) {
        List<InternalUserEntity> foundUsers = userRepo.findAllById(toAdd);
        if (foundUsers.size() != toAdd.size()) {
          Set<UUID> foundIds =
              foundUsers.stream().map(InternalUserEntity::getId).collect(Collectors.toSet());
          UUID missing =
              toAdd.stream().filter(id -> !foundIds.contains(id)).findFirst().orElseThrow();
          throw new PersistenceException("Could not find user: " + missing);
        }

        Set<UUID> alreadyMembers = membersRepo.findExistingUserIds(groupId, toAdd);
        List<GroupMemberEntity> newMembers =
            toAdd.stream()
                .filter(userId -> !alreadyMembers.contains(userId))
                .map(userId -> new GroupMemberEntity(group, userId))
                .toList();
        membersRepo.saveAll(newMembers);
      }

      if (!toRemove.isEmpty()) {
        membersRepo.deleteAllByGroupIdAndUserIds(groupId, toRemove);
      }
    } catch (PersistenceException e) {
      throw e;
    } catch (Exception e) {
      throw new PersistenceException(e.getMessage());
    }
  }

  @Override
  @Transactional
  public GroupResponse patchGroup(UUID groupId, GroupPatchRequest request)
      throws PersistenceException {
    var group =
        groupRepo
            .findById(groupId)
            .orElseThrow(() -> new PersistenceException("Could not find group: " + groupId));
    assertNotScimManaged(group, "modified");
    if (request.name() != null) {
      if (request.name().isBlank()) {
        throw new PersistenceException("Group name cannot be blank");
      }
      String newName = request.name().trim();
      if (groupRepo
          .findByIdpIssuerIsNullAndName(newName)
          .filter(existing -> !existing.getId().equals(group.getId()))
          .isPresent()) {
        throw new PersistenceException("A group with that name already exists");
      }
      group.setName(newName);
    }
    if (request.description() != null) {
      group.setDescription(request.description());
    }
    return GroupMapper.toResponse(groupRepo.save(group));
  }
}
