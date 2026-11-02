// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import type { Allocation } from "./Allocator";
import { BuddyAllocator } from "./BuddyAllocator";

const overlaps = (a: Allocation, b: Allocation) =>
    a.offset < b.offset + b.length && b.offset < a.offset + a.length;

describe("BuddyAllocator.allocate", () => {
    it("rounds the arena and each request up to a power of two", () => {
        const allocator = new BuddyAllocator({ capacity: 10 });

        expect(allocator.capacity).toBe(16);
        expect(allocator.allocate(3)).toMatchObject({ length: 4, requested: 3, generation: 0 });
        expect(allocator.allocate(5)).toMatchObject({ length: 8, requested: 5 });
        expect(allocator.allocate(1)).toMatchObject({ length: 1, requested: 1 });
    });

    it("splits larger blocks, handing out each half", () => {
        const allocator = new BuddyAllocator({ capacity: 16 });

        const offsets = [1, 1, 2, 4, 8].map((slots) => allocator.allocate(slots).offset);

        expect(offsets).toEqual([0, 1, 2, 4, 8]);
        expect(allocator.capacity).toBe(16);
    });

    it("never hands out overlapping blocks", () => {
        const allocator = new BuddyAllocator({ capacity: 64 });

        const allocations = [5, 1, 16, 3, 2, 8, 1, 4].map((slots) => allocator.allocate(slots));

        for (const a of allocations)
            for (const b of allocations) if (a !== b) expect(overlaps(a, b)).toBe(false);
    });

    it("grows when full", () => {
        const allocator = new BuddyAllocator({ capacity: 4 });
        allocator.allocate(4);

        expect(allocator.allocate(1)).toMatchObject({ offset: 4, length: 1 });
        expect(allocator.capacity).toBe(8);
    });

    it("grows to fit a request larger than the arena", () => {
        const allocator = new BuddyAllocator({ capacity: 4 });

        expect(allocator.allocate(16)).toMatchObject({ offset: 0, length: 16 });
        expect(allocator.capacity).toBe(16);
    });

    it("grows past live allocations without moving them", () => {
        const allocator = new BuddyAllocator({ capacity: 4 });
        const low = allocator.allocate(1);

        const high = allocator.allocate(8);

        expect(allocator.capacity).toBe(16);
        expect(low).toMatchObject({ offset: 0, length: 1 });
        expect(high).toMatchObject({ offset: 8, length: 8 });
    });

    it.each([0, -1, 1.5, NaN, 2 ** 30 + 1])("throws for %s slots", (slots) => {
        expect(() => new BuddyAllocator().allocate(slots)).toThrow();
    });

    it("throws for an invalid capacity", () => {
        expect(() => new BuddyAllocator({ capacity: 0 })).toThrow();
    });

    it("cannot grow past 2^30 slots", () => {
        const allocator = new BuddyAllocator({ capacity: 2 ** 30 });
        allocator.allocate(2 ** 30);

        expect(() => allocator.allocate(1)).toThrow();
    });
});

describe("BuddyAllocator.free", () => {
    it("coalesces buddies, so a fully freed arena is one block again", () => {
        const allocator = new BuddyAllocator({ capacity: 16 });
        const allocations = [1, 1, 2, 4, 8].map((slots) => allocator.allocate(slots));

        for (const i of [3, 0, 4, 2, 1]) allocator.free(allocations[i]);

        expect(allocator.allocate(16)).toMatchObject({ offset: 0, length: 16 });
        expect(allocator.capacity).toBe(16);
    });

    it("reuses a freed block", () => {
        const allocator = new BuddyAllocator({ capacity: 16 });
        allocator.allocate(4);
        const freed = allocator.allocate(4);
        allocator.allocate(8);

        allocator.free(freed);

        expect(allocator.allocate(3)).toMatchObject({ offset: freed.offset, length: 4 });
    });
});

