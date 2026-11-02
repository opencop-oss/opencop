// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import type { Device } from "@luma.gl/core";
import type { Allocation } from "./Allocator";
import { FixedTable } from "./FixedTable";
import { VariableTable } from "./VariableTable";

/** A device whose buffers only record their size. */
const device = {
    createBuffer: ({ byteLength }: { byteLength: number }) => ({
        byteLength,
        write: () => {},
        destroy: () => {},
    }),
} as unknown as Device;

const attributes = { values: { type: "f32" as const, size: 1 } };

describe("FixedTable", () => {
    it("halves once at most a quarter is in use, down to its initial capacity", () => {
        const table = new FixedTable({ device, capacity: 4, attributes });
        const allocations: Allocation[] = [];
        for (let i = 0; i < 16; i++) allocations.push(table.allocate());
        expect(table.capacity).toBe(16);

        for (let i = 15; i >= 4; i--) table.free(allocations[i]);
        expect(table.capacity).toBe(8);
        expect(table.attributes.values.capacity).toBe(8);

        for (let i = 3; i >= 0; i--) table.free(allocations[i]);
        expect(table.capacity).toBe(4);
        expect(table.attributes.values.capacity).toBe(4);
    });

    it("keeps the live slots' values when it shrinks", () => {
        const table = new FixedTable({ device, capacity: 2, attributes });
        const allocations = Array.from({ length: 8 }, () => table.allocate());
        allocations.forEach((a, i) => table.attributes.values.write(a.offset, [i]));

        for (let i = 7; i >= 2; i--) table.free(allocations[i]);

        expect(table.capacity).toBe(4);
        // `flush` recreates the buffer at the new size and uploads the surviving prefix.
        expect(table.flush()).toBe(true);
        expect(table.attributes.values.binding().buffer.byteLength).toBe(4 * 4);
    });
});

describe("VariableTable", () => {
    it("shrinks its columns and its allocator together once the upper arena is free", () => {
        const table = new VariableTable({ device, capacity: 8, attributes });
        const low = table.allocate(2);
        const high = table.allocate(32);
        expect(table.capacity).toBe(64);

        table.free(high);

        expect(table.capacity).toBe(8);
        expect(table.attributes.values.capacity).toBe(8);
        expect(table.extent).toBe(low.offset + low.length);
        // The arena still hands out the space it kept.
        expect(table.allocate(4).offset + 4).toBeLessThanOrEqual(8);
    });
});
