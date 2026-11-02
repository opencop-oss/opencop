// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { Polygon, ShapeStyle, StrokePattern } from "@opencop/elements";
import type { Primitive } from "@opencop/scene";
import { PolygonMapper } from "./PolygonMapper";

const mapper = new PolygonMapper();
const ids = (primitives: Primitive[]) => primitives.map((p) => p.id);

function polygon(positions: number[], strokePattern = StrokePattern.Dotted) {
    return Polygon.create({
        positions,
        style: new ShapeStyle({
            fillColor: "#11223344",
            strokeColor: "#aabbccdd",
            strokeWeight: 3,
            strokePattern,
        }),
        meta: { displayName: "Zone" },
    });
}

const SQUARE = [0, 0, 2, 0, 2, 2, 0, 2];
const CLOSED_SQUARE = [0, 0, 0, 2, 0, 0, 2, 2, 0, 0, 2, 0, 0, 0, 0];

describe("PolygonMapper", () => {
    it("draws a closed outline, a label at the centroid, and a fill", () => {
        const [outline, label, fill, ...rest] = mapper.map(polygon(SQUARE));

        expect(outline).toMatchObject({
            id: "path",
            geometry: { type: "path", positions: CLOSED_SQUARE },
            style: {
                type: "path",
                strokeColor: [170, 187, 204, 221],
                strokeWidth: 3,
                strokeDash: [2, 2],
            },
        });
        expect(label).toMatchObject({
            id: "label",
            geometry: { type: "point", positions: [1, 1, 0] },
            style: { type: "text", text: "Zone" },
        });
        expect(fill).toMatchObject({
            id: "polygon",
            geometry: { type: "polygon", positions: CLOSED_SQUARE },
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
        expect(mapper.map(polygon(SQUARE, pattern))[0].style).toMatchObject({ strokeDash: dash });
    });

    it("does not close an already-closed ring twice", () => {
        expect(mapper.map(polygon([...SQUARE, 0, 0]))[0].geometry.positions).toEqual(CLOSED_SQUARE);
    });

    it("fills a triangle", () => {
        expect(ids(mapper.map(polygon([0, 0, 1, 0, 1, 1])))).toEqual(["path", "label", "polygon"]);
    });

    it("draws two vertices as an outline without a fill", () => {
        const primitives = mapper.map(polygon([0, 0, 2, 0]));

        expect(ids(primitives)).toEqual(["path", "label"]);
        expect(primitives[0].geometry.positions).toEqual([0, 0, 0, 2, 0, 0, 0, 0, 0]);
    });

    it("draws a lone vertex as a point outline labelled in place", () => {
        const [outline, label, ...rest] = mapper.map(polygon([3, 4]));

        expect(outline.geometry.positions).toEqual([3, 4, 0, 3, 4, 0]);
        expect(label.geometry.positions).toEqual([3, 4, 0]);
        expect(rest).toEqual([]);
    });

    it("labels an unnamed polygon with the element's default name", () => {
        expect(mapper.map(Polygon.create({ positions: SQUARE }))[1].style).toMatchObject({
            text: "New polygon",
        });
    });
});
