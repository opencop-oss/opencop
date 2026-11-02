// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { createContext, use, useMemo, useState, type ReactNode } from "react";

interface ActiveBasemap {
    /**
     * Id of the tile whose style the map is showing, or null while the map is on the local fallback
     * style because no basemap tile is offered.
     */
    tileId: string | null;
    /**
     * Records the tile whose style has just been handed to `setStyle`. MapLibre loads that style
     * asynchronously, so this is the requested basemap, not proof that it rendered. A switch that
     * fails before `setStyle` never calls it, so the value stays on the tile still shown.
     */
    setActiveBasemap(tileId: string | null): void;
}

const ActiveBasemapContext = createContext<ActiveBasemap | null>(null);

/**
 * Which basemap tile is on the map. Lives and dies with the map: mount it inside the map tree, seeded
 * with the tile the map opens on. That resolved selection stays a live input, not just a seed: when
 * the tile list or the user's default changes, `useInitialMapStyle` re-resolves and the map restyles
 * on the same value, so the credit follows it here. Callers move it with `setActiveBasemap`. Not
 * persisted: the selection itself already lives in sessionStorage and the user's default-tile
 * preference.
 */
export function ActiveBasemapProvider({
    resolvedTileId,
    children,
}: {
    resolvedTileId: string | null;
    children: ReactNode;
}) {
    const [tileId, setTileId] = useState(resolvedTileId);
    // The resolved selection is a live input, not just a seed: when it changes the map restyles on
    // it, so the value follows; otherwise the picker's write stands. Adjusted during render, the way
    // React recommends for state that follows a prop, so there is no effect to order.
    const [followed, setFollowed] = useState(resolvedTileId);
    if (resolvedTileId !== followed) {
        setFollowed(resolvedTileId);
        setTileId(resolvedTileId);
    }
    const value = useMemo(() => ({ tileId, setActiveBasemap: setTileId }), [tileId]);
    return <ActiveBasemapContext value={value}>{children}</ActiveBasemapContext>;
}

function useActiveBasemap(): ActiveBasemap {
    const ctx = use(ActiveBasemapContext);
    if (!ctx) throw new Error("useActiveBasemap must be used within an ActiveBasemapProvider.");
    return ctx;
}

/** The id of the tile on the map, or null on the local fallback style. */
export function useActiveBasemapTileId(): string | null {
    return useActiveBasemap().tileId;
}

export function useSetActiveBasemap(): ActiveBasemap["setActiveBasemap"] {
    return useActiveBasemap().setActiveBasemap;
}
