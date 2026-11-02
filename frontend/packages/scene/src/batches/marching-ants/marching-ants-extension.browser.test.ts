// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { testLayer, getLayerUniforms } from "@deck.gl/test-utils/vitest";
import { PathLayer, ScatterplotLayer } from "@deck.gl/layers";
import { Viewport, type Position } from "@deck.gl/core";
import { Timeline } from "@luma.gl/engine";
import { MarchingAntsExtension } from "./MarchingAntsExtension";

interface LassoDatum {
    path: Position[];
}

const PATH: Position[] = [
    [0, 0],
    [1, 0],
    [1, 1],
    [0, 1],
    [0, 0],
];

const TestPathLayer = PathLayer<LassoDatum>;

const baseProps = {
    id: "lasso-overlay",
    data: [{ path: PATH }],
    getPath: (d: LassoDatum) => d.path,
    getColor: [255, 255, 255, 204] as [number, number, number, number],
    getWidth: 2,
    widthUnits: "pixels" as const,
};

describe("MarchingAntsExtension", () => {
    it("pushes the configured dashSize/gapSize to the shader on the very first frame", () => {
        testLayer({
            Layer: TestPathLayer,
            testCases: [
                {
                    title: "mount with non-default sizes",
                    props: {
                        ...baseProps,
                        extensions: [new MarchingAntsExtension({ dashSize: 6, gapSize: 4 })],
                    },
                    onAfterUpdate: ({ layer }) => {
                        const uniforms = getLayerUniforms(layer, "marchingAnts");
                        // Must reflect the configured options immediately — not the shader
                        // module's own fallback defaults (3, 2) — otherwise the first rendered
                        // frame(s) would show the wrong dash proportions until the animation
                        // has advanced past phase 0.
                        expect(uniforms.dashSize).toBe(6);
                        expect(uniforms.gapSize).toBe(4);
                        expect(uniforms.phase).toBe(0);
                    },
                },
            ],
        });
    });

    it("registers a per-segment instanceDistanceOffsets attribute (no getDashArray attribute needed)", () => {
        testLayer({
            Layer: TestPathLayer,
            testCases: [
                {
                    title: "attribute present",
                    props: { ...baseProps, extensions: [new MarchingAntsExtension()] },
                    onAfterUpdate: ({ layer }) => {
                        const am = layer.getAttributeManager();
                        expect(am?.attributes.instanceDistanceOffsets).toBeDefined();
                        expect(am?.attributes.instanceDashArrays).toBeUndefined();
                    },
                },
            ],
        });
    });

    it("computes exact cumulative instanceDistanceOffsets for a unit-square path", () => {
        testLayer({
            Layer: TestPathLayer,
            // Non-geospatial viewport makes `projectPosition` an identity transform (see
            // Viewport#projectFlat), so the accumulated distances match the raw path's
            // Euclidean side lengths exactly instead of depending on Web Mercator math.
            viewport: new Viewport({ width: 100, height: 100 }),
            testCases: [
                {
                    title: "unit square",
                    props: { ...baseProps, extensions: [new MarchingAntsExtension()] },
                    onAfterUpdate: ({ layer }) => {
                        const offsets =
                            layer.getAttributeManager()?.attributes.instanceDistanceOffsets;
                        expect(offsets?.value).toBeTruthy();
                        // 3 unit sides walked so far (0 -> 1 -> 2 -> 3), then the forced-zero
                        // entry for the final (closing) vertex, which starts no segment of its
                        // own.
                        expect(Array.from(offsets!.value!.slice(0, 5))).toEqual([0, 1, 2, 3, 0]);
                    },
                },
            ],
        });
    });

    it("returns no distance offsets for an empty path", () => {
        const extension = new MarchingAntsExtension();
        const offsets = extension.getDistanceOffsets.call(
            { props: { positionFormat: "XY" } } as never,
            [],
        );

        expect(offsets).toEqual([]);
        expect(Object.hasOwn(offsets, "-1")).toBe(false);
    });

    it("advances phase in lockstep with the timeline, wrapping at dashSize+gapSize", () => {
        const timeline = new Timeline();
        // dashSize=3, gapSize=2 -> period=5; speed=10 dash-widths/sec -> 10 units/sec of phase.
        const extension = new MarchingAntsExtension({ dashSize: 3, gapSize: 2, speed: 10 });

        testLayer({
            Layer: TestPathLayer,
            timeline,
            testCases: [
                {
                    title: "mount at t=200ms",
                    onBeforeUpdate: () => timeline.setTime(200),
                    props: { ...baseProps, extensions: [extension] },
                    onAfterUpdate: ({ layer }) => {
                        expect(getLayerUniforms(layer, "marchingAnts").phase).toBe(2);
                    },
                },
                {
                    title: "advance to t=450ms",
                    onBeforeUpdate: () => timeline.setTime(450),
                    props: { ...baseProps, extensions: [extension] },
                    onAfterUpdate: ({ layer }) => {
                        expect(getLayerUniforms(layer, "marchingAnts").phase).toBe(4.5);
                    },
                },
            ],
        });
    });

    it("throws when attached to a layer that isn't a PathLayer", () => {
        const errors: Error[] = [];

        testLayer({
            Layer: ScatterplotLayer,
            onError: (error) => errors.push(error),
            testCases: [
                {
                    title: "mount on ScatterplotLayer",
                    props: {
                        id: "not-a-path-layer",
                        data: [],
                        extensions: [new MarchingAntsExtension()],
                    },
                },
            ],
        });

        // The initialization error is what we're asserting on; testLayer may also surface a
        // secondary cleanup/resource error for the layer that never finished mounting, so filter
        // rather than assert an exact error count.
        const initErrors = errors.filter((error) => /only supports PathLayer/.test(error.message));
        expect(initErrors).toHaveLength(1);
    });
});
