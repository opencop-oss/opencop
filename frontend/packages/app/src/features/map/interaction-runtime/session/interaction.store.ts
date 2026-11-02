// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * The interaction session store — the ONE machine behind map tools, measure,
 * lasso, and ambient selection.
 *
 * State only; the session SHAPES live in `session.ts` (host-internal, pure).
 * All domain-lifecycle writes (create/place/transfer/delete the in-flight
 * element and its store draft) happen in the named commands in
 * `commands.ts`, executed synchronously, under the session's custody of the
 * element when the tool has one (`custody.ts`). React effects subscribe and
 * release; nothing in a React cleanup may destroy domain state.
 *
 * Deliberately NOT packaged: the store is a module-identity-sensitive
 * singleton — packaged and plugin-importable it would join the federation
 * shared-singleton set or split-brain the session.
 */
import { useStore } from "zustand";
import { createStore } from "zustand/vanilla";
import type { EditSub, IdleSession, Session } from "./session";

export {
    bindingKey,
    isCustodyDraw,
    isCustodyEdit,
    isInFlight,
    sessionDraft,
    type Draft,
    type DrawSession,
    type EditSession,
    type EditSub,
    type IdleSession,
    type Session,
} from "./session";
export interface InteractionState {
    readonly session: Session;
    /**
     * Mutable-Element fence: bumped on every geometry mutation of a session
     * element. Any selector that renders session geometry selects `rev` too.
     */
    readonly rev: number;
}

export const IDLE: IdleSession = { kind: "idle" };

const NO_SUB: EditSub = { hotspot: null, dragging: null };

const initialInteractionState: InteractionState = {
    session: IDLE,
    rev: 0,
};

// Module-private, and a vanilla store rather than a bound hook: the handle
// never leaves this file, so `setState` cannot be reached from a component,
// an effect or a test.
const store = createStore<InteractionState>(() => initialInteractionState);

/** Subscribe a component to a slice of interaction state. */
export function useInteraction<T>(selector: (state: InteractionState) => T): T {
    return useStore(store, selector);
}

/** Synchronous snapshot for non-React readers (the runtime, `MapManager`). */
export function getInteractionState(): InteractionState {
    return store.getState();
}

/** Change notification for non-React readers; returns the unsubscribe. */
export function subscribeInteraction(
    listener: (state: InteractionState, previous: InteractionState) => void,
): () => void {
    return store.subscribe(listener);
}

/**
 * The store's writers. `commands.ts` is the only production importer and
 * `~/test/interaction` the only test-side one — enforced by the
 * `no-restricted-syntax` rule in `eslint.config.js`, pinned by
 * `eslint.boundaries.test.ts`. Everything else reads.
 */
export function setSession(session: Session): void {
    store.setState({ session });
}

/** Bump the mutable-element fence after a session element's geometry changed. */
export function bumpRev(): void {
    store.setState((s) => ({ rev: s.rev + 1 }));
}

/** Fresh edit substate. */
export function emptySub(): EditSub {
    return NO_SUB;
}

// Epoch counter — global and monotonic so a (kind, epoch) pair never repeats.
let epochCounter = 0;
export function nextEpoch(): number {
    return ++epochCounter;
}
