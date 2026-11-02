// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { MULTIPOINT_SYMBOL_SETS, POINT_SYMBOL_SETS } from "./sidc";

describe("Symbol-set partition", () => {
    // The previous hardcoded `MULTIPOINT_CODES = new Set(["25"])` was
    // replaced with a library-derived partition. The user-visible result
    // must still cover SS25 in the graphic editor and the unit-symbol sets
    // in the symbol editor — otherwise the Symbol Set dropdowns silently
    // change content.

    it("MULTIPOINT_SYMBOL_SETS includes SS25", () => {
        expect("25" in MULTIPOINT_SYMBOL_SETS.record).toBe(true);
    });

    it("POINT_SYMBOL_SETS includes the unit-symbol sets users expect", () => {
        // Spot-check a few well-known symbol sets that belong in the
        // Symbol Editor. If the library ever flips one of these to
        // tactical-graphic, this assertion is the canary.
        expect("10" in POINT_SYMBOL_SETS.record).toBe(true); // Land Units
        expect("15" in POINT_SYMBOL_SETS.record).toBe(true); // Land Equipment
        expect("20" in POINT_SYMBOL_SETS.record).toBe(true); // Land Installations
    });

    it("partition is disjoint", () => {
        const point = Object.keys(POINT_SYMBOL_SETS.record);
        const multi = Object.keys(MULTIPOINT_SYMBOL_SETS.record);
        expect(point.some((c) => multi.includes(c))).toBe(false);
    });
});
