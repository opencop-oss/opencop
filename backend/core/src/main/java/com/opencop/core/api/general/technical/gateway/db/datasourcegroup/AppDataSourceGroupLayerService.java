// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.datasourcegroup;

import com.opencop.core.api.general.ports.gateway.datasourcegroup.DataSourceGroupLayerService;
import com.opencop.core.api.general.technical.model.datasourcegroup.DataSourceGroupLayerResponse;
import com.opencop.core.sdk.technical.gateway.db.PersistenceException;
import com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup.DataSourceGroupEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup.IamDataSourceGroupRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup.IamLayerDataSourceGroupRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup.LayerDataSourceGroupEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup.LayerDataSourceGroupIdEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.layer.IamLayerRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.layer.LayerEntity;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppDataSourceGroupLayerService implements DataSourceGroupLayerService {

  private final IamLayerRepository layerRepo;
  private final IamDataSourceGroupRepository dataSourceGroupRepo;
  private final IamLayerDataSourceGroupRepository layerDataSourceGroupRepo;

  public AppDataSourceGroupLayerService(
      IamLayerRepository layerRepo,
      IamDataSourceGroupRepository dataSourceGroupRepo,
      IamLayerDataSourceGroupRepository layerDataSourceGroupRepo) {
    this.layerRepo = layerRepo;
    this.dataSourceGroupRepo = dataSourceGroupRepo;
    this.layerDataSourceGroupRepo = layerDataSourceGroupRepo;
  }

  @Override
  @Transactional(readOnly = true)
  public List<DataSourceGroupLayerResponse> getDataSourceGroupsForLayer(UUID layerId)
      throws PersistenceException {
    if (!layerRepo.existsById(layerId)) {
      throw new PersistenceException("Could not find layer: " + layerId);
    }
    return layerDataSourceGroupRepo.findAllByLayerId(layerId).stream()
        .map(AppDataSourceGroupLayerService::toResponse)
        .toList();
  }

  @Override
  @Transactional
  public List<DataSourceGroupLayerResponse> assignDataSourceGroupsToLayer(
      UUID layerId, Set<UUID> dataSourceGroupIds) throws PersistenceException {
    LayerEntity layer =
        layerRepo
            .findById(layerId)
            .orElseThrow(() -> new PersistenceException("Could not find layer: " + layerId));

    var groups = new LinkedHashMap<UUID, DataSourceGroupEntity>();
    for (UUID dataSourceGroupId : dataSourceGroupIds) {
      DataSourceGroupEntity group =
          dataSourceGroupRepo
              .findById(dataSourceGroupId)
              .orElseThrow(
                  () ->
                      new PersistenceException(
                          "Could not find data source group: " + dataSourceGroupId));
      groups.put(dataSourceGroupId, group);
    }

    var responses = new ArrayList<DataSourceGroupLayerResponse>();
    for (var entry : groups.entrySet()) {
      var id = new LayerDataSourceGroupIdEntity(layerId, entry.getKey());
      var entity =
          layerDataSourceGroupRepo
              .findById(id)
              .orElseGet(() -> new LayerDataSourceGroupEntity(layer, entry.getValue()));
      responses.add(toResponse(layerDataSourceGroupRepo.save(entity)));
    }
    return responses;
  }

  @Override
  @Transactional
  public void removeDataSourceGroupFromLayer(UUID layerId, UUID dataSourceGroupId)
      throws PersistenceException {
    var id = new LayerDataSourceGroupIdEntity(layerId, dataSourceGroupId);
    if (!layerDataSourceGroupRepo.existsById(id)) {
      throw new PersistenceException(
          "Data source group " + dataSourceGroupId + " is not embedded in layer " + layerId);
    }
    layerDataSourceGroupRepo.deleteByLayerIdAndDataSourceGroupId(layerId, dataSourceGroupId);
  }

  @Override
  @Transactional(readOnly = true)
  public List<DataSourceGroupLayerResponse> getLayersForDataSourceGroup(UUID dataSourceGroupId)
      throws PersistenceException {
    if (!dataSourceGroupRepo.existsById(dataSourceGroupId)) {
      throw new PersistenceException("Could not find data source group: " + dataSourceGroupId);
    }
    return layerDataSourceGroupRepo.findAllByDataSourceGroupId(dataSourceGroupId).stream()
        .map(AppDataSourceGroupLayerService::toResponse)
        .toList();
  }

  private static DataSourceGroupLayerResponse toResponse(LayerDataSourceGroupEntity entity) {
    return new DataSourceGroupLayerResponse(
        entity.getDataSourceGroup().getId(),
        entity.getDataSourceGroup().getName(),
        entity.getLayer().getId(),
        entity.getLayer().getName(),
        entity.getAssignedAt());
  }
}
