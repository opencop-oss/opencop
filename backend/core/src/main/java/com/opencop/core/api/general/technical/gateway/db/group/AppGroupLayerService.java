// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.group;

import com.opencop.core.api.general.technical.model.group.GroupLayerAssignRequest;
import com.opencop.core.api.general.technical.model.group.GroupLayerResponse;
import com.opencop.core.sdk.model.iam.group.GroupManagementSource;
import com.opencop.core.sdk.technical.gateway.db.PersistenceException;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerRole;
import com.opencop.core.sdk.technical.gateway.db.iam.group.GroupLayerMemberEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.group.GroupLayerMemberIdEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.group.IamGroupLayerMemberRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.group.IamGroupRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.layer.IamLayerRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppGroupLayerService {

  private final IamGroupRepository groupRepo;
  private final IamLayerRepository layerRepo;
  private final IamGroupLayerMemberRepository groupLayerRepo;

  public AppGroupLayerService(
      IamGroupRepository groupRepo,
      IamLayerRepository layerRepo,
      IamGroupLayerMemberRepository groupLayerRepo) {
    this.groupRepo = groupRepo;
    this.layerRepo = layerRepo;
    this.groupLayerRepo = groupLayerRepo;
  }

  @Transactional(readOnly = true)
  public List<GroupLayerResponse> getLayersForGroup(UUID groupId) throws PersistenceException {
    if (!groupRepo.existsById(groupId)) {
      throw new PersistenceException("Could not find group: " + groupId);
    }
    return groupLayerRepo.findAllByGroupId(groupId).stream()
        .map(
            m ->
                new GroupLayerResponse(
                    m.getGroup().getId(),
                    m.getGroup().getName(),
                    GroupManagementSource.ofIdpIssuer(m.getGroup().getIdpIssuer()),
                    m.getLayer().getId(),
                    m.getLayer().getName(),
                    m.getRole(),
                    m.getAssignedAt()))
        .toList();
  }

  @Transactional(readOnly = true)
  public List<GroupLayerResponse> getGroupsForLayer(UUID layerId) throws PersistenceException {
    if (!layerRepo.existsById(layerId)) {
      throw new PersistenceException("Could not find layer: " + layerId);
    }
    return groupLayerRepo.findAllByLayerId(layerId).stream()
        .map(
            m ->
                new GroupLayerResponse(
                    m.getGroup().getId(),
                    m.getGroup().getName(),
                    GroupManagementSource.ofIdpIssuer(m.getGroup().getIdpIssuer()),
                    m.getLayer().getId(),
                    m.getLayer().getName(),
                    m.getRole(),
                    m.getAssignedAt()))
        .toList();
  }

  @Transactional
  public GroupLayerResponse assignGroupToLayer(UUID groupId, GroupLayerAssignRequest request)
      throws PersistenceException {
    var group =
        groupRepo
            .findById(groupId)
            .orElseThrow(() -> new PersistenceException("Could not find group: " + groupId));
    var layer =
        layerRepo
            .findById(request.layerId())
            .orElseThrow(
                () -> new PersistenceException("Could not find layer: " + request.layerId()));

    LayerRole role = request.role() != null ? request.role() : LayerRole.MEMBER;
    var id = new GroupLayerMemberIdEntity(groupId, request.layerId());
    var existing = groupLayerRepo.findById(id);

    GroupLayerMemberEntity entity;
    if (existing.isPresent()) {
      entity = existing.get();
      entity.setRole(role);
    } else {
      entity = new GroupLayerMemberEntity(group, layer, role);
    }

    var saved = groupLayerRepo.save(entity);
    return new GroupLayerResponse(
        saved.getGroup().getId(),
        saved.getGroup().getName(),
        GroupManagementSource.ofIdpIssuer(saved.getGroup().getIdpIssuer()),
        saved.getLayer().getId(),
        saved.getLayer().getName(),
        saved.getRole(),
        saved.getAssignedAt());
  }

  @Transactional
  public void removeGroupFromLayer(UUID groupId, UUID layerId) throws PersistenceException {
    var id = new GroupLayerMemberIdEntity(groupId, layerId);
    if (!groupLayerRepo.existsById(id)) {
      throw new PersistenceException("Group " + groupId + " is not assigned to layer " + layerId);
    }
    groupLayerRepo.deleteByGroupIdAndLayerId(groupId, layerId);
  }
}
