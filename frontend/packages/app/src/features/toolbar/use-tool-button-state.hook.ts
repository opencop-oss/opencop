// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useHistoricalMode } from "~/features/historical/data-plane/historical.store";
import { activate, exit } from "~/features/map/interaction-runtime/session/commands";
import { canChangeTool } from "~/features/map/interaction-runtime/session/derive/tool-change";
import { useInteraction } from "~/features/map/interaction-runtime/session/interaction.store";

/**
 * The state every toolbar tool button shares, whichever cluster renders it:
 * whether tools may be clicked, which descriptor is active, and the click
 * rule (the active tool toggles off; any other activates). The picker and the
 * interaction cluster differ only in which descriptors they list.
 *
 * `toolsDisabled` is the reactive read of `canChangeTool` — the same
 * derivation `canActivate` applies at click time — over a subscribed session,
 * so the buttons re-render when it changes. The imperative `canActivate` is
 * not callable here: it reads the stores through `getState()`, which would
 * leave a button stale until something else re-rendered it.
 */
export function useToolButtonState(): {
    /** Historical mode, an edit, or a draw with something in flight disable every tool. */
    toolsDisabled: boolean;
    /** The active draw's descriptor id, or null. */
    activeDrawId: string | null;
    isHistorical: boolean;
    handleToolClick: (id: string) => void;
} {
    const session = useInteraction((s) => s.session);
    const isHistorical = useHistoricalMode() === "historical";
    const toolsDisabled = !canChangeTool(session, { isHistorical });
    const activeDrawId = session.kind === "draw" ? session.descriptorId : null;

    function handleToolClick(id: string) {
        if (toolsDisabled) return;
        if (activeDrawId === id) exit();
        else activate(id);
    }

    return { toolsDisabled, activeDrawId, isHistorical, handleToolClick };
}
