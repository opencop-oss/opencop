// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.map.tile;

public class MapTileConfig {

  private TileType type = TileType.BASEMAP;
  private TileConfigExtension extension;
  private TileConfig tile;

  public MapTileConfig() {}

  public MapTileConfig(TileConfig tile) {
    this.tile = tile;
  }

  public MapTileConfig(TileType type, TileConfigExtension extension, TileConfig tile) {
    this.type = type;
    this.extension = extension;
    this.tile = tile;
  }

  public TileType getType() {
    return type;
  }

  public void setType(TileType type) {
    this.type = type;
  }

  public TileConfigExtension getExtension() {
    return extension;
  }

  public void setExtension(TileConfigExtension extension) {
    this.extension = extension;
  }

  public TileConfig getTile() {
    return tile;
  }

  public void setTile(TileConfig tile) {
    this.tile = tile;
  }

  public TileType tileType() {
    return type == null ? TileType.BASEMAP : type;
  }

  public TileKind kind() {
    return tile == null ? null : tile.kind();
  }
}
