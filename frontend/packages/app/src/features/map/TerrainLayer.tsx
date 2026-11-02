// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useEffect, useMemo, type ReactNode } from "react";
import { TerrainLayer as DeckTerrainLayer } from "@deck.gl/geo-layers";
import type { Layer } from "@deck.gl/core";
import { useRegisterLayers } from "./DeckLayerContext";
import { DECK_PRIORITY_TERRAIN } from "./deck-layer-priorities";
import { useMapStore } from "./core/map.store";
import { useTerrainActions, useTerrainState } from "./core/slices/terrain/terrain.hook";

/**
 * Owns the deck.gl `TerrainLayer` mesh and the two-frame warmup sequence.
 *
 * When terrain is enabled (config written to `terrainStore`):
 *   Frame 1 — terrain mesh layer is registered; data layers render flat (drape OFF).
 *   Frame 2 — RAF fires, enables draping so data layers conform to the mesh
 *             (only if the user hasn't explicitly disabled drape in localStorage).
 *
 * Must be rendered inside `<DeckLayerProvider>`.
 */
export function TerrainLayer(): ReactNode {
    const terrainConfig = useTerrainState((s) => s.terrainConfig);
    const { setDrape } = useTerrainActions();

    const layers = useMemo(() => {
        if (!terrainConfig) return [];
        return [
            new DeckTerrainLayer({
                id: "terrain-visual",
                operation: terrainConfig.textureUrl
                    ? ("terrain+draw" as const)
                    : ("terrain" as const),
                pickable: false,
                elevationData: terrainConfig.elevationUrl,
                texture: terrainConfig.textureUrl,
                meshMaxError: terrainConfig.meshMaxError,
                elevationDecoder: terrainConfig.elevationDecoder,
            }) as Layer,
        ];
    }, [terrainConfig]);

    useRegisterLayers("terrain", layers, DECK_PRIORITY_TERRAIN);

    // This is trying to correct an issue where drape isn't registered correctly when passing the extension for the first time.
    // This is not actually how the extension is supposed to function, the comment implies this is normal behavior and it is functioning correctly.
    // There is a cleaner solution to this (create two seperate layers), but this problem is being caused by a current bug with the _TerrainExtension
    // that is worked on being fixed:
    // Original issue: https://github.com/visgl/deck.gl/issues/8450
    // Linked MR: https://github.com/visgl/deck.gl/issues/8450

    // Two-frame warmup: after the terrain mesh is registered (frame 1),
    // wait one RAF so it begins loading, then enable draping (frame 2).
    // Respects the user's persisted preference — if they explicitly
    // disabled drape, the warmup skips auto-enabling it.
    useEffect(() => {
        if (!terrainConfig) return;

        const raf = requestAnimationFrame(() => {
            setDrape(useMapStore.getState().drape);
        });
        return () => cancelAnimationFrame(raf);
    }, [setDrape, terrainConfig]);

    return null;
}
