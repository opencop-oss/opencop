// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.map.tile;

public final class VectorConfig implements TileConfig {
  private Integer tileSize; // optional (often 512 for vector)
  private Integer minZoom;
  private Integer maxZoom;

  public VectorConfig() {}

  public VectorConfig(Integer tileSize, Integer minZoom, Integer maxZoom) {
    this.tileSize = tileSize;
    this.minZoom = minZoom;
    this.maxZoom = maxZoom;
  }

  public Integer getTileSize() {
    return tileSize;
  }

  public void setTileSize(Integer tileSize) {
    this.tileSize = tileSize;
  }

  public Integer getMinZoom() {
    return minZoom;
  }

  public void setMinZoom(Integer minZoom) {
    this.minZoom = minZoom;
  }

  public Integer getMaxZoom() {
    return maxZoom;
  }

  public void setMaxZoom(Integer maxZoom) {
    this.maxZoom = maxZoom;
  }

  @Override
  public TileKind kind() {
    return TileKind.VECTOR;
  }
}
