// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { testLayer } from "@deck.gl/test-utils/vitest";
import {
    LabeledPathLayer as _LabeledPathLayer,
    type LabeledPathLayerProps,
} from "./LabeledPathLayer";
import type { CulledTextLayer } from "../culled-text-layer/CulledTextLayer";

interface PathDatum {
    path: [number, number][];
}

const baseAccessors = {
    getPath: (d) => d.path,
} as const satisfies Partial<LabeledPathLayerProps<PathDatum>>;

// 4-segment path: labels land on segment indices 1..4, default textPlacementMod=2
// places labels on segments 2 and 4 (i.e. every other segment).
const FOUR_SEG: PathDatum = {
    path: [
        [0, 0],
        [1, 0],
        [2, 0],
        [3, 0],
        [4, 0],
    ],
};

const TWO_SEG: PathDatum = {
    path: [
        [0, 0],
        [1, 0],
        [2, 0],
    ],
};

// Flat-form path equivalent of FOUR_SEG, to exercise the flat branch.
const FOUR_SEG_FLAT = {
    path: [0, 0, 1, 0, 2, 0, 3, 0, 4, 0] as unknown as [number, number][],
};

const TOO_SHORT: PathDatum = { path: [[0, 0]] };

const LabeledPathLayer = _LabeledPathLayer<PathDatum>;

describe("lifecycle", () => {
    it("initializes empty state and survives empty data", () => {
        testLayer({
            Layer: LabeledPathLayer,
            testCases: [
                {
                    title: "empty data",
                    props: { id: "path-empty", data: [], ...baseAccessors },
                    onAfterUpdate: ({ layer }) => {
                        expect(layer.state.paths.data).toEqual([]);
                        expect(layer.state.labels.data).toEqual([]);
                    },
                },
            ],
        });
    });

    it("populates paths and labels when data is added", () => {
        testLayer({
            Layer: LabeledPathLayer,
            testCases: [
                {
                    title: "one four-segment element",
                    props: {
                        id: "path-populate",
                        data: [FOUR_SEG],
                        ...baseAccessors,
                    },
                    onAfterUpdate: ({ layer }) => {
                        expect(layer.state.paths.data).toHaveLength(1);
                        // textPlacementMod default 2 → labels on segments 2 and 4.
                        expect(layer.state.labels.data).toHaveLength(2);
                    },
                },
            ],
        });
    });
});

describe("sub-layer composition", () => {
    it("renders paths and labels sub-layers", () => {
        testLayer({
            Layer: LabeledPathLayer,
            testCases: [
                {
                    title: "sub-layer ids",
                    props: { id: "path-subs", data: [FOUR_SEG], ...baseAccessors },
                    onAfterUpdate: ({ subLayers }) => {
                        const ids = subLayers.map((l) => l.id);
                        expect(ids.some((id) => id.endsWith("-paths"))).toBe(true);
                        expect(ids.some((id) => id.endsWith("-text"))).toBe(true);
                    },
                },
            ],
        });
    });

    it("keeps the labels sub-layer mounted and forwards text=false", () => {
        testLayer({
            Layer: LabeledPathLayer,
            testCases: [
                {
                    title: "labeled false",
                    props: {
                        id: "path-no-text",
                        data: [FOUR_SEG],
                        text: false,
                        ...baseAccessors,
                    },
                    onAfterUpdate: ({ subLayers }) => {
                        const labels = subLayers.find((l) =>
                            l.id.endsWith("-text"),
                        ) as CulledTextLayer<unknown>;
                        expect(labels).toBeDefined();
                        expect(labels?.props.text).toBe(false);
                    },
                },
            ],
        });
    });
});

