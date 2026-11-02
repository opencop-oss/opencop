// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { wrapText } from "./wrap-text.util";

// No canvas under vitest, so widths come from the fallback: 0.55 em per char.
const FONT = "monospace";
const emFor = (chars: number) => chars * 0.55;

describe("wrapText", () => {
    it("leaves text alone when it fits", () => {
        expect(wrapText("SHORT", emFor(20), FONT)).toBe("SHORT");
    });

    it("declines a non-positive or non-finite width", () => {
        expect(wrapText("SHORT", 0, FONT)).toBe("SHORT");
        expect(wrapText("SHORT", Number.NaN, FONT)).toBe("SHORT");
    });

    it("wraps on word boundaries", () => {
        const wrapped = wrapText("ALPHA BRAVO CHARLIE", emFor(12), FONT);
        expect(wrapped.split("\n")).toEqual(["ALPHA BRAVO", "CHARLIE"]);
    });

    it("keeps every line within the limit", () => {
        const limit = 10;
        const wrapped = wrapText("If I type in here what will happen?", emFor(limit), FONT);
        for (const line of wrapped.split("\n")) {
            expect(line.length).toBeLessThanOrEqual(limit);
        }
    });

    it("preserves newlines the operator typed", () => {
        expect(wrapText("ONE\nTWO", emFor(20), FONT)).toBe("ONE\nTWO");
    });

    it("breaks a word longer than a whole line", () => {
        const wrapped = wrapText("SUPERCALIFRAGILISTIC", emFor(6), FONT);
        expect(wrapped.split("\n").length).toBeGreaterThan(1);
        for (const line of wrapped.split("\n")) {
            expect(line.length).toBeLessThanOrEqual(6);
        }
    });

    it("loses no characters", () => {
        const text = "ALPHA BRAVO CHARLIE DELTA";
        const wrapped = wrapText(text, emFor(8), FONT);
        expect(wrapped.replace(/\n/g, " ")).toBe(text);
    });
});
