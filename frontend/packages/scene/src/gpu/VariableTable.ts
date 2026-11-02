// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { Allocator, Allocation, AllocatorConstructor } from "./Allocator";
import { AttributeTable, type AttributeTableProps } from "./AttributeTable";
import { BuddyAllocator } from "./BuddyAllocator";

export type VariableTableProps = AttributeTableProps & {
    /** Constructor of a custom allocator of the table. The default allocator is a {@link BuddyAllocator}. */
    allocator?: AllocatorConstructor;
};

/**
 * An {@link AttributeTable} whose views span variable runs of slots.
 * @see {@link FixedTable} for the densely-packed, one-slot variant.
 */
export class VariableTable extends AttributeTable {
    protected readonly allocator: Allocator;

    constructor(props: VariableTableProps) {
        super(props);
        this.allocator = new (props.allocator ?? BuddyAllocator)({
            capacity: props.capacity ?? 1024,
        });
    }

    override get extent() {
        return this.allocator.extent;
    }

    public allocate(slots: number = 1): Allocation {
        const allocation = this.allocator.allocate(slots);
        this.ensure(this.allocator.capacity);
        return allocation;
    }

    public reallocate(allocation: Allocation, slots: number): void {
        if (slots === allocation.requested) return;

        const prev = { ...allocation };
        this.allocator.reallocate(allocation, slots);
        const next = allocation;

        this.ensure(this.allocator.capacity); // Arena may have grown
        const kept = Math.min(prev.requested, next.requested);
        for (const col of Object.values(this.attributes)) {
            if (next.offset !== prev.offset)
                col.move({ start: prev.offset, end: prev.offset + kept }, next.offset);
            // Tombstone all slots of prev that are **before the start** of next.
            // If prev start > next start, this is a no op.
            col.tombstone({
                start: prev.offset,
                end: Math.min(prev.offset + prev.requested, next.offset),
            });
            // Tombstone all slots of prev that are **after the end** of next.
            // If prev end < next end, this is no-op.
            col.tombstone({
                start: Math.max(prev.offset, next.offset + next.requested),
                end: prev.offset + prev.requested,
            });
        }

        this.trim();
    }

    public free(allocation: Allocation): void {
        const { offset, length } = allocation;
        // Tombstone the freed columns so no left over render data.
        for (const col of Object.values(this.attributes))
            col.tombstone({ start: offset, end: offset + length });
        this.allocator.free(allocation);
        this.trim();
    }

    protected override shrink(capacity: number): void {
        this.allocator.shrink(capacity);
        super.shrink(this.allocator.capacity);
    }
}
