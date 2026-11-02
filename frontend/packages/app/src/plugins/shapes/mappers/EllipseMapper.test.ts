// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { BBOX_STEPS, Ellipse, ShapeStyle, StrokePattern } from "@opencop/elements";
import type { Primitive } from "@opencop/scene";
import * as turf from "@turf/turf";
import { EllipseMapper } from "./EllipseMapper";

const mapper = new EllipseMapper();
const ORIGIN: [number, number] = [10, 20];

function ellipse(major: number, minor: number, bearing = 0, strokePattern = StrokePattern.Dashed) {
    return Ellipse.create({
        origin: ORIGIN,
        major,
        minor,
        bearing,
        style: new ShapeStyle({
            fillColor: "#11223344",
            strokeColor: "#aabbccdd",
            strokeWeight: 3,
            strokePattern,
        }),
        meta: { displayName: "Oval" },
    });
}

function vertices(primitive: Primitive): [number, number][] {
    const { positions } = primitive.geometry;
    const out: [number, number][] = [];
    for (let i = 0; i < positions.length; i += 3) out.push([positions[i], positions[i + 1]]);
    return out;
}

/** Bearing of the major axis, in [0, 180). */
function majorAxis(primitive: Primitive): number {
    const distance = (v: [number, number]) => turf.distance(ORIGIN, v, { units: "meters" });
    const tip = vertices(primitive).reduce((a, b) => (distance(b) > distance(a) ? b : a));
    return (turf.bearing(ORIGIN, tip) + 360) % 180;
}

describe("EllipseMapper", () => {
    it("draws an outline, a fill, and a label at the centre", () => {
        const [outline, fill, label, ...rest] = mapper.map(ellipse(1000, 500));

        expect(outline).toMatchObject({
            id: "path",
            geometry: { type: "path" },
            style: {
                type: "path",
                strokeColor: [170, 187, 204, 221],
                strokeWidth: 3,
                strokeDash: [4, 4],
            },
        });
        expect(fill).toMatchObject({
            id: "polygon",
            geometry: { type: "polygon", positions: outline.geometry.positions },
            style: { type: "polygon", fillColor: [17, 34, 51, 68] },
        });
        expect(label).toMatchObject({
            id: "label",
            geometry: { type: "point", positions: [...ORIGIN, 0] },
            style: { type: "text", text: "Oval" },
        });
        expect(rest).toEqual([]);
    });

    it.each([
        ["solid", StrokePattern.Solid, [0, 0]],
        ["outlined", StrokePattern.Outlined, [0, 0]],
        ["dotted", StrokePattern.Dotted, [2, 2]],
    ])("dashes a %s outline as %j", (_, pattern, dash) => {
        expect(mapper.map(ellipse(1000, 500, 0, pattern))[0].style).toMatchObject({
            strokeDash: dash,
        });
    });

    it("tessellates a closed ring between the two semi-axes", () => {
        const ring = vertices(mapper.map(ellipse(1000, 500))[0]);
        const distances = ring.map((v) => turf.distance(ORIGIN, v, { units: "meters" }));

        expect(ring).toHaveLength(BBOX_STEPS + 1);
        expect(ring.at(-1)).toEqual(ring[0]);
        expect(Math.max(...distances)).toBeCloseTo(1000, 3);
        expect(Math.min(...distances)).toBeCloseTo(500, 3);
    });

    it("runs the major axis north-south at bearing 0 and east-west at bearing 90", () => {
        expect(majorAxis(mapper.map(ellipse(1000, 100, 0))[0])).toBeCloseTo(0, 1);
        expect(majorAxis(mapper.map(ellipse(1000, 100, 90))[0])).toBeCloseTo(90, 1);
    });

    it("runs the major axis along an oblique bearing, clockwise from north", () => {
        expect(majorAxis(mapper.map(ellipse(1000, 100, 30))[0])).toBeCloseTo(30, 1);
    });

    it("draws nothing for a zero axis", () => {
        expect(mapper.map(ellipse(0, 500))).toEqual([]);
        expect(mapper.map(ellipse(1000, 0))).toEqual([]);
    });
});
