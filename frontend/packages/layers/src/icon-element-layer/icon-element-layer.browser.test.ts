// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { testLayer } from "@deck.gl/test-utils/vitest";
import {
    IconElementLayer as _IconElementLayer,
    type IconElementLayerProps,
} from "./IconElementLayer";
import type { CulledTextLayer } from "../culled-text-layer/CulledTextLayer";

interface IconDatum {
    position: [number, number];
    sidc: string;
    size: number;
    course?: number;
}

// The layer draws whatever `getIcon` returns and never inspects the code, so
// the tests supply a plain stub icon rather than a real symbol renderer — the
// `sidc` field is just an opaque label the layer ignores.
const STUB_ICON = {
    url: "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/5+hHgAHggJ/PchI7wAAAABJRU5ErkJggg==",
    width: 1,
    height: 1,
};

const baseAccessors = {
    getPosition: (d) => d.position,
    getIcon: () => STUB_ICON,
    getSize: (d) => d.size,
    getCourse: (d) => d.course,
} as const satisfies Partial<IconElementLayerProps<IconDatum>>;

const STATIC: IconDatum = { position: [0, 0], sidc: "static-entity", size: 32 };
const MOVING: IconDatum = {
    position: [10, 10],
    sidc: "static-entity",
    size: 32,
    course: 90,
};
const MEDIA: IconDatum = {
    position: [20, 20],
    sidc: "OPENCOP:URL:/V1/LAYERS/ABC/MEDIA/DEF#32X48",
    size: 32,
};

const IconElementLayer = _IconElementLayer<IconDatum>;

describe("lifecycle", () => {
    it("initializes empty state and survives empty data", () => {
        testLayer({
            Layer: IconElementLayer,
            testCases: [
                {
                    title: "empty data",
                    props: { id: "milicon-empty", data: [], ...baseAccessors },
                    onAfterUpdate: ({ layer }) => {
                        expect(layer.state.icons.data).toEqual([]);
                        expect(layer.state.courses.data).toEqual([]);
                    },
                },
            ],
        });
    });

    it("emits one icon and one course entry per datum", () => {
        testLayer({
            Layer: IconElementLayer,
            testCases: [
                {
                    title: "static + moving",
                    props: {
                        id: "milicon-populate",
                        data: [STATIC, MOVING],
                        ...baseAccessors,
                        // Opaque arrow color so we can verify the no-course row
                        // is clamped to transparent.
                        getArrowColor: [200, 200, 200, 255],
                    },
                    onAfterUpdate: ({ layer }) => {
                        expect(layer.state.icons.data).toHaveLength(2);
                        // Course buffer is 1:1 with icons so diffs stay aligned;
                        // the static row gets a transparent arrow.
                        expect(layer.state.courses.data).toHaveLength(2);
                        expect(layer.state.courses.data[0].color[3]).toBe(0);
                        expect(layer.state.courses.data[1].color[3]).toBe(255);
                    },
                },
            ],
        });
    });
});

