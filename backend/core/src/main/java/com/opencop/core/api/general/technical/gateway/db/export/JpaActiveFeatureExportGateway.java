// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.export;

import com.opencop.core.api.general.business.export.ActiveFeatureExportRow;
import com.opencop.core.api.general.ports.gateway.export.ActiveFeatureExportGateway;
import com.opencop.core.api.general.ports.gateway.export.ActiveFeatureExportGateway.LayerSubject;
import com.opencop.core.sdk.model.SubjectType;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JpaActiveFeatureExportGateway implements ActiveFeatureExportGateway {

  private static final UUID UNMATCHABLE_LAYER_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000000");
  private static final Comparator<ActiveFeatureExportRow> ROW_ORDER =
      Comparator.comparing(ActiveFeatureExportRow::sourceId)
          .thenComparing(ActiveFeatureExportRow::objectId);

  private final ActiveFeatureExportJpaRepository repository;

  public JpaActiveFeatureExportGateway(ActiveFeatureExportJpaRepository repository) {
    this.repository = repository;
  }

  @Override
  @Transactional(readOnly = true)
  public List<ActiveFeatureExportRow> readSourceRows(UUID sourceId) {
    return repository.findSourceRows(sourceId).stream()
        .map(JpaActiveFeatureExportGateway::toRow)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public List<ActiveFeatureExportRow> readSourceRows(List<UUID> sourceIds) {
    if (sourceIds.isEmpty()) {
      return List.of();
    }
    return repository.findSourceRowsBySourceIds(sourceIds).stream()
        .map(JpaActiveFeatureExportGateway::toRow)
        .sorted(ROW_ORDER)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public List<ActiveFeatureExportRow> readLayerRows(UUID layerId, boolean includeTransitiveData) {
    return repository.findLayerRows(layerId, includeTransitiveData).stream()
        .map(JpaActiveFeatureExportGateway::toRow)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public List<ActiveFeatureExportRow> readLayerRows(List<LayerSubject> layers) {
    if (layers.isEmpty()) {
      return List.of();
    }
    var layerIds = layers.stream().map(LayerSubject::layerId).toList();
    var transitiveLayerIds =
        layers.stream()
            .filter(LayerSubject::includeTransitiveData)
            .map(LayerSubject::layerId)
            .toList();
    return repository
        .findLayerRowsByLayerSubjects(layerIds, queryableIds(transitiveLayerIds))
        .stream()
        .map(JpaActiveFeatureExportGateway::toRow)
        .sorted(ROW_ORDER)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public List<ActiveFeatureExportRow> readElementRows(List<UUID> elementIds) {
    if (elementIds.isEmpty()) {
      return List.of();
    }
    return repository.findElementRowsByIds(elementIds).stream()
        .map(JpaActiveFeatureExportGateway::toRow)
        .sorted(ROW_ORDER)
        .toList();
  }

  private static List<UUID> queryableIds(List<UUID> ids) {
    return ids.isEmpty() ? List.of(UNMATCHABLE_LAYER_ID) : ids;
  }

  private static ActiveFeatureExportRow toRow(ActiveFeatureExportProjection projection) {
    return new ActiveFeatureExportRow(
        projection.getId(),
        projection.getSourceId(),
        SubjectType.valueOf(projection.getSourceType()),
        projection.getObjectId(),
        projection.getPayload());
  }
}
