// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import type { Layer } from "@deck.gl/core";
import { PathStyleExtension } from "@deck.gl/extensions";
import type { Device } from "@luma.gl/core";
import type { Binding } from "../../gpu/Column";
import type { PickingIndex } from "../../picking/PickingIndex";
import type { PartialPrimitiveOf } from "../../primitive";
import { PathBatch, SEGMENT, type PathPrimitive } from "./PathBatch";

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

/** `[x, y, z]` per slot of the positions, skipping the head slot the layer reads behind slot 0. */
const xyz = (binding: Binding) =>
    rows(binding)
        .slice(1)
        .map((r) => [r[0] + r[3], r[1] + r[4], r[2] + r[5]]);

const segments = (binding: Binding, start: number, end: number) =>
    rows(binding, Uint8Array)
        .slice(start, end)
        .map(([type]) => type);

function draw(batch: PathBatch) {
    batch.flush();
    const layer = batch.render({});
    const { attributes } = layer.props.data as unknown as { attributes: Record<string, Binding> };
    return { layer, attributes };
}

const pickingIndexOf = (layer: Layer) =>
    (layer.props as unknown as { pickingIndex: PickingIndex }).pickingIndex;

/** A path through `[x, 0, 0]` for each `x`. */
function path(
    id: string,
    xs: number[],
    style: Partial<PathPrimitive["style"]> = {},
): PathPrimitive {
    return {
        id,
        geometry: { type: "path", positions: xs.flatMap((x) => [x, 0, 0]) },
        style: { type: "path", strokeColor: [255, 0, 0, 255], strokeWidth: 3, ...style },
    };
}

const ring = (id: string): PathPrimitive => ({
    ...path(id, []),
    geometry: { type: "path", positions: [0, 0, 0, 1, 0, 0, 1, 1, 0, 0, 1, 0, 0, 0, 0] },
});

function update(batch: PathBatch, prev: PathPrimitive, partial: PartialPrimitiveOf<PathPrimitive>) {
    const patched = {
        ...prev,
        ...partial,
        geometry: { ...prev.geometry, ...partial.geometry },
        style: { ...prev.style, ...partial.style },
    };
    return batch.update(partial, patched as PathPrimitive);
}

const { START_CAP, END_CAP, INVALID } = SEGMENT;

