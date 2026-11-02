// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.ports.gateway.watchbox;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface WatchboxAccessGateway {

  /**
   * Selected layer ids of the active (non-archived) watchbox, or empty when no such watchbox
   * exists. An existing watchbox with zero layers returns an empty set inside a present Optional.
   */
  Optional<Set<UUID>> activeWatchboxLayerIds(UUID watchboxId);

  boolean userIsActiveAdministrator(UUID userId);

  boolean userHasLayerAccess(UUID userId, UUID layerId);
}
