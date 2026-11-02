// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { testLayer } from "@deck.gl/test-utils/vitest";
import {
    LabeledPolygonLayer as _LabeledPolygonLayer,
    type LabeledPolygonLayerProps,
} from "./LabeledPolygonLayer";
import type { CulledTextLayer } from "../culled-text-layer/CulledTextLayer";

interface PolygonDatum {
    polygon: [number, number][];
}

const baseAccessors = {
    getPolygon: (d) => d.polygon,
} as const satisfies Partial<LabeledPolygonLayerProps<PolygonDatum>>;

// Open square with vertex-average centroid at (0.5, 0.5).
const SQUARE: PolygonDatum = {
    polygon: [
        [0, 0],
        [1, 0],
        [1, 1],
        [0, 1],
    ],
};

// Closed square (last vertex equals first). Centroid skips the closing vertex.
const CLOSED_SQUARE: PolygonDatum = {
    polygon: [
        [0, 0],
        [2, 0],
        [2, 2],
        [0, 2],
        [0, 0],
    ],
};

// Triangle centered above origin.
const TRIANGLE: PolygonDatum = {
    polygon: [
        [0, 0],
        [2, 0],
        [1, 2],
    ],
};

const TOO_SHORT: PolygonDatum = { polygon: [[0, 0]] };

const FLAT_SQUARE = {
    polygon: [0, 0, 1, 0, 1, 1, 0, 1] as unknown as [number, number][],
};

const LabeledPolygonLayer = _LabeledPolygonLayer<PolygonDatum>;

describe("lifecycle", () => {
    it("initializes empty state and survives empty data", () => {
        testLayer({
            Layer: LabeledPolygonLayer,
            testCases: [
                {
                    title: "empty data",
                    props: { id: "poly-empty", data: [], ...baseAccessors },
                    onAfterUpdate: ({ layer }) => {
                        expect(layer.state.polygons.data).toEqual([]);
                        expect(layer.state.labels.data).toEqual([]);
                    },
                },
            ],
        });
    });

    it("populates polygons and one label per element", () => {
        testLayer({
            Layer: LabeledPolygonLayer,
            testCases: [
                {
                    title: "two elements",
                    props: {
                        id: "poly-populate",
                        data: [SQUARE, TRIANGLE],
                        ...baseAccessors,
                    },
                    onAfterUpdate: ({ layer }) => {
                        expect(layer.state.polygons.data).toHaveLength(2);
                        expect(layer.state.labels.data).toHaveLength(2);
                    },
                },
            ],
        });
    });
});

