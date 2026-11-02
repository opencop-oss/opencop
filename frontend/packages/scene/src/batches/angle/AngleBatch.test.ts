// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import type { Layer } from "@deck.gl/core";
import type { Device } from "@luma.gl/core";
import type { Binding } from "../../gpu/Column";
import type { PickingIndex } from "../../picking/PickingIndex";
import type { PartialPrimitiveOf } from "../../primitive";
import { AngleBatch, type AnglePrimitive } from "./AngleBatch";

type FakeBuffer = { bytes: Uint8Array<ArrayBuffer> };

/** A device whose buffers keep what is written to them. */
const device = {
    createBuffer: ({ byteLength }: { byteLength: number }) => {
        const bytes = new Uint8Array(byteLength);
        return {
            byteLength,
            bytes,
            write: (data: ArrayBufferView, byteOffset = 0) =>
                bytes.set(
                    new Uint8Array(data.buffer, data.byteOffset, data.byteLength),
                    byteOffset,
                ),
            destroy() {},
        };
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

const xyz = (binding: Binding) => rows(binding).map((r) => [r[0] + r[3], r[1] + r[4], r[2] + r[5]]);

function draw(batch: AngleBatch) {
    batch.flush();
    const layer = batch.render({});
    const { attributes } = layer.props.data as unknown as { attributes: Record<string, Binding> };
    return { layer, attributes };
}

const pickingIndexOf = (layer: Layer) =>
    (layer.props as unknown as { pickingIndex: PickingIndex }).pickingIndex;

function angle(id: string, x = 0): AnglePrimitive {
    return {
        id,
        geometry: { type: "point", positions: [x, 2, 0] },
        style: {
            type: "angle",
            strokeColor: [0, 255, 0, 255],
            strokeWidth: 2,
            radius: 50,
            angles: [0, 1.5],
        },
    };
}

function update(
    batch: AngleBatch,
    prev: AnglePrimitive,
    partial: PartialPrimitiveOf<AnglePrimitive>,
) {
    const patched = { ...prev, ...partial, style: { ...prev.style, ...partial.style } };
    return batch.update(partial, patched as AnglePrimitive);
}

describe("AngleBatch", () => {
    it("writes the arc's center, radius, angles, stroke and picking color into its slot", () => {
        const batch = new AngleBatch({ device });
        batch.add(angle("a", 1));

        const { layer, attributes } = draw(batch);

        expect(layer.props.numInstances).toBe(1);
        expect(xyz(attributes.instancePositions)[0]).toEqual([1, 2, 0]);
        expect(rows(attributes.instanceRadius)[0]).toEqual([50]);
        expect(rows(attributes.instanceAngles)[0]).toEqual([0, 1.5]);
        expect(rows(attributes.instanceLineColors, Uint8Array)[0]).toEqual([0, 255, 0, 255]);
        expect(rows(attributes.instanceLineWidths)[0]).toEqual([2]);
        expect(rows(attributes.instancePickingColors, Uint8Array)[0]).toEqual([1, 0, 0, 0]);
    });

    it("patches the angles and radius an update names", () => {
        const batch = new AngleBatch({ device });
        const a = angle("a");
        batch.add(a);

        update(batch, a, { id: "a", style: { type: "angle", angles: [1, 2], radius: 80 } });

        const { attributes } = draw(batch);
        expect(rows(attributes.instanceAngles)[0]).toEqual([1, 2]);
        expect(rows(attributes.instanceRadius)[0]).toEqual([80]);
        expect(rows(attributes.instanceLineWidths)[0]).toEqual([2]);
    });

    it("refuses a duplicate id and moves the last arc into a removed arc's slot", () => {
        const batch = new AngleBatch({ device });
        batch.add(angle("a", 1));
        batch.add(angle("b", 2));

        expect(batch.add(angle("a", 3))).toBe(false);
        expect(batch.remove("a")).toBe(true);

        const { layer, attributes } = draw(batch);
        expect(layer.props.numInstances).toBe(1);
        expect(xyz(attributes.instancePositions)[0]).toEqual([2, 2, 0]);
        expect(rows(attributes.instancePickingColors, Uint8Array)[0]).toEqual([2, 0, 0, 0]);
    });

    it("renders stroked, unfilled arcs with radii in common units", () => {
        const batch = new AngleBatch({ device });
        batch.add(angle("a"));

        const { layer } = draw(batch);

        expect(layer.props).toMatchObject({
            stroked: true,
            filled: false,
            radiusUnits: "common",
            lineWidthUnits: "pixels",
            pickable: true,
        });
    });

    it("writes and patches the arc's filter", () => {
        const batch = new AngleBatch({ device });
        const a: AnglePrimitive = { ...angle("a"), filter: [0.5] };
        batch.add(a);

        const added = draw(batch).attributes;
        expect(rows(added.filterValues)[0]).toEqual([0.5]);

        update(batch, a, { id: "a", filter: [-1] });

        const patched = draw(batch).attributes;
        expect(rows(patched.filterValues)[0]).toEqual([-1]);
    });

    it("blanks a masked arc's picking color and restores it on unmask", () => {
        const batch = new AngleBatch({ device });
        batch.add(angle("a"));
        batch.add(angle("b"));
        const { layer, attributes } = draw(batch);
        const index = pickingIndexOf(layer);

        index.mask(1);
        expect(rows(attributes.instancePickingColors, Uint8Array)[1]).toEqual([0, 0, 0, 0]);

        index.unmask();
        expect(rows(attributes.instancePickingColors, Uint8Array)[1]).toEqual([2, 0, 0, 0]);
    });
});
