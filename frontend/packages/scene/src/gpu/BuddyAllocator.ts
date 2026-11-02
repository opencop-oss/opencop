// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { Allocator, Allocation, MutableAllocation } from "./Allocator";

/** Largest supported order — `1 << k` is int32 arithmetic, prevents overflow. */
const MAX_ORDER = 30;
export const ceilLog2 = (v: number): number => (v <= 1 ? 0 : 32 - Math.clz32(v - 1));

export type BuddyAllocatorOptions = {
    /**
     * Initial arena capacity, in slots; rounded up to a power of two.
     * @default 1024
     */
    capacity?: number;
};

/**
 * A power-of-two "buddy" allocator over a linear slot space. The arena doubles itself
 * when full, up to `2^30` slots.
 */
export class BuddyAllocator implements Allocator {
    private maxOrder: number;
    /** `freeLists[k]` holds the starts of free blocks of slots `2^k`. */
    private readonly freeLists: Set<number>[];
    private _extent = 0;

    constructor(props: BuddyAllocatorOptions = {}) {
        const { capacity = 1024 } = props;
        this.assertSlots(capacity);

        this.maxOrder = ceilLog2(capacity);
        this.freeLists = Array.from({ length: this.maxOrder + 1 }, () => new Set());
        this.freeLists[this.maxOrder].add(0); // The arena starts as one free root.
    }

    public get capacity(): number {
        return 1 << this.maxOrder;
    }

    public get extent(): number {
        return this._extent;
    }

    public allocate(slots: number): Allocation {
        this.assertSlots(slots);

        // Find the smallest free block that fits.
        const order = ceilLog2(slots);
        let current = order;
        while (current <= this.maxOrder && this.freeLists[current].size === 0) current++;
        // Hits when order > this.maxOrder or when there is no free space left.
        if (current > this.maxOrder) {
            this.grow();
            return this.allocate(slots);
        }

        const found = this.freeLists[current].values().next().value!;
        this.freeLists[current].delete(found);

        // Split down to the requested order, shelving the upper half each time.
        while (current > order) {
            current--;
            this.freeLists[current].add(found + (1 << current));
        }

        const length = 1 << order;
        if (found + length > this._extent) this._extent = found + length;

        return {
            offset: found,
            length,
            requested: slots,
            generation: 0,
        };
    }

    public reallocate(allocation: Allocation, slots: number): void {
        const mutable = allocation as MutableAllocation;
        if (slots <= allocation.length && slots >= allocation.length / 2) {
            mutable.requested = slots;
            mutable.generation++;
            return;
        }

        const next = this.allocate(slots); // before the free, so the new block can't overlap
        this.free(allocation);
        mutable.offset = next.offset;
        mutable.length = next.length;
        mutable.requested = next.requested;
        mutable.generation++;
    }

    public free(allocation: Allocation): void {
        let offset = allocation.offset;
        const length = allocation.length;
        this.assertSlots(length);

        let order = ceilLog2(length);
        const end = offset + (1 << order);

        while (order < this.maxOrder) {
            const buddy = offset ^ (1 << order);
            if (!this.freeLists[order].delete(buddy)) break; // Keep removing buddy from free list and coalesce into parent.
            offset = Math.min(offset, buddy); // The lower sibling's start is the parent's start.
            order++;
        }

        this.freeLists[order].add(offset);

        // Only the topmost block can move the mark; anything below it leaves it untouched.
        if (end === this._extent) this._extent = this.findExtent();
    }

    /** Halves the arena while its upper half is free and the half still holds `capacity` slots. */
    public shrink(capacity: number): void {
        while (this.maxOrder > 0) {
            const half = 1 << (this.maxOrder - 1);
            if (half < capacity || this._extent > half) return;

            // A wholly free arena is one root block; otherwise the free upper half is one
            // block of the order below, coalesced eagerly by `free`.
            if (this.freeLists[this.maxOrder].delete(0)) this.freeLists[this.maxOrder - 1].add(0);
            else if (!this.freeLists[this.maxOrder - 1].delete(half)) return;

            this.freeLists.pop();
            this.maxOrder--;
        }
    }

    /** Scan trailing free blocks to find next extent. */
    private findExtent(): number {
        let extent = this.capacity;

        // Trailing free blocks have strictly decreasing orders — the buddy of a free block must be in use. If it were not,
        // it would mean the eager coalescing failed to remove it from the free-list — contradiction.
        for (let order = this.maxOrder; order >= 0; order--) {
            const start = extent - (1 << order);
            if (start >= 0 && this.freeLists[order].has(start)) extent = start;
        }

        return extent;
    }

    /**
     * Doubles the arena, releasing the new upper half as one free block so it coalesces
     * with an entirely-free old arena through the normal merge path.
     * @throws Past `2^30` slots.
     */
    private grow(): void {
        if (this.maxOrder === MAX_ORDER) throw new Error("Arena cannot grow beyond 2^30 slots.");

        const half = this.capacity;
        this.maxOrder++;
        this.freeLists.push(new Set());
        this.free({
            offset: half,
            length: half,
            requested: half,
            generation: 0,
        });
    }

    /**
     * @param slots Slots requested.
     * @throws If `slots` is not a positive integer, or exceeds the maximum order.
     */
    private assertSlots(slots: number): void {
        if (!Number.isInteger(slots) || slots < 1)
            throw new Error("slots must be a positive integer.");
        if (slots > 1 << MAX_ORDER) throw new Error("slots exceeds the maximum arena capacity.");
    }
}
