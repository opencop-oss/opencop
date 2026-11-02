// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * The runner — the pointer's arbiter. Binds the handler set of whichever slot
 * owns the pointer (`pointer-owners.ts`, ordered by `derive/pointer-ladder.ts`)
 * and rebinds when the owner, or the owner's binding identity, changes: a
 * session begins or ends, its epoch moves, a surface opens or closes. An
 * owner's substate (locked count, drag hotspot, vertices) never rebinds.
 *
 * Exactly one set is bound at a time, and the previous set is disposed before
 * the next is bound. Who owns the pointer is answered by which set is bound —
 * never by a handler checking a flag.
 *
 * A rebind runs synchronously inside the handler that changed the owner, so
 * the outgoing handlers are unsubscribed mid-dispatch. The host's
 * subscriptions go inert on `unsub()`, which is what keeps the click that
 * ended a session from also reaching the handlers it just retired.
 *
 * The runner contains no domain lifecycle: handlers call commands; commands
 * own custody. Unbinding disposes listeners and NOTHING else — disposal of
 * in-flight elements happens only through the session's custody.
 */
import type { InputSubscription, InteractionHost } from "../contract";
import { currentPointerOwner, pointerOwnerOf, subscribePointerOwnership } from "./pointer-owners";

/**
 * Attach the runner: bind the current owner's set and rebind on ownership
 * changes. Returns a disposer that unbinds everything.
 */
export function attachRunner(host: InteractionHost): () => void {
    let currentKey: string | null = null;
    let bound: InputSubscription[] = [];

    const dispose = () => {
        for (const sub of bound) sub.unsub();
        bound = [];
    };

    const rebind = () => {
        const slot = currentPointerOwner();
        const owner = slot ? pointerOwnerOf(slot) : undefined;
        const key = owner ? `${slot}:${owner.identity()}` : null;
        if (key === currentKey) return;
        dispose();
        currentKey = key;
        bound = owner ? owner.bind(host) : [];
    };

    const unsub = subscribePointerOwnership(rebind);
    rebind();

    return () => {
        unsub();
        dispose();
        currentKey = null;
    };
}
