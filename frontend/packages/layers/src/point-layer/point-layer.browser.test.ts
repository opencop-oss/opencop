// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { testLayer } from "@deck.gl/test-utils/vitest";
import { PointLayer as _PointLayer, type PointLayerProps } from "./PointLayer";
import type { ScatterplotLayer } from "@deck.gl/layers";
import type { CulledTextLayer } from "../culled-text-layer/CulledTextLayer";

interface PointDatum {
    position: [number, number];
    radius?: number;
}

const baseAccessors = {
    getPosition: (d) => d.position,
} as const satisfies Partial<PointLayerProps<PointDatum>>;

const A: PointDatum = { position: [0, 0], radius: 4 };
const B: PointDatum = { position: [10, 10], radius: 8 };

const PointLayer = _PointLayer<PointDatum>;

describe("lifecycle", () => {
    it("survives empty data", () => {
        testLayer({
            Layer: PointLayer,
            testCases: [
                {
                    title: "empty data",
                    props: { id: "point-empty", data: [], ...baseAccessors },
                    onAfterUpdate: ({ subLayers }) => {
                        expect(subLayers.length).toBeGreaterThan(0);
                    },
                },
            ],
        });
    });

    it("renders for populated data", () => {
        testLayer({
            Layer: PointLayer,
            testCases: [
                {
                    title: "two elements",
                    props: { id: "point-populate", data: [A, B], ...baseAccessors },
                    onAfterUpdate: ({ subLayers }) => {
                        const scatter = subLayers.find((l) => l.id.endsWith("-scatter"));
                        expect(scatter?.props.data).toHaveLength(2);
                    },
                },
            ],
        });
    });
});

describe("sub-layer composition", () => {
    it("renders scatter and labels sub-layers", () => {
        testLayer({
            Layer: PointLayer,
            testCases: [
                {
                    title: "sub-layer ids",
                    props: { id: "point-subs", data: [A], ...baseAccessors },
                    onAfterUpdate: ({ subLayers }) => {
                        const ids = subLayers.map((l) => l.id);
                        expect(ids.some((id) => id.endsWith("-scatter"))).toBe(true);
                        expect(ids.some((id) => id.endsWith("-text"))).toBe(true);
                    },
                },
            ],
        });
    });

    it("keeps the labels sub-layer mounted and forwards text=false", () => {
        testLayer({
            Layer: PointLayer,
            testCases: [
                {
                    title: "labeled false",
                    props: {
                        id: "point-no-text",
                        data: [A],
                        text: false,
                        ...baseAccessors,
                    },
                    onAfterUpdate: ({ subLayers }) => {
                        const labels = subLayers.find((l) =>
                            l.id.endsWith("text"),
                        ) as CulledTextLayer<unknown>;
                        expect(labels).toBeDefined();
                        expect(labels?.props.text).toBe(false);
                    },
                },
            ],
        });
    });
});

describe("prop forwarding", () => {
    it("forwards radius props to the scatter sub-layer", () => {
        testLayer({
            Layer: PointLayer,
            testCases: [
                {
                    title: "radius props pass through",
                    props: {
                        id: "point-radius",
                        data: [A],
                        radiusUnits: "meters",
                        radiusScale: 5,
                        radiusMinPixels: 2,
                        radiusMaxPixels: 100,
                        ...baseAccessors,
                    },
                    onAfterUpdate: ({ subLayers }) => {
                        const scatter = subLayers.find((l) =>
                            l.id.endsWith("-scatter"),
                        ) as ScatterplotLayer;
                        expect(scatter?.props.radiusUnits).toBe("meters");
                        expect(scatter?.props.radiusScale).toBe(5);
                        expect(scatter?.props.radiusMinPixels).toBe(2);
                        expect(scatter?.props.radiusMaxPixels).toBe(100);
                    },
                },
            ],
        });
    });

    it("passes the same data array to both sub-layers", () => {
        testLayer({
            Layer: PointLayer,
            testCases: [
                {
                    title: "shared data",
                    props: { id: "point-shared", data: [A, B], ...baseAccessors },
                    onAfterUpdate: ({ subLayers }) => {
                        const scatter = subLayers.find((l) => l.id.endsWith("-scatter"));
                        const labels = subLayers.find((l) => l.id.endsWith("-text"));
                        expect(scatter?.props.data).toBe(labels?.props.data);
                    },
                },
            ],
        });
    });
});

