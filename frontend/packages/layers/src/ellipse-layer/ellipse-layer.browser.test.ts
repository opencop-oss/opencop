// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { testLayer } from "@deck.gl/test-utils/vitest";
import { EllipseLayer as _EllipseLayer, type EllipseLayerProps } from "./EllipseLayer";
import type { CulledTextLayer } from "../culled-text-layer/CulledTextLayer";

interface EllipseDatum {
    position: [number, number];
    major: number;
    minor: number;
    angle: number;
}

const baseAccessors = {
    getPosition: (d) => d.position,
    getMajor: (d) => d.major,
    getMinor: (d) => d.minor,
    getAngle: (d) => d.angle,
} as const satisfies Partial<EllipseLayerProps<EllipseDatum>>;

const ROUND: EllipseDatum = { position: [0, 0], major: 1000, minor: 1000, angle: 0 };
const TILTED: EllipseDatum = { position: [10, 10], major: 2000, minor: 500, angle: 45 };

const EllipseLayer = _EllipseLayer<EllipseDatum>;

describe("lifecycle", () => {
    it("initializes empty state and survives empty data", () => {
        testLayer({
            Layer: EllipseLayer,
            testCases: [
                {
                    title: "empty data",
                    props: { id: "ellipse-empty", data: [], ...baseAccessors },
                    onAfterUpdate: ({ layer }) => {
                        expect(layer.state.ellipses.data).toEqual([]);
                    },
                },
            ],
        });
    });

    it("populates ellipses when data is added", () => {
        testLayer({
            Layer: EllipseLayer,
            testCases: [
                {
                    title: "two elements",
                    props: {
                        id: "ellipse-populate",
                        data: [ROUND, TILTED],
                        ...baseAccessors,
                    },
                    onAfterUpdate: ({ layer }) => {
                        expect(layer.state.ellipses.data).toHaveLength(2);
                    },
                },
            ],
        });
    });
});

