// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.map.tile;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "kind")
@JsonSubTypes({
  @JsonSubTypes.Type(value = VectorConfig.class, name = "VECTOR"),
  @JsonSubTypes.Type(value = RasterConfig.class, name = "RASTER"),
  @JsonSubTypes.Type(value = RasterDemConfig.class, name = "RASTER_DEM"),
  @JsonSubTypes.Type(value = TerrainMeshConfig.class, name = "TERRAIN_MESH"),
  @JsonSubTypes.Type(value = Tile3dConfig.class, name = "TILE_3D"),
  @JsonSubTypes.Type(value = WmsConfig.class, name = "WMS"),
  @JsonSubTypes.Type(value = WmtsConfig.class, name = "WMTS")
})
public sealed interface TileConfig
    permits VectorConfig,
        RasterConfig,
        RasterDemConfig,
        TerrainMeshConfig,
        Tile3dConfig,
        WmsConfig,
        WmtsConfig {
  TileKind kind();
}
