// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.map.tile.patch;

import com.opencop.core.sdk.business.model.map.tile.TileKind;
import com.opencop.core.sdk.business.model.map.tile.TileType;
import java.util.Optional;

public record MapTileConfigPatch(
    Optional<TileType> type,
    Optional<TileConfigExtensionPatch> extension,
    Optional<TileKind> kind,
    Optional<TileConfigPatch> tile) {

  public static MapTileConfigPatch empty() {
    return new MapTileConfigPatch(
        Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
  }
}
