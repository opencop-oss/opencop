// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it, vi } from "vitest";
import { FontAtlas } from "./FontAtlas";
import { FontRenderer, type FontMeasurement, type LoadedCharacter } from "./FontRenderer";

/** Every glyph measures 16px square: 12px above the baseline and 4px below. */
class FixedRenderer extends FontRenderer {
    measure(char?: string): FontMeasurement {
        if (char === undefined) return { advance: 0, width: 0, ascent: 12, descent: 4 };
        return { advance: 18, width: 16, ascent: 12, descent: 4 };
    }
    draw(): LoadedCharacter {
        throw new Error("Only SDF atlases draw through the renderer");
    }
}

const fillText = vi.fn();
const createCanvas = () =>
    ({ getContext: () => ({ fillText, clearRect() {} }) }) as unknown as OffscreenCanvas;

/** Four glyphs fit: two per row, two rows, with a 4px buffer around each. */
const atlas = () => new FontAtlas({ size: 64, renderer: FixedRenderer, createCanvas });

const placed = (fonts: FontAtlas) => Object.keys(fonts.placements).sort();

describe("FontAtlas", () => {
    it("draws a glyph once and reports its placement once", () => {
        const fonts = atlas();
        fillText.mockClear();

        fonts.acquire("a");
        fonts.acquire("a");

        expect(fillText).toHaveBeenCalledTimes(1);
        expect(fonts.consumePlacements()).toEqual({ kind: "keys", keys: ["a"] });
        expect(fonts.consumePlacements()).toEqual({ kind: "none" });
        expect(fonts.consumePixels()).toEqual({ kind: "all" });
        expect(fonts.consumePixels()).toEqual({ kind: "none" });
    });

    it("packs glyphs left to right in buffered rows", () => {
        const fonts = atlas();
        for (const char of "abc") fonts.acquire(char);

        expect(fonts.placement("a")).toEqual({
            x: 4,
            y: 4,
            width: 16,
            height: 16,
            advance: 18,
            anchorX: 8,
            anchorY: 12,
        });
        expect(fonts.placement("b")).toMatchObject({ x: 28, y: 4 });
        expect(fonts.placement("c")).toMatchObject({ x: 4, y: 28 });
    });

    it("keeps a released glyph placed until the atlas has to repack", () => {
        const fonts = atlas();
        for (const char of "abcd") fonts.acquire(char);

        fonts.release("a");
        expect(fonts.placement("a")).toBeDefined();
        fonts.consumePlacements();

        fonts.acquire("e");

        expect(placed(fonts)).toEqual(["b", "c", "d", "e"]);
        expect(fonts.placement("b")).toMatchObject({ x: 4, y: 4 });
        expect(fonts.consumePlacements()).toEqual({ kind: "all" });
    });

    it("keeps every glyph still referenced through a repack", () => {
        const fonts = atlas();
        for (const char of "aabcd") fonts.acquire(char);
        fonts.release("a");
        fonts.release("b");

        fonts.acquire("e");

        expect(placed(fonts)).toEqual(["a", "c", "d", "e"]);
    });

    it("ignores releases past zero, so a later acquisition still holds the glyph", () => {
        const fonts = atlas();
        fonts.acquire("a");
        fonts.release("a");
        fonts.release("a");
        fonts.release("unknown");
        for (const char of "abcd") fonts.acquire(char);
        fonts.release("b");

        fonts.acquire("e");

        expect(placed(fonts)).toEqual(["a", "c", "d", "e"]);
    });
});
