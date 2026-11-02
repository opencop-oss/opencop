// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.watchbox.technical.gateway.iam;

import com.opencop.core.api.iam.ports.gateway.repository.LayersRepository;
import com.opencop.core.api.iam.ports.gateway.repository.RepositoryException;
import com.opencop.core.api.watchbox.business.model.WatchboxLayerRole;
import com.opencop.core.api.watchbox.business.model.WatchboxLayerState;
import com.opencop.core.api.watchbox.ports.gateway.WatchboxLayerAccess;
import com.opencop.core.api.watchbox.ports.usecase.WatchboxCaller;
import com.opencop.core.sdk.model.visibility.RecordVisibility;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerRole;
import com.opencop.core.sdk.technical.gateway.iam.LayerAccessService;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Set-based {@link WatchboxLayerAccess}: resolves the caller's roles for a whole layer set with one
 * reachability statement instead of the per-layer effective role walk it replaced (which cost
 * several point reads per layer). WatchboxLayerAccessSemanticsIT pins the expected role for every
 * membership shape the reachability model supports.
 */
public class PostgresWatchboxLayerAccess implements WatchboxLayerAccess {

  private final LayersRepository layersRepository;
  private final LayerAccessService layerAccessService;

  public PostgresWatchboxLayerAccess(
      LayersRepository layersRepository, LayerAccessService layerAccessService) {
    this.layersRepository = layersRepository;
    this.layerAccessService = layerAccessService;
  }

  @Override
  public WatchboxLayerState activeLayerState(UUID layerId) {
    try {
      layersRepository.get(layerId, RecordVisibility.ACTIVE);
    } catch (RepositoryException e) {
      return WatchboxLayerState.MISSING_OR_ARCHIVED;
    }
    return WatchboxLayerState.ACTIVE;
  }

  @Override
  public Map<UUID, WatchboxLayerRole> rolesForLayers(Set<UUID> layerIds, WatchboxCaller caller) {
    return layerAccessService.userRoles(caller.userId(), layerIds).entrySet().stream()
        .collect(
            Collectors.toMap(Map.Entry::getKey, entry -> toWatchboxLayerRole(entry.getValue())));
  }

  private static WatchboxLayerRole toWatchboxLayerRole(LayerRole role) {
    return switch (role) {
      case OWNER -> WatchboxLayerRole.OWNER;
      case ADMIN -> WatchboxLayerRole.ADMIN;
      case EDITOR -> WatchboxLayerRole.EDITOR;
      case MEMBER -> WatchboxLayerRole.MEMBER;
    };
  }
}
