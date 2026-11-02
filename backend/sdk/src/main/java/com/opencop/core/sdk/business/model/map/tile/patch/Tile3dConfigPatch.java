// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.map.tile.patch;

import java.util.Optional;

public record Tile3dConfigPatch(
    Optional<String> tilesetUrl, Optional<Double> maximumScreenSpaceError)
    implements TileConfigPatch {
  public static Tile3dConfigPatch empty() {
    return new Tile3dConfigPatch(Optional.empty(), Optional.empty());
  }
}
