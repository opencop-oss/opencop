// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { Buffer, Device } from "@luma.gl/core";
import { RangeSet, type Range } from "./RangeSet";
import { FORMATS, type ColumnType, type Format, type TypedArray } from "./format";

/** A logical position in a {@link Column}. */
export type Slot = number;
/** Describes how a column is bound to a GPU buffer. */
export type Binding = {
    buffer: Buffer;
    offset: number;
    stride: number;
    value?: TypedArray;
};

/** Configuration of the underlying luma.gl {@link Buffer}. */
export type BufferConfig = {
    /** GPU buffer usage flags. */
    usage?: number;
    /** Index type for the GPU buffer, when used as an index buffer. */
    indexType?: "uint16" | "uint32";
    /** Number of slot-sized units to reserve before the column's data. */
    offset?: number;
    /** Number of additional slot-sized units to reserve after the column's data. */
    padding?: number;
};

/** Configuration for a {@link Column}. */
export type ColumnProps = {
    /** The luma.gl {@link Device}. */
    device: Device;
    /** Typed-array type used to store the column's values. */
    type: ColumnType;
    /**
     * Number of **logical components** stored in each slot.
     *
     * The underlying storage may occupy more **physical array elements** per slot than `size`.
     * This is the case for double-precision columns.
     */
    size: number;

    /** The total size of the column. */
    capacity?: number;
    /** Value used to initialize newly allocated slots. */
    tombstoneValue?: ArrayLike<number>;
    /** The configuration of the luma.gl {@link Buffer}. */
    bufferConfig?: BufferConfig;
};

/**
 * Stores a column of per-slot data in CPU memory and mirrors it to a GPU buffer.
 *
 * Writes are made to the CPU-side array first and tracked as dirty ranges.
 * {@link flush} uploads those changes to the GPU.
 */
export class Column {
    protected mirror: TypedArray;
    protected buffer: Buffer;
    protected _binding?: Binding;

    protected readonly device: Device;
    protected readonly format: Format;
    protected readonly bufferConfig: BufferConfig;
    protected readonly dirty: { slots: RangeSet; resize: boolean };

    private readonly tombstoneValue?: ArrayLike<number>;

    /**
     * Creates a column with the given storage configuration.
     *
     * The CPU-side array and GPU buffer are allocated with the requested
     * capacity.
     */
    constructor(props: ColumnProps) {
        const {
            device,
            type,
            size,

            tombstoneValue,
            capacity = 0,
            bufferConfig = {},
        } = props;
        this.device = device;
        this.format = new FORMATS[type](size);

        this.bufferConfig = bufferConfig;
        this.device = device;
        this.tombstoneValue = tombstoneValue;

        this.mirror = this.format.create(capacity);
        this.buffer = this.createBuffer(capacity);

        this.dirty = { slots: new RangeSet(), resize: false };
        this.tombstone({ start: 0, end: capacity });
    }

    /** Number of slots currently allocated. */
    public get capacity(): number {
        return this.mirror.length / this.format.elements;
    }

    /** Number of bytes used by one slot. */
    public get bytesPerSlot(): number {
        return this.mirror.BYTES_PER_ELEMENT * this.format.elements;
    }

    /** GPU buffer containing the column's data. */
    public get gpu(): Buffer {
        return this.buffer;
    }

    /**
     * Writes values to a slot and marks the affected slots as dirty.
     *
     * Values are written to the CPU-side array and uploaded on the next
     * {@link flush}.
     */
    public write(slot: Slot, values: ArrayLike<number>): void {
        this.format.encode(this.mirror, values, slot * this.format.elements);
        this.dirty.slots.add(slot, slot + values.length / this.format.size);
    }

    /**
     * Fills a range of slots with the given value.
     *
     * The affected range is marked dirty and uploaded on the next
     * {@link flush}.
     */
    public fill(value: ArrayLike<number>, range: Range): void {
        const { start, end } = range;
        const count = end - start;
        if (count <= 0) return;

        let written: number;
        const offset = start * this.format.elements;
        this.format.encode(this.mirror, value, offset);

        written = 1;
        while (written < count) {
            const copy = Math.min(written, count - written);
            this.mirror.copyWithin(
                offset + written * this.format.elements,
                offset,
                offset + copy * this.format.elements,
            );
            written += copy;
        }

        this.dirty.slots.add(start, end);
    }

    /**
     * Copies a range of slots to a new location.
     *
     * The destination range is marked dirty.
     */
    public move(range: Range, to: Slot) {
        const { start, end } = range;
        const count = end - start;

        this.mirror.copyWithin(
            to * this.format.elements,
            start * this.format.elements,
            end * this.format.elements,
        );
        this.dirty.slots.add(to, to + count);
    }

    /** Fills a range with the configured tombstone value, if one exists. */
    public tombstone(range: Range): void {
        if (this.tombstoneValue) this.fill(this.tombstoneValue, range);
    }

    /**
     * Ensures the column has room for the requested number of slots.
     *
     * @returns `true` if the column was resized, `false` if it already had
     * enough capacity.
     */
    public ensure(slots: number): boolean {
        if (slots <= this.capacity) return false;
        this.allocate(slots);
        return true;
    }

    /**
     * Shrinks the column to the requested number of slots, dropping the slots past it.
     *
     * @returns `true` if the column was resized, `false` if it was already that small.
     */
    public shrink(slots: number): boolean {
        if (slots >= this.capacity) return false;
        this.allocate(slots);
        return true;
    }

    /**
     * Flushes any changes to the resident GPU buffer.
     * @returns `true` if the buffer was written to, `false` otherwise.
     */
    public flush(): boolean {
        const resize = this.dirty.resize;
        if (resize) {
            this.dirty.resize = false;
            this.buffer.destroy();
            this._binding = undefined;
            this.buffer = this.createBuffer(this.capacity);
            this.dirty.slots.clear();
            this.dirty.slots.add(0, this.capacity);
        }

        const range = this.dirty.slots.consume();
        if (range) {
            const { start, end } = range;
            this.buffer.write(
                this.mirror.subarray(start * this.format.elements, end * this.format.elements),
                (start + (this.bufferConfig.offset ?? 0)) * this.bytesPerSlot,
            );
        }

        return resize || !!range;
    }

    /** Destroys underlying GPU buffer before garbage collection. */
    public destroy(): void {
        this.buffer.destroy();
    }

    /** Retrieve a cached binding to give to a deck.gl attribute. */
    public binding(): Binding {
        return (this._binding ??= {
            buffer: this.buffer,
            offset: 0,
            stride: this.bytesPerSlot,
            value: this.format.hint,
        });
    }

    private createBuffer(capacity: number): Buffer {
        return this.device.createBuffer({
            byteLength:
                ((this.bufferConfig.offset ?? 0) + capacity + (this.bufferConfig.padding ?? 0)) *
                this.bytesPerSlot,
            usage: this.bufferConfig.usage,
            indexType: this.bufferConfig.indexType,
        });
    }

    private allocate(slots: number): void {
        const next = this.format.create(slots);
        next.set(this.mirror.subarray(0, next.length));
        const old = this.capacity;
        this.mirror = next;
        this.tombstone({ start: old, end: this.capacity });
        this.dirty.resize = true;
    }
}
