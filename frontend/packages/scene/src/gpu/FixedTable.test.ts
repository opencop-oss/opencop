// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import type { Device } from "@luma.gl/core";
import type { Allocation } from "./Allocator";
import type { Column } from "./Column";
import { FixedTable } from "./FixedTable";

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

const attributes = {
    ids: { type: "f32" as const, size: 1 },
    colors: { type: "u8" as const, size: 4 },
};

const gpuBytes = (column: Column) => (column.gpu as unknown as { bytes: Uint8Array }).bytes;

function setup(capacity: number) {
    const table = new FixedTable({ device, capacity, attributes });
    const write = (allocation: Allocation, value: number) => {
        table.attributes.ids.write(allocation.offset, [value]);
        table.attributes.colors.write(allocation.offset, [value, value, value, value]);
    };
    /** Flushes, then reads back each column's values at the allocation's slot. */
    const read = (allocation: Allocation) => {
        table.flush();
        const ids = new Float32Array(gpuBytes(table.attributes.ids).buffer);
        const colors = gpuBytes(table.attributes.colors);
        const slot = allocation.offset;
        return { id: ids[slot], color: Array.from(colors.subarray(slot * 4, slot * 4 + 4)) };
    };
    return { table, write, read };
}

describe("FixedTable", () => {
    it("packs allocations densely from slot zero", () => {
        const { table } = setup(4);

        const allocations = [table.allocate(), table.allocate(), table.allocate()];

        expect(allocations.map((a) => a.offset)).toEqual([0, 1, 2]);
        expect(allocations.every((a) => a.length === 1 && a.generation === 0)).toBe(true);
        expect(table.extent).toBe(3);
    });

    it("grows its columns past capacity, keeping their values", () => {
        const { table, write, read } = setup(2);
        const first = table.allocate();
        write(first, 7);

        table.allocate();
        table.allocate();

        expect(table.capacity).toBe(4);
        expect(table.attributes.ids.capacity).toBe(4);
        expect(read(first)).toEqual({ id: 7, color: [7, 7, 7, 7] });
    });

    it("leaves an allocation untouched when reallocated", () => {
        const { table } = setup(4);
        const allocation = table.allocate();
        const prev = { ...allocation };

        table.reallocate(allocation, 5);

        expect(allocation).toEqual(prev);
    });

    describe("free", () => {
        it("moves the last slot into the hole in every column", () => {
            const { table, write, read } = setup(4);
            const [a, b, c, d] = Array.from({ length: 4 }, () => table.allocate());
            [a, b, c, d].forEach(write);

            table.free(b);

            expect(table.extent).toBe(3);
            expect(d).toMatchObject({ offset: 1, generation: 1 });
            expect(read(d)).toEqual({ id: 3, color: [3, 3, 3, 3] });
            expect(a).toMatchObject({ offset: 0, generation: 0 });
            expect(c).toMatchObject({ offset: 2, generation: 0 });
        });

        it("moves nothing when freeing the tail", () => {
            const { table, write } = setup(4);
            const [a, b, c] = Array.from({ length: 3 }, () => table.allocate());
            [a, b, c].forEach(write);
            table.flush();

            table.free(c);

            expect(table.extent).toBe(2);
            expect(table.flush()).toBe(false);
            expect([a, b].map((x) => [x.offset, x.generation])).toEqual([
                [0, 0],
                [1, 0],
            ]);
        });

        it("keeps every live handle on its own values when freed in any order", () => {
            const { table, write, read } = setup(4);
            let next = 0;
            const live = new Map<Allocation, number>();
            const add = () => {
                const allocation = table.allocate();
                write(allocation, next);
                live.set(allocation, next++);
            };
            const expectIntact = () => {
                expect(table.extent).toBe(live.size);
                const offsets = [...live.keys()].map((a) => a.offset).sort((x, y) => x - y);
                expect(offsets).toEqual([...Array(live.size).keys()]);
                for (const [allocation, value] of live)
                    expect(read(allocation)).toEqual({ id: value, color: Array(4).fill(value) });
            };

            for (let i = 0; i < 12; i++) add();
            const handles = [...live.keys()];
            for (const i of [5, 0, 11, 3, 7, 1]) {
                table.free(handles[i]);
                live.delete(handles[i]);
                expectIntact();
            }
            add();
            add();
            expectIntact();
            for (const allocation of [...live.keys()].reverse().filter((_, i) => i % 2 === 0)) {
                table.free(allocation);
                live.delete(allocation);
                expectIntact();
            }
        });
    });
});
