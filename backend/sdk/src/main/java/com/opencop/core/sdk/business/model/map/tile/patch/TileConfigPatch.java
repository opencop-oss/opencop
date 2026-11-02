// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.map.tile.patch;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXTERNAL_PROPERTY,
    property = "kind")
@JsonSubTypes({
  @JsonSubTypes.Type(value = VectorConfigPatch.class, name = "VECTOR"),
  @JsonSubTypes.Type(value = RasterConfigPatch.class, name = "RASTER"),
  @JsonSubTypes.Type(value = RasterDemConfigPatch.class, name = "RASTER_DEM"),
  @JsonSubTypes.Type(value = TerrainMeshConfigPatch.class, name = "TERRAIN_MESH"),
  @JsonSubTypes.Type(value = Tile3dConfigPatch.class, name = "TILE_3D"),
  @JsonSubTypes.Type(value = WmsConfigPatch.class, name = "WMS"),
  @JsonSubTypes.Type(value = WmtsConfigPatch.class, name = "WMTS")
})
public sealed interface TileConfigPatch
    permits VectorConfigPatch,
        RasterConfigPatch,
        RasterDemConfigPatch,
        TerrainMeshConfigPatch,
        Tile3dConfigPatch,
        WmsConfigPatch,
        WmtsConfigPatch {}
