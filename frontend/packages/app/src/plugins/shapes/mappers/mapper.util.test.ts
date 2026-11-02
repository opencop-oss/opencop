// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import * as turf from "@turf/turf";
import { angleBetween, vector } from "./mapper.util";

const ORIGIN = [0, 0] as const;
const toward = (bearing: number) =>
    turf.destination([...ORIGIN], 100, bearing).geometry.coordinates as [number, number];

describe("vector", () => {
    it("projects lng/lat to web mercator metres, north up", () => {
        expect(vector(0, 0).x).toBeCloseTo(0);
        expect(vector(0, 0).y).toBeCloseTo(0);
        expect(vector(180, 0).x).toBeCloseTo(20037508.34, 1);
        expect(vector(0, 45).y).toBeCloseTo(5621521.49, 1);
        expect(vector(-90, -45).toArray()).toEqual(turf.toMercator([-90, -45]));
    });
});

describe("angleBetween", () => {
    const east = toward(90);
    const north = toward(0);

    it("measures clockwise or counter-clockwise from the first arm to the second", () => {
        expect(angleBetween(ORIGIN, east, north, "cw")).toBeCloseTo(270);
        expect(angleBetween(ORIGIN, east, north, "ccw")).toBeCloseTo(90);
        expect(angleBetween(ORIGIN, north, east, "cw")).toBeCloseTo(90);
        expect(angleBetween(ORIGIN, north, east, "ccw")).toBeCloseTo(270);
    });

    it("wraps across north and south", () => {
        expect(angleBetween(ORIGIN, toward(-10), toward(10), "cw")).toBeCloseTo(20);
        expect(angleBetween(ORIGIN, toward(-10), toward(10), "ccw")).toBeCloseTo(340);
        expect(angleBetween(ORIGIN, toward(170), toward(-170), "cw")).toBeCloseTo(20);
        expect(angleBetween(ORIGIN, toward(170), toward(-170), "ccw")).toBeCloseTo(340);
    });

    it("is zero both ways for coincident arms", () => {
        expect(angleBetween(ORIGIN, east, east, "cw")).toBe(0);
        expect(angleBetween(ORIGIN, east, east, "ccw")).toBe(0);
    });
});
