// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { IdIndexer } from "./IdIndexer";

describe("IdIndexer", () => {
    it("push and getId return correct values", () => {
        const idx = new IdIndexer();
        idx.push("alpha");
        idx.push("bravo");
        idx.push("charlie");

        expect(idx.getId(0)).toBe("alpha");
        expect(idx.getId(1)).toBe("bravo");
        expect(idx.getId(2)).toBe("charlie");
        expect(idx.length).toBe(3);
    });

    it("getIndex returns correct index for id", () => {
        const idx = new IdIndexer();
        idx.push("alpha");
        idx.push("bravo");

        expect(idx.getIndex("alpha")).toBe(0);
        expect(idx.getIndex("bravo")).toBe(1);
        expect(idx.getIndex("missing")).toBeUndefined();
    });

    it("pop removes the last entry", () => {
        const idx = new IdIndexer();
        idx.push("alpha");
        idx.push("bravo");
        idx.pop();

        expect(idx.length).toBe(1);
        expect(idx.getId(0)).toBe("alpha");
        expect(idx.getId(1)).toBeUndefined();
        expect(idx.getIndex("bravo")).toBeUndefined();
    });

    it("swapPop moves last element to deleted position", () => {
        const idx = new IdIndexer();
        idx.push("alpha");
        idx.push("bravo");
        idx.push("charlie");

        idx.swapPop(0); // remove alpha, move charlie to 0

        expect(idx.length).toBe(2);
        expect(idx.getId(0)).toBe("charlie");
        expect(idx.getId(1)).toBe("bravo");
        expect(idx.getIndex("alpha")).toBeUndefined();
        expect(idx.getIndex("charlie")).toBe(0);
    });

    it("swapPop on last element is equivalent to pop", () => {
        const idx = new IdIndexer();
        idx.push("alpha");
        idx.push("bravo");

        idx.swapPop(1);

        expect(idx.length).toBe(1);
        expect(idx.getId(0)).toBe("alpha");
        expect(idx.getIndex("bravo")).toBeUndefined();
    });

    it("remove shifts subsequent entries", () => {
        const idx = new IdIndexer();
        idx.push("alpha");
        idx.push("bravo");
        idx.push("charlie");

        idx.remove(1); // remove bravo, shift charlie to 1

        expect(idx.length).toBe(2);
        expect(idx.getId(0)).toBe("alpha");
        expect(idx.getId(1)).toBe("charlie");
        expect(idx.getIndex("bravo")).toBeUndefined();
        expect(idx.getIndex("charlie")).toBe(1);
    });

    describe("numeric string objectIds (key-space collision)", () => {
        it("getId returns string id, not numeric index, for numeric objectIds", () => {
            const idx = new IdIndexer();
            // Push UUIDs first, then numeric string ids that collide with indices
            idx.push("uuid-0");
            idx.push("uuid-1");
            idx.push("2"); // objectId "2" stored at index 2 — collides!
            idx.push("3"); // objectId "3" stored at index 3 — collides!

            // getId(2) must return "2" (the objectId at index 2),
            // NOT the index of the element with objectId "2"
            expect(idx.getId(2)).toBe("2");
            expect(idx.getId(3)).toBe("3");
            expect(typeof idx.getId(2)).toBe("string");
            expect(typeof idx.getId(3)).toBe("string");
        });

        it("swapPop with numeric objectIds maintains correct mappings", () => {
            const idx = new IdIndexer();
            idx.push("0"); // objectId "0" at index 0 — worst-case collision
            idx.push("1"); // objectId "1" at index 1
            idx.push("2"); // objectId "2" at index 2

            idx.swapPop(0); // remove "0", move "2" to index 0

            expect(idx.length).toBe(2);
            expect(idx.getId(0)).toBe("2");
            expect(idx.getId(1)).toBe("1");
            expect(idx.getIndex("0")).toBeUndefined();
            expect(idx.getIndex("2")).toBe(0);
            expect(idx.getIndex("1")).toBe(1);
        });

        it("pop does not corrupt entries when a numeric objectId collides with the popped index", () => {
            const idx = new IdIndexer();
            idx.push("1"); // objectId "1" at index 0
            idx.push("uuid"); // objectId "uuid" at index 1

            idx.pop(); // should remove index 1 ("uuid"), not objectId "1"

            expect(idx.length).toBe(1);
            expect(idx.getId(0)).toBe("1");
            expect(idx.getIndex("1")).toBe(0);
            expect(idx.getIndex("uuid")).toBeUndefined();
        });

        it("mixed UUID and numeric ids maintain correct lookups after mutations", () => {
            const idx = new IdIndexer();
            // Simulate real-world data: UUIDs first, then numeric ids from a data source
            idx.push("abc-def-001");
            idx.push("abc-def-002");
            idx.push("10");
            idx.push("11");
            idx.push("12");

            // Delete a UUID — last element ("12") moves to index 0
            idx.swapPop(0);

            expect(idx.getId(0)).toBe("12");
            expect(idx.getId(1)).toBe("abc-def-002");
            expect(idx.getId(2)).toBe("10");
            expect(idx.getId(3)).toBe("11");
            expect(idx.getIndex("abc-def-001")).toBeUndefined();

            // Delete a numeric id — last element ("11") moves to index 2
            idx.swapPop(2);

            expect(idx.getId(0)).toBe("12");
            expect(idx.getId(1)).toBe("abc-def-002");
            expect(idx.getId(2)).toBe("11");
            expect(idx.getIndex("10")).toBeUndefined();
            expect(idx.getIndex("11")).toBe(2);
        });
    });

    describe("clear", () => {
        it("removes all entries from both maps", () => {
            const idx = new IdIndexer();
            idx.push("A");
            idx.push("B");
            idx.push("C");

            idx.clear();

            expect(idx.length).toBe(0);
            expect(idx.getIndex("A")).toBeUndefined();
            expect(idx.getIndex("B")).toBeUndefined();
            expect(idx.getIndex("C")).toBeUndefined();
        });

        it("allows push after clear with correct indices", () => {
            const idx = new IdIndexer();
            idx.push("A");
            idx.push("B");
            idx.clear();

            idx.push("X");
            idx.push("Y");

            expect(idx.length).toBe(2);
            expect(idx.getIndex("X")).toBe(0);
            expect(idx.getIndex("Y")).toBe(1);
            expect(idx.getIndex("A")).toBeUndefined();
        });
    });

    describe("remove edge cases", () => {
        it("is a no-op for out-of-bounds index", () => {
            const idx = new IdIndexer();
            idx.push("A");

            idx.remove(5);
            idx.remove(-1);

            expect(idx.length).toBe(1);
            expect(idx.getIndex("A")).toBe(0);
        });

        it("stays consistent after sequential removes from middle", () => {
            const idx = new IdIndexer();
            idx.push("A");
            idx.push("B");
            idx.push("C");
            idx.push("D");

            idx.remove(1); // [A, C, D]
            idx.remove(1); // [A, D]

            expect(idx.length).toBe(2);
            expect(idx.getIndex("A")).toBe(0);
            expect(idx.getIndex("D")).toBe(1);
            expect(idx.getId(0)).toBe("A");
            expect(idx.getId(1)).toBe("D");
        });

        it("removes single element", () => {
            const idx = new IdIndexer();
            idx.push("A");

            idx.remove(0);

            expect(idx.length).toBe(0);
            expect(idx.getIndex("A")).toBeUndefined();
        });
    });

    it("iterates over all entries", () => {
        const idx = new IdIndexer();
        idx.push("alpha");
        idx.push("bravo");

        const entries = [...idx];
        expect(entries).toEqual([
            ["alpha", 0],
            ["bravo", 1],
        ]);
    });
});
