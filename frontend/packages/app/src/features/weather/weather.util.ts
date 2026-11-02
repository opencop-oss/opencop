// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { MapTile, TileConfig } from "@opencop/api";
import { getTileKind, getTileType } from "~/features/imagery/tile-config.util";

const SUPPORTED_WEATHER_TILE_KINDS = new Set<TileConfig["kind"]>(["RASTER", "WMTS"]);

export function isWeatherTile(tile: MapTile): boolean {
    return getTileType(tile) === "WEATHER";
}

export function isSupportedWeatherTile(tile: MapTile): boolean {
    return isWeatherTile(tile) && SUPPORTED_WEATHER_TILE_KINDS.has(getTileKind(tile));
}
