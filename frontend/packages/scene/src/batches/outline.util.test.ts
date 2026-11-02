// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { OutlineExtension, TerrainOutlineExtension } from "@opencop/layers";
import type { BatchRenderProps, OutlineProps } from "../SceneProps";
import { outlineExtensions, outlineValue, terrainExtension } from "./outline.util";

const outline = (overrides: Partial<OutlineProps> = {}): OutlineProps => ({
    enabled: true,
    color: [1, 0, 0],
    width: 2,
    ...overrides,
});

describe("outlineExtensions", () => {
    it("is empty without outline props", () => {
        expect(outlineExtensions({})).toEqual([]);
        expect(outlineExtensions({ terrainDrawMode: "offset" })).toEqual([]);
    });

    it("gives undraped layers a screen-space OutlineExtension with the outline options", () => {
        for (const terrainDrawMode of [undefined, "offset"] as const) {
            const [extension, ...rest] = outlineExtensions({ outline: outline(), terrainDrawMode });
            expect(rest).toEqual([]);
            expect(extension).toBeInstanceOf(OutlineExtension);
            expect(extension.opts).toEqual({ enabled: true, color: [1, 0, 0], width: 2 });
        }
    });

    it("gives draped layers no screen-space outline", () => {
        expect(outlineExtensions({ outline: outline(), terrainDrawMode: "drape" })).toEqual([]);
    });

    it("reuses one instance for equal outline settings", () => {
        const [a] = outlineExtensions({ outline: outline() });
        const [b] = outlineExtensions({ outline: outline(), terrainDrawMode: "offset" });
        expect(b).toBe(a);
    });

    it("creates a new instance when any outline setting changes", () => {
        const [base] = outlineExtensions({ outline: outline() });
        const variants = [
            outline({ enabled: false }),
            outline({ color: [0, 1, 0] }),
            outline({ width: 5 }),
        ].map((o) => outlineExtensions({ outline: o })[0]);
        expect(new Set([base, ...variants]).size).toBe(4);
    });
});

describe("terrainExtension", () => {
    it("gives every undraped layer one shared, non-outlining TerrainOutlineExtension", () => {
        const props: BatchRenderProps[] = [
            {},
            { terrainDrawMode: "offset" },
            { terrainDrawMode: "offset", outline: outline() },
        ];
        const [first, ...rest] = props.map(terrainExtension);
        expect(first).toBeInstanceOf(TerrainOutlineExtension);
        expect((first as TerrainOutlineExtension).opts.outline).toBe(false);
        for (const extension of rest) expect(extension).toBe(first);
    });

    it("outlines draped layers, even without outline props", () => {
        const extension = terrainExtension({ terrainDrawMode: "drape" });
        expect(extension).toBeInstanceOf(TerrainOutlineExtension);
        expect((extension as TerrainOutlineExtension).opts).toEqual({
            outline: true,
            color: [1, 1, 1],
        });
    });

    it("outlines draped layers in the outline color", () => {
        const extension = terrainExtension({
            terrainDrawMode: "drape",
            outline: outline({ color: [0, 0, 1] }),
        });
        expect((extension as TerrainOutlineExtension).opts).toEqual({
            outline: true,
            color: [0, 0, 1],
        });
    });

    it("reuses one draped instance per color, ignoring other outline settings", () => {
        const a = terrainExtension({ terrainDrawMode: "drape", outline: outline() });
        const b = terrainExtension({
            terrainDrawMode: "drape",
            outline: outline({ enabled: false, width: 9 }),
        });
        const c = terrainExtension({
            terrainDrawMode: "drape",
            outline: outline({ color: [0, 1, 1] }),
        });
        expect(b).toBe(a);
        expect(c).not.toBe(a);
        expect(terrainExtension({ terrainDrawMode: "offset" })).not.toBe(a);
    });
});

describe("outlineValue", () => {
    it("writes 1 for outlined primitives and 0 otherwise", () => {
        expect(outlineValue(true)).toEqual([1]);
        expect(outlineValue(false)).toEqual([0]);
        expect(outlineValue(undefined)).toEqual([0]);
    });
});
