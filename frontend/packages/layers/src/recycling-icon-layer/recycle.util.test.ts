// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { RECYCLE_AT_PX, shouldRecycleAtlas } from "./recycle.util";

describe("shouldRecycleAtlas", () => {
    describe("pressure gate", () => {
        it("never recycles below the height threshold, however bloated", () => {
            expect(
                shouldRecycleAtlas({
                    atlasHeight: RECYCLE_AT_PX - 1,
                    packedSlots: 5000,
                    liveDistinct: 1,
                }),
            ).toBe(false);
        });

        it("treats the threshold itself as under pressure (>=)", () => {
            expect(
                shouldRecycleAtlas({
                    atlasHeight: RECYCLE_AT_PX,
                    packedSlots: 200,
                    liveDistinct: 60,
                }),
            ).toBe(true);
        });

        it("recycles past the threshold when bloated", () => {
            expect(
                shouldRecycleAtlas({
                    atlasHeight: 8192,
                    packedSlots: 900,
                    liveDistinct: 60,
                }),
            ).toBe(true);
        });
    });

    describe("bloat gate (anti-thrash)", () => {
        it("does not recycle when packed ≈ live (the genuine-capacity regime)", () => {
            // 60/60 is the post-recycle steady state: a fresh atlas just packed
            // exactly the live set, so there is nothing stale to reclaim.
            expect(
                shouldRecycleAtlas({ atlasHeight: 8192, packedSlots: 60, liveDistinct: 60 }),
            ).toBe(false);
        });

        it("requires packed to strictly exceed live × 1.5", () => {
            // 150 == 100 × 1.5 exactly — the ratio is `>`, not `>=`, so no recycle.
            expect(
                shouldRecycleAtlas({ atlasHeight: 8192, packedSlots: 150, liveDistinct: 100 }),
            ).toBe(false);
            // One slot past the ratio (and well past the floor) flips it.
            expect(
                shouldRecycleAtlas({ atlasHeight: 8192, packedSlots: 151, liveDistinct: 100 }),
            ).toBe(true);
        });

        it("holds off when the absolute bloat is below the floor, even past the ratio", () => {
            // 40 > 20 × 1.5 = 30 (ratio passes), but 40 − 20 = 20 < 32 (floor fails).
            expect(
                shouldRecycleAtlas({ atlasHeight: 8192, packedSlots: 40, liveDistinct: 20 }),
            ).toBe(false);
        });

        it("recycles once both the ratio and the floor are satisfied", () => {
            // 200 > 60 × 1.5 = 90 and 200 − 60 = 140 ≥ 32.
            expect(
                shouldRecycleAtlas({ atlasHeight: 8192, packedSlots: 200, liveDistinct: 60 }),
            ).toBe(true);
        });
    });
});