describe("geometry derivation", () => {
    it("flattens nested path coordinates into a single array", () => {
        testLayer({
            Layer: LabeledPathLayer,
            testCases: [
                {
                    title: "nested path flattens",
                    props: { id: "path-nested", data: [FOUR_SEG], ...baseAccessors },
                    onAfterUpdate: ({ layer }) => {
                        expect(layer.state.paths.data[0].path).toEqual([
                            0, 0, 1, 0, 2, 0, 3, 0, 4, 0,
                        ]);
                    },
                },
            ],
        });
    });

    it("accepts flat path coordinates", () => {
        testLayer({
            Layer: LabeledPathLayer,
            testCases: [
                {
                    title: "flat path passes through",
                    props: { id: "path-flat", data: [FOUR_SEG_FLAT], ...baseAccessors },
                    onAfterUpdate: ({ layer }) => {
                        expect(layer.state.paths.data[0].path).toEqual([
                            0, 0, 1, 0, 2, 0, 3, 0, 4, 0,
                        ]);
                    },
                },
            ],
        });
    });

    it("skips paths with fewer than two points", () => {
        testLayer({
            Layer: LabeledPathLayer,
            testCases: [
                {
                    title: "too short path",
                    props: { id: "path-short", data: [TOO_SHORT], ...baseAccessors },
                    onAfterUpdate: ({ layer }) => {
                        expect(layer.state.paths.data).toHaveLength(0);
                        expect(layer.state.labels.data).toHaveLength(0);
                    },
                },
            ],
        });
    });

    it("places labels according to textPlacementMod", () => {
        testLayer({
            Layer: LabeledPathLayer,
            testCases: [
                {
                    title: "mod=1 labels every segment",
                    props: {
                        id: "path-mod1",
                        data: [FOUR_SEG],
                        textPlacementMod: 1,
                        ...baseAccessors,
                    },
                    onAfterUpdate: ({ layer }) => {
                        // 4 segments, label every one.
                        expect(layer.state.labels.data).toHaveLength(4);
                    },
                },
                {
                    title: "mod=4 labels only fourth segment",
                    updateProps: { textPlacementMod: 4 },
                    onAfterUpdate: ({ layer }) => {
                        expect(layer.state.labels.data).toHaveLength(1);
                    },
                },
            ],
        });
    });

    it("produces finite label positions and angles", () => {
        testLayer({
            Layer: LabeledPathLayer,
            testCases: [
                {
                    title: "finite labels",
                    props: { id: "path-finite", data: [FOUR_SEG], ...baseAccessors },
                    onAfterUpdate: ({ layer }) => {
                        for (const lbl of layer.state.labels.data) {
                            expect(Number.isFinite(lbl.position[0])).toBe(true);
                            expect(Number.isFinite(lbl.position[1])).toBe(true);
                            expect(Number.isFinite(lbl.angle)).toBe(true);
                        }
                    },
                },
            ],
        });
    });
});

