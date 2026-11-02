// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { Layer } from "@deck.gl/core";
import type { MapTile, WmsTileConfig } from "@opencop/api";
import { RasterOverlayLayer } from "@opencop/layers";
import { getTileMaxZoom, getTileMinZoom, getTileSize } from "./tile-config.util";

export interface RasterLayerEntry {
    opacity: number;
    tile: MapTile;
    time: string | null;
}

export function buildRasterLayer(id: string, entries: RasterLayerEntry[]): Layer {
    return new RasterOverlayLayer<RasterLayerEntry>({
        id,
        data: entries,
        getId: (d) => d.tile.id,
        getTile: (d) => d.tile.url ?? "",
        getToken: (d) => d.tile.token,
        getOpacity: (d) => d.opacity,
        getIsWms: (d) => d.tile.config.tile.kind === "WMS",
        getWmsLayers: (d) => (d.tile.config.tile as WmsTileConfig).layers,
        getWmsVersion: (d) => (d.tile.config.tile as WmsTileConfig).version,
        getWmsFormat: (d) => (d.tile.config.tile as WmsTileConfig).format,
        getWmsTransparent: (d) => (d.tile.config.tile as WmsTileConfig).transparent,
        getTime: (d) => d.time,
        getTileSize: (d) => getTileSize(d.tile),
        getMinZoom: (d) => getTileMinZoom(d.tile),
        getMaxZoom: (d) => getTileMaxZoom(d.tile),
    }) as Layer;
}
