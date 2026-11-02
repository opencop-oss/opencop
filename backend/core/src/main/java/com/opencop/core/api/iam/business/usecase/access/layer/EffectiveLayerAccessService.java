// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.iam.business.usecase.access.layer;

import com.opencop.core.api.iam.ports.gateway.repository.LayerMemberRepository;
import com.opencop.core.api.iam.ports.gateway.repository.LayersRepository;
import com.opencop.core.api.iam.ports.gateway.repository.RepositoryException;
import com.opencop.core.sdk.business.model.iam.access.Layer;
import com.opencop.core.sdk.business.model.iam.access.LayerMember;
import com.opencop.core.sdk.model.iam.layer.LayerReadFilter;
import com.opencop.core.sdk.model.iam.source.LayerInfo;
import com.opencop.core.sdk.model.visibility.RecordVisibility;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerAccessScope;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerLifecycleState;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerRole;
import com.opencop.core.sdk.technical.gateway.db.iam.group.IamGroupLayerMemberRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.group.IamGroupMembersRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.layer.LayerEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.IamLayerGroupLayerRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.IamLayerGroupMembersRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.IamLayerGroupRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.IamLayerGroupUserGroupRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.LayerGroupAccessScope;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.LayerGroupEntity;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

public class EffectiveLayerAccessService {

  private final LayersRepository layersRepository;
  private final LayerMemberRepository layerMemberRepository;
  private final IamGroupMembersRepository groupMembersRepository;
  private final IamGroupLayerMemberRepository groupLayerRepository;
  private final IamLayerGroupMembersRepository layerGroupMembersRepository;
  private final IamLayerGroupLayerRepository layerGroupLayerRepository;
  private final IamLayerGroupUserGroupRepository layerGroupUserGroupRepository;
  private final IamLayerGroupRepository iamLayerGroupRepository;

  public EffectiveLayerAccessService(
      LayersRepository layersRepository,
      LayerMemberRepository layerMemberRepository,
      IamGroupMembersRepository groupMembersRepository,
      IamGroupLayerMemberRepository groupLayerRepository,
      IamLayerGroupMembersRepository layerGroupMembersRepository,
      IamLayerGroupLayerRepository layerGroupLayerRepository,
      IamLayerGroupUserGroupRepository layerGroupUserGroupRepository,
      IamLayerGroupRepository iamLayerGroupRepository) {
    this.layersRepository = layersRepository;
    this.layerMemberRepository = layerMemberRepository;
    this.groupMembersRepository = groupMembersRepository;
    this.groupLayerRepository = groupLayerRepository;
    this.layerGroupMembersRepository = layerGroupMembersRepository;
    this.layerGroupLayerRepository = layerGroupLayerRepository;
    this.layerGroupUserGroupRepository = layerGroupUserGroupRepository;
    this.iamLayerGroupRepository = iamLayerGroupRepository;
  }

