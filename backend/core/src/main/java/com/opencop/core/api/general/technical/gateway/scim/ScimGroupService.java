// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.scim;

import static com.opencop.core.api.general.technical.gateway.scim.ScimGroupMapper.toScim;
import static com.opencop.core.api.general.technical.gateway.scim.ScimHelper.normalizePath;
import static com.opencop.core.api.general.technical.gateway.scim.ScimHelper.validatePatch;

import com.opencop.core.api.general.technical.endpoint.scim.ScimExceptions;
import com.opencop.core.api.general.technical.gateway.scim.user.InternalUserEntity;
import com.opencop.core.api.general.technical.gateway.scim.user.InternalUserRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.group.GroupEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.group.GroupMemberEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.group.IamGroupMembersRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.group.IamGroupRepository;
import com.unboundid.scim2.common.messages.PatchOpType;
import com.unboundid.scim2.common.messages.PatchOperation;
import com.unboundid.scim2.common.messages.PatchRequest;
import com.unboundid.scim2.common.types.GroupResource;
import com.unboundid.scim2.common.types.Member;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;

@Service
public class ScimGroupService {

  private static final Logger log = LoggerFactory.getLogger(ScimGroupService.class);

  private final String issuer;
  private final InternalUserRepository userRepository;
  private final IamGroupRepository groupRepository;
  private final IamGroupMembersRepository groupMembersRepository;

  public ScimGroupService(
      @Value("${opencop.idp.issuer}") String issuer,
      InternalUserRepository userRepository,
      IamGroupRepository groupRepository,
      IamGroupMembersRepository groupMembersRepository) {
    this.issuer = issuer;
    this.userRepository = userRepository;
    this.groupRepository = groupRepository;
    this.groupMembersRepository = groupMembersRepository;
  }

  @Transactional
  public GroupResource createGroup(GroupResource group) {
    validateDisplayName(group.getDisplayName());

    rejectDuplicateExternalId(group.getExternalId(), null);
    rejectDuplicateDisplayName(group.getDisplayName(), null);

    GroupEntity internalGroup = new GroupEntity();
    internalGroup.setIdpIssuer(issuer);
    internalGroup.setIdpGroupId(group.getExternalId());
    internalGroup.setName(group.getDisplayName());
    var saved = groupRepository.save(internalGroup);
    updateGroupMembers(group.getMembers(), saved);
    return withMembers(saved);
  }

  @Transactional(readOnly = true)
  public GroupResource getGroup(UUID groupId) {
    return withMembers(findScimGroupById(groupId));
  }

  @Transactional(readOnly = true)
  public Page<GroupResource> listGroups(String filter, int startIndex, int count) {
    int safeStart = Math.max(startIndex, 1);
    int safeCount = count <= 0 ? 100 : Math.min(count, 200);
    int pageIndex = (safeStart - 1) / safeCount;

    PageRequest pageRequest = PageRequest.of(pageIndex, safeCount);

    List<GroupEntity> issuerGroups = scimGroups();

    if (filter == null || filter.isBlank()) {
      return toListPage(issuerGroups, pageRequest);
    }

    ScimHelper.MultiEqFilter eqFilter =
        ScimHelper.parseFilterSafe(filter)
            .orElseThrow(() -> new ScimExceptions.ScimInvalidFilterException(filter));

    String field = eqFilter.field().toLowerCase();
    List<String> values = eqFilter.values();

    List<GroupEntity> filtered =
        switch (field) {
          case "displayname" -> {
            if (values.size() == 1) {
              yield issuerGroups.stream()
                  .filter(group -> values.getFirst().equals(group.getName()))
                  .toList();
            }
            yield issuerGroups.stream().filter(group -> values.contains(group.getName())).toList();
          }
          case "id" -> {
            List<UUID> ids;
            try {
              ids = values.stream().map(UUID::fromString).toList();
            } catch (IllegalArgumentException _) {
              throw new ScimExceptions.ScimInvalidFilterException(filter);
            }
            yield ids.stream()
                .map(id -> groupRepository.findByIdAndIdpIssuer(id, issuer))
                .flatMap(Optional::stream)
                .toList();
          }
          case "externalid" -> {
            if (values.size() == 1) {
              yield groupRepository
                  .findByIdpIssuerAndIdpGroupId(issuer, values.getFirst())
                  .map(List::of)
                  .orElseGet(List::of);
            }
            yield issuerGroups.stream()
                .filter(group -> values.contains(group.getIdpGroupId()))
                .toList();
          }
          default -> throw new ScimExceptions.ScimInvalidFilterException(filter);
        };

    return toListPage(filtered, pageRequest);
  }

  private List<Member> getGroupMembers(UUID groupId) {
    List<UUID> userIds =
        groupMembersRepository.findAllByGroupId(groupId).stream()
            .map(GroupMemberEntity::getUserId)
            .toList();
    List<InternalUserEntity> usersInGroup = userRepository.findAllById(userIds);
    if (usersInGroup == null || usersInGroup.isEmpty()) {
      return List.of();
    }
    return usersInGroup.stream()
        .map(
            user ->
                new Member()
                    .setValue(user.getId().toString())
                    .setType("User")
                    .setRef(URI.create("/scim/v1/provisioning/Users/" + user.getId()))
                    .setDisplay(user.getDisplayName()))
        .toList();
  }

