// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { subscribeSessionLifecycle } from "~/features/map/interaction-runtime/session/session-lifecycle";
import { registerEscapeClaimant } from "~/features/map/interaction-runtime/arbitration/escape-claimants";
import { useMapStore } from "./core/map.store";
import type { MapManager } from "./MapManager";

/**
 * How the selection follows an interaction session — the map feature's
 * reaction to the session lifecycle, written through `MapManager` like every
 * other selection write. Custody is the whole test: a session over a
 * persisted element is what the selection and the events panel show, and a
 * session-local product (a measurement, a lasso ring, a watchbox polygon)
 * never touches either.
 *
 * - A custody draw dismisses the current selection — and, through the
 *   dismiss tick, any open panel — as it starts: its completion will select
 *   its product. Arming a lasso again leaves the last pick alone.
 * - A custody edit selects its element the moment it begins, so the events
 *   panel has something to show on its first render.
 * - Cancelling a custody edit dismisses the selection; saving leaves the
 *   saved element selected, and leaving any other way (a tool switch, a
 *   teardown) leaves the selection to whoever comes next.
 *
 * Closing map-owned surfaces on a session start is no longer here: that is a
 * general rule the session commands apply themselves, once for every surface
 * (see `interaction-runtime/surface.ts`).
 */
export function attachSelectionPolicy(mapManager: MapManager): () => void {
    return subscribeSessionLifecycle((event) => {
        switch (event.type) {
            case "activated":
                if (event.custody) mapManager.handleEmptyClick();
                return;
            case "edit-started":
                if (event.session.custody) mapManager.selectElement(event.session.product);
                return;
            case "ended":
                if (
                    event.session.kind === "edit" &&
                    event.session.custody &&
                    event.how === "cancelled"
                ) {
                    mapManager.handleEmptyClick();
                }
                return;
        }
    });
}

/**
 * The map selection as an Escape claimant — the `map-selection` slot of the
 * runtime's Escape channel, registered by the map feature directly with
 * arbitration (naming a slot in the closed order is not a contribution, so
 * the member is not on the contribution context). Active while an element is
 * selected; dismissed the way an empty-space click clears it, so the mapbar's
 * panels hear the dismissal too. The events panel is not told anything: it is
 * the presentation of the selection and follows it.
 */
export function attachMapSelectionEscape(mapManager: MapManager): () => void {
    return registerEscapeClaimant("map-selection", {
        isActive: () => useMapStore.getState().selected !== null,
        dismiss: () => mapManager.handleEmptyClick(),
    });
}
