// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * React-facing selectors over the interaction session — the humble mirrors.
 * Peripheral consumers (panels, toggles, toolbar chrome) read these instead
 * of re-deriving mode conjunctions.
 */
import type { DraftObject } from "@opencop/elements";
import { isCustodyDraw, isCustodyEdit, sessionDraft, useInteraction } from "./interaction.store";

/**
 * True while a CUSTODY draw is open — a draw whose product will enter the
 * live ElementStore. This is what the historical toggle and the mapbar's
 * draw-time rules key off: drawing into the live store has no meaning over a
 * historical snapshot, and its completion selects the product and pins the
 * events panel. A measurement, a lasso or a watchbox polygon is also a draw
 * session, but one whose product the store never sees — nothing it does
 * conflicts with a panel or a snapshot, so it is not "drawing" in this sense.
 */
export function useIsDrawing(): boolean {
    return useInteraction((s) => isCustodyDraw(s.session));
}

/**
 * True while a CUSTODY edit is open — an ElementStore element locked under
 * Save/Cancel, which the events panel is showing. This is what the panel
 * chrome, the mapbar toggles and the historical toggle key off. A finished
 * measurement is also an edit session, but one the runtime holds no custody
 * over: nothing is pending, so it is not "editing" in this sense.
 */
export function useIsEditing(): boolean {
    return useInteraction((s) => isCustodyEdit(s.session));
}

/** The active session's descriptor id, or null when idle. */
export function useActiveDescriptorId(): string | null {
    return useInteraction((s) => (s.session.kind === "idle" ? null : s.session.descriptorId));
}

/** Geometry-change fence — select alongside anything that renders session geometry. */
export function useInteractionRev(): number {
    return useInteraction((s) => s.rev);
}

/**
 * The object being drawn or adjusted (the draw draft or the edit session's
 * product), or null when idle. Resolved from the session alone — the edit
 * session holds its product, so no store lookup is involved.
 */
export function useSessionDraft(): DraftObject | null {
    return useInteraction((s) => sessionDraft(s.session));
}

/** The finished product under edit while `descriptorId`'s edit session is live; null otherwise. */
export function useSessionProductOf(descriptorId: string): DraftObject | null {
    return useInteraction((s) =>
        s.session.kind === "edit" && s.session.descriptorId === descriptorId
            ? s.session.product
            : null,
    );
}
