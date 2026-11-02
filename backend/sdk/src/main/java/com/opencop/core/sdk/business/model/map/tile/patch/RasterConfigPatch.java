// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.map.tile.patch;

import java.util.Optional;

public record RasterConfigPatch(
    Optional<Integer> tileSize, Optional<Integer> minZoom, Optional<Integer> maxZoom)
    implements TileConfigPatch {
  public static RasterConfigPatch empty() {
    return new RasterConfigPatch(Optional.empty(), Optional.empty(), Optional.empty());
  }
}
