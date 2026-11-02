// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { Allocation, MutableAllocation } from "./Allocator";
import { AttributeTable } from "./AttributeTable";

export class FixedTable extends AttributeTable {
    /** Current allocations, so we can mutate while allowing consumer to keep their handle. */
    private readonly allocations: Allocation[] = [];

    public allocate(): Allocation {
        const allocation = { offset: this.extent, length: 1, requested: 1, generation: 0 };
        this.ensure(++this.extent);
        this.allocations.push(allocation);
        return allocation;
    }

    public reallocate(allocation: Allocation, slots: number): void {
        void allocation;
        void slots;
    }

    public free(allocation: Allocation): void {
        if (this.allocations.length === 0) return;

        const tail = --this.extent;
        const last = this.allocations.pop() as MutableAllocation;
        const { offset: freedOffset } = allocation;

        // End of array, no need to swap.
        if (freedOffset === tail) return this.trim();

        // Move last's values into the freed slot in every column.
        for (const column of Object.values(this.attributes))
            column.move({ start: last.offset, end: last.offset + last.length }, freedOffset);

        // Swap the allocated range of last to free'd view.
        last.offset = freedOffset;
        last.generation++;

        // Swap position of last in allocations array.
        this.allocations[freedOffset] = last;
        this.trim();
    }
}
