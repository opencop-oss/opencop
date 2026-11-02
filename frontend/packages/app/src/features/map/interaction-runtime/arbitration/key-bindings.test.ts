// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { afterEach, describe, expect, it, vi } from "vitest";
import { matchKeyBinding, registerKeyBinding, type KeyBinding } from "./key-bindings";

/**
 * The chord registry: one binding per chord, matched off a keydown's key and
 * modifier, replaced on re-registration. The registry learns whether a party
 * can act and how — nothing else.
 */

const binding = (key: string, canRun = true): KeyBinding => ({
    key,
    modifier: "ctrlOrMeta",
    canRun: () => canRun,
    run: vi.fn(),
});

const chord = (key: string, mods: { ctrlKey?: boolean; metaKey?: boolean } = {}) => ({
    key,
    ctrlKey: false,
    metaKey: false,
    ...mods,
});

const disposers: Array<() => void> = [];
const register = (b: KeyBinding) => {
    const dispose = registerKeyBinding(b);
    disposers.push(dispose);
    return dispose;
};

afterEach(() => {
    disposers.splice(0).forEach((dispose) => dispose());
});

describe("matchKeyBinding", () => {
    it("matches the chord with Ctrl or with Cmd, case-insensitively", () => {
        const c = binding("c");
        register(c);
        expect(matchKeyBinding(chord("c", { ctrlKey: true }))).toBe(c);
        expect(matchKeyBinding(chord("C", { metaKey: true }))).toBe(c);
    });

    it("a plain key matches nothing — the single-letter shortcuts are the session's", () => {
        register(binding("c"));
        expect(matchKeyBinding(chord("c"))).toBeUndefined();
    });

    it("an unregistered chord matches nothing", () => {
        register(binding("c"));
        expect(matchKeyBinding(chord("v", { ctrlKey: true }))).toBeUndefined();
    });

    it("the disposer withdraws the binding", () => {
        const dispose = register(binding("c"));
        dispose();
        expect(matchKeyBinding(chord("c", { ctrlKey: true }))).toBeUndefined();
    });

    it("a second registration for the chord replaces the first, whose stale disposer is inert", () => {
        const first = binding("c");
        const second = binding("c");
        const disposeFirst = register(first);
        register(second);

        disposeFirst();

        expect(matchKeyBinding(chord("c", { ctrlKey: true }))).toBe(second);
    });
});
