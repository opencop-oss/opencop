// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.map.tile;

public final class RasterDemConfig implements TileConfig {
  private Integer tileSize; // usually 256
  private Integer minZoom;
  private Integer maxZoom;
  private ElevationEncoding encoding; // TERRARIUM / TERRAIN_RGB

  public RasterDemConfig() {}

  public RasterDemConfig(
      Integer tileSize, Integer minZoom, Integer maxZoom, ElevationEncoding encoding) {
    this.tileSize = tileSize;
    this.minZoom = minZoom;
    this.maxZoom = maxZoom;
    this.encoding = encoding;
  }

  @Override
  public TileKind kind() {
    return TileKind.RASTER_DEM;
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

  public ElevationEncoding getEncoding() {
    return encoding;
  }

  public void setEncoding(ElevationEncoding encoding) {
    this.encoding = encoding;
  }
}
