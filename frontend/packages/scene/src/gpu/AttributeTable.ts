// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { Allocation } from "./Allocator";
import { Column, type Binding, type ColumnProps } from "./Column";
import type { Device } from "@luma.gl/core";

export type AttributesBinding = Record<string, Binding>;
export type ColumnSpec = Omit<ColumnProps, "device" | "capacity">;

export type AttributeTableProps = {
    device: Device;
    capacity?: number;
    attributes?: Record<string, ColumnSpec>;
};

export abstract class AttributeTable {
    public readonly attributes: Record<string, Column>;

    protected readonly device: Device;

    private _extent: number = 0;
    private _capacity: number;
    /** The capacity the table was created with, which it never shrinks below. */
    private readonly minCapacity: number;

    constructor(props: AttributeTableProps) {
        const { device, capacity = 1024, attributes = {} } = props;
        this.device = device;
        this._capacity = capacity;
        this.minCapacity = capacity;
        this.attributes = {};
        this.add(attributes);
    }

    public abstract allocate(slots?: number): Allocation;
    public abstract reallocate(allocation: Allocation, slots: number): void;
    public abstract free(allocation: Allocation): void;

    /**
     * One past the highest slot any live allocation occupies — the smallest `n` for which
     * every live allocation lies within `[0, n)`. Zero when the table is empty.
     */
    public get extent(): number {
        return this._extent;
    }

    public get capacity(): number {
        return this._capacity;
    }

    public ensure(slots: number): boolean {
        if (slots <= this.capacity) return false;
        const next = Math.max(slots, this.capacity * 2);
        this.capacity = next;
        return Object.values(this.attributes).reduce((acc, col) => col.ensure(next) || acc, false);
    }

    public bindings(): AttributesBinding {
        return Object.fromEntries(
            Object.entries(this.attributes).map(([attr, col]) => [attr, col.binding()]),
        );
    }

    public add(attributes: Record<string, ColumnSpec>) {
        for (const [attr, spec] of Object.entries(attributes)) {
            this.attributes[attr] = new Column({
                ...spec,
                device: this.device,
                capacity: this.capacity,
            });
        }
    }

    public remove(attribute: string): void {
        this.attributes[attribute]?.destroy();
        delete this.attributes[attribute];
    }

    /**
     * Flushes all {@link Column Columns} in the table.
     * @returns `true` if an underlying GPU buffer was written to. `false` otherwise.
     */
    public flush(): boolean {
        return Object.values(this.attributes).reduce((acc, col) => col.flush() || acc, false);
    }

    /** Destroys buffers of every column. */
    public destroy(): void {
        for (const col of Object.values(this.attributes)) col.destroy();
    }

    protected set capacity(capacity: number) {
        this._capacity = capacity;
    }

    protected set extent(extent: number) {
        this._extent = extent;
    }

    /**
     * Halves the table while at most a quarter of it is in use, never below its initial
     * capacity. A quarter rather than a half, so a table hovering at a boundary does not
     * resize on every add and remove.
     */
    protected trim(): void {
        let next = this.capacity;
        while (next / 2 >= this.minCapacity && this.extent <= next / 4) next /= 2;
        if (next < this.capacity) this.shrink(next);
    }

    /** Shrinks every column to `capacity` slots; all live allocations lie below it. */
    protected shrink(capacity: number): void {
        this.capacity = capacity;
        for (const col of Object.values(this.attributes)) col.shrink(capacity);
    }
}
