// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { ArrowLayer as _ArrowLayer, type ArrowLayerProps } from "./ArrowLayer";
import type { CulledTextLayer } from "../culled-text-layer/CulledTextLayer";
import { testLayer } from "@deck.gl/test-utils/vitest";
import * as turf from "@turf/turf";

interface ArrowDatum {
    start: [number, number];
    end: [number, number];
}

const baseAccessors = {
    getStart: (d) => d.start,
    getEnd: (d) => d.end,
} as const satisfies Partial<ArrowLayerProps<ArrowDatum>>;

const HORIZONTAL: ArrowDatum = {
    start: [0, 0],
    end: [1, 0],
};

const VERTICAL: ArrowDatum = {
    start: [0, 0],
    end: [0, 1],
};

const ArrowLayer = _ArrowLayer<ArrowDatum>;

describe("lifecycle", () => {
    it("initializes empty state and survives empty data", () => {
        testLayer({
            Layer: ArrowLayer,
            testCases: [
                {
                    title: "empty data",
                    props: { id: "arrow-empty", data: [], ...baseAccessors },
                    onAfterUpdate: ({ layer }) => {
                        expect(layer.state.lines.data).toEqual([]);
                        expect(layer.state.heads.data).toEqual([]);
                    },
                },
            ],
        });
    });

    it("populates all four sub-collections when data is added", () => {
        testLayer({
            Layer: ArrowLayer,
            testCases: [
                {
                    title: "two elements",
                    props: {
                        id: "arrow-populate",
                        data: [HORIZONTAL, VERTICAL],
                        ...baseAccessors,
                    },
                    onAfterUpdate: ({ layer }) => {
                        expect(layer.state.heads.data).toHaveLength(2);
                        // Three text rows per element: length + bearing measurements + label.
                        expect(layer.state.text.data).toHaveLength(6);
                        expect(layer.state.lines.data).toHaveLength(2);
                    },
                },
            ],
        });
    });
});

