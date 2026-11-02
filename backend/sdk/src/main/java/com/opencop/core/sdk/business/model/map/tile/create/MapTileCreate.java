// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.map.tile.create;

import com.opencop.core.sdk.business.model.map.tile.MapTileConfig;
import com.opencop.core.sdk.technical.gateway.db.iam.MapTileAccessScope;

/**
 * @param accessScope null means {@link MapTileAccessScope#PRIVATE}. Requesting {@code GLOBAL} is
 *     rejected for callers who are not global admins.
 * @param attribution credit shown on the map while the tile is visible; null or blank means none.
 */
public record MapTileCreate(
    String name,
    String url,
    String description,
    String token,
    MapTileAccessScope accessScope,
    MapTileConfig config,
    String attribution) {

  public MapTileCreate(
      String name, String url, String description, String token, MapTileConfig config) {
    this(name, url, description, token, null, config, null);
  }
}
