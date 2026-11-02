// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { Arrow, ArrowStyle, StrokePattern } from "@opencop/elements";
import { MEASUREMENT_COLOR, type Primitive } from "@opencop/scene";
import type { MapperState } from "@opencop/contributions";
import { ArrowMapper, type ArrowState } from "./ArrowMapper";

const mapper = new ArrowMapper();
const METRIC_DEGREES: ArrowState = { unit: "degrees", system: "METRIC" };
const wrap = (degrees: number) => ((degrees % 360) + 360) % 360;

function arrow(start: [number, number], end: [number, number], pattern = StrokePattern.Dashed) {
    return Arrow.create({
        start,
        end,
        style: new ArrowStyle({ color: "#ff000080", weight: 4, pattern }),
        meta: { displayName: "Push" },
    });
}

const byId = (primitives: Primitive[], id: string) => primitives.find((p) => p.id === id)!;
const textAngle = (primitive: Primitive) => (primitive.style as { angle: number }).angle;

describe("ArrowMapper", () => {
    const east = mapper.map(arrow([0, 0], [1, 0]), METRIC_DEGREES);

    it("draws a shaft, a head, length and bearing readouts, and a label", () => {
        expect(east.map((p) => p.id)).toEqual(["shaft", "head", "length", "bearing", "label"]);
    });

    it("draws the shaft from start to end in the style's colour, weight and dash", () => {
        expect(byId(east, "shaft")).toMatchObject({
            geometry: { type: "path", positions: [0, 0, 0, 1, 0, 0] },
            style: {
                type: "path",
                strokeColor: [255, 0, 0, 128],
                strokeWidth: 4,
                strokeDash: [4, 4],
            },
        });
    });

    it.each([
        ["solid", StrokePattern.Solid, [0, 0]],
        ["outlined", StrokePattern.Outlined, [0, 0]],
        ["dotted", StrokePattern.Dotted, [2, 2]],
    ])("dashes a %s shaft as %j", (_, pattern, dash) => {
        const primitives = mapper.map(arrow([0, 0], [1, 0], pattern), METRIC_DEGREES);

        expect(byId(primitives, "shaft").style).toMatchObject({ strokeDash: dash });
    });

    it("tips the end with a flat, masked head tinted the stroke colour", () => {
        expect(byId(east, "head")).toMatchObject({
            geometry: { type: "point", positions: [1, 0, 0] },
            style: {
                type: "icon",
                id: "arrow-head",
                source: expect.stringMatching(/^data:image\/svg\+xml/),
                width: 32,
                height: 32,
                size: 25,
                billboard: false,
                mask: true,
                color: [255, 0, 0, 128],
            },
        });
    });

    it("turns the head to point along the shaft", () => {
        const headAngle = (end: [number, number]) =>
            textAngle(byId(mapper.map(arrow([0, 0], end), METRIC_DEGREES), "head"));

        expect(headAngle([0, 1])).toBeCloseTo(0);
        expect(headAngle([1, 0])).toBeCloseTo(-90);
        expect(headAngle([-1, 0])).toBeCloseTo(90);
        expect(wrap(headAngle([0, -1]))).toBeCloseTo(180);
    });

    it("writes the length near the start and the bearing near the end", () => {
        expect(byId(east, "length")).toMatchObject({
            geometry: { type: "point", positions: [expect.closeTo(1 / 6), 0, 0] },
            style: {
                type: "text",
                text: "111.20km",
                anchor: "start",
                color: MEASUREMENT_COLOR,
                billboard: false,
            },
        });
        expect(byId(east, "bearing")).toMatchObject({
            geometry: { type: "point", positions: [expect.closeTo(5 / 6), 0, 0] },
            style: { type: "text", text: "90.00°", anchor: "end", billboard: false },
        });
    });

    it("labels the middle of the shaft with the name, lying flat", () => {
        expect(byId(east, "label")).toMatchObject({
            geometry: { type: "point", positions: [expect.closeTo(0.5), 0, 0] },
            style: { type: "text", text: "Push", billboard: false },
        });
    });

    it("writes the readouts in the selected units", () => {
        const texts = (state: ArrowState) =>
            mapper
                .map(arrow([0, 0], [1, 0]), state)
                .filter((p) => p.id === "length" || p.id === "bearing")
                .map((p) => (p.style as { text: string }).text);

        expect(texts({ unit: "mils", system: "IMPERIAL" })).toEqual(["69.09mi", "1600.00mils"]);
        expect(texts({ unit: "both", system: "METRIC" })).toEqual([
            "111.20km",
            "90.00° | 1600.00mils",
        ]);
    });

    it("runs the text along the shaft, never upside down", () => {
        const angles = (end: [number, number]) =>
            mapper
                .map(arrow([0, 0], end), METRIC_DEGREES)
                .filter((p) => p.style.type === "text")
                .map((p) => wrap(textAngle(p)));

        expect(angles([1, 0])).toEqual([0, 0, 0]);
        expect(angles([-1, 0])).toEqual([0, 0, 0]);
        expect(angles([1, 1])).toEqual(Array(3).fill(expect.closeTo(45, 1)));
        expect(angles([-1, 1])).toEqual(Array(3).fill(expect.closeTo(315, 1)));
    });

    it("draws a zero-length arrow at its start with a zero length", () => {
        const primitives = mapper.map(arrow([3, 4], [3, 4]), METRIC_DEGREES);

        expect(byId(primitives, "shaft").geometry.positions).toEqual([3, 4, 0, 3, 4, 0]);
        expect(byId(primitives, "length").style).toMatchObject({ text: "0.00m" });
        for (const primitive of primitives)
            expect(primitive.geometry.positions.every(Number.isFinite)).toBe(true);
    });

    it("selects the angle unit and measurement system, compared shallowly", () => {
        const state = {
            units: { angle: "mils", angleOrientation: "cw", measurement: "IMPERIAL" },
        } as MapperState;
        const selected = mapper.selector.select(state);

        expect(selected).toEqual({ unit: "mils", system: "IMPERIAL" });
        expect(mapper.selector.equalityFn(selected, mapper.selector.select(state))).toBe(true);
        expect(mapper.selector.equalityFn(selected, METRIC_DEGREES)).toBe(false);
    });
});
