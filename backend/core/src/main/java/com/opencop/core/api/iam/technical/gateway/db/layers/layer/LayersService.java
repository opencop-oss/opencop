// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.iam.technical.gateway.db.layers.layer;

import com.opencop.core.api.iam.ports.gateway.mapper.RepositoryMapper;
import com.opencop.core.api.iam.ports.gateway.repository.LayersRepository;
import com.opencop.core.api.iam.ports.gateway.repository.RepositoryException;
import com.opencop.core.api.iam.ports.gateway.repository.RepositoryExceptionCodes;
import com.opencop.core.api.iam.technical.gateway.db.layers.member.LayerMemberMapper;
import com.opencop.core.sdk.business.model.iam.access.Layer;
import com.opencop.core.sdk.model.iam.layer.LayerReadFilter;
import com.opencop.core.sdk.model.visibility.RecordVisibility;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerAccessScope;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerLifecycleState;
import com.opencop.core.sdk.technical.gateway.db.iam.layer.IamLayerRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.layer.LayerEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;

public class LayersService implements LayersRepository {

  private static final String ACTIVE_LAYER_NAME_INDEX = "uk_layer_name_active";

  private final RepositoryMapper<LayerEntity, Layer> mapper;
  private final IamLayerRepository repository;
  private final LayerActivityReader activityReader;
  private final EntityManager entityManager;

  public LayersService(
      final IamLayerRepository repository,
      final LayerActivityReader activityReader,
      final EntityManager entityManager) {
    this.entityManager = entityManager;
    this.mapper = new LayerMapper(new LayerMemberMapper());
    this.repository = repository;
    this.activityReader = activityReader;
  }

  /**
   * Stamps activity onto layers on their way out, in one lookup for the whole result.
   *
   * <p>Creation and update responses deliberately skip this: a layer that has just been made has no
   * activity to report, and a null there is the truth rather than a gap.
   */
  private List<Layer> withActivity(List<Layer> layers) {
    if (layers.isEmpty()) {
      return layers;
    }
    var activity = activityReader.findAll(layers.stream().map(Layer::getId).toList());
    layers.forEach(layer -> layer.setLastEventAt(activity.get(layer.getId())));
    return layers;
  }

  @Override
  @Transactional
  public Layer create(String name, LayerAccessScope accessScope, UUID createdBy)
      throws RepositoryException {
    if (hasNameReservedLayerWithName(name)) {
      throw new RepositoryException(RepositoryExceptionCodes.ALREADY_EXIST);
    }
    LayerEntity entity = new LayerEntity();
    entity.setName(name);
    entity.setAccessScope(accessScope == null ? LayerAccessScope.PRIVATE : accessScope);
    entity.setLifecycleState(LayerLifecycleState.ACTIVE);
    entity.setCreatedBy(createdBy);
    // our response includes `createdAt` which will be null unless we flush
    return saveCreatedLayer(entity);
  }

  @Override
  @Transactional
  public Layer createPendingMerge(String name, UUID createdBy) throws RepositoryException {
    if (hasNameReservedLayerWithName(name)) {
      throw new RepositoryException(RepositoryExceptionCodes.ALREADY_EXIST);
    }
    LayerEntity entity = new LayerEntity();
    entity.setName(name);
    entity.setAccessScope(LayerAccessScope.PRIVATE);
    entity.setLifecycleState(LayerLifecycleState.PENDING_MERGE);
    entity.setCreatedBy(createdBy);
    return saveCreatedLayer(entity);
  }

  @Override
  @Transactional
  public Layer get(UUID layerId) throws RepositoryException {
    var entity = repository.findById(layerId);
    if (entity.isEmpty()) {
      throw new RepositoryException(RepositoryExceptionCodes.DOES_NOT_EXIST);
    }
    return withActivity(List.of(mapper.toModel(entity.get()))).getFirst();
  }

  @Override
  @Transactional
  public Layer get(UUID layerId, RecordVisibility recordVisibility) throws RepositoryException {
    var filter = new LayerReadFilter(recordVisibility, null, null, layerId, null);
    var layers = list(filter);
    if (layers.isEmpty()) {
      throw new RepositoryException(RepositoryExceptionCodes.DOES_NOT_EXIST);
    }
    return layers.getFirst();
  }

  @Override
  @Transactional
  public List<Layer> list() {
    return list(new LayerReadFilter(null, null, null, null, null));
  }

  @Override
  @Transactional
  public List<Layer> list(LayerReadFilter filter) {
    return withActivity(
        repository.findAll(LayerSpecifications.from(filter)).stream()
            .map(mapper::toSanitizedModel)
            .toList());
  }

  @Override
  @Transactional(Transactional.TxType.MANDATORY)
  public void lock(UUID layerId) throws RepositoryException {
    if (entityManager.find(LayerEntity.class, layerId, LockModeType.PESSIMISTIC_WRITE) == null) {
      throw new RepositoryException(RepositoryExceptionCodes.DOES_NOT_EXIST);
    }
  }

