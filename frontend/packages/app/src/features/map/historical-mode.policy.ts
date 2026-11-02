// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useHistoricalStore } from "~/features/historical/data-plane/historical.store";
import { resetInteraction } from "~/features/map/interaction-runtime/session/commands";
import type { MapManager } from "./MapManager";

/**
 * The map's reaction to the live ↔ historical flip: drop selection, hover, the
 * area selection and the stacked-element picker, and end any interaction
 * session. Interaction state is keyed by element key, and the two planes' keys
 * never resolve against each other — a historical key (`source id:chunkIndex`)
 * means nothing in live mode. Selection is also URL-persisted, so without this
 * a stale key would survive a reload.
 *
 * A store subscription, so it fires on an actual transition only — never on
 * mount, where an unconditional reset would wipe a URL-restored selection
 * before `SelectionRestorer` can apply it.
 */
export function attachHistoricalModePolicy(mapManager: MapManager): () => void {
    return useHistoricalStore.subscribe((state, previous) => {
        if (state.mode === previous.mode) return;
        mapManager.resetInteractionState();
        resetInteraction();
    });
}
