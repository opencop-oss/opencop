// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import type { Layer } from "@deck.gl/core";
import type { Device } from "@luma.gl/core";
import type { Binding } from "../../gpu/Column";
import type { PickingIndex } from "../../picking/PickingIndex";
import type { PartialPrimitiveOf } from "../../primitive";
import { PointBatch, type PointPrimitive } from "./PointBatch";

type FakeBuffer = { bytes: Uint8Array<ArrayBuffer>; writes: number };

/** A device whose buffers keep what is written to them. */
const device = {
    createBuffer: ({ byteLength }: { byteLength: number }) => {
        const buffer = {
            byteLength,
            bytes: new Uint8Array(byteLength),
            writes: 0,
            write(data: ArrayBufferView, byteOffset = 0) {
                buffer.writes++;
                buffer.bytes.set(
                    new Uint8Array(data.buffer, data.byteOffset, data.byteLength),
                    byteOffset,
                );
            },
            destroy() {},
        };
        return buffer;
    },
} as unknown as Device;

type ArrayType = typeof Float32Array | typeof Uint8Array;

/** The uploaded contents of a binding's buffer, one row per slot. */
function rows(binding: Binding, Type: ArrayType = Float32Array): number[][] {
    const { bytes } = binding.buffer as unknown as FakeBuffer;
    const values = Array.from(new Type(bytes.buffer, 0, bytes.byteLength / Type.BYTES_PER_ELEMENT));
    const width = binding.stride / Type.BYTES_PER_ELEMENT;
    return Array.from({ length: values.length / width }, (_, i) =>
        values.slice(i * width, (i + 1) * width),
    );
}

/** `[x, y, z]` per slot of an `f64` column. */
const xyz = (binding: Binding) => rows(binding).map((r) => [r[0] + r[3], r[1] + r[4], r[2] + r[5]]);

function draw(batch: PointBatch) {
    batch.flush();
    const layer = batch.render({});
    const { attributes } = layer.props.data as unknown as { attributes: Record<string, Binding> };
    return { layer, attributes };
}

const pickingIndexOf = (layer: Layer) =>
    (layer.props as unknown as { pickingIndex: PickingIndex }).pickingIndex;

function point(id: string, x = 0, style: Partial<PointPrimitive["style"]> = {}): PointPrimitive {
    return {
        id,
        geometry: { type: "point", positions: [x, 20, 30] },
        style: {
            type: "point",
            fillColor: [255, 0, 0, 255],
            strokeColor: [0, 0, 255, 128],
            strokeWidth: 2,
            radius: 6,
            ...style,
        },
    };
}

function update(
    batch: PointBatch,
    prev: PointPrimitive,
    partial: PartialPrimitiveOf<PointPrimitive>,
) {
    const patched = { ...prev, ...partial, style: { ...prev.style, ...partial.style } };
    return batch.update(partial, patched as PointPrimitive);
}