  public Optional<LayerRole> getEffectiveRole(UUID layerId, UUID userId) {
    Layer layer;
    try {
      layer = layersRepository.get(layerId, RecordVisibility.ALL);
    } catch (RepositoryException e) {
      return Optional.empty();
    }

    LayerRole highestRole = null;

    // Direct membership
    var explicitMembership = layerMemberRepository.findMember(layerId, userId);
    if (explicitMembership.isPresent()) {
      highestRole = explicitMembership.get().getRole();
    }

    // Group-based membership
    Set<UUID> groupIds = groupMembersRepository.findGroupIdsByUserId(userId);
    if (!groupIds.isEmpty()) {
      var groupAssignments = groupLayerRepository.findAllByGroupIdsAndLayerId(groupIds, layerId);
      for (var assignment : groupAssignments) {
        if (highestRole == null || assignment.getRole().isHigherThan(highestRole)) {
          highestRole = assignment.getRole();
        }
      }
    }

    // Layer-group-based membership (direct user → layer group)
    var layerGroupIds = layerGroupMembersRepository.findLayerGroupIdsByUserId(userId);
    if (!layerGroupIds.isEmpty()) {
      var lgAssignments =
          layerGroupLayerRepository.findAllByLayerGroupIdsAndLayerId(layerGroupIds, layerId);
      for (var assignment : lgAssignments) {
        if (highestRole == null || assignment.getRole().isHigherThan(highestRole)) {
          highestRole = assignment.getRole();
        }
      }
    }

    // User-group → layer-group → layer path
    if (!groupIds.isEmpty()) {
      var transitiveLayerGroupIds =
          layerGroupUserGroupRepository.findLayerGroupIdsByUserGroupIdIn(groupIds);
      if (!transitiveLayerGroupIds.isEmpty()) {
        var ugLgAssignments =
            layerGroupLayerRepository.findAllByLayerGroupIdsAndLayerId(
                transitiveLayerGroupIds, layerId);
        for (var assignment : ugLgAssignments) {
          if (highestRole == null || assignment.getRole().isHigherThan(highestRole)) {
            highestRole = assignment.getRole();
          }
        }
      }
    }

    // GLOBAL layer group path
    var globalGroups = iamLayerGroupRepository.findAllByAccessScope(LayerGroupAccessScope.GLOBAL);
    if (!globalGroups.isEmpty()) {
      Set<UUID> globalGroupIds =
          globalGroups.stream().map(LayerGroupEntity::getId).collect(Collectors.toSet());
      var assignments =
          layerGroupLayerRepository.findAllByLayerGroupIdsAndLayerId(globalGroupIds, layerId);
      for (var assignment : assignments) {
        LayerRole role = assignment.getRole();
        if (highestRole == null || role.isHigherThan(highestRole)) {
          highestRole = role;
        }
      }
    }

    if (highestRole != null) {
      return Optional.of(highestRole);
    }

    return layer.getAccessScope() == LayerAccessScope.GLOBAL
        ? Optional.of(LayerRole.MEMBER)
        : Optional.empty();
  }

  public List<LayerInfo> getEffectiveLayers(UUID userId) {
    return getEffectiveLayers(userId, RecordVisibility.ACTIVE);
  }

  public List<LayerInfo> getEffectiveLayers(UUID userId, RecordVisibility recordVisibility) {
    return getEffectiveLayersById(userId, recordVisibility).values().stream()
        .sorted(Comparator.comparing(LayerInfo::createdAt))
        .toList();
  }

  private Map<UUID, LayerInfo> getEffectiveLayersById(
      UUID userId, RecordVisibility recordVisibility) {
    Map<UUID, LayerInfo> map = new HashMap<>();
    applyGlobalLayers(map, recordVisibility);
    applyDirectMemberships(map, userId, recordVisibility);
    Set<UUID> groupIds = groupMembersRepository.findGroupIdsByUserId(userId);
    applyGroupAccess(map, groupIds, recordVisibility);
    applyLayerGroupAccess(map, userId, recordVisibility);
    applyUserGroupLayerGroupAccess(map, groupIds, recordVisibility);
    applyGlobalLayerGroupAccess(map, recordVisibility);
    return map;
  }

  private void applyGlobalLayers(Map<UUID, LayerInfo> map, RecordVisibility recordVisibility) {
    var filter = new LayerReadFilter(recordVisibility, LayerAccessScope.GLOBAL, null, null, null);
    for (Layer layer : layersRepository.list(filter)) {
      mergeIfHigher(map, toLayerInfo(layer, LayerRole.MEMBER));
    }
  }

  private void applyDirectMemberships(
      Map<UUID, LayerInfo> map, UUID userId, RecordVisibility recordVisibility) {
    List<LayerMember> memberships = layerMemberRepository.getLayersByUserId(userId);
    if (memberships.isEmpty()) {
      return;
    }

    Set<UUID> layerIds =
        memberships.stream().map(LayerMember::getLayerId).collect(Collectors.toSet());
    var filter = new LayerReadFilter(recordVisibility, null, null, null, layerIds);
    Map<UUID, Layer> layersById =
        layersRepository.list(filter).stream()
            .collect(Collectors.toMap(Layer::getId, Function.identity()));

    for (LayerMember membership : memberships) {
      Layer layer = layersById.get(membership.getLayerId());
      if (layer == null) {
        continue;
      }
      mergeIfHigher(map, toLayerInfo(layer, membership.getRole()));
    }
  }

