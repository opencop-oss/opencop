// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { Circle, Line, Polygon } from "@opencop/elements";
import {
    buildSegmentLabels,
    buildTotalLabel,
    polygonLabel,
    circleLabel,
    summarizeMeasurement,
} from "./measure.util";
import { MEASURE_PRODUCT_TYPE, type MeasureGeometry, type Measurement } from "./measurement.model";

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

const META = { objectId: "test", source: "test", stale: Infinity } as const;

// A line with two well-separated points (~111km at the equator)
const twoPointLine = Line.create({
    positions: [
        [0, 0],
        [1, 0],
    ],
    meta: META,
});

// A line with three points — two segments
const threePointLine = Line.create({
    positions: [
        [0, 0],
        [1, 0],
        [2, 0],
    ],
    meta: META,
});

// A triangle with a reasonable area
const triangle = Polygon.create({
    positions: [
        [0, 0],
        [1, 0],
        [0.5, 1],
    ],
    meta: META,
});

// A circle of 10 km radius
const circle = Circle.create({ origin: [0, 0], radius: 10_000, meta: META });

// ---------------------------------------------------------------------------

// ---------------------------------------------------------------------------

describe("buildSegmentLabels", () => {
    it("returns one label per segment", () => {
        expect(buildSegmentLabels(twoPointLine, "METRIC")).toHaveLength(1);
        expect(buildSegmentLabels(threePointLine, "METRIC")).toHaveLength(2);
    });

    it("each label has a position, text, and angle", () => {
        const [label] = buildSegmentLabels(twoPointLine, "METRIC");
        expect(label.position).toHaveLength(2);
        expect(label.text).toBeTruthy();
        expect(typeof label.angle).toBe("number");
    });

    it("uses km for METRIC and mi for IMPERIAL", () => {
        const [metric] = buildSegmentLabels(twoPointLine, "METRIC");
        const [imperial] = buildSegmentLabels(twoPointLine, "IMPERIAL");
        expect(metric.text).toMatch(/km/);
        expect(imperial.text).toMatch(/mi/);
    });
});

// ---------------------------------------------------------------------------

describe("buildTotalLabel", () => {
    it("returns a single total label for a 2-point line", () => {
        expect(buildTotalLabel(twoPointLine, "METRIC")).toHaveLength(1);
    });

    it("returns an empty array for a single-point line", () => {
        const onePoint = Line.create({ positions: [[0, 0]], meta: META });
        expect(buildTotalLabel(onePoint, "METRIC")).toHaveLength(0);
    });

    it("the label text is a formatted distance", () => {
        const [label] = buildTotalLabel(twoPointLine, "METRIC");
        expect(label.text).toBeTruthy();
        expect(label.position).toHaveLength(2);
    });

    it("METRIC and IMPERIAL produce different unit labels", () => {
        const [metric] = buildTotalLabel(twoPointLine, "METRIC");
        const [imperial] = buildTotalLabel(twoPointLine, "IMPERIAL");
        expect(metric.text).not.toBe(imperial.text);
    });
});

// ---------------------------------------------------------------------------

describe("polygonLabel", () => {
    it("returns a non-empty string for a valid polygon", () => {
        const label = polygonLabel(triangle, "METRIC");
        expect(label).toBeTruthy();
    });

    it("separates area and perimeter with a newline", () => {
        const label = polygonLabel(triangle, "METRIC");
        expect(label).toContain("\n");
    });

    it("returns an empty string for a degenerate polygon (< 3 vertices)", () => {
        const degenerate = Polygon.create({
            positions: [
                [0, 0],
                [1, 0],
            ],
            meta: META,
        });
        expect(polygonLabel(degenerate, "METRIC")).toBe("");
    });
});

// ---------------------------------------------------------------------------

describe("circleLabel", () => {
    it("returns a non-empty area string", () => {
        expect(circleLabel(10_000, "METRIC")).toBeTruthy();
    });

    it("METRIC and IMPERIAL produce different labels", () => {
        expect(circleLabel(10_000, "METRIC")).not.toBe(circleLabel(10_000, "IMPERIAL"));
    });
});

// ---------------------------------------------------------------------------

function measured<G extends MeasureGeometry>(geometry: G): Measurement<G> {
    return { key: "m", type: MEASURE_PRODUCT_TYPE, geometry };
}

describe("summarizeMeasurement", () => {
    it("dispatches to summarizeCircle for a circle", () => {
        expect(summarizeMeasurement(measured(circle.geometry), "METRIC")).toHaveLength(2);
    });

    it("dispatches to summarizeLine for a line", () => {
        expect(summarizeMeasurement(measured(twoPointLine.geometry), "METRIC")).toHaveLength(1);
    });

    it("dispatches to summarizePolygon for a polygon", () => {
        expect(summarizeMeasurement(measured(triangle.geometry), "METRIC")).toHaveLength(2);
    });
});