describe("sub-layer composition", () => {
    it("renders icons, courses, and labels sub-layers", () => {
        testLayer({
            Layer: IconElementLayer,
            testCases: [
                {
                    title: "sub-layer ids",
                    props: { id: "milicon-subs", data: [MOVING], ...baseAccessors },
                    onAfterUpdate: ({ subLayers }) => {
                        const ids = subLayers.map((l) => l.id);
                        // The icon sub-layer is a RecyclingIconLayer (id `-icons`);
                        // it bounds its own atlas internally, no id suffix.
                        expect(ids.some((id) => id.endsWith("-icons"))).toBe(true);
                        expect(ids.some((id) => id.endsWith("-courses"))).toBe(true);
                        expect(ids.some((id) => id.endsWith("-text"))).toBe(true);
                    },
                },
            ],
        });
    });

    it("omits the course sub-layer when course is false", () => {
        testLayer({
            Layer: IconElementLayer,
            testCases: [
                {
                    title: "course false",
                    props: {
                        id: "milicon-no-course",
                        data: [MOVING],
                        course: false,
                        ...baseAccessors,
                    },
                    onAfterUpdate: ({ subLayers }) => {
                        const ids = subLayers.map((l) => l.id);
                        expect(ids.some((id) => id.endsWith("-courses"))).toBe(false);
                    },
                },
            ],
        });
    });

    it("keeps the labels sub-layer mounted and forwards text=false", () => {
        testLayer({
            Layer: IconElementLayer,
            testCases: [
                {
                    title: "labeled false",
                    props: {
                        id: "milicon-no-text",
                        data: [STATIC],
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
    // The layer draws whatever `getIcon` returns and never inspects a code, so
    // the scheme is the caller's business. This asserts that pass-through
    // directly: an arbitrary icon, from no scheme at all, reaches the icon data
    // verbatim.
    it("draws whatever getIcon returns, without inspecting the code", () => {
        const arbitrary = {
            url: "http://localhost/api/v1/layers/abc/media/def",
            width: 32,
            height: 48,
            id: "some-caller-supplied-id",
        };

        testLayer({
            Layer: IconElementLayer,
            testCases: [
                {
                    title: "icon pass-through",
                    props: {
                        id: "icon-passthrough",
                        data: [MEDIA],
                        ...baseAccessors,
                        getIcon: () => arbitrary,
                    },
                    onAfterUpdate: ({ layer }) => {
                        expect(layer.state.icons.data[0]).toMatchObject({
                            ...arbitrary,
                            size: MEDIA.size,
                        });
                    },
                },
            ],
        });
    });

    it("stores the icon size on each icon entry", () => {
        testLayer({
            Layer: IconElementLayer,
            testCases: [
                {
                    title: "size pass-through",
                    props: { id: "milicon-size", data: [STATIC], ...baseAccessors },
                    onAfterUpdate: ({ layer }) => {
                        expect(layer.state.icons.data[0].size).toBe(STATIC.size);
                    },
                },
            ],
        });
    });

    it("stores course angle as negative degrees", () => {
        testLayer({
            Layer: IconElementLayer,
            testCases: [
                {
                    title: "course angle",
                    props: { id: "milicon-course-angle", data: [MOVING], ...baseAccessors },
                    onAfterUpdate: ({ layer }) => {
                        expect(layer.state.courses.data[0].angle).toBe(-MOVING.course!);
                    },
                },
            ],
        });
    });

    it("produces finite course offsets", () => {
        testLayer({
            Layer: IconElementLayer,
            testCases: [
                {
                    title: "course offset finite",
                    props: { id: "milicon-course-offset", data: [MOVING], ...baseAccessors },
                    onAfterUpdate: ({ layer }) => {
                        const [x, y] = layer.state.courses.data[0].offset;
                        expect(Number.isFinite(x)).toBe(true);
                        expect(Number.isFinite(y)).toBe(true);
                    },
                },
            ],
        });
    });

    it("emits a transparent course entry when the datum has no course", () => {
        testLayer({
            Layer: IconElementLayer,
            testCases: [
                {
                    title: "no-course → transparent arrow",
                    props: {
                        id: "milicon-no-course",
                        data: [STATIC],
                        ...baseAccessors,
                        getArrowColor: [200, 200, 200, 255],
                    },
                    onAfterUpdate: ({ layer }) => {
                        expect(layer.state.courses.data).toHaveLength(1);
                        expect(layer.state.courses.data[0].color[3]).toBe(0);
                        // Angle still resolves to a finite value (0 by default).
                        expect(layer.state.courses.data[0].angle).toBe(-0);
                    },
                },
            ],
        });
    });
});

describe("invalidation / partial updates", () => {
    it("appends new entries when data grows via _dataDiff", () => {
        testLayer({
            Layer: IconElementLayer,
            testCases: [
                {
                    title: "initial single element",
                    props: { id: "milicon-grow", data: [STATIC], ...baseAccessors },
                    onAfterUpdate: ({ layer }) => {
                        expect(layer.state.icons.data).toHaveLength(1);
                        // Course buffer is 1:1 with icons even for course-less data.
                        expect(layer.state.courses.data).toHaveLength(1);
                    },
                },
                {
                    title: "append moving element",
                    updateProps: {
                        data: [STATIC, MOVING],
                        _dataDiff: () => [{ startRow: 1, endRow: 2 }],
                    },
                    onAfterUpdate: ({ layer }) => {
                        expect(layer.state.icons.data).toHaveLength(2);
                        expect(layer.state.courses.data).toHaveLength(2);
                    },
                },
            ],
        });
    });

    it("updates an existing entry via _dataDiff", () => {
        const RESIZED: IconDatum = { ...STATIC, size: 128 };
        testLayer({
            Layer: IconElementLayer,
            testCases: [
                {
                    title: "initial two elements",
                    props: {
                        id: "milicon-update",
                        data: [STATIC, MOVING],
                        ...baseAccessors,
                    },
                    onAfterUpdate: ({ layer }) => {
                        expect(layer.state.icons.data[0].size).toBe(STATIC.size);
                    },
                },
                {
                    title: "update first element's size",
                    updateProps: {
                        data: [RESIZED, MOVING],
                        _dataDiff: () => [{ startRow: 0, endRow: 1 }],
                    },
                    onAfterUpdate: ({ layer }) => {
                        expect(layer.state.icons.data).toHaveLength(2);
                        expect(layer.state.icons.data[0].size).toBe(128);
                        // Row 1 untouched.
                        expect(layer.state.icons.data[1].size).toBe(MOVING.size);
                    },
                },
            ],
        });
    });

    it("rebuilds course entry when course value changes via _dataDiff", () => {
        const TURNED: IconDatum = { ...MOVING, course: 180 };
        testLayer({
            Layer: IconElementLayer,
            testCases: [
                {
                    title: "initial moving element",
                    props: {
                        id: "milicon-course-update",
                        data: [MOVING],
                        ...baseAccessors,
                    },
                    onAfterUpdate: ({ layer }) => {
                        expect(layer.state.courses.data[0].angle).toBe(-MOVING.course!);
                    },
                },
                {
                    title: "update course",
                    updateProps: {
                        data: [TURNED],
                        _dataDiff: () => [{ startRow: 0, endRow: 1 }],
                    },
                    onAfterUpdate: ({ layer }) => {
                        expect(layer.state.courses.data[0].angle).toBe(-TURNED.course!);
                    },
                },
            ],
        });
    });

    it("deletes an entry via _dataDiff", () => {
        testLayer({
            Layer: IconElementLayer,
            testCases: [
                {
                    title: "initial two elements",
                    props: {
                        id: "milicon-delete",
                        data: [STATIC, MOVING],
                        ...baseAccessors,
                    },
                    onAfterUpdate: ({ layer }) => {
                        expect(layer.state.icons.data).toHaveLength(2);
                        expect(layer.state.courses.data).toHaveLength(2);
                    },
                },
                {
                    title: "delete moving element",
                    updateProps: {
                        data: [STATIC],
                        _dataDiff: () => [{ startRow: 1, endRow: 2 }],
                    },
                    onAfterUpdate: ({ layer }) => {
                        expect(layer.state.icons.data).toHaveLength(1);
                        expect(layer.state.courses.data).toHaveLength(1);
                    },
                },
            ],
        });
    });

    it("handles multiple disjoint _dataDiff ranges", () => {
        const A: IconDatum = { position: [0, 0], sidc: STATIC.sidc, size: 16 };
        const B: IconDatum = { position: [1, 1], sidc: STATIC.sidc, size: 32 };
        const C: IconDatum = { position: [2, 2], sidc: STATIC.sidc, size: 48 };
        const A2: IconDatum = { ...A, size: 100 };
        const C2: IconDatum = { ...C, size: 200 };
        testLayer({
            Layer: IconElementLayer,
            testCases: [
                {
                    title: "initial three elements",
                    props: {
                        id: "milicon-multi-diff",
                        data: [A, B, C],
                        ...baseAccessors,
                    },
                    onAfterUpdate: ({ layer }) => {
                        expect(layer.state.icons.data).toHaveLength(3);
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
                        expect(layer.state.icons.data[0].size).toBe(100);
                        expect(layer.state.icons.data[1].size).toBe(B.size);
                        expect(layer.state.icons.data[2].size).toBe(200);
                    },
                },
            ],
        });
    });
});
