// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.iam.technical.gateway.db.server;

import com.opencop.core.api.iam.ports.gateway.mapper.RepositoryMapper;
import com.opencop.core.api.iam.technical.gateway.db.server.layer.entity.ServerLayerAccessEntity;
import com.opencop.core.sdk.business.model.iam.access.Server;
import java.util.List;
import java.util.UUID;

public class ServerMapper implements RepositoryMapper<List<ServerLayerAccessEntity>, Server> {

  @Override
  public Server toModel(List<ServerLayerAccessEntity> dbEntities) {
    return new Server.Builder().withServerId(getServerId(dbEntities)).build();
  }

  private UUID getServerId(List<ServerLayerAccessEntity> dbEntity) {
    for (ServerLayerAccessEntity entity : dbEntity) {
      return entity.getServerId();
    }
    return null;
  }

  @Override
  public Server toSanitizedModel(List<ServerLayerAccessEntity> dbEntities) {
    return new Server.Builder().withServerId(getServerId(dbEntities)).build();
  }
}
