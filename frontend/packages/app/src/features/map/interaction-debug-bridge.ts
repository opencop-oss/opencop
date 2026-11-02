// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * The e2e/debug bridge — the live interaction session, the two pick stores,
 * the map store, the commands and the manager, synchronously readable from
 * `window.__opencop_interaction`. DEV-only, zero production cost; the e2e
 * interaction helpers are its reader. Installed from the map's composition
 * point because it exposes feature stores the runtime itself may not import.
 */
import { useMapStore } from "./core/map.store";
import { useStackedObjectSelectionStore } from "./stacked-object-selection.store";
import { useAreaSelectionStore } from "~/features/selection/area-selection.store";
import {
    getInteractionState,
    subscribeInteraction,
} from "./interaction-runtime/session/interaction.store";
import * as commands from "./interaction-runtime/session/commands";
import { currentSurface } from "./interaction-runtime/arbitration/surface";
import { currentPointerOwner } from "./interaction-runtime/arbitration/pointer-owners";
import type { MapManager } from "./MapManager";

export function installInteractionDebugBridge(mapManager: MapManager): void {
    (window as unknown as Record<string, unknown>).__opencop_interaction = {
        store: { getState: getInteractionState, subscribe: subscribeInteraction },
        stacked: useStackedObjectSelectionStore,
        areaSelection: useAreaSelectionStore,
        map: useMapStore,
        /** The open map surface — arbitration's slot, read on demand. */
        surface: currentSurface,
        /** Which slot owns the pointer now — `session`, `surface` or `ambient`. */
        pointer: currentPointerOwner,
        commands,
        // Kept under `deps` for the e2e helpers, which project through `deps.mapManager`.
        deps: { mapManager },
    };
}
