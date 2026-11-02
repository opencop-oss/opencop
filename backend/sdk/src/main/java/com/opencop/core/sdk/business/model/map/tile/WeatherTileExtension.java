// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.map.tile;

import com.opencop.core.sdk.business.model.map.tile.weather.WeatherFeatureConfig;
import java.util.Set;
import java.util.stream.Collectors;

public record WeatherTileExtension(Set<WeatherFeatureConfig> features)
    implements TileConfigExtension {

  public Set<String> enabledFeatures() {
    if (features == null) {
      return Set.of();
    }
    return features.stream().map(WeatherFeatureConfig::feature).collect(Collectors.toSet());
  }
}
