// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { Point, PointStyle } from "@opencop/elements";
import { PointMapper } from "./PointMapper";

const mapper = new PointMapper();

describe("PointMapper", () => {
    it("draws a dot in the style's colour and radius, with no outline", () => {
        const point = Point.create({
            position: [10, 20],
            style: new PointStyle({ color: "#ff000080", radius: 8 }),
        });

        const [dot] = mapper.map(point);

        expect(dot).toMatchObject({
            id: "point",
            geometry: { type: "point", positions: [10, 20, 0] },
            style: {
                type: "point",
                fillColor: [255, 0, 0, 128],
                radius: 8,
                strokeColor: [0, 0, 0, 0],
                strokeWidth: 0,
            },
        });
    });

    it("labels the dot with its name, offset below it by the radius", () => {
        const point = Point.create({
            position: [10, 20],
            style: new PointStyle({ color: "#ff0000ff", radius: 8 }),
            meta: { displayName: "Alpha" },
        });

        expect(mapper.map(point)[1]).toMatchObject({
            id: "label",
            geometry: { type: "point", positions: [10, 20, 0] },
            style: { type: "text", text: "Alpha", offset: [0, 13], billboard: true },
        });
    });

    it("labels an unnamed point with the element's default name", () => {
        expect(mapper.map(Point.create({ position: [0, 0] }))[1].style).toMatchObject({
            text: "New point",
        });
    });

    it("has no selector", () => {
        expect(mapper.type).toBe("point");
        expect("selector" in mapper).toBe(false);
    });
});