describe("sub-layer composition", () => {
    it("renders stroke, fill, and text sub-layers", () => {
        testLayer({
            Layer: EllipseLayer,
            testCases: [
                {
                    title: "sub-layer ids",
                    props: { id: "ellipse-subs", data: [ROUND], text: true, ...baseAccessors },
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
            Layer: EllipseLayer,
            testCases: [
                {
                    title: "text false",
                    props: {
                        id: "ellipse-no-text",
                        data: [ROUND],
                        text: false,
                        ...baseAccessors,
                    },
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
});

describe("geometry derivation", () => {
    it("ellipse polygon vertex count matches steps", () => {
        const steps = 32;
        testLayer({
            Layer: EllipseLayer,
            testCases: [
                {
                    title: "steps drives polygon size",
                    props: {
                        id: "ellipse-steps",
                        data: [ROUND],
                        steps,
                        ...baseAccessors,
                    },
                    onAfterUpdate: ({ layer }) => {
                        const poly = layer.state.ellipses.data[0];
                        // turf.ellipse returns steps+1 vertices (closed ring);
                        // path is [lng,lat,...] → 2*(steps+1) numbers.
                        expect(poly.polygon.length).toBe((steps + 1) * 2);
                    },
                },
            ],
        });
    });

    it("produces finite coordinates for tilted ellipses", () => {
        testLayer({
            Layer: EllipseLayer,
            testCases: [
                {
                    title: "tilted finite",
                    props: { id: "ellipse-tilted", data: [TILTED], ...baseAccessors },
                    onAfterUpdate: ({ layer }) => {
                        for (const v of layer.state.ellipses.data[0].polygon) {
                            expect(Number.isFinite(v)).toBe(true);
                        }
                    },
                },
            ],
        });
    });
});

describe("invalidation / partial updates", () => {
    it("rebuilds collection when steps changes", () => {
        testLayer({
            Layer: EllipseLayer,
            testCases: [
                {
                    title: "initial steps=16",
                    props: {
                        id: "ellipse-invalidate-steps",
                        data: [ROUND],
                        steps: 16,
                        ...baseAccessors,
                    },
                    onAfterUpdate: ({ layer }) => {
                        expect(layer.state.ellipses.data[0].polygon.length).toBe((16 + 1) * 2);
                    },
                },
                {
                    title: "update steps=64 rebuilds",
                    updateProps: { steps: 64 },
                    onAfterUpdate: ({ layer }) => {
                        expect(layer.state.ellipses.data[0].polygon.length).toBe((64 + 1) * 2);
                    },
                },
            ],
        });
    });

    it("appends new entries when data grows via _dataDiff", () => {
        testLayer({
            Layer: EllipseLayer,
            testCases: [
                {
                    title: "initial single element",
                    props: {
                        id: "ellipse-grow",
                        data: [ROUND],
                        ...baseAccessors,
                    },
                    onAfterUpdate: ({ layer }) => {
                        expect(layer.state.ellipses.data).toHaveLength(1);
                    },
                },
                {
                    title: "append second element",
                    updateProps: {
                        data: [ROUND, TILTED],
                        _dataDiff: () => [{ startRow: 1, endRow: 2 }],
                    },
                    onAfterUpdate: ({ layer }) => {
                        expect(layer.state.ellipses.data).toHaveLength(2);
                    },
                },
            ],
        });
    });

    it("updates an existing entry via _dataDiff", () => {
        const UPDATED: EllipseDatum = { ...ROUND, major: 5000 };
        let initialPolygon: number[] = [];
        testLayer({
            Layer: EllipseLayer,
            testCases: [
                {
                    title: "initial two elements",
                    props: {
                        id: "ellipse-update",
                        data: [ROUND, TILTED],
                        ...baseAccessors,
                    },
                    onAfterUpdate: ({ layer }) => {
                        expect(layer.state.ellipses.data).toHaveLength(2);
                        initialPolygon = layer.state.ellipses.data[0].polygon.slice();
                    },
                },
                {
                    title: "update first element's major axis",
                    updateProps: {
                        data: [UPDATED, TILTED],
                        _dataDiff: () => [{ startRow: 0, endRow: 1 }],
                    },
                    onAfterUpdate: ({ layer }) => {
                        expect(layer.state.ellipses.data).toHaveLength(2);
                        const updatedPolygon = layer.state.ellipses.data[0].polygon;
                        // Major-axis change should produce a visibly different polygon.
                        expect(updatedPolygon).not.toEqual(initialPolygon);
                    },
                },
            ],
        });
    });

    it("deletes an entry via _dataDiff", () => {
        testLayer({
            Layer: EllipseLayer,
            testCases: [
                {
                    title: "initial two elements",
                    props: {
                        id: "ellipse-delete",
                        data: [ROUND, TILTED],
                        ...baseAccessors,
                    },
                    onAfterUpdate: ({ layer }) => {
                        expect(layer.state.ellipses.data).toHaveLength(2);
                    },
                },
                {
                    title: "delete second element",
                    updateProps: {
                        data: [ROUND],
                        _dataDiff: () => [{ startRow: 1, endRow: 2 }],
                    },
                    onAfterUpdate: ({ layer }) => {
                        expect(layer.state.ellipses.data).toHaveLength(1);
                    },
                },
            ],
        });
    });

    it("handles multiple disjoint _dataDiff ranges", () => {
        const A: EllipseDatum = { position: [0, 0], major: 100, minor: 100, angle: 0 };
        const B: EllipseDatum = { position: [1, 1], major: 200, minor: 200, angle: 0 };
        const C: EllipseDatum = { position: [2, 2], major: 300, minor: 300, angle: 0 };
        const A2: EllipseDatum = { ...A, major: 1000, minor: 1000 };
        const C2: EllipseDatum = { ...C, major: 3000, minor: 3000 };
        testLayer({
            Layer: EllipseLayer,
            testCases: [
                {
                    title: "initial three elements",
                    props: {
                        id: "ellipse-multi-diff",
                        data: [A, B, C],
                        ...baseAccessors,
                    },
                    onAfterUpdate: ({ layer }) => {
                        expect(layer.state.ellipses.data).toHaveLength(3);
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
                        expect(layer.state.ellipses.data).toHaveLength(3);
                    },
                },
            ],
        });
    });
});
