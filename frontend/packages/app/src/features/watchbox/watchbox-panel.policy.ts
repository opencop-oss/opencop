// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { exit } from "~/features/map/interaction-runtime/session/commands";
import { getInteractionState } from "~/features/map/interaction-runtime/session/interaction.store";
import { useMapbarStore } from "~/features/mapbar/mapbar.store";
import { WATCHBOX_TOOL_ID } from "./watchbox.descriptor";

/** The mapbar panel id registered in `mapbar.config.tsx`. */
export const WATCHBOX_PANEL_ID = "watchbox";

/**
 * The one UI reaction watchbox owns, now that its own "Create watchbox"
 * control is what starts a draw: the panel is already open before any
 * watchbox session exists, so nothing needs to open it anymore. What still
 * needs a reaction is leaving — closing the panel, or switching to another
 * one — while a watchbox draw or a finished-but-unsaved polygon is live. Left
 * alone, that draft would sit behind a panel the operator can no longer see,
 * with no control left to reach it. `exit()` tears down whatever the session
 * holds, in flight or not, and is a no-op over an idle one, so it is safe to
 * call unconditionally once the descriptor check below passes.
 */
export function attachWatchboxPanelPolicy(): () => void {
    return useMapbarStore.subscribe((state, previous) => {
        if (previous.sidebarPanel !== WATCHBOX_PANEL_ID) return;
        if (state.sidebarPanel === WATCHBOX_PANEL_ID) return;
        const { session } = getInteractionState();
        if (session.kind !== "idle" && session.descriptorId === WATCHBOX_TOOL_ID) exit();
    });
}
