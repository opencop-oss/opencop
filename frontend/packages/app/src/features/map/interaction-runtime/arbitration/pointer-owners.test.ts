// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { afterEach, describe, expect, it, vi } from "vitest";
import {
    currentPointerOwner,
    pointerOwnerOf,
    registerPointerOwners,
    subscribePointerOwnership,
    type PointerOwner,
} from "./pointer-owners";

/**
 * The pointer channel's registry: the closed set of owners seated in one call
 * by one registrant, the ladder over their `isActive()`, and a change signal
 * the runner rebinds on. The registry learns whether a slot is active and what
 * to bind — nothing else.
 */

function owner(active = true, identity = "x"): PointerOwner & { change(): void } {
    const listeners = new Set<() => void>();
    return {
        isActive: () => active,
        identity: () => identity,
        bind: vi.fn(() => []),
        subscribe: (listener) => {
            listeners.add(listener);
            return () => listeners.delete(listener);
        },
        change: () => listeners.forEach((listener) => listener()),
    };
}

const set = (overrides: Partial<Record<"session" | "surface" | "ambient", PointerOwner>> = {}) => ({
    session: owner(false),
    surface: owner(false),
    ambient: owner(true),
    ...overrides,
});

const disposers: Array<() => void> = [];
const register = (owners: ReturnType<typeof set>) => {
    const dispose = registerPointerOwners(owners);
    disposers.push(dispose);
    return dispose;
};

afterEach(() => {
    disposers.splice(0).forEach((dispose) => dispose());
});

describe("registerPointerOwners", () => {
    it("nobody owns the pointer until the set is seated; then the ladder runs over the owners' activity", () => {
        expect(currentPointerOwner()).toBeNull();
        register(set());
        expect(currentPointerOwner()).toBe("ambient");
    });

    it("an owner's change signal reaches the ownership listeners", () => {
        const heard = vi.fn();
        const unsubscribe = subscribePointerOwnership(heard);
        const session = owner(true);
        register(set({ session }));
        heard.mockClear();

        session.change();

        expect(heard).toHaveBeenCalledTimes(1);
        unsubscribe();
    });

    it("seating and withdrawing the set are changes too, and withdrawing empties every slot", () => {
        const heard = vi.fn();
        const unsubscribe = subscribePointerOwnership(heard);

        const dispose = register(set());
        expect(heard).toHaveBeenCalled();
        heard.mockClear();
        dispose();
        expect(heard).toHaveBeenCalled();
        expect(pointerOwnerOf("session")).toBeUndefined();
        expect(pointerOwnerOf("surface")).toBeUndefined();
        expect(pointerOwnerOf("ambient")).toBeUndefined();
        unsubscribe();
    });

    it("a second set replaces the first, whose stale disposer and change signals are then inert", () => {
        const heard = vi.fn();
        const unsubscribe = subscribePointerOwnership(heard);
        const firstAmbient = owner(true, "first");
        const secondAmbient = owner(true, "second");
        const disposeFirst = register(set({ ambient: firstAmbient }));
        register(set({ ambient: secondAmbient }));
        heard.mockClear();

        disposeFirst();
        firstAmbient.change();

        expect(heard).not.toHaveBeenCalled();
        expect(pointerOwnerOf("ambient")).toBe(secondAmbient);
        unsubscribe();
    });
});
