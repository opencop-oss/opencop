// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { afterEach, describe, expect, it, vi } from "vitest";
import { dismissEscapeSlot, isEscapeSlotActive, registerEscapeClaimant } from "./escape-claimants";

/**
 * The Escape channel's registry: claiming is open, ordering is closed. A party
 * registers into a slot; the arbiter learns whether the slot holds something
 * and how to dismiss it, and nothing else.
 */

const claimant = (active: boolean) => ({
    isActive: () => active,
    dismiss: vi.fn(),
});

const disposers: Array<() => void> = [];
const register = (...args: Parameters<typeof registerEscapeClaimant>) => {
    const dispose = registerEscapeClaimant(...args);
    disposers.push(dispose);
    return dispose;
};

afterEach(() => {
    disposers.splice(0).forEach((dispose) => dispose());
});

describe("registerEscapeClaimant", () => {
    it("an empty slot holds nothing and dismisses nothing", () => {
        expect(isEscapeSlotActive("map-selection")).toBe(false);
        expect(dismissEscapeSlot("map-selection")).toBe(false);
    });

    it("reports the claimant's activity and runs its dismissal — knowing nothing else about it", () => {
        const c = claimant(true);
        register("map-selection", c);

        expect(isEscapeSlotActive("map-selection")).toBe(true);
        expect(dismissEscapeSlot("map-selection")).toBe(true);
        expect(c.dismiss).toHaveBeenCalledTimes(1);
    });

    it("an inactive claimant leaves its slot inactive", () => {
        register("area-selection", claimant(false));
        expect(isEscapeSlotActive("area-selection")).toBe(false);
    });

    it("the disposer withdraws the claimant", () => {
        const dispose = register("session", claimant(true));
        dispose();
        expect(isEscapeSlotActive("session")).toBe(false);
    });

    it("a second registration replaces the first, and the first's stale disposer cannot evict it", () => {
        const first = claimant(true);
        const second = claimant(true);
        const disposeFirst = register("surface", first);
        register("surface", second);

        disposeFirst();

        expect(isEscapeSlotActive("surface")).toBe(true);
        dismissEscapeSlot("surface");
        expect(second.dismiss).toHaveBeenCalledTimes(1);
        expect(first.dismiss).not.toHaveBeenCalled();
    });

    it("slots are independent", () => {
        register("session", claimant(true));
        register("area-selection", claimant(false));
        expect(isEscapeSlotActive("session")).toBe(true);
        expect(isEscapeSlotActive("area-selection")).toBe(false);
        expect(isEscapeSlotActive("map-selection")).toBe(false);
    });
});
