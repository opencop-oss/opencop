// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useMapStore } from "~/features/map/core/map.store";
import type { PickResponse } from "@opencop/contributions";
import { useMapbarStore } from "~/features/mapbar/mapbar.store";
import { WATCHBOX_PANEL_ID } from "./watchbox-panel.policy";
import { useWatchboxPanelStore } from "./watchbox-panel.store";
import { WATCHBOX_DRAFT_KEY } from "./watchbox.store";

/**
 * What a click on a saved watchbox does: open the Watchbox panel on it.
 * Registered by `installWatchbox` under the watchbox store's id, so the click
 * reaches here only while nothing owns the pointer and no element sits under
 * it — a symbol inside a watchbox is still the symbol's click.
 */
export const watchboxPickResponse: PickResponse = {
    onClick: (target) => {
        const watchboxId = target.hit.ref.key;
        // The watchbox being drawn is not a saved one: there is nothing to open.
        if (watchboxId === WATCHBOX_DRAFT_KEY) return;
        useMapbarStore.getState().openPanel(WATCHBOX_PANEL_ID);
        useWatchboxPanelStore.getState().select(watchboxId);
    },
};

/**
 * Keeps the panel's selected watchbox in step with the rest of map
 * selection: picking an element, or clicking empty ground, clears it. A
 * watchbox's own click never runs this — it doesn't touch the map's
 * selection — so opening one leaves it selected until something else does.
 *
 * Subscribed to `dismissTick` as well as `selected`: an empty click when
 * nothing was already selected leaves `selected` at `null` on both sides of
 * the click, so it alone wouldn't signal the click happened.
 */
export function attachWatchboxSelectionSync(): () => void {
    return useMapStore.subscribe((state, previous) => {
        if (state.selected !== previous.selected || state.dismissTick !== previous.dismissTick) {
            useWatchboxPanelStore.getState().select(null);
        }
    });
}
