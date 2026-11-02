// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * The cursor — ONE pure derivation, applied by one diffed effector
 * (`../appliers.ts`). Nothing else writes the map cursor.
 *
 * The cursor is the one input-surface choice a tool gets to make: while its
 * session is drawing (waiting for the first click included) the canvas shows
 * the descriptor's `cursor`, crosshair by default. An edit session derives its
 * cursor from what is under the pointer, for every tool alike; idle shows a
 * pointer over a pickable map object, including a saved watchbox.
 *
 * Host policy, kept pure over the session snapshot + environment.
 */
import type { Session } from "../../session/session";
import type { DescriptorResolver } from "../../contract";

export interface CursorEnv {
    /** A pickable map object is under the pointer (hover state). */
    readonly hoveringPickable: boolean;
}

const DEFAULT_DRAW_CURSOR = "crosshair";

/** Cursor for edit hover/drag substate. */
function editSubCursor(sub: { hotspot: string | null; dragging: string | null }): string | null {
    if (sub.dragging === "translate") return "grabbing";
    if (sub.dragging) return "move";
    if (sub.hotspot === "handle" || sub.hotspot === "boundary") return "move";
    if (sub.hotspot === "body") return "grab";
    return null;
}

/** Derive the cursor the map canvas should show. */
export function deriveCursor(
    session: Session,
    resolve: DescriptorResolver,
    env: CursorEnv,
): string {
    switch (session.kind) {
        case "idle":
            return env.hoveringPickable ? "pointer" : "default";
        case "edit":
            return editSubCursor(session.sub) ?? "default";
        case "draw":
            return resolve(session.descriptorId)?.cursor ?? DEFAULT_DRAW_CURSOR;
    }
}
