// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { RangeSet } from "./RangeSet";

describe("RangeSet", () => {
    it("starts empty", () => {
        const set = new RangeSet();

        expect(set.isEmpty()).toBe(true);
        expect(set.consume()).toBeUndefined();
    });

    it("ignores empty ranges", () => {
        const set = new RangeSet();

        set.add(3, 3);

        expect(set.isEmpty()).toBe(true);
    });

    it("consumes the added range once", () => {
        const set = new RangeSet();
        set.add(2, 5);

        expect(set.consume()).toEqual({ start: 2, end: 5 });
        expect(set.isEmpty()).toBe(true);
        expect(set.consume()).toBeUndefined();
    });

    it("covers disjoint ranges with one bounding range", () => {
        const set = new RangeSet();
        set.add(6, 8);
        set.add(2, 3);
        set.add(10, 12);

        expect(set.consume()).toEqual({ start: 2, end: 12 });
    });

    it("keeps its bounds when a range falls inside them", () => {
        const set = new RangeSet();
        set.add(2, 10);
        set.add(4, 6);

        expect(set.consume()).toEqual({ start: 2, end: 10 });
    });

    it("extends from a range that starts at zero", () => {
        const set = new RangeSet();
        set.add(0, 2);
        set.add(5, 8);

        expect(set.consume()).toEqual({ start: 0, end: 8 });
    });

    it("extends down to zero", () => {
        const set = new RangeSet();
        set.add(4, 6);
        set.add(0, 1);

        expect(set.consume()).toEqual({ start: 0, end: 6 });
    });

    it("clears", () => {
        const set = new RangeSet();
        set.add(0, 4);

        set.clear();

        expect(set.isEmpty()).toBe(true);
    });
});
