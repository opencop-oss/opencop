// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { Angle, StrokePattern, StrokeStyle } from "@opencop/elements";
import { MEASUREMENT_COLOR, type AngleStyle, type Primitive } from "@opencop/scene";
import type { MapperState } from "@opencop/contributions";
import { AngleMapper, formatAngle, type AngleState } from "./AngleMapper";

const mapper = new AngleMapper();
const CCW: AngleState = { unit: "degrees", orientation: "ccw" };
const CW: AngleState = { unit: "degrees", orientation: "cw" };
const TWO_PI = 2 * Math.PI;
/** Web mercator common units per projected metre: 512 across the world. */
const COMMON_PER_METRE = 512 / (2 * Math.PI * 6378137);

const turn = (rad: number) => ((rad % TWO_PI) + TWO_PI) % TWO_PI;
const half = (degrees: number) => ((degrees % 180) + 180) % 180;

function angle(
    first: [number, number],
    second: [number, number],
    third: [number, number],
    pattern = new StrokePattern([6, 2]),
) {
    return Angle.create({
        first,
        second,
        third,
        style: new StrokeStyle({ color: "#0000ff80", weight: 2, pattern }),
        meta: { displayName: "Bend" },
    });
}

const byId = (primitives: Primitive[], id: string) => primitives.find((p) => p.id === id)!;
const arc = (primitives: Primitive[]) => byId(primitives, "angle").style as AngleStyle;
const texts = (primitives: Primitive[]) =>
    ["text-0", "text-1"].map((id) => byId(primitives, id).style as { text: string; angle: number });

const EAST_NORTH = angle([2, 0], [0, 0], [0, 2]);

describe("AngleMapper", () => {
    it("draws the arms, an arc, a readout on each arm, and a label", () => {
        expect(mapper.map(EAST_NORTH, CCW).map((p) => p.id)).toEqual([
            "arms",
            "angle",
            "text-0",
            "text-1",
            "label",
        ]);
    });

    it("draws the arms through the vertex in the style's colour, weight and dash", () => {
        expect(byId(mapper.map(EAST_NORTH, CCW), "arms")).toMatchObject({
            geometry: { type: "path", positions: [2, 0, 0, 0, 0, 0, 0, 2, 0] },
            style: {
                type: "path",
                strokeColor: [0, 0, 255, 128],
                strokeWidth: 2,
                strokeDash: [6, 2],
            },
        });
    });

    it.each([
        ["solid", StrokePattern.Solid, [0, 0]],
        ["dashed", StrokePattern.Dashed, [4, 4]],
        ["dotted", StrokePattern.Dotted, [2, 2]],
    ])("dashes %s arms as %j", (_, pattern, dash) => {
        const primitives = mapper.map(angle([2, 0], [0, 0], [0, 2], pattern), CCW);

        expect(byId(primitives, "arms").style).toMatchObject({ strokeDash: dash });
    });

    it("centres the arc on the vertex in the stroke colour and weight", () => {
        expect(byId(mapper.map(EAST_NORTH, CCW), "angle")).toMatchObject({
            geometry: { type: "point", positions: [0, 0, 0] },
            style: { type: "angle", strokeColor: [0, 0, 255, 128], strokeWidth: 2 },
        });
    });

    it("sweeps the arc counter-clockwise or clockwise from the first arm", () => {
        const ccw = arc(mapper.map(EAST_NORTH, CCW)).angles;
        const cw = arc(mapper.map(EAST_NORTH, CW)).angles;

        expect(ccw[0]).toBeCloseTo(0);
        expect(ccw[1] - ccw[0]).toBeCloseTo(Math.PI / 2);
        expect(cw[0]).toBeCloseTo(0);
        expect(cw[1] - cw[0]).toBeCloseTo((-3 * Math.PI) / 2);
    });

    it("starts the arc on the first arm and ends it on the second", () => {
        const [start, end] = arc(mapper.map(angle([0, 2], [0, 0], [-2, 0]), CCW)).angles;

        expect(turn(start)).toBeCloseTo(Math.PI / 2);
        expect(turn(end)).toBeCloseTo(Math.PI);
    });

    it("sizes the arc by the shorter arm only", () => {
        const radius = (first: [number, number], third: [number, number]) =>
            arc(mapper.map(angle(first, [0, 0], third), CCW)).radius;

        expect(radius([2, 0], [0, 4])).toBeCloseTo(2 * radius([1, 0], [0, 4]));
        expect(radius([1, 0], [0, 8])).toBeCloseTo(radius([1, 0], [0, 4]));
    });

    it("sizes the arc a quarter of the shorter arm, in common units", () => {
        const { radius } = arc(mapper.map(angle([1, 0], [0, 0], [0, 2]), CCW));

        expect(radius).toBeCloseTo(0.25 * 111319.49 * COMMON_PER_METRE, 3);
    });

    it("writes the angle at the middle of each arm in the selected orientation", () => {
        const primitives = mapper.map(EAST_NORTH, CCW);

        expect(byId(primitives, "text-0")).toMatchObject({
            geometry: { type: "point", positions: [expect.closeTo(1), expect.closeTo(0), 0] },
            style: { type: "text", color: MEASUREMENT_COLOR, billboard: false },
        });
        expect(byId(primitives, "text-1").geometry.positions).toEqual([
            expect.closeTo(0),
            expect.closeTo(1, 2),
            0,
        ]);
        expect(texts(primitives).map((t) => t.text)).toEqual(["90.00°", "90.00°"]);
        expect(texts(mapper.map(EAST_NORTH, CW)).map((t) => t.text)).toEqual([
            "270.00°",
            "270.00°",
        ]);
    });

    it("writes the angle in the selected unit", () => {
        const text = (unit: AngleState["unit"]) =>
            texts(mapper.map(EAST_NORTH, { unit, orientation: "ccw" }))[0].text;

        expect(text("mils")).toBe("1600.00mils");
        expect(text("both")).toBe("90.00° | 1600.00mils");
    });

    it("runs the readouts along north and east arms", () => {
        const [first, second] = texts(mapper.map(EAST_NORTH, CCW));

        expect(half(first.angle)).toBeCloseTo(0);
        expect(half(second.angle)).toBeCloseTo(90);
    });

    it("runs the readout along a diagonal arm", () => {
        const [first] = texts(mapper.map(angle([1, 1], [0, 0], [2, 0]), CCW));

        expect(half(first.angle)).toBeCloseTo(45, 0);
    });

    it("labels the vertex with the name", () => {
        expect(byId(mapper.map(EAST_NORTH, CCW), "label")).toMatchObject({
            geometry: { type: "point", positions: [0, 0, 0] },
            style: { type: "text", text: "Bend" },
        });
    });

    it("selects the angle unit and orientation, compared shallowly", () => {
        const state = {
            units: { angle: "mils", angleOrientation: "cw", measurement: "METRIC" },
        } as MapperState;
        const selected = mapper.selector.select(state);
        const equal = mapper.selector.equalityFn!;

        expect(selected).toEqual({ unit: "mils", orientation: "cw" });
        expect(equal(selected, mapper.selector.select(state))).toBe(true);
        expect(equal(selected, CW)).toBe(false);
    });
});

describe("formatAngle", () => {
    it("formats degrees, mils, or both to two places", () => {
        expect(formatAngle(45, "degrees")).toBe("45.00°");
        expect(formatAngle(45, "mils")).toBe("800.00mils");
        expect(formatAngle(45, "both")).toBe("45.00° | 800.00mils");
    });
});
