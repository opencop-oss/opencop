// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.watchbox.technical.gateway.db;

import com.opencop.core.api.watchbox.business.model.WatchboxConfig;
import com.opencop.core.api.watchbox.business.model.WatchboxSourceStatusRecord;
import com.opencop.core.api.watchbox.ports.gateway.WatchboxConfigRepository;
import com.opencop.core.sdk.model.watchbox.WatchboxFilter;
import com.opencop.core.sdk.technical.gateway.db.watchbox.WatchboxEntity;
import com.opencop.core.sdk.technical.gateway.db.watchbox.WatchboxJpaRepository;
import com.opencop.core.sdk.technical.gateway.db.watchbox.WatchboxLayerEntity;
import com.opencop.core.sdk.technical.gateway.db.watchbox.WatchboxLayerIdEntity;
import com.opencop.core.sdk.technical.gateway.db.watchbox.WatchboxLayerJpaRepository;
import com.opencop.core.sdk.technical.gateway.db.watchbox.WatchboxSourceStatusEntity;
import com.opencop.core.sdk.technical.gateway.db.watchbox.WatchboxSourceStatusJpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.geojson.Polygon;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Repository
public class JpaWatchboxConfigRepository implements WatchboxConfigRepository {

  private final WatchboxJpaRepository watchboxes;
  private final WatchboxLayerJpaRepository layers;
  private final WatchboxSourceStatusJpaRepository sourceStatuses;
  private final ObjectMapper objectMapper;

  public JpaWatchboxConfigRepository(
      WatchboxJpaRepository watchboxes,
      WatchboxLayerJpaRepository layers,
      WatchboxSourceStatusJpaRepository sourceStatuses,
      ObjectMapper objectMapper) {
    this.watchboxes = watchboxes;
    this.layers = layers;
    this.sourceStatuses = sourceStatuses;
    this.objectMapper = objectMapper;
  }

  @Override
  @Transactional
  public WatchboxConfig create(WatchboxConfig config, UUID createdBy) {
    var entity = toEntity(config);
    entity.setCreatedBy(createdBy);
    var saved = watchboxes.saveAndFlush(entity);
    replaceLayers(saved.getId(), config.layerIds());
    return toConfig(saved, config.layerIds());
  }

  @Override
  @Transactional
  public WatchboxConfig update(WatchboxConfig config) {
    var entity =
        watchboxes
            .findByIdAndArchivedAtIsNull(config.id())
            .orElseThrow(() -> new IllegalArgumentException("Watchbox not found"));
    entity.setName(config.name());
    entity.setDescription(config.description());
    entity.setEnabled(config.enabled());
    entity.setDirection(config.direction());
    entity.setPolygonJson(writeJson(config.polygon()));
    // WatchboxFilter JSON is implicit version 1; absence of a top-level version field means v1.
    entity.setFilterJson(writeJson(config.filter()));
    entity.setConfigEpoch(config.configEpoch());
    var saved = watchboxes.saveAndFlush(entity);
    replaceLayers(saved.getId(), config.layerIds());
    return toConfig(saved, config.layerIds());
  }

