// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import {
    createContext,
    use,
    useCallback,
    useEffect,
    useMemo,
    useState,
    type ReactNode,
} from "react";
import { MapboxOverlay } from "@deck.gl/mapbox";
import { useControl } from "react-map-gl/maplibre";
import type { Layer } from "@deck.gl/core";
import { useMapManager } from "./MapManagerContext";
import type { RegisterLayers } from "./deck-layer-priorities";
import type { Device } from "@luma.gl/core";
import Logger from "js-logger";

type DeckLayerContextValue = {
    registerLayers: RegisterLayers;
    device: Device;
    deck: MapboxOverlay;
};

const DeckLayerContext = createContext<DeckLayerContextValue | null>(null);

/**
 * Owns the single `MapboxOverlay` deck.gl instance and exposes registration function for layers.
 *
 * Visual layer components (`SceneLayer`, `EditHandlesLayer`) and
 * side-effect components (`EventClipboard`) render as children to compose
 * the deck.gl layer stack declaratively.
 */
export function DeckLayerProvider({ children }: { children: ReactNode }): ReactNode {
    const logger = Logger.get("map");
    const mapManager = useMapManager();
    const [registry] = useState<Map<string, { layers: Layer[]; priority: number }>>(
        () => new Map(),
    );
    const [device, setDevice] = useState<Device | null>(null);
    const [error, setError] = useState<Error | null>(null);
    const [deck] = useState(
        () =>
            new MapboxOverlay({
                pickingRadius: 3,
                interleaved: false,
                layers: [],
                // @ts-expect-error — internal deck.gl prop, not in public types
                _enableGPUTimings: true,
                onDeviceInitialized: (device) => setDevice(device),
                onError: (error, layer) => {
                    if (layer) logger.error(`[deck] layer "${layer.id}" failed`, error);
                    else setError(error);
                },
            }),
    );

    if (error) throw error;

    const registerLayers = useCallback<RegisterLayers>(
        (id, layers, priority) => {
            const active = layers.filter((l): l is Layer => l != null);
            if (active.length === 0) {
                registry.delete(id);
            } else {
                registry.set(id, { layers: active, priority });
            }

            const sorted = [...registry.values()]
                .sort((a, b) => a.priority - b.priority)
                .flatMap((entry) => entry.layers);
            if (import.meta.env.DEV && window.__opencop_perf)
                performance.mark("opencop:deck-setprops");
            logger.debug("Rendering layers: ", sorted);
            deck.setProps({ layers: sorted });
        },
        [logger, deck, registry],
    );

    useControl(
        () => {
            mapManager.bindDeck(deck);
            // Expose overlay for perf tests to read deck.metrics (DEV only)
            if (import.meta.env.DEV) window.__opencop_deck_overlay = deck;
            return deck;
        },
        () => {
            if (import.meta.env.DEV) delete window.__opencop_deck_overlay;
        },
    );

    const value = useMemo(
        () => (device ? { registerLayers, device, deck } : null),
        [registerLayers, device, deck],
    );

    if (!value) return null;

    return <DeckLayerContext value={value}>{children}</DeckLayerContext>;
}

function useDeckLayerContext(): DeckLayerContextValue {
    const ctx = use(DeckLayerContext);
    if (!ctx) throw new Error("useDeckLayerContext must be used within DeckLayerProvider.");
    return ctx;
}

export const useDevice = () => useDeckLayerContext().device;
export const useRegister = () => useDeckLayerContext().registerLayers;

export function useRegisterLayers(
    id: string,
    layers: (Layer | null | undefined)[],
    priority: number,
): void {
    const { registerLayers: register } = useDeckLayerContext();

    useEffect(() => {
        register?.(id, layers, priority);
        return () => register?.(id, [], priority);
    }, [id, layers, priority, register]);
}