describe("PointBatch", () => {
    it("writes the position, style, outline, filter and picking color into the point's slot", () => {
        const batch = new PointBatch({ device });
        batch.add({ ...point("a", 10), outline: true, filter: [0.5] });

        const { attributes } = draw(batch);

        expect(xyz(attributes.instancePositions)[0]).toEqual([10, 20, 30]);
        expect(rows(attributes.instanceFillColors, Uint8Array)[0]).toEqual([255, 0, 0, 255]);
        expect(rows(attributes.instanceLineColors, Uint8Array)[0]).toEqual([0, 0, 255, 128]);
        expect(rows(attributes.instanceLineWidths)[0]).toEqual([2]);
        expect(rows(attributes.instanceRadius)[0]).toEqual([6]);
        expect(rows(attributes.outlines)[0]).toEqual([1]);
        expect(rows(attributes.filterValues)[0]).toEqual([0.5]);
        // Handle 0, encoded as handle + 1.
        expect(rows(attributes.instancePickingColors, Uint8Array)[0]).toEqual([1, 0, 0, 0]);
    });

    it("writes an unfiltered value when the primitive has no filter", () => {
        const batch = new PointBatch({ device });
        batch.add(point("a"));

        expect(rows(draw(batch).attributes.filterValues)[0]).toEqual([1]);
    });

    it("refuses a duplicate id and leaves the original in place", () => {
        const batch = new PointBatch({ device });
        batch.add(point("a"));

        expect(batch.add(point("a", 0, { radius: 99 }))).toBe(false);

        const { layer, attributes } = draw(batch);
        expect(layer.props.numInstances).toBe(1);
        expect(rows(attributes.instanceRadius)[0]).toEqual([6]);
    });

    it("patches only the columns an update names", () => {
        const batch = new PointBatch({ device });
        const a = point("a");
        batch.add(a);
        const { attributes } = draw(batch);
        const writes = () =>
            Object.fromEntries(
                Object.entries(attributes).map(([name, b]) => [
                    name,
                    (b.buffer as unknown as FakeBuffer).writes,
                ]),
            );
        const before = writes();

        update(batch, a, { id: "a", style: { type: "point", fillColor: [0, 255, 0, 255] } });
        expect(batch.flush()).toBe(true);

        const after = writes();
        const changed = Object.keys(after).filter((name) => after[name] !== before[name]);
        expect(changed).toEqual(["instanceFillColors"]);
        expect(rows(attributes.instanceFillColors, Uint8Array)[0]).toEqual([0, 255, 0, 255]);
    });

    it("refuses to update or remove an unknown id", () => {
        const batch = new PointBatch({ device });

        expect(update(batch, point("a"), { id: "a", outline: true })).toBe(false);
        expect(batch.remove("a")).toBe(false);
    });

    it("moves the last point into a removed point's slot", () => {
        const batch = new PointBatch({ device });
        const [a, b, c] = [point("a", 1), point("b", 2), point("c", 3)];
        for (const p of [a, b, c]) batch.add(p);

        expect(batch.remove("a")).toBe(true);
        update(batch, c, { id: "c", style: { type: "point", radius: 12 } });

        const { layer, attributes } = draw(batch);
        expect(layer.props.numInstances).toBe(2);
        expect(xyz(attributes.instancePositions).slice(0, 2)).toEqual([
            [3, 20, 30],
            [2, 20, 30],
        ]);
        expect(rows(attributes.instanceRadius)[0]).toEqual([12]);
        expect(rows(attributes.instancePickingColors, Uint8Array)[0]).toEqual([3, 0, 0, 0]);
    });

    it("re-adds a removed id with a recycled picking handle", () => {
        const batch = new PointBatch({ device });
        batch.add(point("a"));
        batch.add(point("b"));
        batch.remove("a");

        expect(batch.add(point("c"))).toBe(true);

        const { layer, attributes } = draw(batch);
        expect(rows(attributes.instancePickingColors, Uint8Array)[1]).toEqual([1, 0, 0, 0]);
        expect(pickingIndexOf(layer).resolve(0)).toBe("c");
    });

    it("reports a flush only when something was written", () => {
        const batch = new PointBatch({ device });
        expect(batch.flush()).toBe(false);

        batch.add(point("a"));
        expect(batch.flush()).toBe(true);
        expect(batch.flush()).toBe(false);
    });

    it("renders one instance per point, bound to the table's columns, as the table grows", () => {
        const batch = new PointBatch({ device, capacity: 2 });
        for (let i = 0; i < 3; i++) batch.add(point(`${i}`, i));

        const { layer, attributes } = draw(batch);

        expect(layer.props.numInstances).toBe(3);
        expect(attributes.instancePositions.stride).toBe(6 * 4);
        expect(
            xyz(attributes.instancePositions)
                .slice(0, 3)
                .map(([x]) => x),
        ).toEqual([0, 1, 2]);
        expect(draw(batch).attributes.instancePositions).toBe(attributes.instancePositions);
    });

    it("blanks a masked point's picking color and restores it on unmask", () => {
        const batch = new PointBatch({ device });
        batch.add(point("a"));
        batch.add(point("b"));
        const { layer, attributes } = draw(batch);
        const index = pickingIndexOf(layer);

        index.mask(1);
        expect(rows(attributes.instancePickingColors, Uint8Array)[1]).toEqual([0, 0, 0, 0]);

        index.unmask();
        expect(rows(attributes.instancePickingColors, Uint8Array)[1]).toEqual([2, 0, 0, 0]);
    });

    it("patches a stroke width of 0", () => {
        const batch = new PointBatch({ device });
        const a = point("a");
        batch.add(a);

        update(batch, a, { id: "a", style: { type: "point", strokeWidth: 0 } });

        expect(rows(draw(batch).attributes.instanceLineWidths)[0]).toEqual([0]);
    });
});
