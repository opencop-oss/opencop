// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.map.tile;

public final class Tile3dConfig implements TileConfig {
  private String tilesetUrl; // usually same as url, but explicit is nicer
  private Double maximumScreenSpaceError;

  public Tile3dConfig() {}

  public Tile3dConfig(String tilesetUrl, Double maximumScreenSpaceError) {
    this.tilesetUrl = tilesetUrl;
    this.maximumScreenSpaceError = maximumScreenSpaceError;
  }

  @Override
  public TileKind kind() {
    return TileKind.TILE_3D;
  }

  public String getTilesetUrl() {
    return tilesetUrl;
  }

  public void setTilesetUrl(String tilesetUrl) {
    this.tilesetUrl = tilesetUrl;
  }

  public Double getMaximumScreenSpaceError() {
    return maximumScreenSpaceError;
  }

  public void setMaximumScreenSpaceError(Double maximumScreenSpaceError) {
    this.maximumScreenSpaceError = maximumScreenSpaceError;
  }
}
