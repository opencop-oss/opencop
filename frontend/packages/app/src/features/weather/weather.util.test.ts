// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { isSupportedWeatherTile, isWeatherTile } from "./weather.util";
import type { MapTile, TileConfig } from "@opencop/api";

function wrapBasemap(tile: TileConfig): MapTile["config"] {
    return { type: "BASEMAP", extension: null, tile };
}

function wrapWeather(
    tile: Extract<TileConfig, { kind: "RASTER" | "WMS" | "WMTS" }>,
    featureIds: string[],
): MapTile["config"] {
    return {
        type: "WEATHER",
        extension: {
            extensionType: "WEATHER",
            features: featureIds.map((f) => ({
                feature: f,
                url: `https://example.com/${f}/{z}/{x}/{y}.png`,
            })),
        },
        tile,
    };
}

describe("isWeatherTile", () => {
    it("returns true for a RASTER tile with type WEATHER", () => {
        const tile: MapTile = {
            id: "w",
            name: "w",
            description: "",
            url: "https://example.com/{z}/{x}/{y}.png",
            token: null,
            attribution: null,
            primary: false,
            enabled: true,
            accessScope: "GLOBAL",
            config: wrapWeather({ kind: "RASTER", tileSize: 256, minZoom: null, maxZoom: null }, [
                "PRECIPITATION",
            ]),
        };
        expect(isWeatherTile(tile)).toBe(true);
    });

    it("returns false for a plain RASTER tile with no type field", () => {
        const tile: MapTile = {
            id: "r",
            name: "r",
            description: "",
            url: "https://example.com/{z}/{x}/{y}.png",
            token: null,
            attribution: null,
            primary: false,
            enabled: true,
            accessScope: "GLOBAL",
            config: wrapBasemap({ kind: "RASTER", tileSize: 256, minZoom: null, maxZoom: null }),
        };
        expect(isWeatherTile(tile)).toBe(false);
    });

    it("returns false for a VECTOR tile", () => {
        const tile: MapTile = {
            id: "v",
            name: "v",
            description: "",
            url: "https://example.com/style.json",
            token: null,
            attribution: null,
            primary: false,
            enabled: true,
            accessScope: "GLOBAL",
            config: wrapBasemap({ kind: "VECTOR", tileSize: 512, minZoom: null, maxZoom: null }),
        };
        expect(isWeatherTile(tile)).toBe(false);
    });

    it("returns false for a TERRAIN_MESH tile", () => {
        const tile: MapTile = {
            id: "t",
            name: "t",
            description: "",
            url: "https://example.com/{z}/{x}/{y}.png",
            token: null,
            attribution: null,
            primary: false,
            enabled: true,
            accessScope: "GLOBAL",
            config: wrapBasemap({
                kind: "TERRAIN_MESH",
                encoding: "TERRARIUM",
                meshMaxError: 4,
                elevationUrl: undefined,
                decoderConfig: undefined,
                exaggeration: undefined,
                boundsPadding: undefined,
            }),
        };
        expect(isWeatherTile(tile)).toBe(false);
    });
});

describe("isSupportedWeatherTile", () => {
    it("returns true for frontend-supported weather raster tiles", () => {
        const tile: MapTile = {
            id: "wr",
            name: "wr",
            description: "",
            url: "https://example.com/{z}/{x}/{y}.png",
            token: null,
            attribution: null,
            primary: false,
            enabled: true,
            accessScope: "GLOBAL",
            config: wrapWeather({ kind: "RASTER", tileSize: 256, minZoom: null, maxZoom: null }, [
                "PRECIPITATION",
            ]),
        };

        expect(isSupportedWeatherTile(tile)).toBe(true);
    });

    it("returns false for weather WMS tiles while frontend support is disabled", () => {
        const tile: MapTile = {
            id: "ww",
            name: "ww",
            description: "",
            url: "https://example.com/wms",
            token: null,
            attribution: null,
            primary: false,
            enabled: true,
            accessScope: "GLOBAL",
            config: wrapWeather(
                {
                    kind: "WMS",
                    version: "1.3.0",
                    layers: ["wind"],
                    styles: [],
                    format: "image/png",
                    transparent: true,
                    crs: "EPSG:3857",
                },
                ["WIND_10M"],
            ),
        };

        expect(isSupportedWeatherTile(tile)).toBe(false);
    });
});