describe("invalidation / partial updates", () => {
    it("rebuilds collection when textPlacementMod changes", () => {
        testLayer({
            Layer: LabeledPathLayer,
            testCases: [
                {
                    title: "initial mod=2",
                    props: {
                        id: "path-invalidate-mod",
                        data: [FOUR_SEG],
                        textPlacementMod: 2,
                        ...baseAccessors,
                    },
                    onAfterUpdate: ({ layer }) => {
                        expect(layer.state.labels.data).toHaveLength(2);
                    },
                },
                {
                    title: "update mod=1 rebuilds",
                    updateProps: { textPlacementMod: 1 },
                    onAfterUpdate: ({ layer }) => {
                        expect(layer.state.labels.data).toHaveLength(4);
                    },
                },
            ],
        });
    });

    it("appends new entries when data grows via _dataDiff", () => {
        testLayer({
            Layer: LabeledPathLayer,
            testCases: [
                {
                    title: "initial single element",
                    props: { id: "path-grow", data: [TWO_SEG], ...baseAccessors },
                    onAfterUpdate: ({ layer }) => {
                        expect(layer.state.paths.data).toHaveLength(1);
                    },
                },
                {
                    title: "append second element",
                    updateProps: {
                        data: [TWO_SEG, FOUR_SEG],
                        _dataDiff: () => [{ startRow: 1, endRow: 2 }],
                    },
                    onAfterUpdate: ({ layer }) => {
                        expect(layer.state.paths.data).toHaveLength(2);
                    },
                },
            ],
        });
    });

    it("updates an existing entry via _dataDiff", () => {
        const UPDATED: PathDatum = {
            path: [
                [0, 0],
                [5, 5],
                [10, 10],
            ],
        };
        testLayer({
            Layer: LabeledPathLayer,
            testCases: [
                {
                    title: "initial two elements",
                    props: {
                        id: "path-update",
                        data: [TWO_SEG, FOUR_SEG],
                        ...baseAccessors,
                    },
                    onAfterUpdate: ({ layer }) => {
                        expect(layer.state.paths.data[0].path).toEqual([0, 0, 1, 0, 2, 0]);
                    },
                },
                {
                    title: "update first element's path",
                    updateProps: {
                        data: [UPDATED, FOUR_SEG],
                        _dataDiff: () => [{ startRow: 0, endRow: 1 }],
                    },
                    onAfterUpdate: ({ layer }) => {
                        expect(layer.state.paths.data).toHaveLength(2);
                        expect(layer.state.paths.data[0].path).toEqual([0, 0, 5, 5, 10, 10]);
                        // FOUR_SEG row untouched.
                        expect(layer.state.paths.data[1].path).toEqual([
                            0, 0, 1, 0, 2, 0, 3, 0, 4, 0,
                        ]);
                    },
                },
            ],
        });
    });

    it("deletes an entry via _dataDiff", () => {
        testLayer({
            Layer: LabeledPathLayer,
            testCases: [
                {
                    title: "initial two elements",
                    props: {
                        id: "path-delete",
                        data: [TWO_SEG, FOUR_SEG],
                        ...baseAccessors,
                    },
                    onAfterUpdate: ({ layer }) => {
                        expect(layer.state.paths.data).toHaveLength(2);
                    },
                },
                {
                    title: "delete second element",
                    updateProps: {
                        data: [TWO_SEG],
                        _dataDiff: () => [{ startRow: 1, endRow: 2 }],
                    },
                    onAfterUpdate: ({ layer }) => {
                        expect(layer.state.paths.data).toHaveLength(1);
                        expect(layer.state.paths.data[0].path).toEqual([0, 0, 1, 0, 2, 0]);
                    },
                },
            ],
        });
    });

    it("handles multiple disjoint _dataDiff ranges", () => {
        const A: PathDatum = TWO_SEG;
        const B: PathDatum = FOUR_SEG;
        const C: PathDatum = TWO_SEG;
        const A2: PathDatum = {
            path: [
                [0, 0],
                [9, 9],
                [18, 18],
            ],
        };
        const C2: PathDatum = {
            path: [
                [0, 0],
                [7, 7],
                [14, 14],
            ],
        };
        testLayer({
            Layer: LabeledPathLayer,
            testCases: [
                {
                    title: "initial three elements",
                    props: {
                        id: "path-multi-diff",
                        data: [A, B, C],
                        ...baseAccessors,
                    },
                    onAfterUpdate: ({ layer }) => {
                        expect(layer.state.paths.data).toHaveLength(3);
                    },
                },
                {
                    title: "update rows 0 and 2 in one diff",
                    updateProps: {
                        data: [A2, B, C2],
                        _dataDiff: () => [
                            { startRow: 0, endRow: 1 },
                            { startRow: 2, endRow: 3 },
                        ],
                    },
                    onAfterUpdate: ({ layer }) => {
                        expect(layer.state.paths.data[0].path).toEqual([0, 0, 9, 9, 18, 18]);
                        expect(layer.state.paths.data[2].path).toEqual([0, 0, 7, 7, 14, 14]);
                    },
                },
            ],
        });
    });
});
