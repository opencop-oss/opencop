// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.iam.ports.gateway.repository;

import com.opencop.core.sdk.business.model.iam.access.LayerMember;
import com.opencop.core.sdk.technical.gateway.db.PersistenceException;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerRole;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface LayerMemberRepository {

  LayerMember getMember(UUID layerId, UUID userId) throws PersistenceException;

  Optional<LayerMember> findMember(UUID layerId, UUID userId);

  List<LayerMember> getLayerMembers(UUID layerId);

  List<LayerMember> getLayersByUserId(UUID userId);

  void addLayerMemberRoles(UUID layerId, Set<UUID> userIds, LayerRole roles)
      throws RepositoryException;

  Map<UUID, LayerRole> addLayerMembers(UUID layerId, Map<UUID, LayerRole> userIdsAndRoles)
      throws RepositoryException;

  void removeLayerMembers(UUID layerId, Set<UUID> userIds);

  Set<UUID> getOwnerUserIds(UUID layerId);
}
