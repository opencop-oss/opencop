// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { MapTile } from "@opencop/api";
import { getTileKind, getTileType } from "./tile-config.util";

const PRIMARY_ELIGIBLE_KINDS = new Set(["VECTOR", "RASTER", "WMS", "WMTS"]);

/**
 * Whether a tile may be made the primary basemap.
 *
 * Mirrors `MapTilePolicy.canBePrimary` on the server, which requires a basemap that is enabled and
 * GLOBAL. A primary any operator can be denied leaves them with no basemap at all, so the server
 * rejects the other cases and offering the action for them only produces a failed request.
 */
export function canBePrimary(tile: MapTile): boolean {
    return (
        PRIMARY_ELIGIBLE_KINDS.has(getTileKind(tile)) &&
        getTileType(tile) !== "WEATHER" &&
        tile.enabled &&
        tile.accessScope === "GLOBAL"
    );
}

export const KIND_COLOR: Record<string, string> = {
    VECTOR: "blue",
    RASTER: "green",
    WMS: "teal",
    WMTS: "teal",
    RASTER_DEM: "orange",
    TERRAIN_MESH: "purple",
    TERRAIN_RGB: "purple",
    TERRARIUM: "purple",
    TILE_3D: "gray",
    WEATHER: "cyan",
};
