// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { testLayer } from "@deck.gl/test-utils/vitest";
import { CircleLayer as _CircleLayer, type CircleLayerProps } from "./CircleLayer";
import type { MeasuredTextDatum } from "../text.props";
import type { CulledTextLayer } from "../culled-text-layer/CulledTextLayer";

interface CircleDatum {
    position: [number, number];
    radius: number;
}

const baseAccessors = {
    getPosition: (d) => d.position,
    getRadius: (d) => d.radius,
} as const satisfies Partial<CircleLayerProps<CircleDatum>>;

const SMALL: CircleDatum = { position: [0, 0], radius: 100 };
const LARGE: CircleDatum = { position: [10, 10], radius: 2000 };

const CircleLayer = _CircleLayer<CircleDatum>;

/** Measurement rows of the consolidated text collection (skips the label row). */
const measurements = (data: MeasuredTextDatum[]) => data.filter((d) => d.kind === "measurement");

describe("lifecycle", () => {
    it("initializes empty state and survives empty data", () => {
        testLayer({
            Layer: CircleLayer,
            testCases: [
                {
                    title: "empty data",
                    props: { id: "circle-empty", data: [], ...baseAccessors },
                    onAfterUpdate: ({ layer }) => {
                        expect(layer.state.circles.data).toEqual([]);
                        expect(layer.state.text.data).toEqual([]);
                    },
                },
            ],
        });
    });

    it("populates circles plus four measurements and one label per element", () => {
        testLayer({
            Layer: CircleLayer,
            testCases: [
                {
                    title: "two elements",
                    props: {
                        id: "circle-populate",
                        data: [SMALL, LARGE],
                        ...baseAccessors,
                    },
                    onAfterUpdate: ({ layer }) => {
                        expect(layer.state.circles.data).toHaveLength(2);
                        // Five text rows per circle: four cardinal measurements + one label.
                        expect(layer.state.text.data).toHaveLength(10);
                        expect(measurements(layer.state.text.data)).toHaveLength(8);
                    },
                },
            ],
        });
    });
});

