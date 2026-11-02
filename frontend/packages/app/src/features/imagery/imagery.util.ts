// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { StyleSpecification } from "maplibre-gl";
import type { MapTile, RasterTileConfig } from "@opencop/api";
import { TileKind, getStyleUrl } from "@opencop/api";
import { getTileConfig, getTileKind, getTileType } from "./tile-config.util";
import type { TFunction } from "i18next";
import type { RoleDescriptor } from "~/features/layer-groups/layer-groups.util";
import type { SelectItem } from "@opencop/ui";

/**
 * Resolves a MapTile into the appropriate MapLibre style input.
 * - RASTER tiles (XYZ templates): wraps in a StyleSpecification so MapLibre handles {z}/{x}/{y}
 * - All other tiles (VECTOR, etc.): returns the URL string for style JSON fetching
 */
export function resolveMapStyle(tile: MapTile): string | StyleSpecification {
    const url = getStyleUrl(tile);
    const tileConfig = getTileConfig(tile);

    if (tileConfig.kind === "RASTER") {
        const config = tileConfig as RasterTileConfig;
        return {
            version: 8,
            sources: {
                "raster-tiles": {
                    type: "raster",
                    tiles: [url],
                    tileSize: config.tileSize ?? 256,
                    ...(config.minZoom != null && { minzoom: config.minZoom }),
                    ...(config.maxZoom != null && { maxzoom: config.maxZoom }),
                },
            },
            layers: [{ id: "raster-layer", type: "raster", source: "raster-tiles" }],
        };
    }

    return url;
}

/** Known query parameter names that carry API tokens/keys, mapped to URL placeholders. */
const TOKEN_PARAMS: Record<string, string> = {
    key: "{key}",
    token: "{token}",
    access_token: "{token}",
    api_key: "{api_key}",
    apikey: "{api_key}",
};

export const hasTokenPlaceholder = (url: string): boolean =>
    /\{(token|key|api_key|apikey|access_token)\}/.test(url);

/** RASTER, WMS, and WMTS tiles that are not weather — the set that can be used as overlays. */
export function isImageryOverlayCapable(tile: MapTile): boolean {
    const kind = getTileKind(tile);
    return (
        (kind === TileKind.RASTER || kind === TileKind.WMS || kind === TileKind.WMTS) &&
        getTileType(tile) !== "WEATHER"
    );
}

/**
 * Parses a tile URL and extracts any known token query parameter.
 * Returns the cleaned URL (with a placeholder replacing the token value)
 * and the extracted token value.
 *
 * Example: "https://api.maptiler.com/maps/sat/{z}/{x}/{y}.jpg?key=ABC123"
 *   → { url: "https://api.maptiler.com/maps/sat/{z}/{x}/{y}.jpg?key={key}", token: "ABC123" }
 */
export function parseUrlToken(rawUrl: string): { url: string; token: string | null } {
    const qIndex = rawUrl.indexOf("?");
    if (qIndex === -1) return { url: rawUrl, token: null };

    const base = rawUrl.substring(0, qIndex);
    const queryString = rawUrl.substring(qIndex + 1);
    const pairs = queryString.split("&");

    for (const [paramName, placeholder] of Object.entries(TOKEN_PARAMS)) {
        const idx = pairs.findIndex((p) => p.startsWith(`${paramName}=`));
        if (idx !== -1) {
            const token = pairs[idx].substring(paramName.length + 1);
            if (!token) continue;
            pairs[idx] = `${paramName}=${placeholder}`;
            return { url: `${base}?${pairs.join("&")}`, token };
        }
    }

    return { url: rawUrl, token: null };
}

/**
 * What each access scope means for a tile.
 *
 * Worded for imagery rather than reusing the layer descriptions: a tile grant carries no role, so
 * "member access" would describe something that does not exist here.
 */
export const TILE_ACCESS_SCOPE_DESCRIPTIONS: RoleDescriptor[] = [
    { role: "GLOBAL", color: "green", descriptionKey: "roles.globalTileScopeDesc" },
    { role: "PRIVATE", color: "blue", descriptionKey: "roles.privateTileScopeDesc" },
];

/** Scope choices for the tile create and edit forms. */
export function accessScopeOptions(t: TFunction): SelectItem[] {
    return [
        { label: t("adminLayers.scopePrivate"), value: "PRIVATE" },
        { label: t("adminLayers.scopeGlobal"), value: "GLOBAL" },
    ];
}
