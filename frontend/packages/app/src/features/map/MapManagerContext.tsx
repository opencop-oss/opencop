// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { createContext, use, useEffect, useState, type ReactNode } from "react";
import { useMap } from "react-map-gl/maplibre";
import { MapManager } from "./MapManager";
import {
    _useIngestContext,
    createActions,
    type ElementStore,
    type LocalSource,
} from "@opencop/ingest";
import { registerPerfBridge } from "./perf-bridge";
import { createInteractionRuntime } from "./interaction-runtime/create";
import type { InteractionRuntime } from "./interaction-runtime/contract";
import { createInteractionEnvironment } from "./interaction-environment";
import { installInteractionDebugBridge } from "./interaction-debug-bridge";
import { attachMapSelectionEscape, attachSelectionPolicy } from "./selection.policy";
import { attachHistoricalModePolicy } from "./historical-mode.policy";
import { attachStackedObjectSelectionSurface } from "./stacked-object-selection.store";

/**
 * Construct the map's long-lived pieces once: the pipeline, the manager and the
 * interaction runtime. Construction only — nothing binds here. The manager
 * needs element actions for the runtime's custody sink, and those route local
 * elements through `LocalSource`, so the ingest context's source comes along.
 */
function initMapManager(
    elementStore: ElementStore,
    localSource: LocalSource,
): {
    manager: MapManager;
    interaction: InteractionRuntime;
} {
    const actions = createActions(elementStore, localSource);
    const manager = new MapManager(elementStore, actions);
    const interaction = createInteractionRuntime();

    if (import.meta.env.DEV) {
        registerPerfBridge(elementStore);
        installInteractionDebugBridge(manager);
    }

    return { manager, interaction };
}

const MapManagerContext = createContext<MapManager | null>(null);

export function useMapManager(): MapManager {
    const ctx = use(MapManagerContext);
    if (!ctx) throw new Error("useMapManager must be used within a MapManagerProvider.");
    return ctx;
}

export function MapManagerProvider({ children }: { children: ReactNode }) {
    // TODO: REMOVE USE OF HOOK.
    const { store, localSource } = _useIngestContext();
    const [{ manager, interaction }] = useState(() => initMapManager(store, localSource));
    const { current: mapRef } = useMap();

    useEffect(() => {
        if (!mapRef) return;
        const map = mapRef.getMap();
        // The runtime binds before the map: its `onMap` subscriptions queue in
        // the manager until `bindMap` flushes them, and a session can never
        // see a manager without a runtime.
        manager.bindInteraction(interaction, createInteractionEnvironment());
        manager.bindMap(map);
        manager.init();
        // The map feature's own reactions — the selection following a custody
        // session, the disambiguation popover declaring itself a map surface,
        // the live↔historical flip dropping the interaction state — and its
        // claim on Escape for the map selection. Attached by the feature that
        // owns them, not by the runtime; detached after `dispose()`, so they
        // still hear the session that teardown ends.
        const detachPolicies = [
            attachSelectionPolicy(manager),
            attachStackedObjectSelectionSurface(),
            attachMapSelectionEscape(manager),
            attachHistoricalModePolicy(manager),
        ];
        if (import.meta.env.DEV && window.__opencop_perf) {
            window.__opencop_perf._map = map;
        }
        return () => {
            manager.dispose();
            for (const detach of detachPolicies) detach();
        };
    }, [interaction, manager, mapRef]);

    return <MapManagerContext value={manager}>{children}</MapManagerContext>;
}
