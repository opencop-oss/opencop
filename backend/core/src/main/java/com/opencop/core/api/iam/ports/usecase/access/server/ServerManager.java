// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.iam.ports.usecase.access.server;

import com.opencop.core.sdk.business.model.iam.access.SourceLayer;
import com.opencop.core.sdk.business.model.iam.access.SourceUser;
import com.opencop.core.sdk.model.iam.source.UpdateSourcePermissionsRequest;
import com.opencop.core.sdk.technical.gateway.db.iam.ServerRole;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface ServerManager<S> {

  S add(UUID serverId, Set<UUID> layers, Set<UUID> userIds, UUID creatorUserId);

  Set<UUID> list();

  void delete(UUID serverId);

  Set<SourceLayer> getLayers(UUID serverId);

  void updateLayers(UUID serverId, UpdateSourcePermissionsRequest request);

  void deleteLayers(UUID serverId, Set<UUID> layerIds);

  Set<SourceUser> getUsers(UUID serverId);

  /**
   * @return the ids actually granted, mapped to the role they ended with.
   */
  Map<UUID, ServerRole> updateUsers(UUID serverId, UpdateSourcePermissionsRequest request);

  void deleteUsers(UUID serverId, Set<UUID> userIds);
}
