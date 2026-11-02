// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useState, useCallback } from "react";
import type { ViewState } from "react-map-gl/maplibre";
import Logger from "js-logger";

const logger = Logger.get("map");

const VIEWPORT_STORAGE_KEY = "map-viewport-state";

export interface PersistedViewport {
    longitude: number;
    latitude: number;
    zoom: number;
    bearing: number;
    pitch: number;
}

/**
 * Hook to manage persisted map viewport state
 * Stores viewport in sessionStorage and restores it during the current browser session
 *
 * The initial view preference from user preferences is only used when there's no session data
 * After that, sessionStorage takes precedence to maintain user's position within the session
 * When the browser tab is closed, the viewport resets to the user preference or default
 */
export function usePersistedViewport(
    preferenceInitialView: PersistedViewport | null,
    fallbackInitialView: PersistedViewport,
) {
    // Check sessionStorage once (it's only set within the current browser session)
    const [sessionViewport] = useState<PersistedViewport | null>(() => {
        try {
            const stored = sessionStorage.getItem(VIEWPORT_STORAGE_KEY);
            if (stored) {
                const parsed = JSON.parse(stored) as PersistedViewport;
                // Validate that parsed data has required fields
                if (
                    typeof parsed.longitude === "number" &&
                    typeof parsed.latitude === "number" &&
                    typeof parsed.zoom === "number"
                ) {
                    return parsed;
                }
            }
        } catch (err) {
            if (import.meta.env.DEV) logger.warn("Failed to parse stored viewport:", err);
        }
        return null;
    });

    // Get initial viewport: sessionStorage > preference > fallback
    // This is reactive to preferenceInitialView changes (when preferences load)
    const initialViewState = sessionViewport ?? preferenceInitialView ?? fallbackInitialView;

    // Save viewport to sessionStorage
    const persistViewport = useCallback((viewState: Partial<ViewState>) => {
        try {
            const viewport: PersistedViewport = {
                longitude: viewState.longitude ?? 0,
                latitude: viewState.latitude ?? 0,
                zoom: viewState.zoom ?? 1,
                bearing: viewState.bearing ?? 0,
                pitch: viewState.pitch ?? 0,
            };
            sessionStorage.setItem(VIEWPORT_STORAGE_KEY, JSON.stringify(viewport));
        } catch (err) {
            if (import.meta.env.DEV) logger.warn("Failed to persist viewport:", err);
        }
    }, []);

    return { initialViewState, persistViewport };
}

/**
 * Clear persisted viewport (useful when user explicitly sets a new initial view preference)
 */
export function clearPersistedViewport() {
    try {
        sessionStorage.removeItem(VIEWPORT_STORAGE_KEY);
    } catch (err) {
        if (import.meta.env.DEV) logger.warn("Failed to clear persisted viewport:", err);
    }
}
