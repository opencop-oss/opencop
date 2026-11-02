// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { summarizeCircle, summarizeLine, summarizePolygon } from "./summary";

/** One degree of longitude apart on the equator: about 111 km. */
const path = (...pairs: [number, number][]) => ({ getAllPos: () => pairs.flat() });

describe("summarizeCircle", () => {
    const circle = { getRadius: () => 10_000 };

    it("returns [area, circumference]", () => {
        expect(summarizeCircle(circle, "METRIC")).toEqual(["314.16km²", "62.83km"]);
    });

    it("reads in the operator's unit system", () => {
        expect(summarizeCircle(circle, "IMPERIAL")).toEqual(["121.30mi²", "39.04mi"]);
    });
});

describe("summarizeLine", () => {
    it("returns [length] for a path", () => {
        expect(summarizeLine(path([0, 0], [1, 0]), "METRIC")).toEqual(["111.20km"]);
    });

    it("returns null with fewer than two positions", () => {
        expect(summarizeLine(path([0, 0]), "METRIC")).toBeNull();
    });
});

describe("summarizePolygon", () => {
    const triangle = { ...path([0, 0], [1, 0], [0.5, 1]), getArea: () => 6_000_000_000 };

    it("returns [area, perimeter], closing the ring for the perimeter", () => {
        const [area, perimeter] = summarizePolygon(triangle, "METRIC")!;
        expect(area).toBe("6000.00km²");
        expect(perimeter).toMatch(/km$/);
        expect(parseFloat(perimeter)).toBeCloseTo(359.8, 0);
    });

    it("returns null with fewer than three positions", () => {
        expect(
            summarizePolygon({ ...path([0, 0], [1, 0]), getArea: () => 0 }, "METRIC"),
        ).toBeNull();
    });
});