describe("BuddyAllocator.extent", () => {
    it("is one past the highest live block", () => {
        const allocator = new BuddyAllocator({ capacity: 16 });
        expect(allocator.extent).toBe(0);

        allocator.allocate(4);
        expect(allocator.extent).toBe(4);

        allocator.allocate(8);
        expect(allocator.extent).toBe(16);
    });

    it("is unchanged by freeing a block below the top", () => {
        const allocator = new BuddyAllocator({ capacity: 16 });
        const low = allocator.allocate(4);
        allocator.allocate(4);

        allocator.free(low);

        expect(allocator.extent).toBe(8);
    });

    it("falls past every trailing free block when the top is freed", () => {
        const allocator = new BuddyAllocator({ capacity: 16 });
        const [, b, c, d, e] = [1, 1, 2, 4, 8].map((slots) => allocator.allocate(slots));

        allocator.free(b);
        expect(allocator.extent).toBe(16);

        allocator.free(e);
        expect(allocator.extent).toBe(8);

        allocator.free(c);
        allocator.free(d);
        expect(allocator.extent).toBe(1);
    });

    it("is zero once everything is freed", () => {
        const allocator = new BuddyAllocator({ capacity: 16 });
        const allocations = [3, 1, 7].map((slots) => allocator.allocate(slots));

        allocations.forEach((a) => allocator.free(a));

        expect(allocator.extent).toBe(0);
    });
});

describe("BuddyAllocator.reallocate", () => {
    it("resizes in place while the block is at most twice the request", () => {
        const allocator = new BuddyAllocator({ capacity: 16 });
        const allocation = allocator.allocate(5);

        allocator.reallocate(allocation, 8);
        allocator.reallocate(allocation, 4);

        expect(allocation).toMatchObject({ offset: 0, length: 8, requested: 4 });
    });

    it("moves to a smaller block, freeing the old one", () => {
        const allocator = new BuddyAllocator({ capacity: 16 });
        const allocation = allocator.allocate(8);
        const prev = { ...allocation };

        allocator.reallocate(allocation, 3);

        expect(allocation).toMatchObject({
            length: 4,
            requested: 3,
            generation: prev.generation + 1,
        });
        expect(overlaps(allocation, prev)).toBe(false);
        expect(allocator.allocate(8)).toMatchObject({ offset: prev.offset });
    });

    it("moves to a larger block that does not overlap the old one", () => {
        const allocator = new BuddyAllocator({ capacity: 8 });
        const allocation = allocator.allocate(4);
        const prev = { ...allocation };

        allocator.reallocate(allocation, 8);

        expect(allocation).toMatchObject({
            length: 8,
            requested: 8,
            generation: prev.generation + 1,
        });
        expect(overlaps(allocation, prev)).toBe(false);
        expect(allocator.allocate(4)).toMatchObject({ offset: prev.offset });
    });

    it("throws for an invalid size", () => {
        const allocator = new BuddyAllocator({ capacity: 8 });
        const allocation = allocator.allocate(4);

        expect(() => allocator.reallocate(allocation, 0)).toThrow();
    });
});

describe("BuddyAllocator.shrink", () => {
    it("halves a wholly free arena down to the requested capacity", () => {
        const allocator = new BuddyAllocator({ capacity: 64 });

        allocator.shrink(8);

        expect(allocator.capacity).toBe(8);
        expect(allocator.allocate(8)).toMatchObject({ offset: 0, length: 8 });
    });

    it("stops at the extent, keeping every live allocation where it is", () => {
        const allocator = new BuddyAllocator({ capacity: 64 });
        const low = allocator.allocate(4);
        const high = allocator.allocate(16);
        allocator.free(high);

        allocator.shrink(1);

        expect(allocator.capacity).toBe(4);
        expect(low).toMatchObject({ offset: 0, length: 4 });
    });

    it("does not shrink while the upper half is in use", () => {
        const allocator = new BuddyAllocator({ capacity: 16 });
        allocator.allocate(8);
        allocator.allocate(8);

        allocator.shrink(1);

        expect(allocator.capacity).toBe(16);
    });

    it("grows again after shrinking", () => {
        const allocator = new BuddyAllocator({ capacity: 64 });
        allocator.shrink(4);

        allocator.allocate(4);
        const next = allocator.allocate(4);

        expect(allocator.capacity).toBe(8);
        expect(next).toMatchObject({ offset: 4, length: 4 });
    });
});
