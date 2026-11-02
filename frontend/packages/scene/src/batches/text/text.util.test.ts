// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import type { CharacterPlacement } from "./FontAtlas";
import { autoWrapping, transformParagraph } from "./text.util";

const glyph = (advance: number, height = 10): CharacterPlacement => ({
    x: 0,
    y: 0,
    width: advance,
    height,
    anchorX: advance / 2,
    anchorY: height / 2,
    advance,
});

const placements: Record<string, CharacterPlacement> = {
    a: glyph(10),
    b: glyph(20, 14),
    " ": glyph(5),
};

const paragraph = (
    text: string,
    maxWidth = -1,
    wordBreak: "break-word" | "break-all" = "break-word",
) =>
    transformParagraph({
        paragraph: text,
        baselineOffset: 0,
        lineHeight: 1,
        wordBreak,
        maxWidth,
        placements,
    });

describe("autoWrapping", () => {
    const wrap = (text: string, maxWidth: number, wordBreak: "break-word" | "break-all") => {
        const chars = Array.from(text);
        return autoWrapping({
            text: chars,
            wordBreak,
            startIndex: 0,
            endIndex: chars.length,
            maxWidth,
            placements,
        });
    };

    it("breaks before the character that overflows the row with break-all", () => {
        expect(wrap("aaaaa", 25, "break-all")).toEqual([2, 4]);
    });

    it("breaks after the space before an overflowing word with break-word", () => {
        expect(wrap("aa aa", 25, "break-word")).toEqual([3]);
    });

    it("falls back to break-all for a word wider than a row", () => {
        expect(wrap("a aaaa", 25, "break-word")).toEqual([2, 4]);
    });

    it("does not break text that fits", () => {
        expect(wrap("aa aa", 100, "break-word")).toEqual([]);
        expect(wrap("aaaaa", 100, "break-all")).toEqual([]);
    });

    it("treats a character missing from the atlas as zero width", () => {
        expect(wrap("a?a", 15, "break-all")).toEqual([2]);
    });
});

describe("transformParagraph", () => {
    it("lays out one row by advance, centering each glyph on its anchor", () => {
        const { x, y, rowWidth, size } = paragraph("ab a");

        expect(x).toEqual([5, 20, 32.5, 40]);
        expect(y).toEqual([0.5, 0.5, 0.5, 0.5]);
        expect(rowWidth).toEqual([45, 45, 45, 45]);
        expect(size).toEqual([45, 1]);
    });

    it("starts a row at each newline, keeping one entry per character", () => {
        const { x, y, rowWidth, size } = paragraph("aa\nb");

        expect(x).toEqual([5, 15, 0, 10]);
        expect(y).toEqual([0.5, 0.5, 0, 1.5]);
        expect(rowWidth).toEqual([20, 20, 0, 20]);
        expect(size).toEqual([20, 2]);
    });

    it("offsets rows by the baseline and line height", () => {
        const { y } = transformParagraph({
            paragraph: "a\na",
            baselineOffset: 2,
            lineHeight: 10,
            wordBreak: "break-word",
            maxWidth: -1,
            placements,
        });

        expect(y).toEqual([7, 0, 17]);
    });

    it("advances a missing character by a fixed width", () => {
        const { x, size } = paragraph("a?a");

        expect(x).toEqual([5, 10, 47]);
        expect(size[0]).toBe(52);
    });

    it("ignores maxWidth when it is not positive and finite", () => {
        for (const maxWidth of [-1, 0, Infinity]) {
            expect(paragraph("aaaaa", maxWidth, "break-all").size).toEqual([50, 1]);
        }
    });

    it("counts each wrapped row in the paragraph height", () => {
        expect(paragraph("aaaaa", 25, "break-all").size).toEqual([20, 3]);
    });

    it("positions every character of a wrapped paragraph", () => {
        const { x, y, rowWidth } = paragraph("aaaaa", 25, "break-all");

        expect(x).toEqual([5, 15, 5, 15, 5]);
        expect(y).toEqual([0.5, 0.5, 1.5, 1.5, 2.5]);
        expect(rowWidth).toEqual([20, 20, 20, 20, 10]);
    });
});