  @Transactional
  public GroupResource updateGroup(String groupId, GroupResource group) {
    GroupEntity existingGroup = findScimGroupById(parseGroupId(groupId));

    validateDisplayName(group.getDisplayName());
    enforceImmutableExternalId(group, existingGroup);
    rejectDuplicateDisplayName(group.getDisplayName(), existingGroup.getId());

    ScimGroupMapper.applyReplace(group, existingGroup);

    groupRepository.save(existingGroup);

    updateGroupMembers(group.getMembers(), existingGroup);

    return withMembers(existingGroup);
  }

  @Transactional
  public GroupResource patchGroup(UUID groupId, PatchRequest patch) {
    validatePatch(patch);

    GroupEntity groupEntity = findScimGroupById(groupId);

    applyPatchToGroup(patch, groupEntity);

    groupRepository.save(groupEntity);

    return withMembers(groupEntity);
  }

  private void updateGroupMembers(List<Member> members, GroupEntity groupEntity) {
    List<InternalUserEntity> resolved = new ArrayList<>();
    if (members != null) {
      for (Member member : members) {
        String value = member == null ? null : member.getValue();
        if (value == null || value.isBlank()) {
          throw new ScimExceptions.ScimBadRequestException("Group member entry is missing a value");
        }
        resolved.add(resolveUserByMemberValue(value));
      }
    }
    groupMembersRepository.deleteAll(groupMembersRepository.findAllByGroupId(groupEntity.getId()));
    for (InternalUserEntity user : resolved) {
      groupMembersRepository.save(new GroupMemberEntity(groupEntity, user.getId()));
    }
  }

  @Transactional
  public void deleteGroup(String groupId) {
    GroupEntity group = findScimGroupById(parseGroupId(groupId));

    groupMembersRepository.deleteAll(groupMembersRepository.findAllByGroupId(group.getId()));
    groupRepository.delete(group);
  }

  private GroupEntity findScimGroupById(UUID groupId) {
    return groupRepository
        .findByIdAndIdpIssuer(groupId, issuer)
        .orElseThrow(() -> new ScimExceptions.ScimNotFoundException("Group not found: " + groupId));
  }

  private UUID parseGroupId(String groupId) {
    try {
      return UUID.fromString(groupId);
    } catch (IllegalArgumentException _) {
      throw new ScimExceptions.ScimNotFoundException("Group not found: " + groupId);
    }
  }

  private void applyPatchToGroup(PatchRequest patchRequest, GroupEntity groupEntity) {
    for (PatchOperation operation : patchRequest.getOperations()) {
      switch (operation.getOpType()) {
        case PatchOpType.REPLACE -> applyReplaceOperation(operation, groupEntity);
        case PatchOpType.ADD -> applyAddOperation(operation, groupEntity);
        case PatchOpType.REMOVE -> applyRemoveOperation(operation, groupEntity);
        default ->
            throw new ScimExceptions.ScimBadRequestException(
                "Unsupported operation type: " + operation.getOpType());
      }
    }
  }

  private void applyReplaceOperation(PatchOperation operation, GroupEntity groupEntity) {
    try {
      String path = normalizePath(operation.getPath());
      if ("displayName".equals(path)) {
        String displayName = operation.getValue(String.class);
        validateDisplayName(displayName);
        rejectDuplicateDisplayName(displayName, groupEntity.getId());
        groupEntity.setName(displayName);
      } else if ("members".equals(path)) {
        updateGroupMembers(membersFromOperation(operation), groupEntity);
      } else {
        throw new ScimExceptions.ScimBadRequestException("Unsupported path: " + path);
      }
    } catch (JacksonException _) {
      throw new ScimExceptions.ScimBadRequestException(
          "Could not deserialize value from operation: " + operation);
    }
  }

  private List<Member> membersFromOperation(PatchOperation operation) {
    JsonNode node = operation.getJsonNode();
    if (node == null || node.isNull()) {
      return List.of();
    }
    List<Member> members = new ArrayList<>();
    if (node.isArray()) {
      for (JsonNode item : node) {
        members.add(new Member().setValue(requireMemberValue(item)));
      }
    } else {
      members.add(new Member().setValue(requireMemberValue(node)));
    }
    return members;
  }

  private static String requireMemberValue(JsonNode item) {
    if (item == null || !item.isObject()) {
      throw new ScimExceptions.ScimBadRequestException(
          "Group member entry must be an object with a value");
    }
    JsonNode valueNode = item.get("value");
    if (valueNode == null || valueNode.isNull()) {
      throw new ScimExceptions.ScimBadRequestException("Group member entry is missing a value");
    }
    String value = valueNode.asString(null);
    if (value == null || value.isBlank()) {
      throw new ScimExceptions.ScimBadRequestException("Group member entry is missing a value");
    }
    return value;
  }

