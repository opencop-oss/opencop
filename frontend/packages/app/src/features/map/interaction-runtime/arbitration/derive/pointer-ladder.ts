// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * Pointer-ladder RESOLUTION — which party owns the pointer, as a pure function
 * of which slots are active. The runner (`../runner.ts`) binds the winner's
 * handler set and nothing else; this file decides, the runner does.
 *
 * The written order (top wins):
 *   1. a live session — waiting, in flight, or editing → the session's bindings
 *      (`session/bind.ts`)
 *   2. an open map-owned SURFACE (the click-disambiguation popover, the map
 *      context menu) → the surface's: a primary click closes it
 *   3. ambient — always active: what a click means when nobody else owns the
 *      pointer, and the right-click that opens the context menu
 *
 * Unlike Escape, the pointer is not resolved per event. The winner is resolved
 * when a slot's activity changes, and the arbiter binds that owner's set until
 * the answer changes — "the second handler set does not exist" is the
 * guarantee, never "the second handler checks a flag".
 */

/**
 * The slots a party may own the pointer from (`../pointer-owners.ts`), in the
 * order the ladder ranks them. Closed: an owner never chooses where its slot
 * sits, and a feature never registers an owner — it registers a claimant for
 * the things it draws, which ambient dispatches to.
 */
export type PointerSlot = "session" | "surface" | "ambient";

export const POINTER_ORDER: readonly PointerSlot[] = ["session", "surface", "ambient"];

/** Resolve which slot owns the pointer — the first active one in the order; null when none is. */
export function resolvePointerOwner(
    active: Readonly<Record<PointerSlot, boolean>>,
): PointerSlot | null {
    return POINTER_ORDER.find((slot) => active[slot]) ?? null;
}
