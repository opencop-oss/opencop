// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.me.permissions;

import com.opencop.core.api.general.business.model.me.MyLayerResponse;
import com.opencop.core.api.general.ports.usecase.me.RetrieveUserLayers;
import com.opencop.core.api.iam.ports.gateway.repository.LayersRepository;
import com.opencop.core.api.iam.ports.usecase.access.me.UserSpecificManager;
import com.opencop.core.sdk.business.model.iam.access.Layer;
import com.opencop.core.sdk.model.iam.layer.LayerReadFilter;
import com.opencop.core.sdk.model.iam.source.LayerInfo;
import com.opencop.core.sdk.model.visibility.RecordVisibility;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

public class RetrieveUserLayersUsecase implements RetrieveUserLayers {

  private final UserSpecificManager service;
  private final LayersRepository layersRepository;

  public RetrieveUserLayersUsecase(
      final UserSpecificManager service, final LayersRepository layersRepository) {
    this.service = service;
    this.layersRepository = layersRepository;
  }

  /**
   * Effective layers for a user with each layer's audit metadata attached.
   *
   * <p>The audit fields come from one batched layer read rather than from the effective-access
   * computation, which also runs on authorization paths that have no use for them.
   */
  @Override
  public List<MyLayerResponse> get(UUID userId, RecordVisibility recordVisibility) {
    var infos = this.service.getUserLayers(userId, recordVisibility);
    if (infos.isEmpty()) {
      return List.of();
    }
    Set<UUID> ids = infos.stream().map(LayerInfo::layerId).collect(Collectors.toSet());
    Map<UUID, Layer> layersById =
        this.layersRepository
            .list(new LayerReadFilter(recordVisibility, null, null, null, ids))
            .stream()
            .collect(Collectors.toMap(Layer::getId, Function.identity()));
    return infos.stream().map(info -> toResponse(info, layersById.get(info.layerId()))).toList();
  }

  private static MyLayerResponse toResponse(LayerInfo info, Layer layer) {
    var audited = Optional.ofNullable(layer);
    return new MyLayerResponse(
        info.layerId(),
        info.layerName(),
        info.role(),
        info.accessScope(),
        info.createdAt(),
        audited.map(Layer::getCreatedBy).orElse(null),
        audited.map(Layer::getUpdatedAt).orElse(null),
        audited.map(Layer::getUpdatedBy).orElse(null),
        audited.map(Layer::getLastEventAt).orElse(null),
        audited.map(Layer::getArchivedAt).orElse(null),
        audited.map(Layer::getArchivedBy).orElse(null),
        info.defaultVisible());
  }
}
