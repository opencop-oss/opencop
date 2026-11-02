// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * The session's Enter key. The keyboard router (`../arbitration/keyboard-router.ts`)
 * owns the key: it stands down while a text control has focus, and otherwise
 * delivers Enter here. A draw grammar hears it through `DrawInput.onEnter`, bound
 * in its `bind` and released with the session's other bindings, and decides
 * itself whether the press completes its draft.
 */
import type { InputSubscription } from "../contract";

const listeners = new Set<() => void>();

/** Hear the session's Enter until the returned subscription is released. */
export function onSessionEnter(listener: () => void): InputSubscription {
    // Wrapped, so one function bound twice is two bindings, each released alone.
    const entry = () => listener();
    listeners.add(entry);
    return { event: "enter", unsub: () => listeners.delete(entry) };
}

/** Deliver one Enter press to every bound listener. */
export function deliverSessionEnter(): void {
    for (const listener of [...listeners]) {
        // A listener released by an earlier one in this press hears nothing more.
        if (listeners.has(listener)) listener();
    }
}
