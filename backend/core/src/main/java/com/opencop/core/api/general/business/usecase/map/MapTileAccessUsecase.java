// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.map;

import com.opencop.core.api.general.business.policy.MapTilePolicy;
import com.opencop.core.api.general.technical.gateway.tile.MapTileRepository;
import com.opencop.core.api.general.technical.service.EffectiveMapTileAccessService;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Whether a caller may put a tile on a map.
 *
 * <p>Not a direct {@link MapTilePolicy} call: the decision needs the tile row for its enabled flag
 * and a grant lookup, and the row is read once and reused for both. Callers that already hold those
 * inputs should ask the policy themselves rather than come through here.
 */
@Service
public class MapTileAccessUsecase {

  private final EffectiveMapTileAccessService effectiveAccess;
  private final MapTileRepository mapTileRepository;

  public MapTileAccessUsecase(
      EffectiveMapTileAccessService effectiveAccess, MapTileRepository mapTileRepository) {
    this.effectiveAccess = effectiveAccess;
    this.mapTileRepository = mapTileRepository;
  }

  /**
   * Whether the caller may actually put this tile on a map: it exists, it is enabled, and they can
   * see it.
   */
  public boolean canUse(UUID mapTileId, OpenCopPrincipal principal) {
    return mapTileRepository
        .findById(mapTileId)
        .map(
            tile ->
                MapTilePolicy.canUse(
                    effectiveAccess.canReach(tile, principal.internalUserId()), tile.isEnabled()))
        .orElse(false);
  }
}
