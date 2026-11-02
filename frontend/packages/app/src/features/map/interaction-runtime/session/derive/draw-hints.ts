// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * The draw hints for the current session — ONE pure derivation over the
 * session and the descriptor that owns it, read by the host's hint outlet.
 *
 * The session decides the phase: a draw session is `armed` until its draft is
 * placed (a draft that only follows the cursor is not placed) and `placing`
 * after; an edit session is `editing`. Which lines show is the descriptor's
 * `hints` over its grammar's defaults. Whether an edit ends with Save is the
 * host's to say, not the hint state's: it follows the session's custody, which
 * holds for an element edit whether or not a tool produces its type.
 */
import {
    resolveGrammar,
    resolveDrawHints,
    type DrawGrammar,
    type DrawHint,
    type DrawHintState,
    type ToolDescriptor,
} from "@opencop/tools";
import { editModelOf } from "../grammar-resolution";
import type { DescriptorResolver } from "../../contract";
import type { Session } from "../session";

/** The hint state for a live session; `null` when idle. */
export function drawHintState(
    session: Session,
    grammar: DrawGrammar | undefined,
    movable = true,
): DrawHintState | null {
    const bounds = { ...grammar?.bounds, movable };
    switch (session.kind) {
        case "idle":
            return null;
        case "draw":
            return session.draft?.placed
                ? { phase: "placing", placed: session.locked, ...bounds }
                : { phase: "armed", placed: 0, ...bounds };
        case "edit":
            return { phase: "editing", placed: 0, ...bounds };
    }
}

/**
 * The grammar for the session's params, or undefined. A grammar factory that
 * throws is left undefined here: activation has already refused or reported
 * it, and nothing derived at render time may report it a second time.
 */
export function grammarFor(
    descriptor: ToolDescriptor | undefined,
    session: Session,
): DrawGrammar | undefined {
    if (!descriptor || session.kind === "idle") return undefined;
    try {
        return resolveGrammar(descriptor, session.params);
    } catch {
        return undefined;
    }
}

/**
 * The lines to show for the session. An edit of an existing element whose
 * type no tool produces has no descriptor and gets the host's edit defaults.
 * Calls the descriptor's kit's `hints`, which is contributed code — the caller
 * contains it.
 */
export function sessionDrawHints(
    session: Session,
    resolve: DescriptorResolver,
): readonly DrawHint[] {
    if (session.kind === "idle") return [];
    const descriptor = session.descriptorId === null ? undefined : resolve(session.descriptorId);
    const grammar = grammarFor(descriptor, session);
    const state = drawHintState(session, grammar, editModelOf(descriptor).translate !== false);
    if (!state) return [];
    return resolveDrawHints(descriptor?.draw?.hints, grammar, state, { savable: session.custody });
}
