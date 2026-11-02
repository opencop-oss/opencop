// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.model.map;

import java.util.Optional;

public record MapCapturePatch(Optional<String> name, Optional<MapView> mapView) {

  public MapCapturePatch {
    name = name == null ? Optional.empty() : name;
    mapView = mapView == null ? Optional.empty() : mapView;
  }
}
