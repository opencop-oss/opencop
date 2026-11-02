// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { afterEach, describe, expect, it, vi } from "vitest";
import { DataFilterExtension } from "@deck.gl/extensions";
import type { Device } from "@luma.gl/core";
import { TextCollisionExtension } from "@opencop/layers";
import type { Binding } from "../../gpu/Column";
import type { PartialPrimitiveOf } from "../../primitive";
import type { TextRenderProps } from "../../SceneProps";
import { FontAtlas } from "./FontAtlas";
import { TextBatch, type TextPrimitive } from "./TextBatch";

type FakeBuffer = { bytes: Uint8Array<ArrayBuffer> };

const texture = { copyExternalImage: vi.fn(), generateMipmapsWebGL: vi.fn(), destroy() {} };

/** A device whose buffers keep what is written to them; glyphs rasterize on a real canvas. */
const device = {
    type: "webgl",
    limits: { maxTextureDimension2D: 4096 },
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

function draw(batch: TextBatch, props: TextRenderProps = {}) {
    batch.flush();
    const layer = batch.render({ text: props });
    const { attributes } = layer.props.data as unknown as { attributes: Record<string, Binding> };
    return { layer, attributes };
}

function text(id: string, value: string, filter?: number[]): TextPrimitive {
    return {
        id,
        geometry: { type: "point", positions: [1, 2, 3] },
        style: { type: "text", text: value, size: 12, color: [255, 255, 0, 255] },
        filter,
    };
}

function update(batch: TextBatch, prev: TextPrimitive, partial: PartialPrimitiveOf<TextPrimitive>) {
    const patched = { ...prev, ...partial, style: { ...prev.style, ...partial.style } };
    batch.update(partial, patched as TextPrimitive);
    return patched as TextPrimitive;
}

afterEach(() => vi.restoreAllMocks());

describe("TextBatch", () => {
    it("gives every character of the text a slot, counting code points", () => {
        const batch = new TextBatch({ device });
        batch.add(text("a", "ab"));
        batch.add(text("b", "a😀"));

        const { layer, attributes } = draw(batch);

        expect(layer.props.numInstances).toBe(4);
        expect(xyz(attributes.instancePositions).slice(0, 4)).toEqual(Array(4).fill([1, 2, 3]));
        expect(rows(attributes.instanceSizes).slice(0, 4)).toEqual(Array(4).fill([12]));
        expect(rows(attributes.instanceColors, Uint8Array)[3]).toEqual([255, 255, 0, 255]);
    });

    it("refuses empty text and a duplicate id", () => {
        const batch = new TextBatch({ device });

        expect(batch.add(text("a", ""))).toBe(false);
        expect(batch.add(text("a", "x"))).toBe(true);
        expect(batch.add(text("a", "y"))).toBe(false);
    });

    it("writes a two-channel filter, each missing channel unfiltered", () => {
        const batch = new TextBatch({ device });
        batch.add(text("none", "a"));
        batch.add(text("one", "a", [0.25]));
        batch.add(text("both", "a", [0.25, 0.75]));

        const { attributes } = draw(batch);

        expect(attributes.filterValues.stride).toBe(2 * 4);
        expect(rows(attributes.filterValues).slice(0, 3)).toEqual([
            [1, 1],
            [0.25, 1],
            [0.25, 0.75],
        ]);
    });

    it("filters on both channels only while given their ranges", () => {
        const batch = new TextBatch({ device });
        batch.add(text("a", "a"));

        const unfiltered = draw(batch).layer;
        const filtered = draw(batch, {
            filterRange: [
                [0, 1],
                [0.5, 1],
            ],
        }).layer;

        expect(unfiltered.props.extensions.some((e) => e instanceof DataFilterExtension)).toBe(
            false,
        );
        const filter = filtered.props.extensions.find((e) => e instanceof DataFilterExtension);
        expect(filter?.opts).toMatchObject({ filterSize: 2 });
        expect(filtered.props).toMatchObject({
            filterRange: [
                [0, 1],
                [0.5, 1],
            ],
        });
        expect(filtered.props.extensions.at(-1)).toBeInstanceOf(TextCollisionExtension);
    });

    it("marks every glyph of text that opts out of collision, and only that text", () => {
        const batch = new TextBatch({ device });
        batch.add(text("label", "ab"));
        batch.add({
            ...text("content", "cd"),
            style: { ...text("content", "cd").style, collides: false },
        });

        const { attributes } = draw(batch);
        // Four slots: "ab" collides as a label does, "cd" is opted out.
        expect(rows(attributes.instanceNoCollide, Uint8Array).flat().slice(0, 4)).toEqual([
            0, 0, 1, 1,
        ]);
    });

    it("clears the collision opt-out when a patch turns it back on", () => {
        const batch = new TextBatch({ device });
        const content = {
            ...text("content", "ab"),
            style: { ...text("content", "ab").style, collides: false },
        };
        batch.add(content);
        update(batch, content, { id: "content", style: { type: "text", collides: true } });

        const { attributes } = draw(batch);
        expect(rows(attributes.instanceNoCollide, Uint8Array).flat().slice(0, 2)).toEqual([0, 0]);
    });

    it("writes each glyph's atlas frame, laid out left to right, once flushed", () => {
        const batch = new TextBatch({ device });
        batch.add(text("a", "ab"));
        texture.copyExternalImage.mockClear();

        expect(batch.flush()).toBe(true);
        expect(texture.copyExternalImage).toHaveBeenCalled();
        expect(batch.flush()).toBe(false);

        const [a, b] = rows(draw(batch).attributes.instanceIconDefs);
        expect(a[4]).toBeGreaterThan(0);
        expect(a[6]).toBe(1);
        expect(b[0]).toBeGreaterThan(a[0]);
        expect([b[2], b[3]]).not.toEqual([a[2], a[3]]);
    });

    it("patches same-length text in place with the new glyphs' frames", () => {
        const batch = new TextBatch({ device });
        const a = text("a", "ab");
        batch.add(a);
        batch.add(text("b", "cd"));
        const before = rows(draw(batch).attributes.instanceIconDefs);

        update(batch, a, { id: "a", style: { type: "text", text: "dc" } });

        const after = rows(draw(batch).attributes.instanceIconDefs);
        expect(after[0].slice(2, 6)).toEqual(before[3].slice(2, 6));
        expect(after[1].slice(2, 6)).toEqual(before[2].slice(2, 6));
    });

    it("moves text whose length changes and hides the glyph slots it left", () => {
        const batch = new TextBatch({ device });
        const a = text("a", "ab");
        batch.add(a);
        batch.add(text("b", "cd"));

        update(batch, a, { id: "a", style: { type: "text", text: "abc" } });

        const { layer, attributes } = draw(batch);
        expect(layer.props.numInstances).toBe(8);
        expect(rows(attributes.instanceSizes).slice(0, 8).flat()).toEqual([
            0, 0, 12, 12, 12, 12, 12, 0,
        ]);
        expect(rows(attributes.instanceClipRect).slice(0, 2)).toEqual([
            [0, 0, -1, -1],
            [0, 0, -1, -1],
        ]);
    });

    it("releases glyphs text no longer uses and keeps those other text still uses", () => {
        const acquire = vi.spyOn(FontAtlas.prototype, "acquire");
        const release = vi.spyOn(FontAtlas.prototype, "release");
        const held = () => {
            const refs: Record<string, number> = {};
            for (const [char] of acquire.mock.calls) refs[char] = (refs[char] ?? 0) + 1;
            for (const [char] of release.mock.calls) refs[char] = (refs[char] ?? 0) - 1;
            return Object.fromEntries(Object.entries(refs).filter(([, n]) => n !== 0));
        };
        const batch = new TextBatch({ device });
        const a = text("a", "ab");
        batch.add(a);
        batch.add(text("b", "bc"));

        const cd = update(batch, a, { id: "a", style: { type: "text", text: "cd" } });
        expect(held()).toEqual({ b: 1, c: 2, d: 1 });

        batch.remove("b");
        expect(held()).toEqual({ c: 1, d: 1 });

        update(batch, cd, { id: "a", style: { type: "text", text: "xyy" } });
        expect(held()).toEqual({ x: 1, y: 2 });

        batch.remove("a");
        expect(held()).toEqual({});
    });

    it("patches an angle of 0", () => {
        const batch = new TextBatch({ device });
        const a = text("a", "ab");
        a.style.angle = 90;
        batch.add(a);

        update(batch, a, { id: "a", style: { type: "text", angle: 0 } });

        expect(rows(draw(batch).attributes.instanceAngles).slice(0, 2)).toEqual([[0], [0]]);
    });
});
