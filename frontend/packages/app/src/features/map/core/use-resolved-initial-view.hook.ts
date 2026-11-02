// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useMemo } from "react";
import type { MapView } from "@opencop/api";
import { useMapStore } from "./map.store";
import { dummyState, type ViewState } from "./slices/view/view.slice";

/**
 * Resolve the camera to seed MapLibre with on `/map` mount.
 *
 * Priority chain:
 *   1. URL params — already hydrated into the store by `multiPersist` at module init
 *   2. In-memory store survivor — set by a prior `/map` mount in this tab. Zustand
 *      stores are module-level singletons, so SPA navigation away and back preserves
 *      the camera even though `MapView` unmounts
 *   3. `preferences.initialView.mapView` — user's saved default
 *   4. `undefined` — falls through to MapLibre's built-in default
 *
 * Pass `preferencesReady=false` while the preferences query is still loading so
 * the hook holds off resolving. Once `preferencesReady` is `true`, the value
 * settles and won't recompute until `preferredView` reference changes.
 *
 * **Implementation note:** reads `useMapStore.getState()` non-reactively inside
 * `useMemo`. We only need the initial value here; subsequent camera changes flow
 * through MapLibre `onMove` → store → `multiPersist` → URL, not through this hook.
 *
 * **Load-bearing invariant:** priority #1 (URL → store) works because
 * `multiPersist.storage.getItem` is synchronous on the URL path. If URL hydration
 * is ever made async, the priority chain collapses to in-memory-or-preferred and
 * deep-link entry breaks silently. Worth a guard test if/when that changes.
 */
export function useResolvedInitialView(
    preferencesReady: boolean,
    preferredView: MapView | undefined,
): ViewState | undefined {
    return useMemo<ViewState | undefined>(() => {
        if (!preferencesReady) return undefined;
        const stored = useMapStore.getState();
        const storeHydrated = stored.longitude !== dummyState.longitude;
        if (storeHydrated) {
            return {
                longitude: stored.longitude,
                latitude: stored.latitude,
                zoom: stored.zoom,
                pitch: stored.pitch,
                bearing: stored.bearing,
            };
        }
        return preferredView;
    }, [preferencesReady, preferredView]);
}
