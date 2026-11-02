// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useMemo, type ReactNode } from "react";
import type { Layer } from "@deck.gl/core";
import { useQuery } from "@tanstack/react-query";
import { DEFAULT_OPACITY, useWeatherStore } from "~/features/weather/weather.store";
import { isSupportedWeatherTile } from "~/features/weather/weather.util";
import { tileQueries } from "~/features/imagery/imagery.queries";
import { hasTokenPlaceholder } from "~/features/imagery/imagery.util";
import { buildRasterLayer, type RasterLayerEntry } from "~/features/imagery/raster-layer-factory";
import { useTileReconcile } from "~/features/imagery/use-tile-reconcile.hook";
import { useTileTokens } from "~/features/imagery/use-tile-tokens.hook";
import { useRegisterLayers } from "./DeckLayerContext";
import { DECK_PRIORITY_WEATHER } from "./deck-layer-priorities";

export function WeatherLayer(): ReactNode {
    const reconcile = useWeatherStore((s) => s.reconcile);
    useTileReconcile(isSupportedWeatherTile, reconcile);

    const storeLayers = useWeatherStore((s) => s.layers);
    const hasActiveLayers = storeLayers.some((l) => l.active);
    const { data: allTiles = [] } = useQuery({
        ...tileQueries.list(),
        enabled: hasActiveLayers,
    });

    const activeSources = useMemo(() => {
        const activeIds = new Set(storeLayers.filter((l) => l.active).map((l) => l.id));
        const sources = allTiles.filter((t) => isSupportedWeatherTile(t) && activeIds.has(t.id));
        // List position 0 = highest priority = rendered last (on top) in deck.gl.
        return sources.sort((a, b) => {
            const ai = storeLayers.findIndex((l) => l.id === a.id);
            const bi = storeLayers.findIndex((l) => l.id === b.id);
            const aRank = ai === -1 ? Infinity : ai;
            const bRank = bi === -1 ? Infinity : bi;
            return bRank - aRank;
        });
    }, [allTiles, storeLayers]);

    const tokenizedSourceIds = useMemo(
        () => activeSources.filter((s) => hasTokenPlaceholder(s.url)).map((s) => s.id),
        [activeSources],
    );

    const tokenById = useTileTokens(tokenizedSourceIds, hasActiveLayers);

    const layers = useMemo((): Layer[] => {
        if (!hasActiveLayers) return [];
        const data = activeSources.flatMap((source): RasterLayerEntry[] => {
            const opacity = storeLayers.find((l) => l.id === source.id)?.opacity ?? DEFAULT_OPACITY;
            if (hasTokenPlaceholder(source.url)) {
                const token = tokenById.get(source.id);
                if (token == null) return [];
                return [{ opacity, tile: { ...source, token }, time: null }];
            }
            return [{ opacity, tile: source, time: null }];
        });
        return data.length > 0 ? [buildRasterLayer("weather", data)] : [];
    }, [activeSources, hasActiveLayers, storeLayers, tokenById]);

    useRegisterLayers("weather", layers, DECK_PRIORITY_WEATHER);

    return null;
}
