// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useEffect } from "react";
import { useMap } from "react-map-gl/maplibre";
import { MAPSTORE_URL_STORAGE, useMapStore } from "./core/map.store";
import { parseElementKey } from "@opencop/elements";
import { useMapManager } from "./MapManagerContext";
import Logger from "js-logger";

const logger = Logger.get("map");

// Syncing of ViewState transitions to MapLibre, will be changed when using the DeckGL component.
// Because the react-map-gl component cannot be controlled, we have to wire the subscriptions manually,
// this is not the case with the DeckGL component. MapLibre also does not have the concept of `FlyInterpolators`
// like deck.gl does. `flyTo` state will be replaced with the interpolators instead of target objects.
/**
 * Syncs the `MapStore` to MapLibre due to current limitations. Enables `flyTo`, `jumpTo`, `fitBounds` and url history for map state.
 */
export function MapStoreSync() {
    const { current: mapRef } = useMap();
    const mapManager = useMapManager();

    // Transient subscription to transitions in ViewState.
    useEffect(() => {
        const map = mapRef?.getMap();
        if (!map) return;

        const subs = [
            useMapStore.subscribe(
                (state) => state.flyToTarget,
                (target) => {
                    if (!target) return;
                    map.flyTo(target);
                },
            ),
            useMapStore.subscribe(
                (state) => state.jumpToTarget,
                (target) => {
                    if (!target) return;
                    map.jumpTo(target);
                },
            ),
            useMapStore.subscribe(
                (state) => state.fitBoundsTarget,
                (fitBounds) => {
                    if (!fitBounds) return;
                    map.fitBounds(fitBounds.bounds, fitBounds.options);
                },
            ),
        ];

        // Popstate handler for history. Listener is async because zustand's
        // `rehydrate()` returns a Promise — but browsers don't await listeners,
        // so a rejection here would surface as an unhandled rejection. Wrap the
        // body in try/catch and log; failed rehydrate just leaves the map
        // wherever it was.
        const handler = async () => {
            try {
                // Re-read URL into the store. `rehydrate()` is async — must await
                // before reading state, or `getState()` returns the pre-back camera
                // and we jumpTo the wrong position.
                await useMapStore.persistMap[MAPSTORE_URL_STORAGE].rehydrate();

                const { longitude, latitude, zoom, pitch, bearing, selected } =
                    useMapStore.getState();

                // After rehydration, imperatively move the map to match.
                // The store change alone won't move it — the map is uncontrolled.
                map.jumpTo({
                    center: [longitude, latitude],
                    zoom,
                    pitch,
                    bearing,
                });

                // Apply selection if needed
                const ref = parseElementKey(selected ?? "");
                if (ref) {
                    mapManager.restoreSelection(ref);
                } else {
                    mapManager.clearSelection();
                }
            } catch (err) {
                logger.warn("[MapStoreSync] popstate handler failed:", err);
            }
        };

        window.addEventListener("popstate", handler);

        return () => {
            subs.forEach((unsub) => unsub());
            window.removeEventListener("popstate", handler);
        };
    }, [mapRef, mapManager]);

    return null;
}