describe("sub-layer composition", () => {
    it("renders stroke, fill, and labels sub-layers", () => {
        testLayer({
            Layer: LabeledPolygonLayer,
            testCases: [
                {
                    title: "sub-layer ids",
                    props: { id: "poly-subs", data: [SQUARE], text: true, ...baseAccessors },
                    onAfterUpdate: ({ subLayers }) => {
                        const ids = subLayers.map((l) => l.id);
                        expect(ids.some((id) => id.endsWith("-stroke"))).toBe(true);
                        expect(ids.some((id) => id.endsWith("-fill"))).toBe(true);
                        expect(ids.some((id) => id.endsWith("-text"))).toBe(true);
                    },
                },
            ],
        });
    });

    it("keeps the labels sub-layer mounted and forwards text=false", () => {
        testLayer({
            Layer: LabeledPolygonLayer,
            testCases: [
                {
                    title: "text false",
                    props: {
                        id: "poly-no-text",
                        data: [SQUARE],
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
    it("flattens nested polygon coordinates and closes the loop", () => {
        testLayer({
            Layer: LabeledPolygonLayer,
            testCases: [
                {
                    title: "nested polygon flattens",
                    props: { id: "poly-nested", data: [SQUARE], ...baseAccessors },
                    onAfterUpdate: ({ layer }) => {
                        expect(layer.state.polygons.data[0].polygon).toEqual([
                            0, 0, 1, 0, 1, 1, 0, 1, 0, 0,
                        ]);
                    },
                },
            ],
        });
    });

    it("accepts flat polygon coordinates and closes the loop", () => {
        testLayer({
            Layer: LabeledPolygonLayer,
            testCases: [
                {
                    title: "flat polygon passes through",
                    props: { id: "poly-flat", data: [FLAT_SQUARE], ...baseAccessors },
                    onAfterUpdate: ({ layer }) => {
                        expect(layer.state.polygons.data[0].polygon).toEqual([
                            0, 0, 1, 0, 1, 1, 0, 1, 0, 0,
                        ]);
                    },
                },
            ],
        });
    });

    it("computes vertex-average centroid for open polygon", () => {
        testLayer({
            Layer: LabeledPolygonLayer,
            testCases: [
                {
                    title: "open square centroid",
                    props: { id: "poly-centroid-open", data: [SQUARE], ...baseAccessors },
                    onAfterUpdate: ({ layer }) => {
                        expect(layer.state.labels.data[0].position).toEqual([0.5, 0.5]);
                    },
                },
            ],
        });
    });

    it("skips closing vertex when computing centroid", () => {
        testLayer({
            Layer: LabeledPolygonLayer,
            testCases: [
                {
                    title: "closed square centroid",
                    props: { id: "poly-centroid-closed", data: [CLOSED_SQUARE], ...baseAccessors },
                    onAfterUpdate: ({ layer }) => {
                        // Closing vertex is dropped → average of (0,0),(2,0),(2,2),(0,2) = (1,1).
                        expect(layer.state.labels.data[0].position).toEqual([1, 1]);
                    },
                },
            ],
        });
    });

    it("skips polygons with fewer than two points", () => {
        testLayer({
            Layer: LabeledPolygonLayer,
            testCases: [
                {
                    title: "too short polygon",
                    props: { id: "poly-short", data: [TOO_SHORT], ...baseAccessors },
                    onAfterUpdate: ({ layer }) => {
                        expect(layer.state.polygons.data).toHaveLength(0);
                        expect(layer.state.labels.data).toHaveLength(0);
                    },
                },
            ],
        });
    });
});

describe("invalidation / partial updates", () => {
    it("appends new entries when data grows via _dataDiff", () => {
        testLayer({
            Layer: LabeledPolygonLayer,
            testCases: [
                {
                    title: "initial single element",
                    props: { id: "poly-grow", data: [SQUARE], ...baseAccessors },
                    onAfterUpdate: ({ layer }) => {
                        expect(layer.state.polygons.data).toHaveLength(1);
                        expect(layer.state.labels.data).toHaveLength(1);
                    },
                },
                {
                    title: "append second element",
                    updateProps: {
                        data: [SQUARE, TRIANGLE],
                        _dataDiff: () => [{ startRow: 1, endRow: 2 }],
                    },
                    onAfterUpdate: ({ layer }) => {
                        expect(layer.state.polygons.data).toHaveLength(2);
                        expect(layer.state.labels.data).toHaveLength(2);
                    },
                },
            ],
        });
    });

    it("updates an existing entry via _dataDiff", () => {
        // Different polygon → centroid shifts from (0.5, 0.5) to (5, 5).
        const SHIFTED: PolygonDatum = {
            polygon: [
                [4, 4],
                [6, 4],
                [6, 6],
                [4, 6],
            ],
        };
        testLayer({
            Layer: LabeledPolygonLayer,
            testCases: [
                {
                    title: "initial two elements",
                    props: {
                        id: "poly-update",
                        data: [SQUARE, TRIANGLE],
                        ...baseAccessors,
                    },
                    onAfterUpdate: ({ layer }) => {
                        expect(layer.state.labels.data[0].position).toEqual([0.5, 0.5]);
                    },
                },
                {
                    title: "update first element's polygon",
                    updateProps: {
                        data: [SHIFTED, TRIANGLE],
                        _dataDiff: () => [{ startRow: 0, endRow: 1 }],
                    },
                    onAfterUpdate: ({ layer }) => {
                        expect(layer.state.polygons.data).toHaveLength(2);
                        expect(layer.state.labels.data[0].position).toEqual([5, 5]);
                    },
                },
            ],
        });
    });

    it("deletes an entry via _dataDiff", () => {
        testLayer({
            Layer: LabeledPolygonLayer,
            testCases: [
                {
                    title: "initial two elements",
                    props: {
                        id: "poly-delete",
                        data: [SQUARE, TRIANGLE],
                        ...baseAccessors,
                    },
                    onAfterUpdate: ({ layer }) => {
                        expect(layer.state.polygons.data).toHaveLength(2);
                        expect(layer.state.labels.data).toHaveLength(2);
                    },
                },
                {
                    title: "delete second element",
                    updateProps: {
                        data: [SQUARE],
                        _dataDiff: () => [{ startRow: 1, endRow: 2 }],
                    },
                    onAfterUpdate: ({ layer }) => {
                        expect(layer.state.polygons.data).toHaveLength(1);
                        expect(layer.state.labels.data).toHaveLength(1);
                        // Remaining label keeps SQUARE's centroid.
                        expect(layer.state.labels.data[0].position).toEqual([0.5, 0.5]);
                    },
                },
            ],
        });
    });

    it("handles multiple disjoint _dataDiff ranges", () => {
        const A: PolygonDatum = {
            polygon: [
                [0, 0],
                [1, 0],
                [1, 1],
                [0, 1],
            ],
        };
        const B: PolygonDatum = {
            polygon: [
                [10, 10],
                [11, 10],
                [11, 11],
                [10, 11],
            ],
        };
        const C: PolygonDatum = {
            polygon: [
                [20, 20],
                [21, 20],
                [21, 21],
                [20, 21],
            ],
        };
        const A2: PolygonDatum = {
            polygon: [
                [40, 40],
                [41, 40],
                [41, 41],
                [40, 41],
            ],
        };
        const C2: PolygonDatum = {
            polygon: [
                [60, 60],
                [61, 60],
                [61, 61],
                [60, 61],
            ],
        };
        testLayer({
            Layer: LabeledPolygonLayer,
            testCases: [
                {
                    title: "initial three elements",
                    props: {
                        id: "poly-multi-diff",
                        data: [A, B, C],
                        ...baseAccessors,
                    },
                    onAfterUpdate: ({ layer }) => {
                        expect(layer.state.polygons.data).toHaveLength(3);
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
                        expect(layer.state.labels.data[0].position).toEqual([40.5, 40.5]);
                        expect(layer.state.labels.data[1].position).toEqual([10.5, 10.5]);
                        expect(layer.state.labels.data[2].position).toEqual([60.5, 60.5]);
                    },
                },
            ],
        });
    });
});
