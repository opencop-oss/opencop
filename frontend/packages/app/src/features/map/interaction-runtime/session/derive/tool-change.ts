// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * Whether the operator may start or switch tools right now — ONE pure
 * derivation over the session, read by both the command that performs the
 * change and the chrome that offers it.
 *
 * Three host rules, no descriptor opt-in: refused in historical mode (drawing
 * has no meaning over a snapshot), over an EDIT session (custody or not — a
 * finished measurement is left through Escape or its own editor, not by
 * switching tools), and over an in-flight draw (something placed or captured —
 * switching tools is not a way to abandon it; Escape is). A tool merely
 * waiting for its first click is preemptable.
 *
 * Host policy, kept pure over the session snapshot + environment.
 */
import { isInFlight, type Session } from "../session";

export interface ToolChangeEnv {
    /** Historical mode is showing a snapshot; live drawing has no meaning over it. */
    readonly isHistorical: boolean;
}

/**
 * The session half of the activation gate. `canActivate` adds the one
 * per-descriptor precondition on top (the descriptor must have a draw
 * kit); the toolbar's disabled state is this and nothing else, so a
 * popover tool — which has no draw to activate — is still offered
 * whenever a tool change would be legal.
 */
export function canChangeTool(session: Session, env: ToolChangeEnv): boolean {
    if (env.isHistorical) return false;
    if (session.kind === "edit") return false;
    if (isInFlight(session)) return false;
    return true;
}
