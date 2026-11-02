// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { Device } from "@luma.gl/core";
import type { Binding } from "../../gpu/Column";
import type { PartialPrimitiveOf } from "../../primitive";
import type { DrawableImage } from "./IconAtlas";
import { IconBatch, type IconPrimitive } from "./IconBatch";

type FakeBuffer = { bytes: Uint8Array<ArrayBuffer> };

const texture = { copyExternalImage: vi.fn(), generateMipmapsWebGL: vi.fn(), destroy() {} };

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
    createTexture: () => texture,
} as unknown as Device;

/** happy-dom has no 2D canvas; the atlas only needs one to draw into. */
class FakeCanvas {
    getContext() {
        return { canvas: this, clearRect() {}, drawImage() {} };
    }
}

beforeEach(() => vi.stubGlobal("OffscreenCanvas", FakeCanvas));
afterEach(() => vi.unstubAllGlobals());

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

function draw(batch: IconBatch, sizeScale?: number) {
    batch.flush();
    const layer = batch.render({ icon: { sizeScale } });
    const { data, iconMapping } = layer.props as unknown as {
        data: { attributes: Record<string, Binding> };
        iconMapping: Record<string, unknown>;
    };
    return { layer, attributes: data.attributes, mapping: iconMapping };
}

const settle = () => new Promise((resolve) => setTimeout(resolve));
const loader = () => vi.fn(() => Promise.resolve({ width: 32, height: 32 } as DrawableImage));

function icon(
    id: string,
    source: IconPrimitive["style"]["source"] = loader(),
    style: Partial<IconPrimitive["style"]> = {},
): IconPrimitive {
    return {
        id,
        geometry: { type: "point", positions: [1, 2, 3] },
        style: {
            type: "icon",
            id: "pin",
            source,
            width: 32,
            height: 32,
            billboard: false,
            ...style,
        },
    };
}

function update(batch: IconBatch, prev: IconPrimitive, partial: PartialPrimitiveOf<IconPrimitive>) {
    const patched = {
        ...prev,
        ...partial,
        geometry: { ...prev.geometry, ...partial.geometry },
        style: { ...prev.style, ...partial.style },
    };
    return batch.update(partial, patched as IconPrimitive);
}

/** `[dx, dy, x, y, width, height, mask]` of a 32px icon centered in the atlas's first cell. */
const FIRST_CELL_DEF = [0, 0, 48, 48, 32, 32, 0];

