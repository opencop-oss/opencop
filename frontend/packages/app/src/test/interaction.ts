// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * Test-side writers for the interaction session store. Production code writes
 * the session only through `~/features/map/interaction-runtime/session/commands`; a test that needs
 * a hand-built session in place (a component rendered mid-draw, a hook observed
 * in EDIT) sets it here instead of driving the whole runtime.
 */
import {
    IDLE,
    bumpRev,
    setSession,
    type Session,
} from "~/features/map/interaction-runtime/session/interaction.store";

/** Put `session` in the store as if a command had produced it. */
export function setSessionForTest(session: Session): void {
    setSession(session);
}

/** Return the store to idle. `rev` is a monotonic fence and is left alone. */
export function resetInteractionForTest(): void {
    setSession(IDLE);
}

/** Advance the geometry fence, as a draft mutated in place does. */
export function bumpRevForTest(): void {
    bumpRev();
}
