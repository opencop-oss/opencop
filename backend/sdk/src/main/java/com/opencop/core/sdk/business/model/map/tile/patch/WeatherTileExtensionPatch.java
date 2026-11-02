// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.map.tile.patch;

import com.opencop.core.sdk.business.model.map.tile.weather.WeatherFeatureConfig;
import java.util.Optional;
import java.util.Set;

public record WeatherTileExtensionPatch(Optional<Set<WeatherFeatureConfig>> features)
    implements TileConfigExtensionPatch {

  public static WeatherTileExtensionPatch empty() {
    return new WeatherTileExtensionPatch(Optional.empty());
  }
}
