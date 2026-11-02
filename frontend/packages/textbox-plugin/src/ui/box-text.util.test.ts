// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { TextBox } from "@opencop/elements";
import {
    clampTextSize,
    growToHoldText,
    MAX_TEXT_SIZE_METERS,
    MIN_TEXT_SIZE_METERS,
    wrapToBox,
} from "./box-text.util";

const FONT = "monospace";
const lines = (text: string) => text.split("\n").length;

describe("wrapToBox", () => {
    it("leaves short content on one line", () => {
        const wrapped = wrapToBox("OK", 1000, 100, FONT);
        expect(wrapped.text).toBe("OK");
        expect(lines(wrapped.text)).toBe(1);
    });

    it("breaks content too wide for the box", () => {
        const wrapped = wrapToBox("ALPHA BRAVO CHARLIE DELTA ECHO FOXTROT", 500, 100, FONT);
        expect(lines(wrapped.text)).toBeGreaterThan(1);
    });

    it("loses no characters while breaking", () => {
        const text = "ALPHA BRAVO CHARLIE DELTA";
        const wrapped = wrapToBox(text, 1500, 120, FONT);
        expect(wrapped.text.replace(/\n/g, " ")).toBe(text);
    });

    it("breaks inside a word too wide for the box rather than overflowing", () => {
        const strip = (value: string) => value.replace(/\s+/g, "");
        const text = "SUPERCALIFRAGILISTIC";
        const wrapped = wrapToBox(text, 300, 120, FONT);

        expect(lines(wrapped.text)).toBeGreaterThan(1);
        expect(strip(wrapped.text)).toBe(strip(text));
    });

    it("never shrinks the text — more content means a taller box", () => {
        const short = wrapToBox("ALPHA", 500, 100, FONT);
        const long = wrapToBox("ALPHA BRAVO CHARLIE DELTA ECHO FOXTROT GOLF", 500, 100, FONT);
        expect(long.height).toBeGreaterThan(short.height);
    });

    it("asks for a taller box as the text size grows", () => {
        const small = wrapToBox("ALPHA BRAVO", 500, 50, FONT);
        const large = wrapToBox("ALPHA BRAVO", 500, 100, FONT);
        expect(large.height).toBeGreaterThan(small.height);
    });

    it("declines a degenerate box", () => {
        expect(wrapToBox("TEXT", 0, 50, FONT)).toEqual({ text: "TEXT", height: 0 });
    });

    it("needs no height for no content", () => {
        expect(wrapToBox("", 500, 50, FONT)).toEqual({ text: "", height: 0 });
    });
});

describe("clampTextSize", () => {
    it("holds the size between the floor and the ceiling", () => {
        expect(clampTextSize(0.2)).toBe(MIN_TEXT_SIZE_METERS);
        expect(clampTextSize(1_000_000)).toBe(MAX_TEXT_SIZE_METERS);
        expect(clampTextSize(2000)).toBe(2000);
    });
});

describe("growToHoldText", () => {
    /** A box wider than it is tall, with its first edge running east. */
    const box = (text: string, size: number) => {
        const element = TextBox.create({ positions: [0, 0, 0.05, 0, 0.05, 0.03, 0, 0.03] });
        element.getStyle().setText(text);
        element.getStyle().setTextSize(size);
        return element;
    };

    it("grows the box downwards when the content outruns it", () => {
        const element = box("alpha bravo charlie delta echo foxtrot golf hotel india", 2000);
        const before = element.geometry.getAllPos().slice();

        expect(growToHoldText(element)).toBe(true);

        const after = element.geometry.getAllPos();
        // The first edge is pinned; the far edge moves away from it.
        expect(after.slice(0, 4)).toEqual(before.slice(0, 4));
        expect(after[5]).toBeGreaterThan(before[5]);
        expect(after[7]).toBeGreaterThan(before[7]);
    });

    it("leaves a box that already fits alone", () => {
        const element = box("OK", 20);
        const before = element.geometry.getAllPos().slice();

        expect(growToHoldText(element)).toBe(false);

        expect(element.geometry.getAllPos()).toEqual(before);
    });

    it("declines a degenerate box", () => {
        const element = TextBox.create({ positions: [0, 0, 0, 0, 0, 0, 0, 0] });
        element.getStyle().setText("TEXT");

        expect(growToHoldText(element)).toBe(false);
    });
});