describe("PathBatch", () => {
    it("lays an open path out one slot per vertex, capped at both ends and invalid last", () => {
        const batch = new PathBatch({ device });
        batch.add(path("a", [1, 2, 3]));
        batch.add(path("b", [4, 5]));

        const { attributes } = draw(batch);

        expect(xyz(attributes.vertexPositions).slice(0, 3)).toEqual([
            [1, 0, 0],
            [2, 0, 0],
            [3, 0, 0],
        ]);
        expect(segments(attributes.instanceTypes, 0, 3)).toEqual([START_CAP, END_CAP, INVALID]);
        // `b` takes the next block: both caps on its one segment.
        expect(segments(attributes.instanceTypes, 4, 6)).toEqual([START_CAP + END_CAP, INVALID]);
    });

    it("wraps a closed path's first two edges into two extra slots", () => {
        const batch = new PathBatch({ device });
        batch.add(ring("a"));

        const { attributes } = draw(batch);

        expect(xyz(attributes.vertexPositions).slice(0, 7)).toEqual([
            [0, 0, 0],
            [1, 0, 0],
            [1, 1, 0],
            [0, 1, 0],
            [0, 0, 0],
            [1, 0, 0],
            [1, 1, 0],
        ]);
        expect(segments(attributes.instanceTypes, 0, 7)).toEqual([
            INVALID,
            0,
            0,
            0,
            0,
            INVALID,
            INVALID,
        ]);
    });

    it("fills stroke width, color, dash and picking color across every slot of the path", () => {
        const batch = new PathBatch({ device });
        batch.add(path("solid", [0, 1]));
        batch.add(path("dashed", [0, 1], { strokeDash: [4, 2], strokeColor: [0, 0, 255, 255] }));

        const { attributes } = draw(batch);

        expect(rows(attributes.instanceStrokeWidths).slice(0, 4)).toEqual([[3], [3], [3], [3]]);
        expect(rows(attributes.instanceDashArrays).slice(0, 4)).toEqual([
            [0, 0],
            [0, 0],
            [4, 2],
            [4, 2],
        ]);
        expect(rows(attributes.instanceColors, Uint8Array)[3]).toEqual([0, 0, 255, 255]);
        expect(rows(attributes.instancePickingColors, Uint8Array).slice(0, 4)).toEqual([
            [1, 0, 0, 0],
            [1, 0, 0, 0],
            [2, 0, 0, 0],
            [2, 0, 0, 0],
        ]);
    });

    it("refuses a path with fewer than two vertices, a ragged position list, or a duplicate id", () => {
        const batch = new PathBatch({ device });

        expect(batch.add(path("a", [1]))).toBe(false);
        expect(
            batch.add({
                ...path("a", [1, 2]),
                geometry: { type: "path", positions: [1, 2, 3, 4] },
            }),
        ).toBe(false);
        expect(batch.add(path("a", [1, 2]))).toBe(true);
        expect(batch.add(path("a", [1, 2, 3]))).toBe(false);
        expect(draw(batch).layer.props.numInstances).toBe(2);
    });

    it("patches an update with the same vertex count in place", () => {
        const batch = new PathBatch({ device });
        const a = path("a", [1, 2, 3]);
        batch.add(a);
        batch.add(path("b", [4, 5]));

        update(batch, a, {
            id: "a",
            geometry: { type: "path", positions: [7, 0, 0, 8, 0, 0, 9, 0, 0] },
            style: { type: "path", strokeColor: [0, 255, 0, 255] },
        });

        const { layer, attributes } = draw(batch);
        expect(layer.props.numInstances).toBe(6);
        expect(
            xyz(attributes.vertexPositions)
                .slice(0, 3)
                .map(([x]) => x),
        ).toEqual([7, 8, 9]);
        expect(segments(attributes.instanceTypes, 0, 3)).toEqual([START_CAP, END_CAP, INVALID]);
        expect(rows(attributes.instanceColors, Uint8Array)[2]).toEqual([0, 255, 0, 255]);
        expect(rows(attributes.instanceStrokeWidths)[2]).toEqual([3]);
    });

    it("clears a dash when an update sets it undefined", () => {
        const batch = new PathBatch({ device });
        const a = path("a", [0, 1], { strokeDash: [4, 2] });
        batch.add(a);

        update(batch, a, { id: "a", style: { type: "path", strokeDash: undefined } });

        expect(rows(draw(batch).attributes.instanceDashArrays).slice(0, 2)).toEqual([
            [0, 0],
            [0, 0],
        ]);
    });

    it("moves a path whose vertex count changes and tombstones the slots it left", () => {
        const batch = new PathBatch({ device });
        const a = path("a", [1, 2]);
        batch.add(a);
        batch.add(path("b", [3, 4]));

        update(batch, a, {
            id: "a",
            geometry: { type: "path", positions: [5, 0, 0, 6, 0, 0, 7, 0, 0] },
        });

        const { layer, attributes } = draw(batch);
        expect(segments(attributes.instanceTypes, 0, 8)).toEqual([
            INVALID,
            INVALID,
            START_CAP + END_CAP,
            INVALID,
            START_CAP,
            END_CAP,
            INVALID,
            INVALID,
        ]);
        expect(
            xyz(attributes.vertexPositions)
                .slice(4, 7)
                .map(([x]) => x),
        ).toEqual([5, 6, 7]);
        expect(rows(attributes.instanceStrokeWidths)[4]).toEqual([3]);
        expect(layer.props.numInstances).toBe(8);
    });

    it("tombstones a removed path's segments and drops it from the instance count", () => {
        const batch = new PathBatch({ device });
        batch.add(path("a", [1, 2]));
        batch.add(path("b", [3, 4]));

        expect(batch.remove("b")).toBe(true);
        const { layer, attributes } = draw(batch);
        expect(segments(attributes.instanceTypes, 2, 4)).toEqual([INVALID, INVALID]);
        expect(layer.props.numInstances).toBe(2);

        expect(batch.remove("b")).toBe(false);
        batch.remove("a");
        expect(draw(batch).layer.props.numInstances).toBe(0);
        expect(batch.add(path("a", [1, 2]))).toBe(true);
    });

    it("renders a dashed, pickable path layer bound to the table's columns", () => {
        const batch = new PathBatch({ device, capacity: 8 });
        batch.add(path("a", [1, 2]));

        const { layer, attributes } = draw(batch);

        expect(layer.props.extensions[0]).toBeInstanceOf(PathStyleExtension);
        expect((layer.props.extensions[0] as PathStyleExtension).opts.dash).toBe(true);
        expect(layer.props.pickable).toBe(true);
        // A head slot and three tail slots around the positions, for the layer's sliding window.
        expect((attributes.vertexPositions.buffer as unknown as FakeBuffer).bytes.byteLength).toBe(
            (1 + 8 + 3) * 6 * 4,
        );
        expect(draw(batch).attributes.instanceTypes).toBe(attributes.instanceTypes);
    });

    it("blanks the picking color of a masked path that does not start at slot 0", () => {
        const batch = new PathBatch({ device });
        batch.add(path("a", [1, 2]));
        batch.add(path("b", [3, 4]));
        const { layer, attributes } = draw(batch);

        pickingIndexOf(layer).mask(1);

        expect(rows(attributes.instancePickingColors, Uint8Array).slice(2, 4)).toEqual([
            [0, 0, 0, 0],
            [0, 0, 0, 0],
        ]);
    });

    it("rewrites the segment types when an update closes a path of the same slot count", () => {
        const batch = new PathBatch({ device });
        const a = path("a", [1, 2, 3, 4, 5, 6]);
        batch.add(a);

        // A closed triangle: four positions, six slots.
        const triangle = [0, 0, 0, 1, 0, 0, 1, 1, 0, 0, 0, 0];
        update(batch, a, { id: "a", geometry: { type: "path", positions: triangle } });

        expect(segments(draw(batch).attributes.instanceTypes, 0, 6)).toEqual([
            INVALID,
            0,
            0,
            0,
            INVALID,
            INVALID,
        ]);
    });
});
