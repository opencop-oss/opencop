// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.technical.gateway.watchbox;

import com.opencop.core.sdk.ports.gateway.account.CurrentAccountAccessGateway;
import com.opencop.core.sdk.technical.gateway.db.watchbox.WatchboxJpaRepository;
import com.opencop.core.sdk.technical.gateway.db.watchbox.WatchboxLayerJpaRepository;
import com.opencop.core.sdk.technical.gateway.iam.LayerAccessService;
import com.opencop.websocket.ports.gateway.watchbox.WatchboxAccessGateway;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class DatabaseWatchboxAccessGateway implements WatchboxAccessGateway {

  private final WatchboxJpaRepository watchboxRepository;
  private final WatchboxLayerJpaRepository watchboxLayerRepository;
  private final LayerAccessService layerAccessService;
  private final CurrentAccountAccessGateway currentAccountAccessGateway;

  public DatabaseWatchboxAccessGateway(
      WatchboxJpaRepository watchboxRepository,
      WatchboxLayerJpaRepository watchboxLayerRepository,
      LayerAccessService layerAccessService,
      CurrentAccountAccessGateway currentAccountAccessGateway) {
    this.watchboxRepository = watchboxRepository;
    this.watchboxLayerRepository = watchboxLayerRepository;
    this.layerAccessService = layerAccessService;
    this.currentAccountAccessGateway = currentAccountAccessGateway;
  }

  @Override
  public Optional<Set<UUID>> activeWatchboxLayerIds(UUID watchboxId) {
    if (watchboxRepository.findByIdAndArchivedAtIsNull(watchboxId).isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(
        watchboxLayerRepository.findAllByIdWatchboxId(watchboxId).stream()
            .map(layer -> layer.getId().getLayerId())
            .collect(Collectors.toUnmodifiableSet()));
  }

  @Override
  public boolean userIsActiveAdministrator(UUID userId) {
    return currentAccountAccessGateway
        .findByInternalUserId(userId)
        .map(access -> access.active() && access.administrator())
        .orElse(false);
  }

  @Override
  public boolean userHasLayerAccess(UUID userId, UUID layerId) {
    return layerAccessService.userHasAccess(userId, layerId);
  }
}
