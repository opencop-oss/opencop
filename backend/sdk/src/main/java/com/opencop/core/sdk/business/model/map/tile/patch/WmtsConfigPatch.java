// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.map.tile.patch;

import java.util.Map;
import java.util.Optional;

public record WmtsConfigPatch(
    Optional<String> version,
    Optional<String> layer,
    Optional<String> style,
    Optional<String> format,
    Optional<String> tileMatrixSet,
    Optional<Map<String, String>> extraParams)
    implements TileConfigPatch {
  public static WmtsConfigPatch empty() {
    return new WmtsConfigPatch(
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty());
  }
}
