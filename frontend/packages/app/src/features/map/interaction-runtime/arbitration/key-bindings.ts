// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * Registered KEY BINDINGS — a chord (Ctrl/Cmd + a key) a party outside the
 * session wants, without a `document` listener of its own.
 *
 * The router (`keyboard-router.ts`) hears every keydown once. Escape goes to
 * the ladder; Enter and the single-letter shortcuts are the session's; a
 * modified chord comes here. A binding is `{ key, modifier, canRun, run }`:
 * the chord, whether the party can act on it right now — its own state (a
 * selection to copy, a copied event to paste), never the session's — and the
 * act. The router decides WHO receives the chord: a focused text control or a
 * live session takes precedence and the chord falls through to the browser;
 * when idle, the binding that can run. The default is prevented only when a
 * binding acted, so a chord nobody claims is still the browser's.
 *
 * One binding per chord — two parties on Ctrl+C is a composition mistake, so
 * a second registration replaces the first (logged in DEV) and a stale
 * disposer cannot evict its replacement.
 */
import Logger from "js-logger";

/** The modifier a chord needs. `ctrlOrMeta` is Ctrl on Windows and Linux, Cmd on macOS. */
export type KeyModifier = "ctrlOrMeta";

export interface KeyBinding {
    /** `KeyboardEvent.key`, matched case-insensitively. */
    readonly key: string;
    readonly modifier: KeyModifier;
    /** Whether the party can act on the chord right now — its own state, nothing about the session. */
    canRun(): boolean;
    /** Act. The router has prevented the browser's default. */
    run(): void;
}

/** What the matcher reads off a keydown. */
export type KeyChord = Pick<KeyboardEvent, "key" | "ctrlKey" | "metaKey">;

const logger = Logger.get("interaction");

const bindings = new Map<string, KeyBinding>();

function chordId(modifier: KeyModifier, key: string): string {
    return `${modifier}+${key.toLowerCase()}`;
}

/** Register a binding for its chord. Returns the disposer that withdraws it. */
export function registerKeyBinding(binding: KeyBinding): () => void {
    const id = chordId(binding.modifier, binding.key);
    if (import.meta.env.DEV && bindings.has(id)) {
        logger.warn(`key binding "${id}" re-registered; the previous binding is replaced.`);
    }
    bindings.set(id, binding);
    return () => {
        if (bindings.get(id) === binding) bindings.delete(id);
    };
}

/** The modifier a keydown carries, or null for a plain key. */
function modifierOf(e: KeyChord): KeyModifier | null {
    return e.ctrlKey || e.metaKey ? "ctrlOrMeta" : null;
}

/** The binding registered for this keydown's chord, if any. Plain keys match nothing. */
export function matchKeyBinding(e: KeyChord): KeyBinding | undefined {
    const modifier = modifierOf(e);
    if (!modifier) return undefined;
    return bindings.get(chordId(modifier, e.key));
}
