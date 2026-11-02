// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { testLayer } from "@deck.gl/test-utils/vitest";
import { BinaryTextLayer as _BinaryTextLayer } from "./BinaryTextLayer";
import type { TextLayerProps } from "@deck.gl/layers";

interface TextDatum {
    text: string;
    position: [number, number];
    color?: [number, number, number, number];
}

const baseAccessors = {
    getText: (d) => d.text,
    getPosition: (d) => d.position,
} as const satisfies Partial<TextLayerProps<TextDatum>>;

const BinaryTextLayer = _BinaryTextLayer<TextDatum>;

const SHORT = { text: "short", position: [-1, 1] } as const satisfies TextDatum;
const LONG = { text: "loooooooong", position: [1, 2] } as const satisfies TextDatum;
const NULL = { text: "null", position: [0, 0] } as const satisfies TextDatum;

/** Repeat `[x, y]` n times as a flat [x, y, 0, x, y, 0, ...] array (z=0 for 3-float position). */
function repeatXY(xy: readonly number[], times: number): number[] {
    const out: number[] = [];
    for (let i = 0; i < times; i++) out.push(xy[0], xy[1], 0);
    return out;
}

describe("initialization", () => {
    it("starts with empty binary attributes and zero instances", () => {
        testLayer({
            Layer: BinaryTextLayer,
            testCases: [
                {
                    title: "empty",
                    props: { id: "empty", data: [], ...baseAccessors },
                    onAfterUpdate: ({ layer }) => {
                        const { attributes, numInstances, startIndices } = layer.state;
                        expect(numInstances ?? 0).toEqual(0);
                        expect(startIndices ?? [0]).toEqual([0]);
                        // All binary attributes are initialized (typed array exists).
                        for (const key of [
                            "getPosition",
                            "getColor",
                            "getSize",
                            "getAngle",
                            "getPixelOffset",
                            "getContentBox",
                            "getIcon",
                            "getBillboard",
                        ] as const) {
                            expect(attributes[key].value, `${key} initialized`).toBeDefined();
                        }
                    },
                },
            ],
        });
    });
});

describe("full data updates", () => {
    it("populates the position buffer with one tuple per glyph", () => {
        testLayer({
            Layer: BinaryTextLayer,
            testCases: [
                {
                    title: "single string",
                    props: { id: "single", data: [NULL], ...baseAccessors },
                    onAfterUpdate: ({ layer }) => {
                        const { attributes, numInstances, startIndices } = layer.state;
                        expect(numInstances).toEqual(NULL.text.length);
                        expect(startIndices).toEqual([0, NULL.text.length]);

                        const positions = attributes.getPosition.value;
                        expect(positions.slice(0, 3 * NULL.text.length)).toEqual(
                            new Float64Array(repeatXY(NULL.position, NULL.text.length)),
                        );
                    },
                },
            ],
        });
    });

    it("rebuilds the position buffer for multiple variable-length strings", () => {
        testLayer({
            Layer: BinaryTextLayer,
            testCases: [
                {
                    title: "three strings",
                    props: { id: "three", data: [NULL, SHORT, LONG], ...baseAccessors },
                    onAfterUpdate: ({ layer }) => {
                        const { attributes, numInstances, startIndices } = layer.state;
                        const total = NULL.text.length + SHORT.text.length + LONG.text.length;
                        expect(numInstances).toEqual(total);
                        expect(startIndices).toEqual([
                            0,
                            NULL.text.length,
                            NULL.text.length + SHORT.text.length,
                            total,
                        ]);

                        const expected = new Float64Array(
                            repeatXY(NULL.position, NULL.text.length)
                                .concat(repeatXY(SHORT.position, SHORT.text.length))
                                .concat(repeatXY(LONG.position, LONG.text.length)),
                        );
                        expect(attributes.getPosition.value.slice(0, 3 * total)).toEqual(expected);
                    },
                },
            ],
        });
    });
});

