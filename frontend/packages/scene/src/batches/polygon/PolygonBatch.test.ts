// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import type { Device } from "@luma.gl/core";
import type { Binding } from "../../gpu/Column";
import type { PartialPrimitiveOf } from "../../primitive";
import { PolygonBatch, type PolygonPrimitive } from "./PolygonBatch";

type FakeBuffer = { bytes: Uint8Array<ArrayBuffer> };

/** A device whose buffers keep what is written to them. */
const device = {
    type: "webgl",
    limits: { maxTextureDimension2D: 2048 },
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
    createTexture: () => ({ copyExternalImage() {}, destroy() {} }),
} as unknown as Device;

type ArrayType = typeof Float32Array | typeof Uint8Array | typeof Uint32Array;

/** The uploaded contents of a binding's buffer, one row per slot. */
function rows(binding: Binding, Type: ArrayType = Float32Array): number[][] {
    const { bytes } = binding.buffer as unknown as FakeBuffer;
    const values = Array.from(new Type(bytes.buffer, 0, bytes.byteLength / Type.BYTES_PER_ELEMENT));
    const width = binding.stride / Type.BYTES_PER_ELEMENT;
    return Array.from({ length: values.length / width }, (_, i) =>
        values.slice(i * width, (i + 1) * width),
    );
}

type Triangle = { slots: number[]; points: number[][]; area: number };

/** The triangles the layer draws: its first `vertexCount` indices, resolved to positions. */
function draw(batch: PolygonBatch) {
    batch.flush();
    const layer = batch.render({});
    const { attributes } = layer.props.data as unknown as { attributes: Record<string, Binding> };
    const positions = rows(attributes.vertexPositions).map((r) => [r[0] + r[3], r[1] + r[4]]);
    const triangles: Triangle[] = rows(attributes.indices, Uint32Array)
        .slice(0, (layer.props as unknown as { vertexCount: number }).vertexCount / 3)
        .map((slots) => {
            const points = slots.map((slot) => positions[slot]);
            const [[ax, ay], [bx, by], [cx, cy]] = points;
            const area = Math.abs((bx - ax) * (cy - ay) - (cx - ax) * (by - ay)) / 2;
            return { slots, points, area };
        });
    return { layer, attributes, positions, triangles };
}

const areaOf = (triangles: Triangle[]) => triangles.reduce((sum, t) => sum + t.area, 0);
const drawn = (triangles: Triangle[]) => triangles.filter((t) => t.area > 0);

/** A closed ring through each `[x, y]`. */
function polygon(id: string, ring: number[][]): PolygonPrimitive {
    return {
        id,
        geometry: { type: "polygon", positions: [...ring, ring[0]].flatMap(([x, y]) => [x, y, 0]) },
        style: { type: "polygon", fillColor: [255, 0, 0, 128] },
    };
}

const square = (id: string, x = 0, size = 1) =>
    polygon(id, [
        [x, 0],
        [x + size, 0],
        [x + size, size],
        [x, size],
    ]);

function update(
    batch: PolygonBatch,
    prev: PolygonPrimitive,
    partial: PartialPrimitiveOf<PolygonPrimitive>,
) {
    const patched = {
        ...prev,
        ...partial,
        geometry: { ...prev.geometry, ...partial.geometry },
        style: { ...prev.style, ...partial.style },
    };
    return batch.update(partial, patched as PolygonPrimitive);
}

