// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * Gesture locks (map pan / double-click zoom) — DERIVED from the session and
 * applied by one diffed effector. Derivation beats refcounting: overlapping
 * claimants cannot fight a pure function, and nothing can leave a lock
 * unbalanced.
 *
 * The double-click-zoom lock is a function of the session's kind: every draw
 * session holds it (click-to-place must not zoom), an edit does not, and idle
 * holds it only while a pickable element is under the pointer. The pan lock
 * is a function of what is in flight: a live drag, or a draft of a grammar
 * that draws by dragging (`locksPan`, the freehand trace) — the map must not
 * pan under a moving finger. Neither is a tool's choice.
 *
 * Host policy, kept pure over the session snapshot + environment.
 */
import { resolveGrammar } from "@opencop/tools";
import { sessionDraft, type Session } from "../../session/session";
import type { DescriptorResolver } from "../../contract";

export interface LockEnv {
    /** An element is under the pointer (idle hover disables dbl-click zoom). */
    readonly hoveringElement: boolean;
}

export interface GestureLocks {
    readonly panLocked: boolean;
    readonly dblClickZoomLocked: boolean;
}

/**
 * A draft of a grammar that draws by dragging, with its press held: the draft
 * exists and grows under the pointer (a freehand trace).
 */
function dragDrawInFlight(session: Session, resolve: DescriptorResolver): boolean {
    if (session.kind !== "draw") return false;
    if (sessionDraft(session) === null) return false;
    const descriptor = resolve(session.descriptorId);
    return descriptor ? resolveGrammar(descriptor, session.params)?.locksPan === true : false;
}

/** Derive the map gesture locks for the current snapshot. */
export function deriveLocks(
    session: Session,
    resolve: DescriptorResolver,
    env: LockEnv,
): GestureLocks {
    switch (session.kind) {
        case "idle":
            return { panLocked: false, dblClickZoomLocked: env.hoveringElement };
        case "edit":
            return { panLocked: session.sub.dragging !== null, dblClickZoomLocked: false };
        case "draw":
            return { panLocked: dragDrawInFlight(session, resolve), dblClickZoomLocked: true };
    }
}