describe("partial updates via _dataDiff", () => {
    it("appends without rebuilding existing rows", () => {
        testLayer({
            Layer: BinaryTextLayer,
            testCases: [
                {
                    title: "init",
                    props: { id: "append", data: [NULL], ...baseAccessors },
                    onAfterUpdate: ({ layer }) => {
                        expect(layer.state.numInstances).toEqual(NULL.text.length);
                    },
                },
                {
                    title: "append SHORT",
                    updateProps: {
                        data: [NULL, SHORT],
                        _dataDiff: () => [{ startRow: 1, endRow: 2 }],
                    },
                    onAfterUpdate: ({ layer }) => {
                        const { attributes, numInstances, startIndices } = layer.state;
                        expect(numInstances).toEqual(NULL.text.length + SHORT.text.length);
                        expect(startIndices).toEqual([
                            0,
                            NULL.text.length,
                            NULL.text.length + SHORT.text.length,
                        ]);
                        expect(
                            attributes.getPosition.value.slice(
                                0,
                                3 * (NULL.text.length + SHORT.text.length),
                            ),
                        ).toEqual(
                            new Float64Array(
                                repeatXY(NULL.position, NULL.text.length).concat(
                                    repeatXY(SHORT.position, SHORT.text.length),
                                ),
                            ),
                        );
                    },
                },
            ],
        });
    });

    it("preserves the tail when a middle entry changes length", () => {
        testLayer({
            Layer: BinaryTextLayer,
            testCases: [
                {
                    title: "init",
                    props: { id: "midgrow", data: [NULL, SHORT, NULL], ...baseAccessors },
                },
                {
                    title: "grow middle",
                    updateProps: {
                        data: [NULL, LONG, NULL],
                        _dataDiff: () => [{ startRow: 1, endRow: 2 }],
                    },
                    onAfterUpdate: ({ layer }) => {
                        const { attributes, numInstances, startIndices } = layer.state;
                        const total = NULL.text.length + LONG.text.length + NULL.text.length;
                        expect(numInstances).toEqual(total);
                        expect(startIndices).toEqual([
                            0,
                            NULL.text.length,
                            NULL.text.length + LONG.text.length,
                            total,
                        ]);

                        const expected = new Float64Array(
                            repeatXY(NULL.position, NULL.text.length)
                                .concat(repeatXY(LONG.position, LONG.text.length))
                                .concat(repeatXY(NULL.position, NULL.text.length)),
                        );
                        expect(attributes.getPosition.value.slice(0, 3 * total)).toEqual(expected);
                    },
                },
            ],
        });
    });
});

describe("updateTriggers", () => {
    it("rewrites a single attribute when its trigger changes", () => {
        const RED: [number, number, number, number] = [255, 0, 0, 255];
        const BLUE: [number, number, number, number] = [0, 0, 255, 255];

        testLayer({
            Layer: BinaryTextLayer,
            testCases: [
                {
                    title: "init red",
                    props: {
                        id: "trigger-color",
                        data: [{ ...NULL, color: RED }],
                        ...baseAccessors,
                        getColor: (d) => d.color!,
                        updateTriggers: { getColor: "red" },
                    },
                    onAfterUpdate: ({ layer }) => {
                        const { attributes, numInstances } = layer.state;
                        expect(numInstances).toEqual(NULL.text.length);
                        const colors = attributes.getColor.value;
                        // First glyph's RGBA tuple
                        expect(Array.from(colors.slice(0, 4))).toEqual(RED);
                    },
                },
                {
                    title: "swap to blue via trigger",
                    updateProps: {
                        data: [{ ...NULL, color: BLUE }],
                        getColor: (d) => d.color!,
                        updateTriggers: { getColor: "blue" },
                    },
                    onAfterUpdate: ({ layer }) => {
                        const { attributes, numInstances } = layer.state;
                        // numInstances unchanged — text didn't change
                        expect(numInstances).toEqual(NULL.text.length);

                        const colors = attributes.getColor.value;
                        // Every glyph repainted blue
                        for (let i = 0; i < NULL.text.length; i++) {
                            expect(Array.from(colors.slice(i * 4, i * 4 + 4))).toEqual(BLUE);
                        }
                    },
                },
            ],
        });
    });
});
