// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * Diffed appliers — the ONLY writers of the map cursor, the gesture locks and
 * the hover policy. Each subscribes to what its derivation reads, recomputes
 * the pure answer, and applies only the difference. Derivation beats
 * refcounting: overlapping claimants cannot fight a pure function, an unmount
 * can never leave a lock unbalanced, and a surface cannot forget to release
 * the hover it held — ownership moving on releases it.
 */
import type { InteractionHost } from "../contract";
import { getInteractionState, subscribeInteraction } from "../session/interaction.store";
import { deriveCursor } from "./derive/cursor";
import { deriveHoverPolicy } from "./derive/hover-policy";
import { deriveLocks } from "./derive/locks";
import { currentPointerOwner, subscribePointerOwnership } from "./pointer-owners";

export function attachAppliers(host: InteractionHost): () => void {
    const { sources, actions, environment } = host;

    let cursor: string | null = null;
    let panLocked = false;
    let dblClickZoomLocked = false;
    let hoverClearSuppressed = false;

    const apply = () => {
        const { session } = getInteractionState();

        const hover = deriveHoverPolicy(currentPointerOwner());
        if (hover.suppressClear !== hoverClearSuppressed) {
            hoverClearSuppressed = hover.suppressClear;
            actions.suppressHoverClear(hoverClearSuppressed);
        }

        const nextCursor = deriveCursor(session, environment.tool, {
            hoveringPickable: sources.hoveringPickable(),
        });
        if (nextCursor !== cursor) {
            cursor = nextCursor;
            actions.setCursor(nextCursor);
        }

        const locks = deriveLocks(session, environment.tool, {
            hoveringElement: sources.hoveringElement(),
        });
        if (locks.panLocked !== panLocked) {
            panLocked = locks.panLocked;
            if (panLocked) actions.disablePan();
            else actions.enablePan();
        }
        if (locks.dblClickZoomLocked !== dblClickZoomLocked) {
            dblClickZoomLocked = locks.dblClickZoomLocked;
            if (dblClickZoomLocked) actions.disableDoubleClickZoom();
            else actions.enableDoubleClickZoom();
        }
    };

    const unsubInteraction = subscribeInteraction(apply);
    const unsubOwnership = subscribePointerOwnership(apply);
    const unsubHover = sources.subscribeHover(apply);
    apply();

    return () => {
        unsubInteraction();
        unsubOwnership();
        unsubHover();
        // Leave the map unlocked, hover clearing free, and the cursor neutral on teardown.
        if (panLocked) actions.enablePan();
        if (dblClickZoomLocked) actions.enableDoubleClickZoom();
        if (hoverClearSuppressed) actions.suppressHoverClear(false);
        actions.setCursor("default");
    };
}
