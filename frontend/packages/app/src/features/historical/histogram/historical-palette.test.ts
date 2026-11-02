// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";

import {
    assignSourceColors,
    assignSourceShades,
    HISTOGRAM_SKELETON_SHADES,
    HISTOGRAM_SOURCE_COLORS,
    sourceColor,
    sourceShade,
} from "./historical-palette";

const HEX = /^#[0-9a-fA-F]{6}$/;

describe("historical palette", () => {
    it("both palettes are non-empty valid hex", () => {
        expect(HISTOGRAM_SOURCE_COLORS.length).toBeGreaterThan(0);
        expect(HISTOGRAM_SKELETON_SHADES.length).toBeGreaterThan(0);
        for (const c of HISTOGRAM_SOURCE_COLORS) expect(c).toMatch(HEX);
        for (const s of HISTOGRAM_SKELETON_SHADES) expect(s).toMatch(HEX);
    });

    it("sourceColor cycles past the palette length", () => {
        const n = HISTOGRAM_SOURCE_COLORS.length;
        expect(sourceColor(0)).toBe(HISTOGRAM_SOURCE_COLORS[0]);
        expect(sourceColor(n)).toBe(HISTOGRAM_SOURCE_COLORS[0]);
        expect(sourceColor(n + 1)).toBe(HISTOGRAM_SOURCE_COLORS[1]);
        // Negative index stays in range.
        expect(sourceColor(-1)).toBe(HISTOGRAM_SOURCE_COLORS[n - 1]);
    });

    it("sourceShade cycles past the shade-scale length", () => {
        const n = HISTOGRAM_SKELETON_SHADES.length;
        expect(sourceShade(n)).toBe(HISTOGRAM_SKELETON_SHADES[0]);
    });

    it("assignSourceColors assigns by selection order and is stable", () => {
        const ids = ["src-A", "src-B", "src-C"];
        const m = assignSourceColors(ids);
        expect(m.get("src-A")).toBe(HISTOGRAM_SOURCE_COLORS[0]);
        expect(m.get("src-B")).toBe(HISTOGRAM_SOURCE_COLORS[1]);
        expect(m.get("src-C")).toBe(HISTOGRAM_SOURCE_COLORS[2]);
        // Reordering changes assignment (order is the source of truth).
        const reordered = assignSourceColors(["src-B", "src-A", "src-C"]);
        expect(reordered.get("src-B")).toBe(HISTOGRAM_SOURCE_COLORS[0]);
        expect(reordered.get("src-A")).toBe(HISTOGRAM_SOURCE_COLORS[1]);
    });

    it("assignSourceColors cycles when more sources than palette entries", () => {
        const n = HISTOGRAM_SOURCE_COLORS.length;
        const ids = Array.from({ length: n + 2 }, (_, i) => `src-${i}`);
        const m = assignSourceColors(ids);
        expect(m.get(`src-${n}`)).toBe(HISTOGRAM_SOURCE_COLORS[0]);
        expect(m.get(`src-${n + 1}`)).toBe(HISTOGRAM_SOURCE_COLORS[1]);
        expect(m.size).toBe(n + 2);
    });

    it("assignSourceShades assigns grayscale by order", () => {
        const m = assignSourceShades(["a", "b"]);
        expect(m.get("a")).toBe(HISTOGRAM_SKELETON_SHADES[0]);
        expect(m.get("b")).toBe(HISTOGRAM_SKELETON_SHADES[1]);
    });
});
