// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it, vi } from "vitest";
import type { Device } from "@luma.gl/core";
import { Column, type ColumnProps } from "./Column";

/** A buffer that keeps the bytes written to it, standing in for GPU memory. */
const fakeBuffer = ({ byteLength }: { byteLength: number }) => {
    const bytes = new Uint8Array(byteLength);
    return {
        byteLength,
        bytes,
        write: vi.fn((data: ArrayBufferView, byteOffset: number = 0) => {
            bytes.set(new Uint8Array(data.buffer, data.byteOffset, data.byteLength), byteOffset);
        }),
        destroy: vi.fn(),
    };
};
type FakeBuffer = ReturnType<typeof fakeBuffer>;

function setup(props: Omit<ColumnProps, "device">) {
    const createBuffer = vi.fn(fakeBuffer);
    const column = new Column({ device: { createBuffer } as unknown as Device, ...props });
    const gpu = () => column.gpu as unknown as FakeBuffer;
    /** Flushes, then reads back what the GPU buffer holds. */
    const flushed = () => {
        column.flush();
        return Array.from(new Float32Array(gpu().bytes.buffer));
    };
    return { column, createBuffer, gpu, flushed };
}

describe("Column", () => {
    describe("write", () => {
        it("writes a slot's components and uploads them on flush", () => {
            const { column, flushed } = setup({ type: "f32", size: 2, capacity: 3 });

            column.write(1, [3, 4]);

            expect(flushed()).toEqual([0, 0, 3, 4, 0, 0]);
        });

        it("writes consecutive slots from one call", () => {
            const { column, gpu } = setup({ type: "f32", size: 2, capacity: 4 });

            column.write(1, [1, 2, 3, 4]);
            column.flush();

            expect(gpu().write).toHaveBeenCalledOnce();
            expect(gpu().write).toHaveBeenCalledWith(new Float32Array([1, 2, 3, 4]), 8);
        });
    });

    describe("fill", () => {
        it("repeats a multi-component value across a long range, and nothing past it", () => {
            const { column, gpu, flushed } = setup({ type: "f32", size: 3, capacity: 1000 });

            column.fill([1, 2, 3], { start: 5, end: 998 });
            const values = flushed();

            expect(gpu().write).toHaveBeenCalledWith(expect.any(Float32Array), 5 * 12);
            expect(gpu().write.mock.calls[0][0]).toHaveLength(993 * 3);
            expect(values.slice(0, 15)).toEqual(new Array(15).fill(0));
            expect(values.slice(998 * 3)).toEqual(new Array(6).fill(0));
            for (let slot = 5; slot < 998; slot++)
                expect(values.slice(slot * 3, slot * 3 + 3)).toEqual([1, 2, 3]);
        });

        it("fills a single slot", () => {
            const { column, flushed } = setup({ type: "f32", size: 1, capacity: 3 });

            column.fill([7], { start: 1, end: 2 });

            expect(flushed()).toEqual([0, 7, 0]);
        });

        it("ignores an empty or inverted range", () => {
            const { column } = setup({ type: "f32", size: 1, capacity: 8 });

            column.fill([7], { start: 3, end: 3 });
            column.fill([7], { start: 5, end: 2 });

            expect(column.flush()).toBe(false);
        });
    });

    describe("move", () => {
        it("copies a range to a new slot and dirties only the destination", () => {
            const { column, gpu, flushed } = setup({ type: "f32", size: 1, capacity: 8 });
            column.write(0, [1, 2, 3]);
            column.flush();

            column.move({ start: 0, end: 3 }, 5);

            expect(flushed()).toEqual([1, 2, 3, 0, 0, 1, 2, 3]);
            expect(gpu().write).toHaveBeenLastCalledWith(new Float32Array([1, 2, 3]), 5 * 4);
        });

        it("copies overlapping ranges intact", () => {
            const { column, flushed } = setup({ type: "f32", size: 1, capacity: 6 });
            column.write(0, [1, 2, 3, 4]);

            column.move({ start: 0, end: 4 }, 2);

            expect(flushed()).toEqual([1, 2, 1, 2, 3, 4]);
        });
    });

    describe("tombstone", () => {
        it("fills a range with the tombstone value", () => {
            const { column, flushed } = setup({
                type: "f32",
                size: 2,
                capacity: 3,
                tombstoneValue: [-1, -2],
            });
            column.write(0, [1, 1, 2, 2, 3, 3]);

            column.tombstone({ start: 1, end: 3 });

            expect(flushed()).toEqual([1, 1, -1, -2, -1, -2]);
        });

        it("tombstones the whole column on construction", () => {
            const { flushed } = setup({ type: "f32", size: 1, capacity: 3, tombstoneValue: [9] });

            expect(flushed()).toEqual([9, 9, 9]);
        });

        it("does nothing without a tombstone value", () => {
            const { column } = setup({ type: "f32", size: 1, capacity: 3 });

            column.tombstone({ start: 0, end: 3 });

            expect(column.flush()).toBe(false);
        });
    });

    describe("ensure", () => {
        it("does nothing when the column is already large enough", () => {
            const { column, createBuffer } = setup({ type: "f32", size: 1, capacity: 4 });

            expect(column.ensure(4)).toBe(false);
            expect(createBuffer).toHaveBeenCalledOnce();
        });

        it("grows into a new buffer on the next flush, keeping data and tombstoning the new tail", () => {
            const { column, gpu, flushed } = setup({
                type: "f32",
                size: 1,
                capacity: 2,
                tombstoneValue: [9],
            });
            column.write(0, [1, 2]);
            column.flush();
            const old = gpu();

            expect(column.ensure(5)).toBe(true);

            expect(column.capacity).toBe(5);
            expect(gpu()).toBe(old);
            expect(old.destroy).not.toHaveBeenCalled();
            // The whole mirror is re-uploaded to the fresh buffer.
            expect(flushed()).toEqual([1, 2, 9, 9, 9]);
            expect(old.destroy).toHaveBeenCalledOnce();
            expect(gpu()).not.toBe(old);
            expect(gpu().byteLength).toBe(5 * 4);
            expect(gpu().write).toHaveBeenCalledWith(new Float32Array([1, 2, 9, 9, 9]), 0);
            expect(column.flush()).toBe(false);
        });

        it("keeps the cached binding until the next flush replaces the buffer", () => {
            const { column } = setup({ type: "f32", size: 1, capacity: 2 });
            const before = column.binding();

            column.ensure(4);
            expect(column.binding()).toBe(before);

            column.flush();
            expect(column.binding()).not.toBe(before);
            expect(column.binding().buffer).toBe(column.gpu);
        });
    });

    describe("shrink", () => {
        it("keeps the prefix in a smaller buffer", () => {
            const { column, gpu, flushed } = setup({ type: "f32", size: 1, capacity: 4 });
            column.write(0, [1, 2, 3, 4]);

            expect(column.shrink(2)).toBe(true);

            expect(column.capacity).toBe(2);
            expect(flushed()).toEqual([1, 2]);
            expect(gpu().byteLength).toBe(2 * 4);
        });

        it("does nothing when the column is already that small", () => {
            const { column } = setup({ type: "f32", size: 1, capacity: 4 });

            expect(column.shrink(4)).toBe(false);
            expect(column.shrink(8)).toBe(false);
            expect(column.capacity).toBe(4);
        });

        it("tombstones the dropped slots if the column grows back", () => {
            const { column, flushed } = setup({
                type: "f32",
                size: 1,
                capacity: 4,
                tombstoneValue: [9],
            });
            column.write(0, [1, 2, 3, 4]);

            column.shrink(2);
            column.ensure(4);

            expect(flushed()).toEqual([1, 2, 9, 9]);
        });
    });

    describe("flush", () => {
        it("uploads only the span between the lowest and highest dirty slots", () => {
            const { column, gpu } = setup({ type: "f32", size: 2, capacity: 8 });
            column.write(5, [5, 5]);
            column.write(2, [2, 2]);

            expect(column.flush()).toBe(true);

            expect(gpu().write).toHaveBeenCalledOnce();
            expect(gpu().write).toHaveBeenCalledWith(
                new Float32Array([2, 2, 0, 0, 0, 0, 5, 5]),
                2 * 8,
            );
        });

        it("returns false and uploads nothing once clean", () => {
            const { column, gpu } = setup({ type: "f32", size: 1, capacity: 4 });
            column.write(0, [1]);
            column.flush();

            expect(column.flush()).toBe(false);
            expect(gpu().write).toHaveBeenCalledOnce();
        });

        it("reserves headroom and padding, and uploads past the headroom", () => {
            const { column, gpu, flushed } = setup({
                type: "f32",
                size: 1,
                capacity: 4,
                bufferConfig: { offset: 2, padding: 3 },
            });

            column.write(1, [5]);

            expect(gpu().byteLength).toBe((2 + 4 + 3) * 4);
            expect(flushed()).toEqual([0, 0, 0, 5, 0, 0, 0, 0, 0]);
            expect(gpu().write).toHaveBeenCalledWith(new Float32Array([5]), (1 + 2) * 4);
        });

        it("keeps the headroom and padding when the buffer is recreated", () => {
            const { column, gpu } = setup({
                type: "f32",
                size: 1,
                capacity: 4,
                bufferConfig: { offset: 2, padding: 3 },
            });

            column.ensure(8);
            column.flush();

            expect(gpu().byteLength).toBe((2 + 8 + 3) * 4);
        });

        it("creates the buffer with the configured usage and index type", () => {
            const { createBuffer } = setup({
                type: "u32",
                size: 1,
                capacity: 4,
                bufferConfig: { usage: 16, indexType: "uint32" },
            });

            expect(createBuffer).toHaveBeenCalledWith({
                byteLength: 16,
                usage: 16,
                indexType: "uint32",
            });
        });
    });

    describe("binding", () => {
        it("is cached and strides one slot", () => {
            const { column } = setup({ type: "f32", size: 3, capacity: 4 });

            expect(column.binding()).toBe(column.binding());
            expect(column.binding()).toEqual({
                buffer: column.gpu,
                offset: 0,
                stride: 12,
                value: undefined,
            });
        });
    });

    describe("multi-component u8", () => {
        it("stores one byte per component", () => {
            const { column, gpu } = setup({ type: "unorm8", size: 4, capacity: 3 });

            column.fill([255, 0, 0, 255], { start: 0, end: 2 });
            column.write(2, [0, 128, 0, 64]);
            column.flush();

            expect(column.bytesPerSlot).toBe(4);
            expect(Array.from(gpu().bytes)).toEqual([
                255, 0, 0, 255, 255, 0, 0, 255, 0, 128, 0, 64,
            ]);
        });
    });

    describe("f64", () => {
        const lng = -122.4194155;
        const lat = 37.774929123456;
        const split = (v: number) => [Math.fround(v), Math.fround(v - Math.fround(v))];

        it("stores a high and a low f32 per component and hints a Float64Array", () => {
            const { column, flushed } = setup({ type: "f64", size: 2, capacity: 2 });

            column.write(1, [lng, lat]);

            const [lngHi, lngLo] = split(lng);
            const [latHi, latLo] = split(lat);
            expect(flushed().slice(4)).toEqual([lngHi, latHi, lngLo, latLo]);
            expect(column.bytesPerSlot).toBe(16);
            expect(column.binding()).toMatchObject({ stride: 16, value: expect.any(Float64Array) });
        });

        it("counts written slots in logical components", () => {
            const { column, gpu } = setup({ type: "f64", size: 2, capacity: 4 });

            column.write(1, [1, 2, 3, 4]);
            column.flush();

            expect(gpu().write).toHaveBeenCalledWith(
                new Float32Array([1, 2, 0, 0, 3, 4, 0, 0]),
                1 * 16,
            );
        });

        it("fills a range with the encoded pair", () => {
            const { column, flushed } = setup({ type: "f64", size: 1, capacity: 5 });

            column.fill([lng], { start: 0, end: 5 });

            expect(flushed()).toEqual(new Array(5).fill(split(lng)).flat());
        });
    });
});