describe("IconBatch", () => {
    it("writes the style's values, or their defaults, and the picking color into the slot", () => {
        const batch = new IconBatch({ device });
        batch.add(icon("plain"));
        batch.add(
            icon("styled", loader(), {
                size: 2,
                angle: 45,
                color: [255, 0, 0, 255],
                offset: [3, 4],
                billboard: true,
            }),
        );

        const { attributes } = draw(batch);

        expect(rows(attributes.instanceSizes).slice(0, 2)).toEqual([[1], [2]]);
        expect(rows(attributes.instanceAngles).slice(0, 2)).toEqual([[0], [45]]);
        expect(rows(attributes.instanceColors, Uint8Array).slice(0, 2)).toEqual([
            [255, 255, 255, 255],
            [255, 0, 0, 255],
        ]);
        expect(rows(attributes.instancePixelOffset).slice(0, 2)).toEqual([
            [0, 0],
            [3, 4],
        ]);
        expect(rows(attributes.instanceBillboards, Uint8Array).slice(0, 2)).toEqual([[0], [1]]);
        expect(rows(attributes.instancePickingColors, Uint8Array).slice(0, 2)).toEqual([
            [1, 0, 0, 0],
            [2, 0, 0, 0],
        ]);
        // Not loaded yet: an empty frame.
        expect(rows(attributes.instanceIconDefs)[0]).toEqual([0, 0, 0, 0, 0, 0, 0]);
    });

    it("loads a shared icon once and writes its placement into every user on flush", async () => {
        const batch = new IconBatch({ device });
        const load = loader();
        batch.add(icon("a", load));
        batch.add(icon("b", load));
        batch.flush();
        texture.copyExternalImage.mockClear();

        await settle();
        expect(batch.flush()).toBe(true);
        expect(texture.copyExternalImage).toHaveBeenCalled();

        const { layer, attributes } = draw(batch, 2);
        expect(load).toHaveBeenCalledTimes(1);
        expect(rows(attributes.instanceIconDefs).slice(0, 2)).toEqual([
            FIRST_CELL_DEF,
            FIRST_CELL_DEF,
        ]);
        expect(layer.props).toMatchObject({
            iconMapping: { pin: { x: 48, width: 32 } },
            sizeScale: 2,
        });
        expect(layer.props.data).toMatchObject({ length: 2 });
        expect(batch.flush()).toBe(false);
    });

    it("writes a new icon's placement when an update switches icons", async () => {
        const batch = new IconBatch({ device });
        const a = icon("a");
        batch.add(a);
        batch.add(icon("b", loader(), { id: "other" }));
        await settle();

        update(batch, a, { id: "a", style: { type: "icon", id: "other" } });

        const defs = rows(draw(batch).attributes.instanceIconDefs);
        // The second cell's frame.
        expect(defs[0]).toEqual([0, 0, 128 + 48, 48, 32, 32, 0]);
        expect(defs[1]).toEqual(defs[0]);
    });

    it("releases icons on update and remove so a full atlas can reclaim their cells", async () => {
        // Four cells.
        const batch = new IconBatch({ device, atlasSize: 256, cellSize: 128 });
        const icons = ["i0", "i1", "i2", "i3"].map((id, i) => icon(`p${i}`, loader(), { id }));
        for (const p of icons) batch.add(p);
        await settle();

        update(batch, icons[0], { id: "p0", style: { type: "icon", id: "i4", source: loader() } });
        batch.remove("p1");
        batch.add(icon("p5", loader(), { id: "i5" }));
        await settle();

        expect(Object.keys(draw(batch).mapping).sort()).toEqual(["i2", "i3", "i4", "i5"]);
    });

    it("keeps an icon another primitive still uses", async () => {
        const batch = new IconBatch({ device, atlasSize: 256, cellSize: 128 });
        const shared = icon("a", loader(), { id: "shared" });
        batch.add(shared);
        batch.add(icon("b", loader(), { id: "shared" }));
        for (const id of ["i1", "i2", "i3"]) batch.add(icon(id, loader(), { id }));
        await settle();

        batch.remove("a");
        const next = loader();
        batch.add(icon("c", next, { id: "i4" }));
        await settle();

        expect(next).not.toHaveBeenCalled();
        expect(draw(batch).mapping).toHaveProperty("shared");
    });

    it("moves the last icon into a removed icon's slot", () => {
        const batch = new IconBatch({ device });
        batch.add(icon("a", loader(), { size: 1 }));
        batch.add(icon("b", loader(), { size: 2 }));

        batch.remove("a");

        const { layer, attributes } = draw(batch);
        expect(layer.props.data).toMatchObject({ length: 1 });
        expect(rows(attributes.instanceSizes)[0]).toEqual([2]);
    });

    it("patches an angle of 0", () => {
        const batch = new IconBatch({ device });
        const a = icon("a", loader(), { angle: 90 });
        batch.add(a);

        update(batch, a, { id: "a", style: { type: "icon", angle: 0 } });

        expect(rows(draw(batch).attributes.instanceAngles)[0]).toEqual([0]);
    });

    it("writes the same position on update as on add", () => {
        const batch = new IconBatch({ device });
        const a = icon("a");
        batch.add(a);
        const added = xyz(draw(batch).attributes.instancePositions)[0];

        update(batch, a, { id: "a", geometry: a.geometry });

        expect(xyz(draw(batch).attributes.instancePositions)[0]).toEqual(added);
    });
});
