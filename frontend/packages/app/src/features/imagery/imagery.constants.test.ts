// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { canBePrimary } from "./imagery.constants";
import type { MapTile, TileConfig } from "@opencop/api";

function wrapBasemap(tile: TileConfig): MapTile["config"] {
    return { type: "BASEMAP", extension: null, tile };
}

function wrapWeather(
    tile: Extract<TileConfig, { kind: "RASTER" | "WMS" | "WMTS" }>,
    features: string[],
): MapTile["config"] {
    return {
        type: "WEATHER",
        extension: {
            extensionType: "WEATHER",
            features: features.map((f) => ({ feature: f, url: "" })),
        },
        tile,
    };
}

function makeTile(overrides: Partial<MapTile> = {}): MapTile {
    return {
        id: "tile-1",
        name: "Tile",
        description: "",
        url: "https://example.com/{z}/{x}/{y}.png",
        token: null,
        attribution: null,
        primary: false,
        enabled: true,
        accessScope: "GLOBAL",
        config: wrapBasemap({ kind: "RASTER", tileSize: 256, minZoom: null, maxZoom: null }),
        ...overrides,
    };
}

describe("canBePrimary", () => {
    it("returns true for a plain RASTER tile", () => {
        expect(canBePrimary(makeTile())).toBe(true);
    });

    it("returns true for a VECTOR tile", () => {
        expect(
            canBePrimary(
                makeTile({
                    config: wrapBasemap({
                        kind: "VECTOR",
                        tileSize: 512,
                        minZoom: null,
                        maxZoom: null,
                    }),
                }),
            ),
        ).toBe(true);
    });

    it("returns false for a RASTER tile marked as WEATHER type", () => {
        expect(
            canBePrimary(
                makeTile({
                    config: wrapWeather(
                        {
                            kind: "RASTER",
                            tileSize: 256,
                            minZoom: null,
                            maxZoom: null,
                        },
                        ["PRECIPITATION"],
                    ),
                }),
            ),
        ).toBe(false);
    });

    it("returns false for a WMTS tile marked as WEATHER type", () => {
        expect(
            canBePrimary(
                makeTile({
                    config: wrapWeather(
                        {
                            kind: "WMTS",
                            version: "1.0.0",
                            layer: "precip",
                            style: "default",
                            format: "image/png",
                            tileMatrixSet: "EPSG:4326",
                        },
                        [],
                    ),
                }),
            ),
        ).toBe(false);
    });

    it("returns false for a TERRAIN_MESH tile (not primary-eligible kind)", () => {
        expect(
            canBePrimary(
                makeTile({
                    config: wrapBasemap({
                        kind: "TERRAIN_MESH",
                        encoding: "TERRARIUM",
                        meshMaxError: 4,
                        elevationUrl: undefined,
                        decoderConfig: undefined,
                        exaggeration: undefined,
                        boundsPadding: undefined,
                    }),
                }),
            ),
        ).toBe(false);
    });

    it("returns false for a disabled basemap", () => {
        expect(canBePrimary(makeTile({ enabled: false }))).toBe(false);
    });

    it("returns false for a PRIVATE basemap", () => {
        expect(canBePrimary(makeTile({ accessScope: "PRIVATE" }))).toBe(false);
    });

    it("returns false when a basemap is both disabled and PRIVATE", () => {
        expect(canBePrimary(makeTile({ enabled: false, accessScope: "PRIVATE" }))).toBe(false);
    });

    it("returns false for a RASTER_DEM tile", () => {
        expect(
            canBePrimary(
                makeTile({
                    config: wrapBasemap({
                        kind: "RASTER_DEM",
                        tileSize: 256,
                        minZoom: null,
                        maxZoom: null,
                        encoding: "mapbox",
                    }),
                }),
            ),
        ).toBe(false);
    });
});
