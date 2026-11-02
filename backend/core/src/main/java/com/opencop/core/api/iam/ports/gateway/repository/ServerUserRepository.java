// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.iam.ports.gateway.repository;

import com.opencop.core.sdk.business.model.iam.access.SourceUser;
import com.opencop.core.sdk.technical.gateway.db.iam.ServerRole;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface ServerUserRepository {

  boolean serverExists(UUID serverId);

  SourceUser add(UUID serverId, UUID userId, ServerRole role);

  void remove(UUID serverId, UUID userId, ServerRole role);

  Set<SourceUser> get(UUID serverId, UUID userId) throws RepositoryException;

  List<SourceUser> getServersForUser(UUID userId);

  void delete(UUID serverId);

  Map<UUID, Set<SourceUser>> list();

  /**
   * @return the ids actually granted; unknown ids are skipped rather than rejected.
   */
  Set<UUID> addUsers(UUID serverId, Set<UUID> userIds, ServerRole role);

  void removeUsers(UUID serverId, Set<UUID> userIds);

  Set<SourceUser> getUsers(UUID serverId);

  Set<UUID> getUserIdsForServer(UUID serverId);
}
