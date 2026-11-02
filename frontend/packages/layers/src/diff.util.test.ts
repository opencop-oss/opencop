// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { dataDiff, replaceInRange, type DataRange } from "./diff.util";

describe("replaceInRange", () => {
    type Item = { idx: number; tag?: string };
    const getIndex = (d: unknown) => (d as Item).idx;

    it("replaces a contiguous slice when counts match", () => {
        const data: Item[] = [{ idx: 0 }, { idx: 1 }, { idx: 2 }, { idx: 3 }];
        const replace: Item[] = [
            { idx: 1, tag: "a" },
            { idx: 2, tag: "b" },
        ];

        const result = replaceInRange({
            data,
            getIndex,
            dataRange: { startRow: 1, endRow: 3 },
            replace,
        });

        expect(data).toEqual([{ idx: 0 }, { idx: 1, tag: "a" }, { idx: 2, tag: "b" }, { idx: 3 }]);
        expect(result).toEqual({ startRow: 1, endRow: 3 });
    });

    it("extends the array and shifts the tail when replacement is larger", () => {
        const data: Item[] = [{ idx: 0 }, { idx: 1 }, { idx: 5 }];
        const replace: Item[] = [
            { idx: 1, tag: "a" },
            { idx: 1, tag: "b" },
            { idx: 1, tag: "c" },
        ];

        const result = replaceInRange({
            data,
            getIndex,
            dataRange: { startRow: 1, endRow: 2 },
            replace,
        });

        expect(data).toEqual([
            { idx: 0 },
            { idx: 1, tag: "a" },
            { idx: 1, tag: "b" },
            { idx: 1, tag: "c" },
            { idx: 5 },
        ]);
        expect(result).toEqual({ startRow: 1, endRow: 4 });
    });

    it("shrinks the array and trims when replacement is smaller", () => {
        const data: Item[] = [
            { idx: 0 },
            { idx: 1, tag: "a" },
            { idx: 1, tag: "b" },
            { idx: 1, tag: "c" },
            { idx: 5 },
        ];
        const replace: Item[] = [{ idx: 1, tag: "x" }];

        const result = replaceInRange({
            data,
            getIndex,
            dataRange: { startRow: 1, endRow: 2 },
            replace,
        });

        expect(data).toEqual([{ idx: 0 }, { idx: 1, tag: "x" }, { idx: 5 }]);
        expect(data.length).toBe(3);
        expect(result).toEqual({ startRow: 1, endRow: 2 });
    });

    it("treats endRow=Infinity as 'replace everything from startRow onward'", () => {
        const data: Item[] = [{ idx: 0 }, { idx: 1 }, { idx: 2 }, { idx: 3 }];
        const replace: Item[] = [{ idx: 2, tag: "z" }];

        const result = replaceInRange({
            data,
            getIndex,
            dataRange: { startRow: 2 },
            replace,
        });

        expect(data).toEqual([{ idx: 0 }, { idx: 1 }, { idx: 2, tag: "z" }]);
        expect(result).toEqual({ startRow: 2, endRow: 3 });
    });

    it("defaults startRow to 0 when omitted", () => {
        const data: Item[] = [{ idx: 0 }, { idx: 1 }, { idx: 2 }];
        const replace: Item[] = [{ idx: 0, tag: "x" }];

        const result = replaceInRange({
            data,
            getIndex,
            dataRange: { endRow: 2 },
            replace,
        });

        expect(data).toEqual([{ idx: 0, tag: "x" }, { idx: 2 }]);
        expect(result).toEqual({ startRow: 0, endRow: 1 });
    });

    it("handles sparse source indices (gaps from filtered rows)", () => {
        const data: Item[] = [{ idx: 0 }, { idx: 2 }, { idx: 5 }, { idx: 7 }];
        const replace: Item[] = [{ idx: 5, tag: "new" }];

        const result = replaceInRange({
            data,
            getIndex,
            dataRange: { startRow: 5, endRow: 6 },
            replace,
        });

        expect(data).toEqual([{ idx: 0 }, { idx: 2 }, { idx: 5, tag: "new" }, { idx: 7 }]);
        expect(result).toEqual({ startRow: 2, endRow: 3 });
    });

    it("appends when startRow is past every existing entry", () => {
        const data: Item[] = [{ idx: 0 }, { idx: 1 }];
        const replace: Item[] = [{ idx: 9, tag: "append" }];

        const result = replaceInRange({
            data,
            getIndex,
            dataRange: { startRow: 9 },
            replace,
        });

        expect(data).toEqual([{ idx: 0 }, { idx: 1 }, { idx: 9, tag: "append" }]);
        expect(result).toEqual({ startRow: 2, endRow: 3 });
    });

    it("returns an empty replacement range when nothing to do", () => {
        const data: Item[] = [{ idx: 0 }, { idx: 1 }, { idx: 2 }];

        const result = replaceInRange({
            data,
            getIndex,
            dataRange: { startRow: 5, endRow: 9 },
            replace: [],
        });

        expect(data).toEqual([{ idx: 0 }, { idx: 1 }, { idx: 2 }]);
        expect(result).toEqual({ startRow: 3, endRow: 3 });
    });

    it("handles boundary: startRow exactly equals an entry's index", () => {
        const data: Item[] = [{ idx: 0 }, { idx: 5 }, { idx: 10 }];
        const replace: Item[] = [{ idx: 5, tag: "boundary" }];

        const result = replaceInRange({
            data,
            getIndex,
            dataRange: { startRow: 5, endRow: 10 },
            replace,
        });

        expect(data).toEqual([{ idx: 0 }, { idx: 5, tag: "boundary" }, { idx: 10 }]);
        expect(result).toEqual({ startRow: 1, endRow: 2 });
    });

    it("multi-entry-per-row: replaces all derived entries for a single input row", () => {
        const data: Item[] = [
            { idx: 0 },
            { idx: 1, tag: "L0" },
            { idx: 1, tag: "L1" },
            { idx: 1, tag: "L2" },
            { idx: 1, tag: "L3" },
            { idx: 2 },
        ];
        const replace: Item[] = [
            { idx: 1, tag: "N0" },
            { idx: 1, tag: "N1" },
        ];

        const result = replaceInRange({
            data,
            getIndex,
            dataRange: { startRow: 1, endRow: 2 },
            replace,
        });

        expect(data).toEqual([
            { idx: 0 },
            { idx: 1, tag: "N0" },
            { idx: 1, tag: "N1" },
            { idx: 2 },
        ]);
        expect(result).toEqual({ startRow: 1, endRow: 3 });
    });

    it("operates on an empty data array", () => {
        const data: Item[] = [];
        const replace: Item[] = [{ idx: 0, tag: "first" }];

        const result = replaceInRange({
            data,
            getIndex,
            dataRange: { startRow: 0 },
            replace,
        });

        expect(data).toEqual([{ idx: 0, tag: "first" }]);
        expect(result).toEqual({ startRow: 0, endRow: 1 });
    });
});

describe("dataDiff", () => {
    it("returns undefined when given null", () => {
        expect(dataDiff(null)).toBeUndefined();
    });

    it("returns a function that yields the same array reference (no per-frame allocation)", () => {
        const ranges: DataRange[] = [{ startRow: 0, endRow: 5 }];
        const fn = dataDiff(ranges);
        expect(typeof fn).toBe("function");
        expect(fn!()).toBe(ranges);
    });

    it("returns a function that yields an empty list when given []", () => {
        const ranges: DataRange[] = [];
        const fn = dataDiff(ranges);
        expect(fn!()).toBe(ranges);
    });
});
