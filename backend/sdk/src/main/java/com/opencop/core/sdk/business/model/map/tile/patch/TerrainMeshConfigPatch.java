// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.map.tile.patch;

import com.opencop.core.sdk.business.model.map.tile.ElevationEncoding;
import java.util.Optional;

public record TerrainMeshConfigPatch(
    Optional<String> elevationUrl,
    Optional<ElevationEncoding> encoding,
    Optional<Double> exaggeration,
    Optional<Integer> meshMaxError)
    implements TileConfigPatch {
  public static TerrainMeshConfigPatch empty() {
    return new TerrainMeshConfigPatch(
        Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
  }
}
