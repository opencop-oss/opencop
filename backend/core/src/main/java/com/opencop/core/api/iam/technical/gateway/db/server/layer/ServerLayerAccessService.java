// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.iam.technical.gateway.db.server.layer;

import com.opencop.core.api.iam.ports.gateway.mapper.RepositoryMapper;
import com.opencop.core.api.iam.ports.gateway.repository.RepositoryException;
import com.opencop.core.api.iam.ports.gateway.repository.RepositoryExceptionCodes;
import com.opencop.core.api.iam.ports.gateway.repository.ServerLayerRepository;
import com.opencop.core.api.iam.technical.gateway.db.server.layer.entity.ServerLayerAccessEntity;
import com.opencop.core.sdk.business.model.iam.access.SourceLayer;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerLifecycleState;
import com.opencop.core.sdk.technical.gateway.db.iam.ServerRole;
import com.opencop.core.sdk.technical.gateway.db.iam.layer.IamLayerRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.layer.LayerEntity;
import jakarta.transaction.Transactional;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServerLayerAccessService implements ServerLayerRepository {

  private static final Logger log = LoggerFactory.getLogger(ServerLayerAccessService.class);
  private final RepositoryMapper<ServerLayerAccessEntity, SourceLayer> mapper;
  private final IamServerLayerRepository repository;
  private final IamLayerRepository layersRepository;

  public ServerLayerAccessService(
      final IamServerLayerRepository repository, final IamLayerRepository layersRepository) {
    this.mapper = new ServerLayerMapper();
    this.repository = repository;
    this.layersRepository = layersRepository;
  }

  @Override
  public boolean serverExists(UUID serverId) {
    return this.repository.existsByIdServerId(serverId);
  }

  @Override
  @Transactional
  public SourceLayer add(UUID serverId, UUID layerId, ServerRole role) throws RepositoryException {
    var layer = layersRepository.findById(layerId);
    if (layer.isEmpty()) {
      throw new RepositoryException(RepositoryExceptionCodes.DOES_NOT_EXIST);
    }
    var entity = new ServerLayerAccessEntity(serverId, layer.get(), role);
    var saved = repository.save(entity);
    return mapper.toModel(saved);
  }

  @Override
  @Transactional
  public Set<SourceLayer> get(UUID serverId, UUID layerId) {
    List<ServerLayerAccessEntity> serverLayers =
        repository.findAllByServerAndLayer(serverId, layerId);
    return serverLayers.stream().map(mapper::toSanitizedModel).collect(Collectors.toSet());
  }

  @Override
  @Transactional
  public Map<UUID, Set<SourceLayer>> list() {
    List<ServerLayerAccessEntity> layers =
        repository.findAll(
            //                PageRequest.of(page, pageSize, Sort.by("grantedAt").descending())
            );
    Map<UUID, Set<SourceLayer>> map = new HashMap<>();
    layers.forEach(
        entity -> {
          if (map.containsKey(entity.getServerId())) {
            if (map.get(entity.getServerId()).stream()
                .noneMatch(
                    serverLayer ->
                        serverLayer.getLayer().getId().equals(entity.getLayer().getId()))) {
              Set<SourceLayer> set = new HashSet<>(map.get(entity.getServerId()));
              set.add(mapper.toSanitizedModel(entity));
              map.put(entity.getServerId(), set);
            }
          } else {
            map.put(entity.getServerId(), Set.of(mapper.toSanitizedModel(entity)));
          }
        });
    return map;
  }

  @Override
  @Transactional
  public void delete(UUID serverId) {
    repository.deleteAllByServerId(serverId);
  }

  @Override
  @Transactional
  public Set<SourceLayer> getLayers(UUID serverId) {
    List<ServerLayerAccessEntity> serverLayers = repository.findAllByServerId(serverId);
    return serverLayers.stream()
        .filter(access -> isActiveLayer(access.getLayer()))
        .map(mapper::toSanitizedModel)
        .collect(Collectors.toSet());
  }

  @Override
  @Transactional
  public void addLayers(UUID serverId, Set<UUID> layerIds, ServerRole role)
      throws RepositoryException {
    for (UUID layerId : layerIds) {
      var layer = layersRepository.findById(layerId);
      if (layer.isEmpty()) {
        log.warn("Layer id {} does not exist", layerId);
        continue;
      }
      if (!isActiveLayer(layer.get())) {
        log.warn("Layer id {} is not active and cannot be assigned", layerId);
        continue;
      }
      // An upsert, not a delete-then-insert. The pair is unique, so re-granting the role a layer
      // already holds must leave the row alone: deleting it first relies on the insert landing in
      // the same flush, and Hibernate orders inserts before deletes, so the grant was lost
      // whenever this ran inside a caller's transaction.
      var existing = repository.findAllByServerAndLayer(serverId, layerId);
      if (existing.isEmpty()) {
        add(serverId, layerId, role);
      } else {
        existing.forEach(access -> access.setRole(role));
      }
    }
  }

  @Override
  @Transactional
  public void removeLayers(UUID serverId, Set<UUID> layerIds) {
    if (layerIds == null || layerIds.isEmpty()) {
      return;
    }
    repository.deleteMany(serverId, layerIds.stream().toList());
  }

  @Override
  @Transactional
  public Set<SourceLayer> getLayerServers(UUID layerId) {
    var result = repository.findServerIdsByLayer(layerId);
    return result.stream()
        .filter(access -> isActiveLayer(access.getLayer()))
        .map(mapper::toSanitizedModel)
        .collect(Collectors.toSet());
  }

  private static boolean isActiveLayer(LayerEntity layer) {
    return layer.getLifecycleState() == LayerLifecycleState.ACTIVE;
  }
}
