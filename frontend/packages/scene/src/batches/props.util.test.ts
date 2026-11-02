// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { DataFilterExtension } from "@deck.gl/extensions";
import { OutlineExtension, TerrainOutlineExtension } from "@opencop/layers";
import type { BatchRenderProps } from "../SceneProps";
import { outlineExtensions } from "./outline.util";
import { filterValue, sharedLayerProps } from "./props.util";

const OUTLINE = { enabled: true, color: [1, 0, 0], width: 2 } as const;

/** A batch layer's extensions, composed the way the batches compose them. */
const layerExtensions = (props: BatchRenderProps) => [
    ...outlineExtensions(props),
    ...sharedLayerProps(props, {}).extensions,
];

const terrainOutline = (props: BatchRenderProps) =>
    layerExtensions(props)
        .filter((e) => e instanceof TerrainOutlineExtension)
        .map((e) => (e as TerrainOutlineExtension).opts.outline);

describe("sharedLayerProps", () => {
    it("attaches nothing without terrain or filter props", () => {
        expect(sharedLayerProps({}, { depthWriteEnabled: false })).toEqual({
            extensions: [],
            parameters: { depthWriteEnabled: false },
        });
    });

    it("merges the given parameters over the layer's own without mutating them", () => {
        const own = { depthWriteEnabled: false, depthCompare: "less" } as const;
        const { parameters } = sharedLayerProps({ parameters: { depthCompare: "always" } }, own);
        expect(parameters).toEqual({ depthWriteEnabled: false, depthCompare: "always" });
        expect(own.depthCompare).toBe("less");
    });

    it("attaches the terrain extension and draw mode only when terrain is on", () => {
        for (const terrainDrawMode of ["drape", "offset"] as const) {
            const shared = sharedLayerProps({ terrainDrawMode }, {});
            expect(shared.terrainDrawMode).toBe(terrainDrawMode);
            expect(shared.extensions).toHaveLength(1);
            expect(shared.extensions[0]).toBeInstanceOf(TerrainOutlineExtension);
        }
        expect(sharedLayerProps({}, {})).not.toHaveProperty("terrainDrawMode");
    });

    it("attaches the data filter and its range only when a range is given", () => {
        const filtered = sharedLayerProps({ filterRange: [0, 0.5] }, {});
        expect(filtered.filterRange).toEqual([0, 0.5]);
        expect(filtered.extensions).toHaveLength(1);
        expect(filtered.extensions[0]).toBeInstanceOf(DataFilterExtension);

        const unfiltered = sharedLayerProps({ terrainDrawMode: "drape" }, {});
        expect(unfiltered).not.toHaveProperty("filterRange");
        expect(unfiltered.extensions.some((e) => e instanceof DataFilterExtension)).toBe(false);
    });

    it("orders the terrain extension before the data filter", () => {
        const { extensions } = sharedLayerProps(
            { terrainDrawMode: "offset", filterRange: [0, 1] },
            {},
        );
        expect(extensions[0]).toBeInstanceOf(TerrainOutlineExtension);
        expect(extensions[1]).toBeInstanceOf(DataFilterExtension);
    });

    it("returns the same extension instances for equal props", () => {
        const props = (): BatchRenderProps => ({
            terrainDrawMode: "drape",
            filterRange: [0, 1],
            outline: { ...OUTLINE, color: [...OUTLINE.color] },
        });
        const a = sharedLayerProps(props(), {}).extensions;
        const b = sharedLayerProps(props(), {}).extensions;
        expect(b).not.toBe(a);
        expect(b).toHaveLength(2);
        b.forEach((extension, i) => expect(extension).toBe(a[i]));
    });
});

describe("batch layer extensions", () => {
    it("outline a draped layer on the terrain, not in screen space", () => {
        const props: BatchRenderProps = { terrainDrawMode: "drape", outline: OUTLINE };
        expect(terrainOutline(props)).toEqual([true]);
        expect(layerExtensions(props).some((e) => e instanceof OutlineExtension)).toBe(false);
    });

    it("outline an offset layer in screen space, with a non-outlining terrain extension", () => {
        const props: BatchRenderProps = { terrainDrawMode: "offset", outline: OUTLINE };
        expect(terrainOutline(props)).toEqual([false]);
        expect(layerExtensions(props).filter((e) => e instanceof OutlineExtension)).toHaveLength(1);
    });

    it("have no terrain extension without terrain", () => {
        expect(terrainOutline({ outline: OUTLINE })).toEqual([]);
    });
});

describe("filterValue", () => {
    it("defaults every channel to the unfiltered value 1", () => {
        expect(filterValue(undefined)).toEqual([1]);
        expect(filterValue(undefined, 2)).toEqual([1, 1]);
    });

    it("takes the given channels, padding missing ones and dropping extras", () => {
        expect(filterValue([0.25], 2)).toEqual([0.25, 1]);
        expect(filterValue([0.25, 0.5])).toEqual([0.25]);
        expect(filterValue([0, 0.5], 2)).toEqual([0, 0.5]);
    });
});
