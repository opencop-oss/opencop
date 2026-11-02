// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { BBOX_STEPS, Circle, ShapeStyle, StrokePattern } from "@opencop/elements";
import { MEASUREMENT_COLOR, type Primitive } from "@opencop/scene";
import * as turf from "@turf/turf";
import type { MapperState } from "@opencop/contributions";
import { CircleMapper } from "./CircleMapper";

const mapper = new CircleMapper();
const ORIGIN: [number, number] = [10, 20];

function circle(radius: number, strokePattern = StrokePattern.Dashed) {
    return Circle.create({
        origin: ORIGIN,
        radius,
        style: new ShapeStyle({
            fillColor: "#11223344",
            strokeColor: "#aabbccdd",
            strokeWeight: 3,
            strokePattern,
        }),
        meta: { displayName: "Ring" },
    });
}

function vertices(primitive: Primitive): [number, number][] {
    const { positions } = primitive.geometry;
    const out: [number, number][] = [];
    for (let i = 0; i < positions.length; i += 3) out.push([positions[i], positions[i + 1]]);
    return out;
}

const readouts = (primitives: Primitive[]) =>
    primitives.filter((p) => p.id.startsWith("measurement-"));

describe("CircleMapper", () => {
    it("draws an outline, a fill, a label, and four radius readouts", () => {
        expect(mapper.map(circle(1000), "METRIC").map((p) => p.id)).toEqual([
            "path",
            "polygon",
            "label",
            "measurement-0",
            "measurement-1",
            "measurement-2",
            "measurement-3",
        ]);
    });

    it("tessellates a closed ring at the radius, shared by outline and fill", () => {
        const [outline, fill] = mapper.map(circle(1000), "METRIC");
        const ring = vertices(outline);

        expect(ring).toHaveLength(BBOX_STEPS + 1);
        expect(ring.at(-1)).toEqual(ring[0]);
        for (const vertex of ring)
            expect(turf.distance(ORIGIN, vertex, { units: "meters" })).toBeCloseTo(1000, 3);
        expect(outline.geometry.positions.filter((_, i) => i % 3 === 2).every((z) => z === 0)).toBe(
            true,
        );
        expect(fill.geometry.positions).toEqual(outline.geometry.positions);
    });

    it("styles the outline and fill from the shape style", () => {
        const [outline, fill] = mapper.map(circle(1000), "METRIC");

        expect(outline.style).toEqual({
            type: "path",
            strokeColor: [170, 187, 204, 221],
            strokeWidth: 3,
            strokeDash: [4, 4],
        });
        expect(fill.style).toEqual({ type: "polygon", fillColor: [17, 34, 51, 68] });
    });

    it.each([
        ["solid", StrokePattern.Solid, [0, 0]],
        ["outlined", StrokePattern.Outlined, [0, 0]],
        ["dotted", StrokePattern.Dotted, [2, 2]],
    ])("dashes a %s outline as %j", (_, pattern, dash) => {
        expect(mapper.map(circle(1000, pattern), "METRIC")[0].style).toMatchObject({
            strokeDash: dash,
        });
    });

    it("labels the centre with the name", () => {
        expect(mapper.map(circle(1000), "METRIC")[2]).toMatchObject({
            geometry: { type: "point", positions: [...ORIGIN, 0] },
            style: { type: "text", text: "Ring" },
        });
    });

    it("writes the radius in the selected unit system, tangent to the ring", () => {
        const metric = readouts(mapper.map(circle(1000), "METRIC"));
        const imperial = readouts(mapper.map(circle(1000), "IMPERIAL"));

        expect(metric.map((p) => p.style)).toEqual(
            [45, 135, 225, 315].map((angle) => ({
                type: "text",
                text: "1.00km",
                angle,
                color: MEASUREMENT_COLOR,
                size: expect.any(Number),
                billboard: false,
            })),
        );
        expect(imperial.map((p) => p.style)).toEqual(
            metric.map((p) => expect.objectContaining({ ...p.style, text: "3280.84ft" })),
        );
    });

    it("places the radius readouts on the ring", () => {
        for (const readout of readouts(mapper.map(circle(1000), "METRIC")))
            expect(turf.distance(ORIGIN, vertices(readout)[0], { units: "meters" })).toBeCloseTo(
                1000,
                3,
            );
    });

    it("places the radius readouts on the diagonals", () => {
        const bearings = readouts(mapper.map(circle(1000), "METRIC")).map((p) =>
            turf.bearing(ORIGIN, vertices(p)[0]),
        );

        [-45, -135, 135, 45].forEach((expected, i) => expect(bearings[i]).toBeCloseTo(expected, 0));
    });

    it("collapses a zero radius onto the centre", () => {
        const primitives = mapper.map(circle(0), "METRIC");

        expect(new Set(vertices(primitives[0]).map(String))).toEqual(new Set([String(ORIGIN)]));
        expect(readouts(primitives).map((p) => p.style)).toEqual(
            Array(4).fill(expect.objectContaining({ text: "0.00m" })),
        );
    });

    it("selects the measurement system", () => {
        const state = { units: { measurement: "IMPERIAL" } } as MapperState;

        expect(mapper.selector.select(state)).toBe("IMPERIAL");
    });
});
