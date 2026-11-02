// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.iam.ports.gateway.repository;

import com.opencop.core.sdk.business.model.iam.access.SourceLayer;
import com.opencop.core.sdk.technical.gateway.db.iam.ServerRole;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface ServerLayerRepository {

  boolean serverExists(UUID serverId);

  SourceLayer add(UUID serverId, UUID layerId, ServerRole role) throws RepositoryException;

  Set<SourceLayer> get(UUID serverId, UUID layerId);

  Map<UUID, Set<SourceLayer>> list();

  void delete(UUID serverId);

  Set<SourceLayer> getLayers(UUID serverId);

  void addLayers(UUID serverId, Set<UUID> layerIds, ServerRole role) throws RepositoryException;

  void removeLayers(UUID serverId, Set<UUID> layerIds);

  Set<SourceLayer> getLayerServers(UUID layerId);
}
