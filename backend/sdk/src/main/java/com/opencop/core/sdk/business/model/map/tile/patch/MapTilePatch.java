// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.map.tile.patch;

import com.opencop.core.sdk.technical.gateway.db.iam.MapTileAccessScope;
import java.util.Optional;

public record MapTilePatch(
    Optional<String> name,
    Optional<String> url,
    Optional<String> description,
    // A blank token clears the stored token, as for attribution below.
    Optional<String> token,
    Optional<Boolean> enabled,
    Optional<MapTileAccessScope> accessScope,
    Optional<MapTileConfigPatch> config,
    // A blank string clears the credit: JSON null reads as absent here, so it cannot.
    Optional<String> attribution) {

  public static MapTilePatch empty() {
    return new MapTilePatch(
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty());
  }
}
