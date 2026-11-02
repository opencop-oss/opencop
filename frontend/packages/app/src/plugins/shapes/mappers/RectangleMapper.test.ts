// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { Rectangle, ShapeStyle, StrokePattern } from "@opencop/elements";
import type { Primitive } from "@opencop/scene";
import { RectangleMapper } from "./RectangleMapper";

const mapper = new RectangleMapper();
const ids = (primitives: Primitive[]) => primitives.map((p) => p.id);

function rectangle(positions: number[], strokePattern = StrokePattern.Dashed) {
    return Rectangle.create({
        positions,
        style: new ShapeStyle({
            fillColor: "#11223344",
            strokeColor: "#aabbccdd",
            strokeWeight: 3,
            strokePattern,
        }),
        meta: { displayName: "Box" },
    });
}

const RECT = [0, 0, 4, 0, 4, 2, 0, 2];
const CLOSED_RECT = [0, 0, 0, 4, 0, 0, 4, 2, 0, 0, 2, 0, 0, 0, 0];

describe("RectangleMapper", () => {
    it("draws a closed outline, a label at the centre, and a fill", () => {
        const [outline, label, fill, ...rest] = mapper.map(rectangle(RECT));

        expect(outline).toMatchObject({
            id: "path",
            geometry: { type: "path", positions: CLOSED_RECT },
            style: {
                type: "path",
                strokeColor: [170, 187, 204, 221],
                strokeWidth: 3,
                strokeDash: [4, 4],
            },
        });
        expect(label).toMatchObject({
            id: "label",
            geometry: { type: "point", positions: [2, 1, 0] },
            style: { type: "text", text: "Box" },
        });
        expect(fill).toMatchObject({
            id: "polygon",
            geometry: { type: "polygon", positions: CLOSED_RECT },
            style: { type: "polygon", fillColor: [17, 34, 51, 68] },
        });
        expect(rest).toEqual([]);
    });

    it.each([
        ["solid", StrokePattern.Solid, [0, 0]],
        ["outlined", StrokePattern.Outlined, [0, 0]],
        ["dashed", StrokePattern.Dashed, [4, 4]],
        ["dotted", StrokePattern.Dotted, [2, 2]],
    ])("dashes a %s outline as %j", (_, pattern, dash) => {
        expect(mapper.map(rectangle(RECT, pattern))[0].style).toMatchObject({ strokeDash: dash });
    });

    it("draws fewer than three vertices as an outline without a fill", () => {
        expect(ids(mapper.map(rectangle([0, 0, 4, 0])))).toEqual(["path", "label"]);
        expect(ids(mapper.map(rectangle([0, 0])))).toEqual(["path", "label"]);
    });

    it("labels an unnamed rectangle with the element's default name", () => {
        expect(mapper.map(Rectangle.create({ positions: RECT }))[1].style).toMatchObject({
            text: "New rectangle",
        });
    });
});
