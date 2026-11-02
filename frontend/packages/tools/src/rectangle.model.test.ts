// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { Rectangle, type RectangleElement } from "@opencop/elements";
import { MercatorVector } from "./MercatorVector";
import { rectangleEditModel } from "./rectangle.model";

const ring = [0, 0, 0.002, 0, 0.002, 0.001, 0, 0.001];

function makeRectangle(): RectangleElement {
    const rectangle = Rectangle.create({ positions: [0, 0] });
    rectangle.geometry.setAllPos(ring);
    return rectangle;
}

describe("rectangleEditModel rotate handle", () => {
    it("floats beyond the edge across from the first, away from the rectangle", () => {
        const handle = rectangleEditModel
            .controlPoints(makeRectangle())
            .find((cp) => cp.kind === "rotate");

        expect(handle?.position[0]).toBeCloseTo(0.001, 6);
        expect(handle!.position[1]).toBeGreaterThan(0.001);
    });

    it("turns the ring about its centre and keeps its shape", () => {
        const rectangle = makeRectangle();
        const handle = rectangleEditModel
            .controlPoints(rectangle)
            .find((cp) => cp.kind === "rotate")!;

        // A quarter turn: the handle (north of the centre) is dragged due west of it.
        rectangleEditModel.applyHandle(rectangle, handle, [-0.01, 0.0005], ring);

        const after = rectangle.geometry.getAllPos();
        const [a, b, c, d] = [0, 2, 4, 6].map((i) =>
            MercatorVector.fromLngLat([after[i], after[i + 1]]),
        );
        const edge = (p: MercatorVector, q: MercatorVector) => p.clone().subtract(q);
        expect(edge(a, b).dot(edge(b, c))).toBeCloseTo(0, 12);
        expect(edge(a, b).len()).toBeCloseTo(
            MercatorVector.fromLngLat([0, 0])
                .subtract(MercatorVector.fromLngLat([0.002, 0]))
                .len(),
            10,
        );
        expect(a.clone().add(c).scale(0.5).distance(b.clone().add(d).scale(0.5))).toBeCloseTo(
            0,
            12,
        );
        expect(after).not.toEqual(ring);

        // The handle came round with it: no longer north of the centre, now west.
        const [lng] = rectangleEditModel
            .controlPoints(rectangle)
            .find((cp) => cp.kind === "rotate")!.position;
        expect(lng).toBeLessThan(0.001);
    });
});
