// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type {
    MapTile,
    MapTileConfig,
    TileConfig,
    TileType,
    UpdateMapTileConfig,
    WeatherCapableTileConfig,
    WeatherFeatureEntry,
    WeatherTileExtension,
} from "@opencop/api";
import { TileKind } from "@opencop/api";
import { assertNever } from "assert-never";
import { inferWmtsMaxZoom } from "@opencop/layers";

/** Returns sensible default config for the given tile kind. */
export function getDefaultConfig(kind: TileKind): TileConfig {
    switch (kind) {
        case TileKind.VECTOR:
            return { kind: "VECTOR", tileSize: 512, minZoom: null, maxZoom: null };
        case TileKind.RASTER:
            return { kind: "RASTER", tileSize: 256, minZoom: null, maxZoom: null };
        case TileKind.WMS:
            return {
                kind: "WMS",
                version: "1.3.0",
                layers: [],
                styles: [],
                format: "image/png",
                transparent: true,
                crs: "EPSG:3857",
            };
        case TileKind.WMTS:
            return {
                kind: "WMTS",
                version: "1.0.0",
                layer: "",
                style: "default",
                format: "image/png",
                tileMatrixSet: "",
            };
        case TileKind.RASTER_DEM:
            return {
                kind: "RASTER_DEM",
                tileSize: 256,
                minZoom: null,
                maxZoom: null,
                encoding: "mapbox",
            };
        case TileKind.TERRAIN_MESH:
            return {
                kind: "TERRAIN_MESH",
                encoding: "TERRAIN_RGB",
                meshMaxError: 4,
            };
        case TileKind.TILE_3D:
            return { kind: "TILE_3D", loaderType: "cesium" };
    }
}

/** Parse a string to number or null (for nullable zoom / size fields). */
export function parseNullableInt(value: string): number | null {
    if (value.trim() === "") return null;
    const n = parseInt(value, 10);
    return Number.isNaN(n) ? null : n;
}

export function isWeatherCapableConfig(
    config: TileConfig | null | undefined,
): config is WeatherCapableTileConfig {
    return config != null && ["RASTER", "WMS", "WMTS"].includes(config.kind);
}

export function getTileConfig(tile: MapTile): TileConfig {
    return tile.config.tile;
}

export function getTileType(tile: MapTile): TileType {
    return tile.config.type ?? "BASEMAP";
}

export function getTileKind(tile: MapTile): TileConfig["kind"] {
    return tile.config.tile.kind;
}

export function getTileSize(tile: MapTile): number {
    const config = getTileConfig(tile);
    switch (config.kind) {
        case "RASTER":
            return config.tileSize ?? 256;
        case "WMTS":
            return 256;
        case "VECTOR":
        case "WMS":
        case "RASTER_DEM":
        case "TERRAIN_MESH":
        case "TILE_3D":
            throw new Error(
                `Tile ${tile.id} has a frontend-unsupported config kind: ${config.kind}`,
            );
        default:
            return assertNever(config);
    }
}

export function getTileMinZoom(tile: MapTile): number | undefined {
    const config = getTileConfig(tile);
    switch (config.kind) {
        case "RASTER":
            return config.minZoom ?? undefined;
        case "WMTS":
            return undefined;
        case "VECTOR":
        case "WMS":
        case "RASTER_DEM":
        case "TERRAIN_MESH":
        case "TILE_3D":
            throw new Error(
                `Tile ${tile.id} has a frontend-unsupported config kind: ${config.kind}`,
            );
        default:
            return assertNever(config);
    }
}

export function getTileMaxZoom(tile: MapTile): number | undefined {
    const config = getTileConfig(tile);
    switch (config.kind) {
        case "RASTER":
            return config.maxZoom ?? undefined;
        case "WMTS":
            return inferWmtsMaxZoom(config.tileMatrixSet);
        case "VECTOR":
        case "WMS":
        case "RASTER_DEM":
        case "TERRAIN_MESH":
        case "TILE_3D":
            throw new Error(
                `Tile ${tile.id} has a frontend-unsupported config kind: ${config.kind}`,
            );
        default:
            return assertNever(config);
    }
}

export function getWeatherExtension(
    config: MapTileConfig | null | undefined,
): WeatherTileExtension | null {
    return config?.type === "WEATHER" ? config.extension : null;
}

export function getWeatherFeatures(
    config: MapTileConfig | null | undefined,
): WeatherFeatureEntry[] {
    return getWeatherExtension(config)?.features ?? [];
}

function buildWeatherExtension(features: WeatherFeatureEntry[]): WeatherTileExtension {
    return {
        extensionType: "WEATHER",
        features,
    };
}

export function buildMapTileConfig(
    tileType: TileType,
    tile: TileConfig,
    features: WeatherFeatureEntry[] = [],
): MapTileConfig {
    if (tileType === "WEATHER" && isWeatherCapableConfig(tile)) {
        return {
            type: "WEATHER",
            extension: buildWeatherExtension(features),
            tile,
        };
    }

    return {
        type: "BASEMAP",
        extension: null,
        tile,
    };
}

export function buildMapTileConfigPatch(
    tileType: TileType,
    tile: TileConfig | undefined,
    features: WeatherFeatureEntry[] | undefined,
): UpdateMapTileConfig {
    const patch: UpdateMapTileConfig = {};

    if (tile) {
        patch.tile = tile;
    }

    if (tileType === "WEATHER") {
        patch.type = "WEATHER";
        if (features !== undefined) {
            patch.extension = buildWeatherExtension(features);
        }
    }

    return patch;
}
