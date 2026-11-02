// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.map.tile;

public enum TileKind {
  VECTOR, // Basemap
  RASTER, // Basemap
  RASTER_DEM, // MapLibre terrain
  HILLSHADE, // Visual overlay
  TERRAIN_MESH, // deck terrain (primary elevation)
  TILE_3D, // 3D scene objects
  WMS, // Basemap
  WMTS // Basemap
}
