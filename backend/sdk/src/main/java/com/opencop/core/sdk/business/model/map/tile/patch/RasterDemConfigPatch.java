// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.map.tile.patch;

import com.opencop.core.sdk.business.model.map.tile.ElevationEncoding;
import java.util.Optional;

public record RasterDemConfigPatch(
    Optional<Integer> tileSize,
    Optional<Integer> minZoom,
    Optional<Integer> maxZoom,
    Optional<ElevationEncoding> encoding)
    implements TileConfigPatch {
  public static RasterDemConfigPatch empty() {
    return new RasterDemConfigPatch(
        Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
  }
}
