// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { expect, it } from "vitest";
import { testLayer } from "@deck.gl/test-utils/vitest";
import {
    DiffableTextLayer as _DiffableTextLayer,
    DiffableMultiIconLayer,
} from "./DiffableTextLayer";
import type { Layer } from "@deck.gl/core";
import type { TextLayerProps } from "@deck.gl/layers";

interface TextDatum {
    text: string;
    position: [number, number];
}

const baseAccessors = {
    getText: (d) => d.text,
    getPosition: (d) => d.position,
} as const satisfies Partial<TextLayerProps<TextDatum>>;

const DiffableTextLayer = _DiffableTextLayer<TextDatum>;

const SHORT = {
    text: "short",
    position: [-1, 1],
} as const satisfies TextDatum;

const LONG = {
    text: "loooooooong",
    position: [1, 2],
} as const satisfies TextDatum;

const NULL = {
    text: "null",
    position: [0, 0],
} as const satisfies TextDatum;

const getSubLayer = (subLayers: Layer[]) =>
    subLayers.find((l) => l instanceof DiffableMultiIconLayer) as DiffableMultiIconLayer;

function repeat(arr: number[], times: number): number[] {
    let newArr: number[] = [];

    for (let i = 0; i < times; i++) {
        newArr = newArr.concat([...arr, 0]);
    }

    return newArr;
}