describe("sub-layer composition", () => {
    it("renders lines, heads, and the consolidated text sub-layer", () => {
        testLayer({
            Layer: ArrowLayer,
            testCases: [
                {
                    title: "sub-layer ids",
                    props: { id: "arrow-subs", data: [HORIZONTAL], text: true, ...baseAccessors },
                    onAfterUpdate: ({ subLayers }) => {
                        const ids = subLayers.map((l) => l.id);
                        expect(ids.some((id) => id.endsWith("-lines"))).toBe(true);
                        expect(ids.some((id) => id.endsWith("-heads"))).toBe(true);
                        expect(ids.some((id) => id.endsWith("-text"))).toBe(true);
                    },
                },
            ],
        });
    });

    it("keeps the text sub-layer mounted and forwards text=false", () => {
        testLayer({
            Layer: ArrowLayer,
            testCases: [
                {
                    title: "text false",
                    props: {
                        id: "arrow-no-text",
                        data: [HORIZONTAL],
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
    it("constructs line path as [start, end]", () => {
        testLayer({
            Layer: ArrowLayer,
            testCases: [
                {
                    title: "line path order",
                    props: { id: "arrow-lines", data: [HORIZONTAL], ...baseAccessors },
                    onAfterUpdate: ({ layer }) => {
                        const line = layer.state.lines.data[0];
                        expect(line.path).toEqual([...HORIZONTAL.start, ...HORIZONTAL.end]);
                    },
                },
            ],
        });
    });

    it("constructs head angle as negative rhumb bearing", () => {
        testLayer({
            Layer: ArrowLayer,
            testCases: [
                {
                    title: "head angle",
                    props: { id: "arrow-head-angle", data: [VERTICAL], ...baseAccessors },
                    onAfterUpdate: ({ layer }) => {
                        const head = layer.state.heads.data[0];
                        expect(head.angle).toEqual(
                            -turf.rhumbBearing(VERTICAL.start, VERTICAL.end),
                        );
                    },
                },
            ],
        });
    });

    it("puts head at end of arrow", () => {
        testLayer({
            Layer: ArrowLayer,
            testCases: [
                {
                    title: "head at arrow end",
                    props: { id: "arrow-head-end", data: [HORIZONTAL], ...baseAccessors },
                    onAfterUpdate: ({ layer }) => {
                        const head = layer.state.heads.data[0];
                        expect(head.position).toEqual(HORIZONTAL.end);
                    },
                },
            ],
        });
    });
});

describe("arrow formatting", () => {
    it("applies a custom formatBearing prop", () => {
        const formatBearing = (deg: number) => `~${Math.round(deg)} units`;
        testLayer({
            Layer: ArrowLayer,
            testCases: [
                {
                    title: "custom formatter",
                    props: {
                        id: "arrow-custom-format",
                        data: [VERTICAL],
                        formatBearing,
                        ...baseAccessors,
                    },
                    onAfterUpdate: ({ layer }) => {
                        expect(
                            layer.state.text.data.find((d) => /^~[01] units$/.test(d.text ?? "")),
                        ).not.toBe(undefined);
                    },
                },
            ],
        });
    });

    it("applies a custom formatLength prop", () => {
        const formatLength = (meters: number) => `~${Math.round(meters)} boogaloos`;
        testLayer({
            Layer: ArrowLayer,
            testCases: [
                {
                    title: "custom formatter",
                    props: {
                        id: "arrow-custom-format",
                        data: [VERTICAL],
                        formatLength,
                        ...baseAccessors,
                    },
                    onAfterUpdate: ({ layer }) => {
                        expect(
                            layer.state.text.data.find((d) =>
                                /^~[0-9]* boogaloos$/.test(d.text ?? ""),
                            ),
                        ).not.toBe(undefined);
                    },
                },
            ],
        });
    });
});

describe("invalidation / partial updates", () => {
    it("appends new entries when data grows", () => {
        testLayer({
            Layer: ArrowLayer,
            testCases: [
                {
                    title: "initial single element",
                    props: {
                        id: "arrow-grow",
                        data: [VERTICAL],
                        ...baseAccessors,
                    },
                    onAfterUpdate: ({ layer }) => {
                        expect(layer.state.lines.data).toHaveLength(1);
                        expect(layer.state.heads.data).toHaveLength(1);
                        expect(layer.state.text.data).toHaveLength(3);
                    },
                },
                {
                    title: "append second element",
                    updateProps: {
                        data: [VERTICAL, HORIZONTAL],
                        _dataDiff: () => [{ startRow: 1, endRow: 2 }],
                    },
                    onAfterUpdate: ({ layer }) => {
                        expect(layer.state.lines.data).toHaveLength(2);
                        expect(layer.state.heads.data).toHaveLength(2);
                        expect(layer.state.text.data).toHaveLength(6);
                    },
                },
            ],
        });
    });

    it("updates entry with _dataDiff", () => {
        testLayer({
            Layer: ArrowLayer,
            testCases: [
                {
                    title: "initial elements",
                    props: {
                        id: "arrow-update",
                        data: [VERTICAL, HORIZONTAL],
                        ...baseAccessors,
                    },
                    onAfterUpdate: ({ layer }) => {
                        expect(layer.state.lines.data).toHaveLength(2);
                        expect(layer.state.heads.data).toHaveLength(2);
                        expect(layer.state.text.data).toHaveLength(6);
                    },
                },
                {
                    title: "update first element",
                    updateProps: {
                        data: [HORIZONTAL, HORIZONTAL],
                        _dataDiff: () => [{ startRow: 0, endRow: 1 }],
                    },
                    onAfterUpdate: ({ layer }) => {
                        expect(layer.state.lines.data[0].path).toEqual([
                            ...HORIZONTAL.start,
                            ...HORIZONTAL.end,
                        ]);
                        expect(layer.state.heads.data[0].position).toEqual(HORIZONTAL.end);
                    },
                },
            ],
        });
    });

    it("deletes entry with _dataDiff", () => {
        testLayer({
            Layer: ArrowLayer,
            testCases: [
                {
                    title: "initial elements",
                    props: {
                        id: "arrow-update",
                        data: [VERTICAL, HORIZONTAL],
                        ...baseAccessors,
                    },
                    onAfterUpdate: ({ layer }) => {
                        expect(layer.state.lines.data).toHaveLength(2);
                        expect(layer.state.heads.data).toHaveLength(2);
                        expect(layer.state.text.data).toHaveLength(6);
                    },
                },
                {
                    title: "delete first element",
                    updateProps: {
                        data: [HORIZONTAL],
                        _dataDiff: () => [{ startRow: 0, endRow: 1 }],
                    },
                    onAfterUpdate: ({ layer }) => {
                        expect(layer.state.lines.data[0].path).toEqual([
                            ...HORIZONTAL.start,
                            ...HORIZONTAL.end,
                        ]);
                        expect(layer.state.heads.data[0].position).toEqual(HORIZONTAL.end);
                    },
                },
            ],
        });
    });
});