  @Override
  @Transactional
  public void update(
      UUID layerId,
      String name,
      LayerAccessScope accessScope,
      Boolean defaultVisible,
      UUID updatedBy)
      throws RepositoryException {
    var layerOpt = repository.findById(layerId);
    if (layerOpt.isEmpty()) {
      throw new RepositoryException(RepositoryExceptionCodes.DOES_NOT_EXIST);
    }
    var layer = layerOpt.get();
    if (layer.getLifecycleState() != LayerLifecycleState.ACTIVE) {
      throw new RepositoryException(RepositoryExceptionCodes.DOES_NOT_EXIST);
    }
    var changed = false;
    if (name != null && !name.equals(layer.getName())) {
      if (hasNameReservedLayerWithName(name)) {
        throw new RepositoryException(RepositoryExceptionCodes.NAME_ALREADY_TAKEN);
      }
      layer.setName(name);
      changed = true;
    }
    if (accessScope != null && accessScope != layer.getAccessScope()) {
      layer.setAccessScope(accessScope);
      changed = true;
    }
    if (defaultVisible != null && defaultVisible != layer.isDefaultVisible()) {
      layer.setDefaultVisible(defaultVisible);
      changed = true;
    }
    // A patch that changes nothing must not rewrite attribution or bump the update timestamp.
    if (!changed) {
      return;
    }
    layer.setUpdatedBy(updatedBy);
    repository.save(layer);
  }

  /**
   * Archives conditionally so overlapping callers cannot both succeed. The loser sees the same
   * not-found outcome as a caller that arrived after the archive already happened, which stops it
   * before the eviction and compensation that would otherwise undo the winner's archive.
   */
  @Override
  @Transactional
  public void archive(UUID layerId, UUID archivedBy) throws RepositoryException {
    assertTransitioned(
        repository.archiveIfInState(
            layerId,
            LayerLifecycleState.ACTIVE,
            LayerLifecycleState.ARCHIVED,
            Instant.now(),
            archivedBy));
  }

  @Override
  @Transactional
  public void unarchive(UUID layerId) throws RepositoryException {
    assertTransitioned(
        repository.unarchiveIfInState(
            layerId, LayerLifecycleState.ARCHIVED, LayerLifecycleState.ACTIVE));
  }

  private static void assertTransitioned(int affectedRows) throws RepositoryException {
    if (affectedRows != 1) {
      throw new RepositoryException(RepositoryExceptionCodes.DOES_NOT_EXIST);
    }
  }

  @Override
  @Transactional
  public void lockMergeSources(List<UUID> layerIds, UUID lockedBy) throws RepositoryException {
    var layers = repository.findAllById(layerIds);
    if (layers.size() != layerIds.size()
        || layers.stream()
            .anyMatch(layer -> layer.getLifecycleState() != LayerLifecycleState.ACTIVE)) {
      throw new RepositoryException(RepositoryExceptionCodes.DOES_NOT_EXIST);
    }
    layers.forEach(
        layer -> {
          layer.setLifecycleState(LayerLifecycleState.MERGE_SOURCE_LOCKED);
          layer.setUpdatedBy(lockedBy);
        });
    repository.saveAll(layers);
  }

  @Override
  @Transactional
  public void finalizeMergeSuccess(UUID targetLayerId, List<UUID> sourceLayerIds, UUID archivedBy)
      throws RepositoryException {
    var target = getMergeTarget(targetLayerId);
    var sources = getMergeSources(sourceLayerIds);

    target.setLifecycleState(LayerLifecycleState.ACTIVE);
    target.setUpdatedBy(archivedBy);
    var archivedAt = Instant.now();
    sources.forEach(
        source -> {
          source.setArchivedAt(archivedAt);
          source.setArchivedBy(archivedBy);
          source.setLifecycleState(LayerLifecycleState.ARCHIVED);
          source.setUpdatedBy(archivedBy);
        });
    repository.save(target);
    repository.saveAll(sources);
  }

  @Override
  @Transactional
  public void finalizeMergeFailure(UUID targetLayerId, List<UUID> sourceLayerIds)
      throws RepositoryException {
    var target = getMergeTarget(targetLayerId);
    var sources = getMergeSources(sourceLayerIds);

    // Compensation after a failed merge is the system undoing its own work, so it clears the
    // editor rather than crediting the user who asked for the merge that did not happen.
    target.setLifecycleState(LayerLifecycleState.MERGE_FAILED);
    target.setUpdatedBy(null);
    sources.forEach(
        source -> {
          source.setLifecycleState(LayerLifecycleState.ACTIVE);
          source.setUpdatedBy(null);
        });
    repository.save(target);
    repository.saveAll(sources);
  }

  private boolean hasNameReservedLayerWithName(String name) {
    return repository.count(LayerSpecifications.reservedName(name)) > 0;
  }

  private Layer saveCreatedLayer(LayerEntity entity) throws RepositoryException {
    try {
      return mapper.toModel(repository.saveAndFlush(entity));
    } catch (DataIntegrityViolationException e) {
      if (isActiveLayerNameViolation(e)) {
        throw new RepositoryException(RepositoryExceptionCodes.ALREADY_EXIST);
      }
      throw e;
    }
  }

  private static boolean isActiveLayerNameViolation(DataIntegrityViolationException e) {
    var message = e.getMostSpecificCause().getMessage();
    return message != null && message.contains(ACTIVE_LAYER_NAME_INDEX);
  }

  private LayerEntity getMergeTarget(UUID targetLayerId) throws RepositoryException {
    var target = repository.findById(targetLayerId);
    if (target.isEmpty() || target.get().getLifecycleState() != LayerLifecycleState.PENDING_MERGE) {
      throw new RepositoryException(RepositoryExceptionCodes.DOES_NOT_EXIST);
    }
    return target.get();
  }

  private List<LayerEntity> getMergeSources(List<UUID> sourceLayerIds) throws RepositoryException {
    var sources = repository.findAllById(sourceLayerIds);
    if (sources.size() != sourceLayerIds.size()
        || sources.stream()
            .anyMatch(
                source -> source.getLifecycleState() != LayerLifecycleState.MERGE_SOURCE_LOCKED)) {
      throw new RepositoryException(RepositoryExceptionCodes.DOES_NOT_EXIST);
    }
    return sources;
  }
}
