// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import {
    computeActivityHoverMarkers,
    computeSourceActiveEventUpdates,
} from "./histogram.layout.ts";
import type { HistogramSourceSeries } from "./histogram.types.ts";
import {
    AXIS_TOP_PAD,
    pixelXToTimestamp,
    RIGHT_AXIS_WIDTH,
    timestampToPixelX,
    type BarLayout,
} from "./histogram.util.ts";

const AXIS_Y = 100;

/** Two bars 20px wide at x=10 and x=40 (so midpoints would be 20 and 50). */
const BARS: BarLayout[] = [
    { x: 10, y: 0, width: 20, height: 0, index: 0 },
    { x: 40, y: 0, width: 20, height: 0, index: 1 },
];

function source(activity: number[]): HistogramSourceSeries {
    return {
        sourceId: "alpha",
        name: "Alpha",
        color: "#2ee6d6",
        shade: "#444",
        active: activity.map(() => 0),
        activity,
    };
}

/** Parse "x,y x,y ..." into [[x,y], ...]. */
function parsePoints(points: string): [number, number][] {
    return points
        .trim()
        .split(" ")
        .map((p) => p.split(",").map(Number) as [number, number]);
}

describe("computeSourceActiveEventUpdates", () => {
    it("anchors each bucket point at its left edge, not its midpoint", () => {
        const lines = computeSourceActiveEventUpdates({
            showActiveEventUpdates: true,
            sourceSeries: [source([0, 50])],
            bars: BARS,
            axisY: AXIS_Y,
            max: 50,
        });
        expect(lines).not.toBeNull();
        const pts = parsePoints(lines![0].points);
        // Points: [bar0, bar1, rightEdge]. The bucket vertices sit at bar.x —
        // 10 and 40 — NOT the midpoints (20, 50). A trailing point extends the
        // line to the last bucket's right edge (40 + 20 = 60).
        expect(pts).toHaveLength(3);
        expect(pts[0][0]).toBe(10);
        expect(pts[1][0]).toBe(40);
        expect(pts[2][0]).toBe(60);
        expect(pts.map(([x]) => x)).not.toContain(20);
        expect(pts.map(([x]) => x)).not.toContain(50);
    });

    it("uses the full chart height (no fractional cap): max maps to the top headroom", () => {
        const lines = computeSourceActiveEventUpdates({
            showActiveEventUpdates: true,
            sourceSeries: [source([0, 50])],
            bars: BARS,
            axisY: AXIS_Y,
            max: 50,
        });
        const pts = parsePoints(lines![0].points);
        // activity[1] === max === 50 → top of the chart, less the AXIS_TOP_PAD
        // headroom. With the old 0.5 cap this would have been y=50; the uncapped
        // scale puts it at the top band (AXIS_TOP_PAD), not the lower half.
        expect(pts[1][1]).toBe(AXIS_TOP_PAD);
        // activity[0] === 0 → baseline.
        expect(pts[0][1]).toBe(AXIS_Y);
    });

    it("returns null when there is no positive activity", () => {
        expect(
            computeSourceActiveEventUpdates({
                showActiveEventUpdates: true,
                sourceSeries: [source([0, 0])],
                bars: BARS,
                axisY: AXIS_Y,
                max: 0,
            }),
        ).toBeNull();
    });

    it("returns null when the overlay is hidden", () => {
        expect(
            computeSourceActiveEventUpdates({
                showActiveEventUpdates: false,
                sourceSeries: [source([10, 50])],
                bars: BARS,
                axisY: AXIS_Y,
                max: 50,
            }),
        ).toBeNull();
    });
});

describe("computeActivityHoverMarkers", () => {
    it("marks the hovered bucket at its left-edge x and full-height y per source", () => {
        const markers = computeActivityHoverMarkers({
            hoveredBucketIndex: 1,
            sourceSeries: [source([0, 50])],
            bars: BARS,
            axisY: AXIS_Y,
            max: 50,
        });
        expect(markers).toHaveLength(1);
        expect(markers![0]).toMatchObject({
            sourceId: "alpha",
            color: "#2ee6d6",
            pointX: 40, // bar.x of the hovered bucket
            y: AXIS_TOP_PAD, // value === max → top of the chart, less the headroom
            value: 50,
        });
    });

    it("returns null when nothing is hovered", () => {
        expect(
            computeActivityHoverMarkers({
                hoveredBucketIndex: null,
                sourceSeries: [source([10, 50])],
                bars: BARS,
                axisY: AXIS_Y,
                max: 50,
            }),
        ).toBeNull();
    });
});

describe("rightInset pixel mapping", () => {
    const RANGE_START = "2026-01-01T00:00:00.000Z";
    const RANGE_END = "2026-01-01T12:00:00.000Z";
    const MID = "2026-01-01T06:00:00.000Z";
    const WIDTH = 800;

    it("shifts a timestamp left when a right gutter is reserved", () => {
        const noInset = timestampToPixelX(MID, RANGE_START, RANGE_END, WIDTH, 0);
        const withInset = timestampToPixelX(MID, RANGE_START, RANGE_END, WIDTH, RIGHT_AXIS_WIDTH);
        // Reserving the right gutter narrows the plot, so the same instant maps to
        // a smaller x than it does across the full width — this is what keeps the
        // playhead, brush, and bars sharing one plot edge.
        expect(withInset).toBeLessThan(noInset);
    });

    it("round-trips through pixelXToTimestamp with the same rightInset", () => {
        const x = timestampToPixelX(MID, RANGE_START, RANGE_END, WIDTH, RIGHT_AXIS_WIDTH);
        const iso = pixelXToTimestamp(x, RANGE_START, RANGE_END, WIDTH, RIGHT_AXIS_WIDTH);
        expect(iso).toBe(MID);
    });
});
