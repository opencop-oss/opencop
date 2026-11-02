// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useMemo, type ReactNode } from "react";
import type { Layer } from "@deck.gl/core";
import { useQuery } from "@tanstack/react-query";
import {
    useImageryOverlayStore,
    useImageryOverlayActions,
} from "~/features/imagery/imagery-overlay.store";
import { tileQueries } from "~/features/imagery/imagery.queries";
import { hasTokenPlaceholder, isImageryOverlayCapable } from "~/features/imagery/imagery.util";
import { buildRasterLayer, type RasterLayerEntry } from "~/features/imagery/raster-layer-factory";
import { useTileReconcile } from "~/features/imagery/use-tile-reconcile.hook";
import { useTileTokens } from "~/features/imagery/use-tile-tokens.hook";
import { useRegisterLayers } from "./DeckLayerContext";
import { DECK_PRIORITY_IMAGERY_OVERLAY } from "./deck-layer-priorities";

export function ImageryOverlayLayer(): ReactNode {
    const { reconcile } = useImageryOverlayActions();
    useTileReconcile(isImageryOverlayCapable, reconcile);

    const storeOverlays = useImageryOverlayStore((s) => s.overlays);
    const hasActive = storeOverlays.length > 0;

    const { data: allTiles = [] } = useQuery({ ...tileQueries.list(), enabled: hasActive });

    // Store order: index 0 = on top visually.
    // Deck.gl: last in array = rendered on top.
    // So we reverse before passing to the layer.
    const activeSources = useMemo(() => {
        const activeIds = new Set(storeOverlays.map((o) => o.id));
        const sourcesById = new Map(
            allTiles.filter((t) => activeIds.has(t.id)).map((t) => [t.id, t]),
        );
        return storeOverlays
            .flatMap((o) => {
                if (!o.visible) return [];
                const tile = sourcesById.get(o.id);
                return tile ? [{ opacity: o.opacity, tile, time: o.time }] : [];
            })
            .reverse();
    }, [allTiles, storeOverlays]);

    const tokenizedIds = useMemo(
        () => activeSources.filter((s) => hasTokenPlaceholder(s.tile.url)).map((s) => s.tile.id),
        [activeSources],
    );

    const tokenById = useTileTokens(tokenizedIds, hasActive);

    const layers = useMemo((): Layer[] => {
        if (!hasActive || activeSources.length === 0) return [];

        const data = activeSources.flatMap((source): RasterLayerEntry[] => {
            if (hasTokenPlaceholder(source.tile.url)) {
                const token = tokenById.get(source.tile.id);
                if (token == null) return [];
                return [
                    { opacity: source.opacity, tile: { ...source.tile, token }, time: source.time },
                ];
            }
            return [{ opacity: source.opacity, tile: source.tile, time: source.time }];
        });

        return data.length > 0 ? [buildRasterLayer("imagery-overlay", data)] : [];
    }, [activeSources, hasActive, tokenById]);

    useRegisterLayers("imagery-overlay", layers, DECK_PRIORITY_IMAGERY_OVERLAY);

    return null;
}
