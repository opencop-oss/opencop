// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.map.tile.patch;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public record WmsConfigPatch(
    Optional<String> version,
    Optional<List<String>> layers,
    Optional<List<String>> styles,
    Optional<String> format,
    Optional<Boolean> transparent,
    Optional<String> crs,
    Optional<Map<String, String>> extraParams)
    implements TileConfigPatch {
  public static WmsConfigPatch empty() {
    return new WmsConfigPatch(
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty());
  }
}
