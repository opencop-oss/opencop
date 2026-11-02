// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.layergroup;

import com.opencop.core.api.general.ports.gateway.layergroup.LayerGroupLayerService;
import com.opencop.core.api.general.technical.model.layergroup.LayerGroupLayerResponse;
import com.opencop.core.sdk.technical.gateway.db.PersistenceException;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerRole;
import com.opencop.core.sdk.technical.gateway.db.iam.layer.IamLayerRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.layer.LayerEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.IamLayerGroupLayerRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.IamLayerGroupRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.LayerGroupLayerEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.LayerGroupLayerIdEntity;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppLayerGroupLayerService implements LayerGroupLayerService {

  private final IamLayerGroupRepository layerGroupRepo;
  private final IamLayerRepository layerRepo;
  private final IamLayerGroupLayerRepository layerGroupLayerRepo;

  public AppLayerGroupLayerService(
      IamLayerGroupRepository layerGroupRepo,
      IamLayerRepository layerRepo,
      IamLayerGroupLayerRepository layerGroupLayerRepo) {
    this.layerGroupRepo = layerGroupRepo;
    this.layerRepo = layerRepo;
    this.layerGroupLayerRepo = layerGroupLayerRepo;
  }

  @Override
  @Transactional(readOnly = true)
  public List<LayerGroupLayerResponse> getLayersForLayerGroup(UUID layerGroupId)
      throws PersistenceException {
    if (!layerGroupRepo.existsById(layerGroupId)) {
      throw new PersistenceException("Could not find layer group: " + layerGroupId);
    }
    return layerGroupLayerRepo.findAllByLayerGroupId(layerGroupId).stream()
        .map(AppLayerGroupLayerService::toResponse)
        .toList();
  }

  /**
   * The reverse of {@link #getLayersForLayerGroup}: every layer-group assignment for one layer.
   *
   * <p>Unlike the forward direction this does not assert the subject exists. A layer in no groups
   * is an empty list, not a 404 — the caller has already authorized against the layer itself.
   */
  @Transactional(readOnly = true)
  public List<LayerGroupLayerResponse> getLayerGroupsForLayer(UUID layerId) {
    return layerGroupLayerRepo.findAllByLayerId(layerId).stream()
        .map(AppLayerGroupLayerService::toResponse)
        .toList();
  }

  @Override
  @Transactional
  public List<LayerGroupLayerResponse> assignLayersToLayerGroup(
      UUID layerGroupId, Set<UUID> layerIds, LayerRole role) throws PersistenceException {
    // Lock the group row so concurrent batch assignments to the same group serialize,
    // mirroring AppLayerGroupService.patchMembers.
    var group =
        layerGroupRepo
            .findByIdForUpdate(layerGroupId)
            .orElseThrow(
                () -> new PersistenceException("Could not find layer group: " + layerGroupId));

    // Resolve every layer before writing anything. PersistenceException is checked, so it does
    // not trigger a transaction rollback; a missing layer discovered mid-write would leave
    // earlier rows committed. Failing up front keeps the batch all-or-nothing.
    var layers = new LinkedHashMap<UUID, LayerEntity>();
    for (UUID layerId : layerIds) {
      var layer =
          layerRepo
              .findById(layerId)
              .orElseThrow(() -> new PersistenceException("Could not find layer: " + layerId));
      layers.put(layerId, layer);
    }

    var responses = new ArrayList<LayerGroupLayerResponse>();
    for (var entry : layers.entrySet()) {
      var id = new LayerGroupLayerIdEntity(layerGroupId, entry.getKey());
      var existing = layerGroupLayerRepo.findById(id);

      LayerGroupLayerEntity entity;
      if (existing.isPresent()) {
        entity = existing.get();
        entity.setRole(role);
      } else {
        entity = new LayerGroupLayerEntity(group, entry.getValue(), role);
      }

      responses.add(toResponse(layerGroupLayerRepo.save(entity)));
    }
    return responses;
  }

  @Override
  @Transactional
  public void removeLayerFromLayerGroup(UUID layerGroupId, UUID layerId)
      throws PersistenceException {
    var id = new LayerGroupLayerIdEntity(layerGroupId, layerId);
    if (!layerGroupLayerRepo.existsById(id)) {
      throw new PersistenceException(
          "Layer " + layerId + " is not assigned to layer group " + layerGroupId);
    }
    layerGroupLayerRepo.deleteByLayerGroupIdAndLayerId(layerGroupId, layerId);
  }

  @Override
  @Transactional(readOnly = true)
  public long countNonGlobalLayers(UUID layerGroupId) {
    return layerGroupLayerRepo.countNonGlobalLayers(layerGroupId);
  }

  private static LayerGroupLayerResponse toResponse(LayerGroupLayerEntity entity) {
    return new LayerGroupLayerResponse(
        entity.getGroup().getId(),
        entity.getGroup().getName(),
        entity.getLayer().getId(),
        entity.getLayer().getName(),
        entity.getRole(),
        entity.getAssignedAt());
  }
}
