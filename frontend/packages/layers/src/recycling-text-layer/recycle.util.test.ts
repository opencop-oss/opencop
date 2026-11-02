// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { FONT_RECYCLE_AT_PX, shouldRecycleAtlas } from "./recycle.util";

describe("shouldRecycleAtlas", () => {
    describe("pressure gate", () => {
        it("never recycles below the height threshold, however bloated", () => {
            expect(
                shouldRecycleAtlas({
                    atlasHeight: FONT_RECYCLE_AT_PX - 1,
                    packedGlyphs: 5000,
                    liveDistinct: 1,
                }),
            ).toBe(false);
        });

        it("treats the threshold itself as under pressure (>=)", () => {
            expect(
                shouldRecycleAtlas({
                    atlasHeight: FONT_RECYCLE_AT_PX,
                    packedGlyphs: 2000,
                    liveDistinct: 150,
                }),
            ).toBe(true);
        });

        it("recycles past the threshold when bloated", () => {
            expect(
                shouldRecycleAtlas({ atlasHeight: 8192, packedGlyphs: 4000, liveDistinct: 150 }),
            ).toBe(true);
        });
    });

    describe("bloat gate (anti-thrash)", () => {
        it("does not recycle when packed ≈ live (the genuine-capacity regime)", () => {
            // A fresh atlas that just packed exactly the live set — nothing stale.
            expect(
                shouldRecycleAtlas({ atlasHeight: 8192, packedGlyphs: 150, liveDistinct: 150 }),
            ).toBe(false);
        });

        it("requires packed to strictly exceed live × 1.5", () => {
            // 150 == 100 × 1.5 exactly — the ratio is `>`, not `>=`, so no recycle.
            expect(
                shouldRecycleAtlas({ atlasHeight: 8192, packedGlyphs: 150, liveDistinct: 100 }),
            ).toBe(false);
            // One glyph past the ratio (and well past the floor) flips it.
            expect(
                shouldRecycleAtlas({ atlasHeight: 8192, packedGlyphs: 151, liveDistinct: 100 }),
            ).toBe(true);
        });

        it("holds off when the absolute bloat is below the floor, even past the ratio", () => {
            // 40 > 20 × 1.5 = 30 (ratio passes), but 40 − 20 = 20 < 32 (floor fails).
            expect(
                shouldRecycleAtlas({ atlasHeight: 8192, packedGlyphs: 40, liveDistinct: 20 }),
            ).toBe(false);
        });

        it("recycles once both the ratio and the floor are satisfied", () => {
            // 200 > 60 × 1.5 = 90 and 200 − 60 = 140 ≥ 32.
            expect(
                shouldRecycleAtlas({ atlasHeight: 8192, packedGlyphs: 200, liveDistinct: 60 }),
            ).toBe(true);
        });
    });
});
