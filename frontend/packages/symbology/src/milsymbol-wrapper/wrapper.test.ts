// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it, beforeEach } from "vitest";
import { canRender, renderIcon, _resetIconCacheForTests } from "./wrapper";
import { FRIEND_INFANTRY, GARBAGE } from "../test-fixtures";

describe("milsymbol wrapper", () => {
    beforeEach(() => {
        _resetIconCacheForTests();
    });

    describe("canRender", () => {
        it("returns true for a known SIDC", () => {
            expect(canRender(FRIEND_INFANTRY)).toBe(true);
        });

        it("catches construction errors and returns false for garbage input", () => {
            expect(canRender(GARBAGE)).toBe(false);
        });
    });

    describe("renderIcon", () => {
        it("returns a populated WrapperIconResult", () => {
            const result = renderIcon(FRIEND_INFANTRY);
            expect(result.url).toMatch(/^data:/);
            expect(result.width).toBeGreaterThan(0);
            expect(result.height).toBeGreaterThan(0);
        });

        it("returns the same reference on repeat calls (cache hit)", () => {
            const first = renderIcon(FRIEND_INFANTRY);
            const second = renderIcon(FRIEND_INFANTRY);
            expect(second).toBe(first);
        });

        it("treats different sizes as separate cache entries", () => {
            const a = renderIcon(FRIEND_INFANTRY, { size: 50 });
            const b = renderIcon(FRIEND_INFANTRY, { size: 80 });
            expect(b).not.toBe(a);
        });

        it("renders smaller dimensions at a smaller octagon size (the atlas-footprint win)", () => {
            // The map path packs icons at 64px instead of the 100px default to
            // shrink the atlas slot; a smaller octagon must yield a proportionally
            // smaller SVG so the slot actually shrinks.
            const small = renderIcon(FRIEND_INFANTRY, { size: 64 });
            const full = renderIcon(FRIEND_INFANTRY, { size: 100 });
            expect(small.width).toBeLessThan(full.width);
            expect(small.height).toBeLessThan(full.height);
            // ~0.64 linear scale → smaller, with slack for fixed frame/padding
            // that doesn't scale linearly.
            expect(small.width / full.width).toBeLessThan(0.8);
        });

        it("treats different modifier values as separate cache entries", () => {
            const a = renderIcon(FRIEND_INFANTRY, {
                modifiers: { uniqueDesignation: "A" },
            });
            const b = renderIcon(FRIEND_INFANTRY, {
                modifiers: { uniqueDesignation: "B" },
            });
            expect(b).not.toBe(a);
        });

        it("returns a populated result for garbage input (milsymbol's permissive '?' renderer)", () => {
            // milsymbol returns the "unknown" placeholder for any input it
            // can parse; the wrapper's "always returns" contract holds even
            // for inputs that other layers (canRender, the strategy) reject.
            const result = renderIcon(GARBAGE);
            expect(result.url).toMatch(/^data:/);
            expect(result.width).toBeGreaterThan(0);
            expect(result.height).toBeGreaterThan(0);
        });
    });
});