describe("invalidation / partial updates", () => {
    it("propagates data growth to the scatter sub-layer", () => {
        testLayer({
            Layer: PointLayer,
            testCases: [
                {
                    title: "initial single element",
                    props: { id: "point-grow", data: [A], ...baseAccessors },
                    onAfterUpdate: ({ subLayers }) => {
                        const scatter = subLayers.find((l) => l.id.endsWith("-scatter"));
                        expect(scatter?.props.data).toHaveLength(1);
                    },
                },
                {
                    title: "append second element",
                    updateProps: { data: [A, B] },
                    onAfterUpdate: ({ subLayers }) => {
                        const scatter = subLayers.find((l) => l.id.endsWith("-scatter"));
                        expect(scatter?.props.data).toHaveLength(2);
                    },
                },
            ],
        });
    });

    it("forwards _dataDiff to both sub-layers", () => {
        const diff = () => [{ startRow: 1, endRow: 2 }];
        testLayer({
            Layer: PointLayer,
            testCases: [
                {
                    title: "initial single element",
                    props: { id: "point-diff-forward", data: [A], ...baseAccessors },
                    onAfterUpdate: () => {},
                },
                {
                    title: "append with _dataDiff",
                    updateProps: { data: [A, B], _dataDiff: diff },
                    onAfterUpdate: ({ subLayers }) => {
                        const scatter = subLayers.find((l) => l.id.endsWith("scatter"));
                        const labels = subLayers.find((l) => l.id.endsWith("text"));
                        expect(scatter?.props._dataDiff).toBe(diff);
                        expect(labels?.props._dataDiff).toBe(diff);
                    },
                },
            ],
        });
    });

    it("forwards an update _dataDiff range to sub-layers", () => {
        const updateDiff = () => [{ startRow: 0, endRow: 1 }];
        const A2: PointDatum = { ...A, radius: 100 };
        testLayer({
            Layer: PointLayer,
            testCases: [
                {
                    title: "initial two elements",
                    props: { id: "point-diff-update", data: [A, B], ...baseAccessors },
                    onAfterUpdate: ({ subLayers }) => {
                        const scatter = subLayers.find((l) => l.id.endsWith("-scatter"));
                        expect(scatter?.props.data).toHaveLength(2);
                    },
                },
                {
                    title: "update row 0 with _dataDiff",
                    updateProps: { data: [A2, B], _dataDiff: updateDiff },
                    onAfterUpdate: ({ subLayers }) => {
                        const scatter = subLayers.find((l) => l.id.endsWith("-scatter"));
                        expect(scatter?.props.data).toHaveLength(2);
                        expect(scatter?.props._dataDiff).toBe(updateDiff);
                    },
                },
            ],
        });
    });

    it("forwards a delete _dataDiff range to sub-layers", () => {
        const deleteDiff = () => [{ startRow: 1, endRow: 2 }];
        testLayer({
            Layer: PointLayer,
            testCases: [
                {
                    title: "initial two elements",
                    props: { id: "point-diff-delete", data: [A, B], ...baseAccessors },
                    onAfterUpdate: ({ subLayers }) => {
                        const scatter = subLayers.find((l) => l.id.endsWith("-scatter"));
                        expect(scatter?.props.data).toHaveLength(2);
                    },
                },
                {
                    title: "delete row 1 with _dataDiff",
                    updateProps: { data: [A], _dataDiff: deleteDiff },
                    onAfterUpdate: ({ subLayers }) => {
                        const scatter = subLayers.find((l) => l.id.endsWith("-scatter"));
                        expect(scatter?.props.data).toHaveLength(1);
                        expect(scatter?.props._dataDiff).toBe(deleteDiff);
                    },
                },
            ],
        });
    });
});
