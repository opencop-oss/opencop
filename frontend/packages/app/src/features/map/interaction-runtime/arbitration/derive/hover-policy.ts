// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * The hover policy — ONE pure derivation over who owns the pointer, applied
 * by the diffed applier (`../appliers.ts`).
 *
 * Hover is observational: it runs in every session and no session may
 * suppress it. One presentation rule is nonetheless the pointer owner's to
 * decide. A map-owned surface over the canvas — the disambiguation popover,
 * the context menu — intercepts the mouse, so deck reports hover-on-nothing
 * beneath its DOM; applied, that clear would drop the highlight on the very
 * row the operator is choosing from. So while the SURFACE owns the pointer a
 * hover clear is withheld, and when ownership moves on the clear the surface
 * left standing is applied — the release every surface used to perform by
 * hand, and forget.
 *
 * Host policy, kept pure over the ownership record.
 */
import type { PointerSlot } from "./pointer-ladder";

export interface HoverPolicy {
    /** Withhold a hover clear — the pointer is over a surface's DOM, not the canvas. */
    readonly suppressClear: boolean;
}

/** Derive the hover policy for the slot that owns the pointer now. */
export function deriveHoverPolicy(owner: PointerSlot | null): HoverPolicy {
    return { suppressClear: owner === "surface" };
}