  @Override
  @Transactional
  public void archive(UUID watchboxId, UUID archivedBy) {
    watchboxes
        .findByIdAndArchivedAtIsNull(watchboxId)
        .ifPresent(
            entity -> {
              entity.setArchivedAt(Instant.now());
              entity.setArchivedBy(archivedBy);
              watchboxes.save(entity);
            });
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<WatchboxConfig> findActive(UUID watchboxId) {
    return watchboxes.findByIdAndArchivedAtIsNull(watchboxId).map(this::toConfig);
  }

  // Not readOnly: FOR UPDATE is a write-intent statement PostgreSQL rejects in a
  // read-only transaction. In practice this joins the patch path's read-write transaction.
  @Override
  @Transactional
  public Optional<WatchboxConfig> findActiveForUpdate(UUID watchboxId) {
    return watchboxes.findWithLockByIdAndArchivedAtIsNull(watchboxId).map(this::toConfig);
  }

  @Override
  @Transactional(readOnly = true)
  public List<WatchboxConfig> listActive() {
    List<WatchboxEntity> activeWatchboxes =
        watchboxes.findAllByArchivedAtIsNullOrderByCreatedAtDesc();
    if (activeWatchboxes.isEmpty()) {
      return List.of();
    }
    Set<UUID> watchboxIds =
        activeWatchboxes.stream().map(WatchboxEntity::getId).collect(Collectors.toSet());
    Map<UUID, Set<UUID>> layerIdsByWatchboxId =
        layers.findAllByIdWatchboxIdIn(watchboxIds).stream()
            .collect(
                Collectors.groupingBy(
                    layer -> layer.getId().getWatchboxId(),
                    Collectors.mapping(layer -> layer.getId().getLayerId(), Collectors.toSet())));
    return activeWatchboxes.stream()
        .map(
            entity -> toConfig(entity, layerIdsByWatchboxId.getOrDefault(entity.getId(), Set.of())))
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public List<WatchboxSourceStatusRecord> findSourceStatuses(UUID watchboxId, long configEpoch) {
    return sourceStatuses.findAllByIdWatchboxIdAndIdConfigEpoch(watchboxId, configEpoch).stream()
        .map(this::toSourceStatus)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public Map<UUID, List<WatchboxSourceStatusRecord>> findSourceStatusesForWatchboxes(
      Map<UUID, Long> configEpochByWatchboxId) {
    if (configEpochByWatchboxId.isEmpty()) {
      return Map.of();
    }
    // Fetching by watchbox id and filtering the epoch in memory keeps this a derived query.
    // Superseded-epoch rows are retained by the Processor's status GC for its forensic window
    // (default 72h) before deletion, so the over-fetch is bounded by that window's superseded
    // churn-tripped rows - small in practice because the churn upsert is the table's only
    // production writer. A (watchbox_id, config_epoch) tuple-IN query is the tightening if the
    // over-fetch ever matters.
    return sourceStatuses.findAllByIdWatchboxIdIn(configEpochByWatchboxId.keySet()).stream()
        .filter(
            entity ->
                Objects.equals(
                    configEpochByWatchboxId.get(entity.getId().getWatchboxId()),
                    entity.getId().getConfigEpoch()))
        .collect(
            Collectors.groupingBy(
                entity -> entity.getId().getWatchboxId(),
                Collectors.mapping(this::toSourceStatus, Collectors.toList())));
  }

  private void replaceLayers(UUID watchboxId, Set<UUID> layerIds) {
    var existing = layers.findAllByIdWatchboxId(watchboxId);
    var existingLayerIds =
        existing.stream().map(layer -> layer.getId().getLayerId()).collect(Collectors.toSet());

    var removed =
        existing.stream().filter(layer -> !layerIds.contains(layer.getId().getLayerId())).toList();
    if (!removed.isEmpty()) {
      layers.deleteAll(removed);
      layers.flush();
    }

    layers.saveAll(
        layerIds.stream()
            .filter(layerId -> !existingLayerIds.contains(layerId))
            .map(layerId -> new WatchboxLayerEntity(new WatchboxLayerIdEntity(watchboxId, layerId)))
            .toList());
  }

  private WatchboxEntity toEntity(WatchboxConfig config) {
    var entity = new WatchboxEntity();
    entity.setName(config.name());
    entity.setDescription(config.description());
    entity.setEnabled(config.enabled());
    entity.setDirection(config.direction());
    entity.setPolygonJson(writeJson(config.polygon()));
    // WatchboxFilter JSON is implicit version 1; absence of a top-level version field means v1.
    entity.setFilterJson(writeJson(config.filter()));
    entity.setConfigEpoch(config.configEpoch());
    return entity;
  }

  private WatchboxConfig toConfig(WatchboxEntity entity) {
    Set<UUID> layerIds =
        layers.findAllByIdWatchboxId(entity.getId()).stream()
            .map(layer -> layer.getId().getLayerId())
            .collect(Collectors.toSet());
    return toConfig(entity, layerIds);
  }

  private WatchboxConfig toConfig(WatchboxEntity entity, Set<UUID> layerIds) {
    return new WatchboxConfig(
        entity.getId(),
        entity.getName(),
        entity.getDescription(),
        entity.isEnabled(),
        entity.getDirection(),
        readJson(entity.getPolygonJson(), Polygon.class),
        readJson(entity.getFilterJson(), WatchboxFilter.class),
        layerIds,
        entity.getConfigEpoch(),
        entity.getCreatedAt(),
        entity.getUpdatedAt());
  }

  private WatchboxSourceStatusRecord toSourceStatus(WatchboxSourceStatusEntity entity) {
    return new WatchboxSourceStatusRecord(
        entity.getId().getSourceId(),
        entity.getStatus(),
        entity.getReasonCode(),
        entity.getSinceAt(),
        entity.getObservedCount(),
        entity.getCap(),
        entity.getUpdatedAt());
  }

  private String writeJson(Object value) {
    return objectMapper.writeValueAsString(value);
  }

  private <T> T readJson(String value, Class<T> type) {
    return objectMapper.readValue(value, type);
  }
}
