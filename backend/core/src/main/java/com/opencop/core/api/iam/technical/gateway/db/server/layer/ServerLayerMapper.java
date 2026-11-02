// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.iam.technical.gateway.db.server.layer;

import com.opencop.core.api.iam.ports.gateway.mapper.RepositoryMapper;
import com.opencop.core.api.iam.technical.gateway.db.layers.layer.LayerMapper;
import com.opencop.core.api.iam.technical.gateway.db.server.layer.entity.ServerLayerAccessEntity;
import com.opencop.core.sdk.business.model.iam.access.Layer;
import com.opencop.core.sdk.business.model.iam.access.SourceLayer;
import com.opencop.core.sdk.technical.gateway.db.iam.layer.LayerEntity;

public class ServerLayerMapper implements RepositoryMapper<ServerLayerAccessEntity, SourceLayer> {

  private final RepositoryMapper<LayerEntity, Layer> layerMapper;

  public ServerLayerMapper() {
    this.layerMapper = new LayerMapper(null);
  }

  @Override
  public SourceLayer toModel(ServerLayerAccessEntity dbEntity) {
    return new SourceLayer.Builder()
        .withServerId(dbEntity.getServerId())
        .withLayer(this.layerMapper.toSanitizedModel(dbEntity.getLayer()))
        .withRole(dbEntity.getRole())
        .withGrantedAt(dbEntity.getGrantedAt())
        .build();
  }

  @Override
  public SourceLayer toSanitizedModel(ServerLayerAccessEntity dbEntity) {
    return this.toModel(dbEntity);
  }
}
