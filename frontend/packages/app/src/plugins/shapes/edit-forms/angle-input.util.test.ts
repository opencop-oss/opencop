// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { Angle, type Position2D } from "@opencop/elements";
import * as turf from "@turf/turf";
import { bearingForAngle, compassBearing, normalizeBearing } from "./angle-input.util";

describe("normalizeBearing", () => {
    it.each([
        [0, 0],
        [180, -180],
        [-180, -180],
        [360, 0],
        [270, -90],
        [-90, -90],
        [450, 90],
        [-270, 90],
        [720.5, 0.5],
    ])("maps %d° into [-180, 180) → %d°", (input, expected) => {
        expect(normalizeBearing(input)).toBeCloseTo(expected, 6);
    });
});

describe("compassBearing", () => {
    const origin: Position2D = [0, 0];

    it("returns 0 for due north", () => {
        expect(compassBearing(origin, [0, 1])).toBeCloseTo(0, 4);
    });

    it("returns ~90 for due east", () => {
        expect(compassBearing(origin, [1, 0])).toBeCloseTo(90, 4);
    });

    it("wraps due west to ~270, never negative", () => {
        const bearing = compassBearing(origin, [-1, 0]);
        expect(bearing).toBeCloseTo(270, 4);
        expect(bearing).toBeGreaterThanOrEqual(0);
    });
});

describe("bearingForAngle ↔ Angle.getDirectionalAngle round trip", () => {
    const vertex: Position2D = [10, 45];
    const armLengthKm = 5;

    // Arm 0 fixed, pointing roughly NNE from the vertex.
    const arm0 = turf.destination(vertex, armLengthKm, 30, { units: "kilometers" }).geometry
        .coordinates as Position2D;
    const arm0Bearing = turf.bearing(vertex, arm0);

    const targets = [0, 15, 45, 90, 135, 179, 180, 210, 300, 350];

    it.each(["cw", "ccw"] as const)(
        "placing arm 1 at bearingForAngle(...) reproduces the target angle (%s)",
        (orientation) => {
            for (const target of targets) {
                const arm1Bearing = bearingForAngle(arm0Bearing, target, orientation);
                const arm1 = turf.destination(vertex, armLengthKm, arm1Bearing, {
                    units: "kilometers",
                }).geometry.coordinates as Position2D;

                const el = Angle.create({ first: arm0, second: vertex, third: arm1 });
                const read = Angle.getDirectionalAngle(el, orientation);
                // 0 and 360 are the same sweep, and `turf.destination` -> `turf.bearing` is not an
                // exact inverse — a collinear pair reads back as 359.99999999999505 under `cw`.
                // Compare on the circle, not the number line.
                const delta = Math.min(Math.abs(read - target), 360 - Math.abs(read - target));
                expect(delta).toBeCloseTo(0, 2);
            }
        },
    );

    it("cw and ccw solutions for the same target are mirror images about arm 0", () => {
        const cw = bearingForAngle(arm0Bearing, 70, "cw");
        const ccw = bearingForAngle(arm0Bearing, 70, "ccw");
        // cw sweeps +70 from arm 0, ccw sweeps -70 → the two land 140° apart.
        expect(normalizeBearing(cw - ccw)).toBeCloseTo(140, 6);
    });
});
