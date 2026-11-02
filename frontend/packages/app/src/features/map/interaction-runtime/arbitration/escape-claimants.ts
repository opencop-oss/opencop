// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * The Escape channel's claimant registry — WHO may receive an Escape press.
 *
 * A **claimant** is a party that may receive the key, registered as
 * `{ isActive(), dismiss() }` into a **slot**: a named position in the closed
 * order the ladder owns (`derive/keyboard-ladder.ts`). Claiming is open —
 * a feature registers into a slot from wherever it composes — and ordering is
 * closed: a claimant never chooses where its slot sits, and the arbiter never
 * learns what a `dismiss()` does. The router asks each slot `isActive()`, the
 * pure ladder names the winner, and the winner's `dismiss()` runs. Nothing
 * else about the party reaches this module.
 *
 * One claimant per slot. The four slots today: the session (registered by the
 * runtime's own composition point, `../create.ts`), the open map surface
 * (`./surface.ts`), the map selection (the map feature) and the area selection
 * (the selection feature). The foreign-overlay rung above them all never
 * registers — a dialog or a combobox has no reason to know the map exists —
 * and is the router's own DOM sniff.
 */
import Logger from "js-logger";
import type { EscapeSlot } from "./derive/keyboard-ladder";

export type { EscapeSlot } from "./derive/keyboard-ladder";

export interface EscapeClaimant {
    /** Whether this party holds something Escape should reach right now. */
    isActive(): boolean;
    /** Escape reached this party: dismiss what it holds. */
    dismiss(): void;
}

const logger = Logger.get("interaction");

const claimants = new Map<EscapeSlot, EscapeClaimant>();

/**
 * Register the claimant for a slot. Returns the disposer that withdraws it.
 * A second registration into an occupied slot replaces the first (logged in
 * DEV — two parties for one slot is a composition mistake); a stale disposer
 * cannot evict the claimant that replaced it.
 */
export function registerEscapeClaimant(slot: EscapeSlot, claimant: EscapeClaimant): () => void {
    if (import.meta.env.DEV && claimants.has(slot)) {
        logger.warn(`escape slot "${slot}" re-registered; the previous claimant is replaced.`);
    }
    claimants.set(slot, claimant);
    return () => {
        if (claimants.get(slot) === claimant) claimants.delete(slot);
    };
}

/** Whether the slot's claimant holds something — false for an empty slot. */
export function isEscapeSlotActive(slot: EscapeSlot): boolean {
    return claimants.get(slot)?.isActive() ?? false;
}

/** Run the slot's `dismiss()`. Returns false for an empty slot. */
export function dismissEscapeSlot(slot: EscapeSlot): boolean {
    const claimant = claimants.get(slot);
    if (!claimant) return false;
    claimant.dismiss();
    return true;
}