describe("lifecycle", () => {
    it("should initialize correctly with no data", () => {
        testLayer({
            Layer: DiffableTextLayer,
            testCases: [
                {
                    title: "initialize layer",
                    props: {
                        id: "initialize layer",
                        data: [],
                        ...baseAccessors,
                    },
                    onAfterUpdate: ({ subLayers }) => {
                        const multiIcon = getSubLayer(subLayers);
                        const { oldStartIndices, oldNumInstances } = multiIcon.state;
                        expect(oldStartIndices.length).toEqual(1);
                        expect(oldNumInstances).toEqual(0);
                    },
                },
            ],
        });
    });

    it("should correctly append data", () => {
        testLayer({
            Layer: DiffableTextLayer,
            testCases: [
                {
                    title: "initialize layer",
                    props: {
                        id: "data-growth",
                        data: [],
                        ...baseAccessors,
                    },
                    onAfterUpdate: ({ subLayers }) => {
                        const multiIcon = getSubLayer(subLayers);
                        const { oldStartIndices, oldNumInstances } = multiIcon.state;
                        expect(oldStartIndices.length).toEqual(1);
                        expect(oldNumInstances).toEqual(0);
                    },
                },
                {
                    title: "grow data",
                    updateProps: {
                        data: [NULL],
                        _dataDiff: () => [{ startRow: 0, endRow: 1 }],
                    },
                    onAfterUpdate: ({ subLayers }) => {
                        const multiIcon = getSubLayer(subLayers);
                        const { oldStartIndices, oldNumInstances } = multiIcon.state;
                        expect(oldStartIndices).toEqual([0, NULL.text.length]);
                        expect(oldNumInstances).toEqual(NULL.text.length);

                        // Ensure position attribute is correct.
                        const attrs = multiIcon.getAttributeManager()!.getAttributes();
                        const positions = attrs["instancePositions"];
                        expect(positions.value?.slice(0, 3 * NULL.text.length)).toEqual(
                            new Float64Array(repeat(NULL.position, NULL.text.length)),
                        );
                    },
                },
            ],
        });
    });

    it("should correctly delete data", () => {
        testLayer({
            Layer: DiffableTextLayer,
            testCases: [
                {
                    title: "initialize layer",
                    props: {
                        id: "delete-data",
                        data: [NULL, NULL, NULL],
                        ...baseAccessors,
                    },
                    onAfterUpdate: ({ subLayers }) => {
                        const multiIcon = getSubLayer(subLayers);
                        const { oldStartIndices, oldNumInstances } = multiIcon.state;
                        expect(oldStartIndices).toEqual([
                            0,
                            NULL.text.length,
                            2 * NULL.text.length,
                            3 * NULL.text.length,
                        ]);
                        expect(oldNumInstances).toEqual(3 * NULL.text.length);
                    },
                },
                {
                    title: "delete data",
                    updateProps: {
                        data: [NULL],
                        // delete the first 2 entries.
                        _dataDiff: () => [{ startRow: 0, endRow: 2 }],
                    },
                    onAfterUpdate: ({ subLayers }) => {
                        const multiIcon = getSubLayer(subLayers);
                        const { oldStartIndices, oldNumInstances } = multiIcon.state;
                        expect(oldStartIndices).toEqual([0, NULL.text.length]);
                        expect(oldNumInstances).toEqual(NULL.text.length);

                        // Ensure position attribute is correct.
                        const attrs = multiIcon.getAttributeManager()!.getAttributes();
                        const positions = attrs["instancePositions"];
                        expect(positions.value?.slice(0, 3 * NULL.text.length)).toEqual(
                            new Float64Array(repeat(NULL.position, NULL.text.length)),
                        );
                    },
                },
            ],
        });
    });

    it("should correctly update last data entry", () => {
        testLayer({
            Layer: DiffableTextLayer,
            testCases: [
                {
                    title: "initialize layer",
                    props: {
                        id: "update-data",
                        data: [NULL, LONG],
                        ...baseAccessors,
                    },
                    onAfterUpdate: ({ subLayers }) => {
                        const multiIcon = getSubLayer(subLayers);
                        const { oldStartIndices, oldNumInstances } = multiIcon.state;
                        expect(oldStartIndices).toEqual([
                            0,
                            NULL.text.length,
                            NULL.text.length + LONG.text.length,
                        ]);
                        expect(oldNumInstances).toEqual(NULL.text.length + LONG.text.length);
                    },
                },
                {
                    title: "update last entry",
                    updateProps: {
                        data: [NULL, SHORT],
                        // Change last entry
                        _dataDiff: () => [{ startRow: 1, endRow: 2 }],
                    },
                    onAfterUpdate: ({ subLayers }) => {
                        const multiIcon = getSubLayer(subLayers);
                        const { oldStartIndices, oldNumInstances } = multiIcon.state;
                        expect(oldStartIndices).toEqual([
                            0,
                            NULL.text.length,
                            NULL.text.length + SHORT.text.length,
                        ]);
                        expect(oldNumInstances).toEqual(NULL.text.length + SHORT.text.length);

                        // Ensure position attribute is correct.
                        const attrs = multiIcon.getAttributeManager()!.getAttributes();
                        const positions = attrs["instancePositions"];
                        expect(
                            positions.value?.slice(0, 3 * (NULL.text.length + SHORT.text.length)),
                        ).toEqual(
                            new Float64Array(
                                repeat(NULL.position, NULL.text.length).concat(
                                    repeat(SHORT.position, SHORT.text.length),
                                ),
                            ),
                        );
                    },
                },
            ],
        });
    });

    it("should correctly update data entry that changes length", () => {
        testLayer({
            Layer: DiffableTextLayer,
            testCases: [
                {
                    title: "initialize layer",
                    props: {
                        id: "variable-length-update",
                        data: [NULL, SHORT, SHORT],
                        ...baseAccessors,
                    },
                    onAfterUpdate: ({ subLayers }) => {
                        const multiIcon = getSubLayer(subLayers);
                        const { oldStartIndices, oldNumInstances } = multiIcon.state;
                        expect(oldStartIndices).toEqual([
                            0,
                            NULL.text.length,
                            NULL.text.length + SHORT.text.length,
                            NULL.text.length + 2 * SHORT.text.length,
                        ]);
                        expect(oldNumInstances).toEqual(NULL.text.length + 2 * SHORT.text.length);
                    },
                },
                {
                    title: "update middle entry",
                    updateProps: {
                        data: [NULL, LONG, SHORT],
                        // Update middle entry, grows in text length
                        _dataDiff: () => [{ startRow: 1, endRow: 2 }],
                    },
                    onAfterUpdate: ({ subLayers }) => {
                        const multiIcon = getSubLayer(subLayers);
                        const { oldStartIndices, oldNumInstances } = multiIcon.state;
                        expect(oldStartIndices).toEqual([
                            0,
                            NULL.text.length,
                            NULL.text.length + LONG.text.length,
                            NULL.text.length + LONG.text.length + SHORT.text.length,
                        ]);
                        expect(oldNumInstances).toEqual(
                            NULL.text.length + LONG.text.length + SHORT.text.length,
                        );

                        // Ensure position attribute is correct.
                        const attrs = multiIcon.getAttributeManager()!.getAttributes();
                        const positions = attrs["instancePositions"];
                        expect(
                            positions.value?.slice(
                                0,
                                3 * (NULL.text.length + LONG.text.length + SHORT.text.length),
                            ),
                        ).toEqual(
                            new Float64Array(
                                repeat(NULL.position, NULL.text.length)
                                    .concat(repeat(LONG.position, LONG.text.length))
                                    .concat(repeat(SHORT.position, SHORT.text.length)),
                            ),
                        );
                    },
                },
            ],
        });
    });

    it("should update entire array when endRow is Infinity", () => {
        testLayer({
            Layer: DiffableTextLayer,
            testCases: [
                {
                    title: "initialize layer",
                    props: {
                        id: "end-row-inf",
                        data: [],
                        ...baseAccessors,
                    },
                    onAfterUpdate: ({ subLayers }) => {
                        const multiIcon = getSubLayer(subLayers);
                        const { oldStartIndices, oldNumInstances } = multiIcon.state;
                        expect(oldStartIndices.length).toEqual(1);
                        expect(oldNumInstances).toEqual(0);
                    },
                },
                {
                    title: "update data with Infinity endRow",
                    updateProps: {
                        data: [NULL],
                        _dataDiff: () => [{ startRow: 0, endRow: Infinity }],
                    },
                    onAfterUpdate: ({ subLayers }) => {
                        const multiIcon = getSubLayer(subLayers);
                        const { oldStartIndices, oldNumInstances } = multiIcon.state;
                        expect(oldStartIndices).toEqual([0, NULL.text.length]);
                        expect(oldNumInstances).toEqual(NULL.text.length);

                        // Ensure position attribute is correct.
                        const attrs = multiIcon.getAttributeManager()!.getAttributes();
                        const positions = attrs["instancePositions"];
                        expect(positions.value?.slice(0, 3 * NULL.text.length)).toEqual(
                            new Float64Array(repeat(NULL.position, NULL.text.length)),
                        );
                    },
                },
            ],
        });
    });
});
