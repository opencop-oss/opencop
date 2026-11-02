// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import type {
    MapTile,
    RasterTileConfig,
    TileConfig,
    VectorTileConfig,
    WeatherCapableTileConfig,
    WmsTileConfig,
    WmtsTileConfig,
} from "@opencop/api";
import { isImageryOverlayCapable } from "./imagery.util";

const RASTER: RasterTileConfig = { kind: "RASTER", tileSize: 256, minZoom: null, maxZoom: null };
const VECTOR: VectorTileConfig = { kind: "VECTOR", tileSize: 512, minZoom: null, maxZoom: null };
const WMS: WmsTileConfig = {
    kind: "WMS",
    version: "1.3.0",
    layers: ["base"],
    styles: null,
    format: "image/png",
    transparent: true,
    crs: "EPSG:3857",
};
const WMTS: WmtsTileConfig = {
    kind: "WMTS",
    version: "1.0.0",
    layer: "base",
    style: "default",
    format: "image/png",
    tileMatrixSet: "GoogleMapsCompatible",
};

const BASE_TILE: Omit<MapTile, "config"> = {
    id: "tile-1",
    name: "Tile",
    description: "",
    url: "https://example.com/{z}/{x}/{y}.png",
    token: null,
    attribution: null,
    primary: false,
    enabled: true,
    accessScope: "GLOBAL",
};

function basemapTile(tile: TileConfig): MapTile {
    return { ...BASE_TILE, config: { type: "BASEMAP", extension: null, tile } };
}

function weatherTile(tile: WeatherCapableTileConfig): MapTile {
    return {
        ...BASE_TILE,
        config: {
            type: "WEATHER",
            extension: { extensionType: "WEATHER", features: [] },
            tile,
        },
    };
}

describe("isImageryOverlayCapable", () => {
    it("returns true for a RASTER basemap tile", () => {
        expect(isImageryOverlayCapable(basemapTile(RASTER))).toBe(true);
    });

    it("returns false for a RASTER weather tile", () => {
        expect(isImageryOverlayCapable(weatherTile(RASTER))).toBe(false);
    });

    it("returns false for a VECTOR tile", () => {
        expect(isImageryOverlayCapable(basemapTile(VECTOR))).toBe(false);
    });

    it("returns true for a WMS tile", () => {
        expect(isImageryOverlayCapable(basemapTile(WMS))).toBe(true);
    });

    it("returns true for a WMTS tile", () => {
        expect(isImageryOverlayCapable(basemapTile(WMTS))).toBe(true);
    });
});
