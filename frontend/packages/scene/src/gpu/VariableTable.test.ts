// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import type { Device } from "@luma.gl/core";
import type { Allocation } from "./Allocator";
import { VariableTable } from "./VariableTable";

/** A device whose buffers keep the bytes written to them, standing in for GPU memory. */
const device = {
    createBuffer: ({ byteLength }: { byteLength: number }) => {
        const bytes = new Uint8Array(byteLength);
        return {
            byteLength,
            bytes,
            write: (data: ArrayBufferView, byteOffset: number = 0) =>
                bytes.set(
                    new Uint8Array(data.buffer, data.byteOffset, data.byteLength),
                    byteOffset,
                ),
            destroy: () => {},
        };
    },
} as unknown as Device;

const TOMBSTONE = -1;

function setup(capacity: number) {
    const table = new VariableTable({
        device,
        capacity,
        attributes: { values: { type: "f32", size: 1, tombstoneValue: [TOMBSTONE] } },
    });
    const column = table.attributes.values;
    /** Flushes, then reads back the slots of `allocation` (or `[start, end)`) from the GPU. */
    const read = (range: Pick<Allocation, "offset" | "length">) => {
        table.flush();
        const gpu = column.gpu as unknown as { bytes: Uint8Array };
        return Array.from(new Float32Array(gpu.bytes.buffer, range.offset * 4, range.length));
    };
    return { table, column, read };
}

describe("VariableTable", () => {
    it("allocates power-of-two blocks and reports the allocator's extent", () => {
        const { table } = setup(16);

        const a = table.allocate(3);
        const b = table.allocate();

        expect(a).toMatchObject({ offset: 0, length: 4, requested: 3 });
        expect(b).toMatchObject({ offset: 4, length: 1 });
        expect(table.extent).toBe(5);

        table.free(b);
        expect(table.extent).toBe(4);
    });

    it("tombstones a freed range", () => {
        const { table, column, read } = setup(16);
        const a = table.allocate(4);
        column.write(a.offset, [1, 2, 3, 4]);

        table.free(a);

        expect(read(a)).toEqual([-1, -1, -1, -1]);
        expect(table.extent).toBe(0);
    });

    describe("reallocate", () => {
        it("keeps the allocation, untouched, at its current length", () => {
            const { table, column } = setup(16);
            const a = table.allocate(4);
            column.write(a.offset, [1, 2, 3, 4]);
            table.flush();
            const prev = { ...a };

            table.reallocate(a, 4);

            expect(a).toEqual(prev);
            expect(table.flush()).toBe(false);
        });

        it("records the new requested size at the block's full length", () => {
            const { table } = setup(16);
            const a = table.allocate(3);

            table.reallocate(a, 4);

            expect(a.requested).toBe(4);
        });

        it("resizes in place within its block, tombstoning the dropped tail", () => {
            const { table, column, read } = setup(16);
            const a = table.allocate(4);
            column.write(a.offset, [1, 2, 3, 4]);

            table.reallocate(a, 2);

            expect(a).toMatchObject({ offset: 0, length: 4, requested: 2 });
            expect(read(a)).toEqual([1, 2, -1, -1]);
        });

        it("moves to a new block, carrying its values and tombstoning the old range", () => {
            const { table, column, read } = setup(16);
            const a = table.allocate(2);
            column.write(a.offset, [1, 2]);
            const prev = { ...a };

            table.reallocate(a, 4);

            expect(a).toMatchObject({ length: 4, requested: 4 });
            expect(a.offset >= prev.offset + prev.length || a.offset + 4 <= prev.offset).toBe(true);
            expect(read({ offset: a.offset, length: 2 })).toEqual([1, 2]);
            expect(read(prev)).toEqual([-1, -1]);
            expect(table.extent).toBe(a.offset + a.length);
        });
    });

    describe("growth", () => {
        it("grows its columns with the arena, keeping data and tombstoning the new tail", () => {
            const { table, column, read } = setup(4);
            const a = table.allocate(4);
            column.write(a.offset, [1, 2, 3, 4]);

            const b = table.allocate(4);

            expect(b.offset).toBe(4);
            expect(table.capacity).toBe(8);
            expect(column.capacity).toBe(8);
            expect(read({ offset: 0, length: 8 })).toEqual([1, 2, 3, 4, -1, -1, -1, -1]);
        });

        it("grows to fit a request larger than the arena", () => {
            const { table, column } = setup(4);

            const a = table.allocate(10);

            expect(a).toMatchObject({ length: 16, requested: 10 });
            expect(column.capacity).toBeGreaterThanOrEqual(a.offset + a.length);
        });
    });

    describe("trim", () => {
        it("does not shrink while a live allocation sits high in the arena", () => {
            const { table } = setup(8);
            const low = table.allocate(2);
            table.allocate(32);

            table.free(low);

            expect(table.capacity).toBe(64);
            expect(table.extent).toBe(64);
        });

        it("shrinks once a high allocation moves down, keeping live values", () => {
            const { table, column, read } = setup(8);
            const low = table.allocate(2);
            column.write(low.offset, [1, 2]);
            const high = table.allocate(32);

            table.reallocate(high, 2);
            column.write(high.offset, [3, 4]);

            expect(table.capacity).toBe(8);
            expect(column.capacity).toBe(8);
            expect(read(low)).toEqual([1, 2]);
            expect(read(high)).toEqual([3, 4]);
        });

        it("never shrinks below its initial capacity", () => {
            const { table, column } = setup(16);
            const a = table.allocate(64);

            table.free(a);

            expect(table.capacity).toBe(16);
            expect(column.capacity).toBe(16);
            expect(table.allocate(16)).toMatchObject({ offset: 0, length: 16 });
            expect(table.capacity).toBe(16);
        });
    });
});
