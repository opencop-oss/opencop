// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.tile;

import java.util.UUID;

public final class MapTileSecretRef {

  private static final String TILE_VAULT_PATH = "opencop/core/secrets/map/tiles/%s";

  private MapTileSecretRef() {}

  public static String fromTileId(UUID tileId) {
    return TILE_VAULT_PATH.formatted(tileId);
  }
}