  private void applyAddOperation(PatchOperation operation, GroupEntity groupEntity) {
    String path = normalizePath(operation.getPath());
    if (!"members".equals(path)) {
      throw new ScimExceptions.ScimBadRequestException("Unsupported path: " + path);
    }

    JsonNode node = operation.getJsonNode();

    if (node == null || node.isNull()) {
      return;
    }

    if (node.isArray()) {
      for (JsonNode item : node) {
        addMember(groupEntity, requireMemberValue(item));
      }
    } else {
      addMember(groupEntity, requireMemberValue(node));
    }
  }

  private InternalUserEntity resolveUserByMemberValue(String value) {
    if (isUuid(value)) {
      var byInternalId =
          userRepository
              .findById(UUID.fromString(value))
              .filter(user -> issuer.equals(user.getIdpIssuer()));
      if (byInternalId.isPresent()) {
        return byInternalId.get();
      }
    }
    return userRepository
        .findByIdpIssuerAndIdpUserId(issuer, value)
        .orElseThrow(() -> new ScimExceptions.ScimNotFoundException("User not found: " + value));
  }

  private static boolean isUuid(String value) {
    if (value == null) {
      return false;
    }
    try {
      UUID.fromString(value);
      return true;
    } catch (IllegalArgumentException _) {
      return false;
    }
  }

  private void addMember(GroupEntity groupEntity, String memberValue) {
    InternalUserEntity user = resolveUserByMemberValue(memberValue);

    if (!groupMembersRepository
        .findExistingUserIds(groupEntity.getId(), List.of(user.getId()))
        .isEmpty()) {
      return;
    }
    groupMembersRepository.save(new GroupMemberEntity(groupEntity, user.getId()));
  }

  private void applyRemoveOperation(PatchOperation operation, GroupEntity groupEntity) {

    String path = normalizePath(operation.getPath());
    if (!"members".equals(path)) {
      throw new ScimExceptions.ScimBadRequestException("Unsupported path: " + path);
    }

    JsonNode node = operation.getJsonNode();

    if (node == null || node.isNull()) {
      return;
    }

    if (node.isArray()) {
      for (JsonNode item : node) {
        this.removeMember(groupEntity, requireMemberValue(item));
      }
    } else {
      this.removeMember(groupEntity, requireMemberValue(node));
    }
  }

  private void removeMember(GroupEntity groupEntity, String memberValue) {
    InternalUserEntity user = resolveUserByMemberValue(memberValue);
    groupMembersRepository.deleteAllByGroupIdAndUserIds(groupEntity.getId(), List.of(user.getId()));
  }

  private GroupResource withMembers(GroupEntity groupEntity) {
    GroupResource groupResource = toScim(groupEntity);
    groupResource.setMembers(getGroupMembers(groupEntity.getId()));
    return groupResource;
  }

  private List<GroupEntity> scimGroups() {
    return groupRepository.findAll().stream()
        .filter(group -> issuer.equals(group.getIdpIssuer()))
        .toList();
  }

  private Page<GroupResource> toListPage(List<GroupEntity> groups, PageRequest pageRequest) {
    int fromIndex =
        Math.min(pageRequest.getPageNumber() * pageRequest.getPageSize(), groups.size());
    int toIndex = Math.min(fromIndex + pageRequest.getPageSize(), groups.size());
    List<GroupResource> resources =
        groups.subList(fromIndex, toIndex).stream().map(this::toListRow).toList();
    return new PageImpl<>(resources, pageRequest, groups.size());
  }

  private GroupResource toListRow(GroupEntity groupEntity) {
    GroupResource groupResource = toScim(groupEntity);
    groupResource.setMembers(null);
    return groupResource;
  }

  private void validateDisplayName(String displayName) {
    if (displayName == null || displayName.isBlank()) {
      throw new ScimExceptions.ScimBadRequestException("Group displayName must not be blank");
    }
  }

  private void enforceImmutableExternalId(GroupResource incoming, GroupEntity existing) {
    if (incoming.getExternalId() == null || incoming.getExternalId().isBlank()) {
      incoming.setExternalId(existing.getIdpGroupId());
      return;
    }
    if (!incoming.getExternalId().equals(existing.getIdpGroupId())) {
      throw new ScimExceptions.ScimConflictException("externalId cannot be changed once linked");
    }
  }

  private void rejectDuplicateDisplayName(String displayName, UUID currentGroupId) {
    if (displayName == null || displayName.isBlank()) {
      return;
    }
    groupRepository
        .findByIdpIssuerAndName(issuer, displayName)
        .filter(existing -> currentGroupId == null || !existing.getId().equals(currentGroupId))
        .ifPresent(
            _ -> {
              throw new ScimExceptions.ScimConflictException(
                  "Group with displayName already exists: " + displayName);
            });
  }

  private void rejectDuplicateExternalId(String externalId, UUID currentGroupId) {
    if (externalId == null || externalId.isBlank()) {
      return;
    }
    groupRepository
        .findByIdpIssuerAndIdpGroupId(issuer, externalId)
        .filter(existing -> currentGroupId == null || !existing.getId().equals(currentGroupId))
        .ifPresent(
            _ -> {
              throw new ScimExceptions.ScimConflictException(
                  "Group with externalId already exists: " + externalId);
            });
  }
}
