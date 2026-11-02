// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * The pointer channel's owner registry — WHO holds the pointer's bindings.
 *
 * An **owner** is a party that may hold the pointer, registered into a
 * **slot**: a named position in the closed order the ladder owns
 * (`derive/pointer-ladder.ts`). It says whether it owns the pointer now, the
 * identity of the handler set it would bind (the runner rebinds when that
 * changes while the owner stays active), the set itself, and how to hear that
 * any of that changed. The runner (`runner.ts`) binds exactly the winning
 * owner's set and disposes the previous one — it never learns what the
 * handlers do.
 *
 * One owner per slot; three slots; one registrant. The runtime's composition
 * point (`../create.ts`) seats all three at once through
 * {@link registerPointerOwners}, which takes the whole closed set as one
 * record — there is no way to register a single owner into a slot, so "a
 * feature never registers an owner" is a fact of the API, not a convention.
 * The three: the session (its draw or edit bindings, `../session/bind.ts`),
 * the open map surface (`surface.ts`) and ambient (`sources/ambient-pointer.ts`).
 * A feature registers a **pick response** for the things it draws
 * (`pick-responses.ts`), and ambient hands it the click that lands on one.
 */
import Logger from "js-logger";
import type { InputSubscription, InteractionHost } from "../contract";
import { POINTER_ORDER, resolvePointerOwner, type PointerSlot } from "./derive/pointer-ladder";

export type { PointerSlot } from "./derive/pointer-ladder";

export interface PointerOwner {
    /** Whether this party owns the pointer right now. */
    isActive(): boolean;
    /**
     * The identity of the handler set it would bind now. The runner rebinds
     * when it changes while the owner stays active — the session's changes
     * with its kind and epoch; a party with one set for its whole active life
     * returns a constant.
     */
    identity(): string;
    /** Bind the handler set. Called once per identity; the runner disposes it before binding the next. */
    bind(host: InteractionHost): InputSubscription[];
    /** Hear that `isActive` or `identity` may have changed. */
    subscribe(listener: () => void): () => void;
}

const logger = Logger.get("interaction");

const owners = new Map<PointerSlot, PointerOwner>();
const ownerUnsubscribes = new Map<PointerSlot, () => void>();
const listeners = new Set<() => void>();

function notify(): void {
    for (const listener of [...listeners]) listener();
}

/**
 * Seat the pointer's owners — every slot of the closed order, in one call.
 * Returns the disposer that withdraws them all. A second set replaces the
 * first slot by slot (logged in DEV — two runtimes seating owners is the
 * composition mistake `../host.ts` turns into a throw), and a stale disposer
 * cannot evict the owners that replaced its own. The ownership listeners hear
 * every change, so the runner re-resolves.
 */
export function registerPointerOwners(
    owners: Readonly<Record<PointerSlot, PointerOwner>>,
): () => void {
    const disposers = POINTER_ORDER.map((slot) => registerPointerOwner(slot, owners[slot]));
    return () => {
        for (const dispose of disposers) dispose();
    };
}

function registerPointerOwner(slot: PointerSlot, owner: PointerOwner): () => void {
    if (import.meta.env.DEV && owners.has(slot)) {
        logger.warn(`pointer slot "${slot}" re-registered; the previous owner is replaced.`);
    }
    ownerUnsubscribes.get(slot)?.();
    owners.set(slot, owner);
    ownerUnsubscribes.set(slot, owner.subscribe(notify));
    notify();
    return () => {
        if (owners.get(slot) !== owner) return;
        ownerUnsubscribes.get(slot)?.();
        ownerUnsubscribes.delete(slot);
        owners.delete(slot);
        notify();
    };
}

/** The slot that owns the pointer now — the ladder over each slot's `isActive()` — or null when no owner is active. */
export function currentPointerOwner(): PointerSlot | null {
    const active = Object.fromEntries(
        POINTER_ORDER.map((slot) => [slot, owners.get(slot)?.isActive() ?? false]),
    ) as Record<PointerSlot, boolean>;
    return resolvePointerOwner(active);
}

/** The owner registered for a slot, if any. */
export function pointerOwnerOf(slot: PointerSlot): PointerOwner | undefined {
    return owners.get(slot);
}

/** Hear that the answer to "who owns the pointer" (or an owner's identity) may have changed. */
export function subscribePointerOwnership(listener: () => void): () => void {
    listeners.add(listener);
    return () => {
        listeners.delete(listener);
    };
}