describe("sub-layer composition", () => {
    it("renders stroke, fill, and text sub-layers", () => {
        testLayer({
            Layer: CircleLayer,
            testCases: [
                {
                    title: "sub-layer ids",
                    props: { id: "circle-subs", data: [SMALL], text: true, ...baseAccessors },
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

    it("keeps the text sub-layer mounted and forwards text=false", () => {
        testLayer({
            Layer: CircleLayer,
            testCases: [
                {
                    title: "text false",
                    props: { id: "circle-no-text", data: [SMALL], text: false, ...baseAccessors },
                    onAfterUpdate: ({ subLayers }) => {
                        const textLayer = subLayers.find((l) =>
                            l.id.endsWith("-text"),
                        ) as CulledTextLayer<unknown>;
                        expect(textLayer).toBeDefined();
                        expect(textLayer?.props.text).toBe(false);
                    },
                },
            ],
        });
    });

    it("omits stroke and fill sub-layers when stroked/filled are false", () => {
        testLayer({
            Layer: CircleLayer,
            testCases: [
                {
                    title: "stroked & filled false",
                    props: {
                        id: "circle-no-shape",
                        data: [SMALL],
                        stroked: false,
                        filled: false,
                        ...baseAccessors,
                    },
                    onAfterUpdate: ({ subLayers }) => {
                        expect(subLayers.find((l) => l.id.endsWith("-stroke"))).toBe(undefined);
                        expect(subLayers.find((l) => l.id.endsWith("-fill"))).toBe(undefined);
                    },
                },
            ],
        });
    });
});

describe("geometry derivation", () => {
    it("circle polygon vertex count matches steps", () => {
        const steps = 32;
        testLayer({
            Layer: CircleLayer,
            testCases: [
                {
                    title: "steps drives polygon size",
                    props: {
                        id: "circle-steps",
                        data: [SMALL],
                        steps,
                        ...baseAccessors,
                    },
                    onAfterUpdate: ({ layer }) => {
                        const poly = layer.state.circles.data[0];
                        // turf.circle returns steps+1 vertices (closed ring);
                        // path is [lng,lat,...] → 2*(steps+1) numbers.
                        expect(poly.polygon.length).toBe((steps + 1) * 2);
                    },
                },
            ],
        });
    });

    it("places measurements at four cardinal angles", () => {
        testLayer({
            Layer: CircleLayer,
            testCases: [
                {
                    title: "cardinal angles",
                    props: { id: "circle-angles", data: [SMALL], ...baseAccessors },
                    onAfterUpdate: ({ layer }) => {
                        const angles = measurements(layer.state.text.data).map((m) => m.angle);
                        expect(angles).toEqual([45, 135, 225, 315]);
                    },
                },
            ],
        });
    });
});

describe("radius formatting", () => {
    it("applies a custom formatRadius prop", () => {
        const formatRadius = (m: number) => `~${Math.round(m)} units`;
        testLayer({
            Layer: CircleLayer,
            testCases: [
                {
                    title: "custom formatter",
                    props: {
                        id: "circle-custom-format",
                        data: [SMALL],
                        formatRadius,
                        ...baseAccessors,
                    },
                    onAfterUpdate: ({ layer }) => {
                        expect(measurements(layer.state.text.data)[0].text).toBe(
                            `~${SMALL.radius} units`,
                        );
                    },
                },
            ],
        });
    });
});

describe("invalidation / partial updates", () => {
    it("rebuilds collection when steps changes", () => {
        testLayer({
            Layer: CircleLayer,
            testCases: [
                {
                    title: "initial steps=16",
                    props: {
                        id: "circle-invalidate-steps",
                        data: [SMALL],
                        steps: 16,
                        ...baseAccessors,
                    },
                    onAfterUpdate: ({ layer }) => {
                        expect(layer.state.circles.data[0].polygon.length).toBe((16 + 1) * 2);
                    },
                },
                {
                    title: "update steps=64 rebuilds",
                    updateProps: { steps: 64 },
                    onAfterUpdate: ({ layer }) => {
                        expect(layer.state.circles.data[0].polygon.length).toBe((64 + 1) * 2);
                    },
                },
            ],
        });
    });

    it("appends new entries when data grows via _dataDiff", () => {
        testLayer({
            Layer: CircleLayer,
            testCases: [
                {
                    title: "initial single element",
                    props: {
                        id: "circle-grow",
                        data: [SMALL],
                        ...baseAccessors,
                    },
                    onAfterUpdate: ({ layer }) => {
                        expect(layer.state.circles.data).toHaveLength(1);
                        expect(measurements(layer.state.text.data)).toHaveLength(4);
                    },
                },
                {
                    title: "append second element",
                    updateProps: {
                        data: [SMALL, LARGE],
                        _dataDiff: () => [{ startRow: 1, endRow: 2 }],
                    },
                    onAfterUpdate: ({ layer }) => {
                        expect(layer.state.circles.data).toHaveLength(2);
                        expect(measurements(layer.state.text.data)).toHaveLength(8);
                    },
                },
            ],
        });
    });

    it("updates an existing entry via _dataDiff", () => {
        const formatRadius = (m: number) => `R=${m}`;
        const UPDATED: CircleDatum = { position: SMALL.position, radius: 500 };
        testLayer({
            Layer: CircleLayer,
            testCases: [
                {
                    title: "initial two elements",
                    props: {
                        id: "circle-update",
                        data: [SMALL, LARGE],
                        formatRadius,
                        ...baseAccessors,
                    },
                    onAfterUpdate: ({ layer }) => {
                        expect(measurements(layer.state.text.data)[0].text).toBe(
                            `R=${SMALL.radius}`,
                        );
                    },
                },
                {
                    title: "update first element's radius",
                    updateProps: {
                        data: [UPDATED, LARGE],
                        _dataDiff: () => [{ startRow: 0, endRow: 1 }],
                    },
                    onAfterUpdate: ({ layer }) => {
                        expect(layer.state.circles.data).toHaveLength(2);
                        const meas = measurements(layer.state.text.data);
                        // First four measurements belong to row 0 — all reflect the new radius.
                        expect(meas.slice(0, 4).map((m) => m.text)).toEqual([
                            "R=500",
                            "R=500",
                            "R=500",
                            "R=500",
                        ]);
                        // Row 1 measurements untouched.
                        expect(meas[4].text).toBe(`R=${LARGE.radius}`);
                    },
                },
            ],
        });
    });

    it("deletes an entry via _dataDiff", () => {
        testLayer({
            Layer: CircleLayer,
            testCases: [
                {
                    title: "initial two elements",
                    props: {
                        id: "circle-delete",
                        data: [SMALL, LARGE],
                        ...baseAccessors,
                    },
                    onAfterUpdate: ({ layer }) => {
                        expect(layer.state.circles.data).toHaveLength(2);
                        expect(measurements(layer.state.text.data)).toHaveLength(8);
                    },
                },
                {
                    title: "delete second element",
                    updateProps: {
                        data: [SMALL],
                        _dataDiff: () => [{ startRow: 1, endRow: 2 }],
                    },
                    onAfterUpdate: ({ layer }) => {
                        expect(layer.state.circles.data).toHaveLength(1);
                        expect(measurements(layer.state.text.data)).toHaveLength(4);
                    },
                },
            ],
        });
    });

    it("handles multiple disjoint _dataDiff ranges", () => {
        const A: CircleDatum = { position: [0, 0], radius: 100 };
        const B: CircleDatum = { position: [1, 1], radius: 200 };
        const C: CircleDatum = { position: [2, 2], radius: 300 };
        const A2: CircleDatum = { position: [0, 0], radius: 999 };
        const C2: CircleDatum = { position: [2, 2], radius: 777 };
        const formatRadius = (m: number) => `R=${m}`;
        testLayer({
            Layer: CircleLayer,
            testCases: [
                {
                    title: "initial three elements",
                    props: {
                        id: "circle-multi-diff",
                        data: [A, B, C],
                        formatRadius,
                        ...baseAccessors,
                    },
                    onAfterUpdate: ({ layer }) => {
                        expect(layer.state.circles.data).toHaveLength(3);
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
                        const meas = measurements(layer.state.text.data);
                        // Both edited rows reflect new radii; middle row stays put.
                        expect(meas[0].text).toBe("R=999");
                        expect(meas[4].text).toBe(`R=${B.radius}`);
                        expect(meas[8].text).toBe("R=777");
                    },
                },
            ],
        });
    });
});