  private void applyGroupAccess(
      Map<UUID, LayerInfo> map, Set<UUID> groupIds, RecordVisibility recordVisibility) {
    if (groupIds.isEmpty()) {
      return;
    }

    for (var assignment : groupLayerRepository.findAllByGroupIdIn(groupIds)) {
      if (!matchesVisibility(assignment.getLayer(), recordVisibility)) {
        continue;
      }
      mergeIfHigher(map, toLayerInfo(assignment.getLayer(), assignment.getRole()));
    }
  }

  private void applyUserGroupLayerGroupAccess(
      Map<UUID, LayerInfo> map, Set<UUID> groupIds, RecordVisibility recordVisibility) {
    if (groupIds.isEmpty()) {
      return;
    }
    Set<UUID> transitiveLayerGroupIds =
        layerGroupUserGroupRepository.findLayerGroupIdsByUserGroupIdIn(groupIds);
    if (transitiveLayerGroupIds.isEmpty()) {
      return;
    }
    for (var assignment :
        layerGroupLayerRepository.findAllByLayerGroupIdIn(transitiveLayerGroupIds)) {
      if (!matchesVisibility(assignment.getLayer(), recordVisibility)) {
        continue;
      }
      mergeIfHigher(map, toLayerInfo(assignment.getLayer(), assignment.getRole()));
    }
  }

  private void applyGlobalLayerGroupAccess(
      Map<UUID, LayerInfo> map, RecordVisibility recordVisibility) {
    var globalGroups = iamLayerGroupRepository.findAllByAccessScope(LayerGroupAccessScope.GLOBAL);
    if (globalGroups.isEmpty()) {
      return;
    }
    Set<UUID> globalGroupIds =
        globalGroups.stream().map(g -> g.getId()).collect(Collectors.toSet());
    for (var assignment : layerGroupLayerRepository.findAllByLayerGroupIdIn(globalGroupIds)) {
      if (!matchesVisibility(assignment.getLayer(), recordVisibility)) {
        continue;
      }
      mergeIfHigher(map, toLayerInfo(assignment.getLayer(), assignment.getRole()));
    }
  }

  private void applyLayerGroupAccess(
      Map<UUID, LayerInfo> map, UUID userId, RecordVisibility recordVisibility) {
    Set<UUID> layerGroupIds = layerGroupMembersRepository.findLayerGroupIdsByUserId(userId);
    if (layerGroupIds.isEmpty()) {
      return;
    }
    for (var assignment : layerGroupLayerRepository.findAllByLayerGroupIdIn(layerGroupIds)) {
      if (!matchesVisibility(assignment.getLayer(), recordVisibility)) {
        continue;
      }
      mergeIfHigher(map, toLayerInfo(assignment.getLayer(), assignment.getRole()));
    }
  }

  private static boolean matchesVisibility(LayerEntity layer, RecordVisibility recordVisibility) {
    return switch (recordVisibility) {
      case ACTIVE -> layer.getLifecycleState() == LayerLifecycleState.ACTIVE;
      case ARCHIVED -> layer.getLifecycleState() == LayerLifecycleState.ARCHIVED;
      case ALL -> true;
    };
  }

  private void mergeIfHigher(Map<UUID, LayerInfo> map, LayerInfo candidate) {
    LayerInfo existing = map.get(candidate.layerId());
    if (existing == null || existing.role().isLowerThan(candidate.role())) {
      map.put(candidate.layerId(), candidate);
    }
  }

  private static LayerInfo toLayerInfo(Layer layer, LayerRole role) {
    return new LayerInfo(
        layer.getId(),
        layer.getName(),
        role,
        layer.getAccessScope(),
        layer.getCreatedAt(),
        layer.isDefaultVisible());
  }

  private static LayerInfo toLayerInfo(LayerEntity layer, LayerRole role) {
    return new LayerInfo(
        layer.getId(),
        layer.getName(),
        role,
        layer.getAccessScope(),
        layer.getCreatedAt(),
        layer.isDefaultVisible());
  }
}
