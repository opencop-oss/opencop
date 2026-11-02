// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { Line, StrokePattern, StrokeStyle } from "@opencop/elements";
import type { Primitive } from "@opencop/scene";
import { LineMapper } from "./LineMapper";

const mapper = new LineMapper();
const ids = (primitives: Primitive[]) => primitives.map((p) => p.id);
const wrap = (degrees: number) => ((degrees % 360) + 360) % 360;

function line(positions: number[], pattern = StrokePattern.Solid) {
    return Line.create({
        positions,
        style: new StrokeStyle({ color: "#00ff0080", weight: 5, pattern }),
        meta: { displayName: "Route" },
    });
}

describe("LineMapper", () => {
    it("draws the vertices as one path in the style's colour and weight", () => {
        const [path] = mapper.map(line([0, 0, 1, 0, 2, 1]));

        expect(path).toMatchObject({
            id: "path",
            geometry: { type: "path", positions: [0, 0, 0, 1, 0, 0, 2, 1, 0] },
            style: { type: "path", strokeColor: [0, 255, 0, 128], strokeWidth: 5 },
        });
    });

    it.each([
        ["solid", StrokePattern.Solid, [0, 0]],
        ["outlined", StrokePattern.Outlined, [0, 0]],
        ["dashed", StrokePattern.Dashed, [4, 4]],
        ["dotted", StrokePattern.Dotted, [2, 2]],
    ])("dashes a %s stroke as %j", (_, pattern, dash) => {
        expect(mapper.map(line([0, 0, 1, 0], pattern))[0].style).toMatchObject({
            strokeDash: dash,
        });
    });

    it("labels every second segment at its midpoint", () => {
        const primitives = mapper.map(line([0, 0, 1, 0, 2, 0, 3, 0, 4, 0]));

        expect(ids(primitives)).toEqual(["path", "label-4", "label-8"]);
        expect(primitives[1]).toMatchObject({
            geometry: { type: "point", positions: [1.5, 0, 0] },
            style: { type: "text", text: "Route", billboard: false },
        });
        expect(primitives[2].geometry.positions[0]).toBeCloseTo(3.5);
    });

    it("leaves a single-segment line unlabelled", () => {
        expect(ids(mapper.map(line([0, 0, 1, 0])))).toEqual(["path"]);
    });

    it("places a label at the mercator midpoint of its segment", () => {
        const [, label] = mapper.map(line([0, 0, 0, 10, 0, 60]));

        expect(label.geometry.positions[1]).toBeCloseTo(39.26, 2);
    });

    it("runs each label along its segment, never upside down", () => {
        const angle = (positions: number[]) =>
            (mapper.map(line(positions))[1].style as { angle: number }).angle;

        expect(angle([0, 0, 0, 0, 1, 0])).toBeCloseTo(0);
        expect(wrap(angle([0, 0, 0, 0, -1, 0]))).toBeCloseTo(0);
        expect(angle([0, 0, 0, 0, 0, 1])).toBeCloseTo(90);
        expect(angle([0, 0, 0, 0, 1, 1])).toBeCloseTo(45, 1);
    });

    it("draws a lone vertex as a one-point path", () => {
        expect(mapper.map(line([3, 4]))).toEqual([
            expect.objectContaining({
                id: "path",
                geometry: { type: "path", positions: [3, 4, 0] },
            }),
        ]);
    });
});
