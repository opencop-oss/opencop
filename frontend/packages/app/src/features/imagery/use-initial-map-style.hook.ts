// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { SELECTED_TILE_KEY, TileKind } from "@opencop/api";
import { useQuery } from "@tanstack/react-query";
import { useMemo } from "react";
import { defaultStyle } from "~/features/map/map.default";
import { tileQueries } from "./imagery.queries";
import { preferencesQueries } from "~/features/preferences/preferences.queries";
import { hasTokenPlaceholder, resolveMapStyle } from "./imagery.util";
import { isWeatherTile } from "~/features/weather/weather.util";
import { getTileKind } from "./tile-config.util";

const IMAGERY_KINDS: Set<string> = new Set([
    TileKind.VECTOR,
    TileKind.RASTER,
    TileKind.WMS,
    TileKind.WMTS,
]);

/**
 * Resolves the initial MapLibre style with the following priority:
 * 1. sessionStorage SELECTED_TILE_KEY (current session selection - cleared when browser closes)
 * 2. User's defaultTile preference (from backend - used for new sessions)
 * 3. Primary tile (system default)
 *
 * For token-protected tiles, eagerly fetches the tile detail endpoint
 * (which includes the real token) so the style can be resolved without
 * waiting for the imagery panel to open.
 *
 * Returns `{ style, tileId }`, which move independently: `style` is null while the lists load, and
 * while a token-protected tile's detail loads even though `tileId` is already known; `tileId` is
 * null when no basemap tile is offered and `style` is the local fallback. The id is the seed and
 * live input of `ActiveBasemapProvider`. This resolves the style to request; it does not know
 * whether the map applied it.
 */
export function useInitialMapStyle() {
    const { data: tiles = [], isLoading: tilesLoading } = useQuery(tileQueries.list());
    const { data: preferences, isLoading: preferencesLoading } = useQuery(
        preferencesQueries.current(),
    );

    const imageryTiles = useMemo(
        () => tiles.filter((t) => IMAGERY_KINDS.has(getTileKind(t)) && !isWeatherTile(t)),
        [tiles],
    );

    const selectedTile = useMemo(() => {
        if (tilesLoading || imageryTiles.length === 0) return null;

        const savedTileId = sessionStorage.getItem(SELECTED_TILE_KEY);
        const savedTile = savedTileId ? imageryTiles.find((t) => t.id === savedTileId) : null;
        // Resolve the stored preference against the live list rather than trusting the embedded
        // copy: a tile the admin has since disabled is no longer a legal starting basemap, and
        // the primary tile is the guaranteed floor.
        const defaultTileId = preferences?.defaultTile?.id;
        const userDefaultTile = defaultTileId
            ? imageryTiles.find((t) => t.id === defaultTileId)
            : null;
        const primaryTile = imageryTiles.find((t) => t.primary);

        return savedTile || userDefaultTile || primaryTile || null;
    }, [imageryTiles, preferences?.defaultTile?.id, tilesLoading]);

    const needsToken = selectedTile != null && hasTokenPlaceholder(selectedTile.url);

    // Eagerly fetch the tile detail when the selected tile has a token placeholder.
    // The detail endpoint returns the real token value that the list endpoint omits.
    const { data: tileDetail } = useQuery({
        ...tileQueries.detail(selectedTile?.id ?? ""),
        enabled: needsToken,
    });

    const style = useMemo(() => {
        // Return null while data is still loading so MapView can defer rendering
        // and avoid flashing the local fallback style.
        if (tilesLoading || preferencesLoading) return null;
        if (!selectedTile) return defaultStyle;

        if (needsToken) {
            return tileDetail ? resolveMapStyle(tileDetail) : null;
        }

        return resolveMapStyle(selectedTile);
    }, [selectedTile, needsToken, tileDetail, tilesLoading, preferencesLoading]);

    const tileId = selectedTile?.id ?? null;
    return useMemo(() => ({ style, tileId }), [style, tileId]);
}
