// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.datasourcegroup;

import com.opencop.core.api.general.ports.gateway.datasourcegroup.DataSourceGroupDataSourceService;
import com.opencop.core.api.general.ports.gateway.repository.DataSourceRepository;
import com.opencop.core.api.general.technical.model.datasourcegroup.DataSourceGroupDataSourceAssignRequest;
import com.opencop.core.api.general.technical.model.datasourcegroup.DataSourceGroupDataSourceResponse;
import com.opencop.core.sdk.model.datasource.DataSource;
import com.opencop.core.sdk.model.datasource.DataSourceReadFilter;
import com.opencop.core.sdk.model.visibility.RecordVisibility;
import com.opencop.core.sdk.technical.gateway.db.PersistenceException;
import com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup.DataSourceGroupDataSourceEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup.DataSourceGroupDataSourceIdEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup.DataSourceGroupEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup.IamDataSourceGroupDataSourceRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup.IamDataSourceGroupRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppDataSourceGroupDataSourceService implements DataSourceGroupDataSourceService {

  private final IamDataSourceGroupRepository dataSourceGroupRepo;
  private final IamDataSourceGroupDataSourceRepository dataSourceRepo;
  private final DataSourceRepository coreDataSourceRepo;

  public AppDataSourceGroupDataSourceService(
      IamDataSourceGroupRepository dataSourceGroupRepo,
      IamDataSourceGroupDataSourceRepository dataSourceRepo,
      DataSourceRepository coreDataSourceRepo) {
    this.dataSourceGroupRepo = dataSourceGroupRepo;
    this.dataSourceRepo = dataSourceRepo;
    this.coreDataSourceRepo = coreDataSourceRepo;
  }

  @Override
  @Transactional(readOnly = true)
  public List<DataSourceGroupDataSourceResponse> getDataSources(UUID dataSourceGroupId)
      throws PersistenceException {
    if (!dataSourceGroupRepo.existsById(dataSourceGroupId)) {
      throw dataSourceGroupNotFound(dataSourceGroupId);
    }
    var assignments = dataSourceRepo.findAllByDataSourceGroupId(dataSourceGroupId);
    Map<UUID, DataSource> dataSourcesById = dataSourcesById(assignments);
    return assignments.stream()
        .map(entity -> toResponse(entity, dataSourcesById.get(entity.getDataSourceId())))
        .toList();
  }

  @Override
  @Transactional
  public List<DataSourceGroupDataSourceResponse> assignDataSources(
      UUID dataSourceGroupId, DataSourceGroupDataSourceAssignRequest request)
      throws PersistenceException {
    DataSourceGroupEntity group =
        dataSourceGroupRepo
            .findByIdForUpdate(dataSourceGroupId)
            .orElseThrow(() -> dataSourceGroupNotFound(dataSourceGroupId));

    var sources = new LinkedHashMap<UUID, DataSource>();
    for (UUID dataSourceId : request.dataSourceIds()) {
      DataSource dataSource =
          coreDataSourceRepo
              .findById(dataSourceId)
              .orElseThrow(
                  () -> new PersistenceException("Could not find data source: " + dataSourceId));
      sources.put(dataSourceId, dataSource);
    }

    var responses = new ArrayList<DataSourceGroupDataSourceResponse>();
    for (var entry : sources.entrySet()) {
      var id = new DataSourceGroupDataSourceIdEntity(dataSourceGroupId, entry.getKey());
      var existing = dataSourceRepo.findById(id);

      DataSourceGroupDataSourceEntity entity;
      if (existing.isPresent()) {
        entity = existing.get();
        entity.setRole(request.role());
      } else {
        entity = new DataSourceGroupDataSourceEntity(group, entry.getKey(), request.role());
      }

      responses.add(toResponse(dataSourceRepo.save(entity), entry.getValue()));
    }
    return responses;
  }

  @Override
  @Transactional
  public void removeDataSource(UUID dataSourceGroupId, UUID dataSourceId)
      throws PersistenceException {
    var id = new DataSourceGroupDataSourceIdEntity(dataSourceGroupId, dataSourceId);
    if (!dataSourceRepo.existsById(id)) {
      throw new PersistenceException(
          "Data source "
              + dataSourceId
              + " is not assigned to data source group "
              + dataSourceGroupId);
    }
    dataSourceRepo.deleteByDataSourceGroupIdAndDataSourceId(dataSourceGroupId, dataSourceId);
  }

  private Map<UUID, DataSource> dataSourcesById(List<DataSourceGroupDataSourceEntity> assignments) {
    var ids =
        assignments.stream()
            .map(DataSourceGroupDataSourceEntity::getDataSourceId)
            .collect(Collectors.toSet());
    if (ids.isEmpty()) {
      return Map.of();
    }
    return coreDataSourceRepo
        .findAll(new DataSourceReadFilter(null, ids, RecordVisibility.ALL, null, null, null))
        .stream()
        .collect(Collectors.toMap(DataSource::id, Function.identity()));
  }

  private static DataSourceGroupDataSourceResponse toResponse(
      DataSourceGroupDataSourceEntity entity, DataSource dataSource) {
    return new DataSourceGroupDataSourceResponse(
        entity.getGroup().getId(),
        entity.getGroup().getName(),
        entity.getDataSourceId(),
        dataSource != null ? dataSource.name() : null,
        entity.getRole(),
        entity.getAssignedAt());
  }

  private static PersistenceException dataSourceGroupNotFound(UUID groupId) {
    return new PersistenceException("Could not find data source group: " + groupId);
  }
}
