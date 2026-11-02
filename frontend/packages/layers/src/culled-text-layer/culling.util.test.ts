// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import {
    areBoundsContained,
    computeArea,
    getAlwaysVisible,
    padBounds,
    wrapLongitude,
    type Bounds,
} from "./culling.util";

describe("wrapLongitude", () => {
    it("passes through in-range longitudes", () => {
        expect(wrapLongitude(0)).toBe(0);
        expect(wrapLongitude(179)).toBe(179);
        expect(wrapLongitude(-179)).toBe(-179);
    });

    it("wraps out-of-range longitudes into [-180, 180)", () => {
        expect(wrapLongitude(190)).toBe(-170);
        expect(wrapLongitude(-190)).toBe(170);
        expect(wrapLongitude(360)).toBe(0);
        expect(wrapLongitude(540)).toBe(-180);
    });
});

describe("areBoundsContained", () => {
    const OUTER: Bounds = [-10, -10, 10, 10];

    it("accepts bounds inside (or equal to) the container", () => {
        expect(areBoundsContained([-5, -5, 5, 5], OUTER)).toBe(true);
        expect(areBoundsContained(OUTER, OUTER)).toBe(true);
    });

    it("rejects bounds extending past any edge", () => {
        expect(areBoundsContained([-15, -5, 5, 5], OUTER)).toBe(false);
        expect(areBoundsContained([-5, -5, 15, 5], OUTER)).toBe(false);
        expect(areBoundsContained([-5, -15, 5, 5], OUTER)).toBe(false);
        expect(areBoundsContained([-5, -5, 5, 15], OUTER)).toBe(false);
    });
});

describe("padBounds", () => {
    const BOUNDS: Bounds = [-1, -1, 1, 1];

    it("is the identity for factor 0", () => {
        const padded = padBounds(BOUNDS, 0);
        padded.forEach((v, i) => expect(v).toBeCloseTo(BOUNDS[i], 6));
    });

    it("expands bounds on every side", () => {
        const [minLng, minLat, maxLng, maxLat] = padBounds(BOUNDS, 0.5);
        expect(minLng).toBeLessThan(BOUNDS[0]);
        expect(minLat).toBeLessThan(BOUNDS[1]);
        expect(maxLng).toBeGreaterThan(BOUNDS[2]);
        expect(maxLat).toBeGreaterThan(BOUNDS[3]);
        expect(areBoundsContained(BOUNDS, [minLng, minLat, maxLng, maxLat])).toBe(true);
    });

    it("expands by the factor per side (identity projection under the maplibre mock)", () => {
        const [minLng, , maxLng] = padBounds(BOUNDS, 0.5);
        // A 2°-wide box padded by 0.5 gains 1° on each side.
        expect(minLng).toBeCloseTo(-2, 6);
        expect(maxLng).toBeCloseTo(2, 6);
    });
});

describe("computeArea", () => {
    it("scales with bounds size", () => {
        const small = computeArea([0, 0, 1, 1]);
        const large = computeArea([0, 0, 2, 2]);
        expect(small).toBeGreaterThan(0);
        // Identity projection under the maplibre mock: 2x the sides = 4x the area.
        expect(large / small).toBeCloseTo(4, 1);
    });

    it("is zero for empty bounds", () => {
        expect(computeArea([5, 5, 5, 5])).toBe(0);
    });
});

describe("getAlwaysVisible", () => {
    const sources = [{ id: "a" }, { id: "b" }];
    const derived = sources.map((sourceObject, index) => ({ index, sourceObject }));

    it("maps source objects to their derived rows", () => {
        expect(getAlwaysVisible([sources[1]], derived)).toEqual([derived[1]]);
        expect(getAlwaysVisible(sources, derived)).toEqual(derived);
    });

    it("maps a store element to a derived row built from its live dataset clone", () => {
        const storeElement = { key: "source\0alpha" };
        const datasetClone = { key: "source\0alpha" };
        const derivedClone = { index: 0, sourceObject: datasetClone };

        expect(getAlwaysVisible([storeElement], [derivedClone], (element) => element.key)).toEqual([
            derivedClone,
        ]);
    });

    it("returns an empty array when no derived row matches", () => {
        expect(getAlwaysVisible([{ id: "c" }], derived)).toEqual([]);
    });

    it("returns undefined when alwaysVisible is absent", () => {
        expect(getAlwaysVisible(undefined, derived)).toBeUndefined();
    });
});