describe("PolygonBatch", () => {
    it("triangulates each polygon over its own absolute vertex slots", () => {
        const batch = new PolygonBatch({ device });
        batch.add(square("a"));
        batch.add(square("b", 10));

        const { triangles } = draw(batch);

        const a = drawn(triangles).filter((t) => t.points[0][0] < 10);
        const b = drawn(triangles).filter((t) => t.points[0][0] >= 10);
        expect(areaOf(a)).toBe(1);
        expect(areaOf(b)).toBe(1);
        expect(a.flatMap((t) => t.slots).every((slot) => slot < 5)).toBe(true);
        // `b`'s five vertices sit in the block after `a`'s eight.
        expect(b.flatMap((t) => t.slots).every((slot) => slot >= 8 && slot < 13)).toBe(true);
    });

    it("fills a concave ring without covering its notch", () => {
        const batch = new PolygonBatch({ device });
        batch.add(
            polygon("l", [
                [0, 0],
                [2, 0],
                [2, 1],
                [1, 1],
                [1, 2],
                [0, 2],
            ]),
        );

        expect(areaOf(draw(batch).triangles)).toBe(3);
    });

    it("leaves the hole of a ring bridged to an inner ring uncovered", () => {
        const batch = new PolygonBatch({ device });
        const outer = [
            [0, 0],
            [4, 0],
            [4, 4],
            [0, 4],
            [0, 0],
        ];
        const inner = [
            [1, 1],
            [1, 3],
            [3, 3],
            [3, 1],
            [1, 1],
        ];
        batch.add(polygon("keyhole", [...outer, ...inner]));

        expect(areaOf(draw(batch).triangles)).toBe(16 - 4);
    });

    it("keeps every index valid once a removed polygon lets the tables shrink", () => {
        const batch = new PolygonBatch({ device, capacity: 8 });
        const ring = Array.from({ length: 40 }, (_, i) => {
            const t = (2 * Math.PI * i) / 40;
            return [100 + Math.cos(t), Math.sin(t)];
        });
        batch.add(square("a"));
        batch.add(polygon("big", ring));
        batch.add(square("c", 10));
        const grown = draw(batch).attributes;

        batch.remove("big");

        const { attributes, positions, triangles } = draw(batch);
        expect(attributes.indices.buffer).not.toBe(grown.indices.buffer);
        expect(rows(attributes.indices, Uint32Array).length).toBeLessThan(
            rows(grown.indices, Uint32Array).length,
        );
        expect(triangles.flatMap((t) => t.slots).every((slot) => slot < positions.length)).toBe(
            true,
        );
        expect(drawn(triangles).every((t) => t.points.every(([x]) => x < 100))).toBe(true);
        expect(areaOf(triangles)).toBe(2);
    });

    it("re-tessellates an update with the same vertex count in place", () => {
        const batch = new PolygonBatch({ device });
        const a = square("a");
        batch.add(a);
        const before = draw(batch).triangles;

        update(batch, a, { id: "a", geometry: square("a", 5, 2).geometry });

        const { triangles } = draw(batch);
        expect(triangles.map((t) => t.slots)).toEqual(before.map((t) => t.slots));
        expect(areaOf(triangles)).toBe(4);
        expect(drawn(triangles).every((t) => t.points.every(([x]) => x >= 5))).toBe(true);
    });

    it("moves a polygon that outgrows its block, leaving no triangles behind", () => {
        const batch = new PolygonBatch({ device });
        const a = square("a");
        batch.add(a);
        batch.add(square("b", 10));
        const strip = polygon("a", [
            [0, 0],
            [1, 0],
            [2, 0],
            [3, 0],
            [3, 1],
            [2, 1],
            [1, 1],
            [0, 1],
            [0, 0.5],
        ]);

        update(batch, a, { id: "a", geometry: strip.geometry });

        const { triangles } = draw(batch);
        expect(areaOf(triangles)).toBe(3 + 1);
        const moved = drawn(triangles).filter((t) => t.points[0][0] < 10);
        expect(moved.flatMap((t) => t.slots).every((slot) => slot >= 16)).toBe(true);
    });

    it("draws nothing of a removed polygon", () => {
        const batch = new PolygonBatch({ device });
        batch.add(square("a"));
        batch.add(square("b", 10));

        expect(batch.remove("a")).toBe(true);
        expect(batch.remove("a")).toBe(false);

        const { triangles } = draw(batch);
        expect(areaOf(triangles)).toBe(1);
        expect(drawn(triangles).every((t) => t.points.every(([x]) => x >= 10))).toBe(true);
    });

    it("refuses a ring of fewer than four positions, a ragged ring, or a duplicate id", () => {
        const batch = new PolygonBatch({ device });
        const open = square("a");
        open.geometry.positions = open.geometry.positions.slice(0, 9);

        expect(batch.add(open)).toBe(false);
        expect(
            batch.add({ ...square("a"), geometry: { type: "polygon", positions: [0, 0, 0, 1] } }),
        ).toBe(false);
        expect(batch.add(square("a"))).toBe(true);
        expect(batch.add(square("a", 5))).toBe(false);
    });

    it("writes the fill color, outline, filter and picking color to every vertex", () => {
        const batch = new PolygonBatch({ device });
        batch.add({ ...square("a"), outline: true, filter: [0.25] });
        batch.add(square("b", 10));

        const { attributes } = draw(batch);

        expect(new Set(rows(attributes.fillColors, Uint8Array).slice(0, 5).map(String))).toEqual(
            new Set(["255,0,0,128"]),
        );
        expect(rows(attributes.outlines).slice(0, 5).flat()).toEqual([1, 1, 1, 1, 1]);
        expect(rows(attributes.filterValues).slice(0, 5).flat()).toEqual([
            0.25, 0.25, 0.25, 0.25, 0.25,
        ]);
        expect(rows(attributes.filterValues)[8]).toEqual([1]);
        expect(rows(attributes.pickingColors, Uint8Array)[8]).toEqual([2, 0, 0, 0]);
    });

    it("patches only the fill color when the style alone changes", () => {
        const batch = new PolygonBatch({ device });
        const a = square("a");
        batch.add(a);
        const before = draw(batch).triangles;

        update(batch, a, { id: "a", style: { type: "polygon", fillColor: [0, 0, 255, 255] } });

        const { attributes, triangles } = draw(batch);
        expect(rows(attributes.fillColors, Uint8Array)[4]).toEqual([0, 0, 255, 255]);
        expect(triangles).toEqual(before);
    });

    it("leaves no stale triangle when an update drops a vertex in place", () => {
        const batch = new PolygonBatch({ device });
        const pentagon = polygon("a", [
            [0, 0],
            [2, 0],
            [2, 2],
            [1, 3],
            [0, 2],
        ]);
        batch.add(pentagon);

        update(batch, pentagon, { id: "a", geometry: square("a", 0, 2).geometry });

        const { triangles } = draw(batch);
        expect(triangles.filter((t) => t.slots.some((slot) => slot !== 0))).toHaveLength(2);
        expect(areaOf(triangles)).toBe(4);
    });
});
