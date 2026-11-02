// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { boxFrame, grownToHeight } from "./box-geometry.util";

/** An axis-aligned box of `w`×`h` degrees at the equator, counter-clockwise from the SW corner. */
function aligned(w: number, h: number): number[] {
    return [0, 0, w, 0, w, h, 0, h];
}

/** The same box turned 90°: the first edge runs north instead of east. */
function turned(w: number, h: number): number[] {
    return [0, 0, 0, w, -h, w, -h, 0];
}

describe("boxFrame", () => {
    it("measures along the box's own edges, not north and east", () => {
        const upright = boxFrame(aligned(0.01, 0.005))!;
        const rotated = boxFrame(turned(0.01, 0.005))!;

        expect(rotated.width).toBeCloseTo(upright.width, -1);
        expect(rotated.height).toBeCloseTo(upright.height, -1);
    });

    it("declines degenerate geometry", () => {
        expect(boxFrame([])).toBeNull();
        expect(boxFrame([0, 0, 0, 0, 0, 0, 0, 0])).toBeNull();
    });
});

describe("grownToHeight", () => {
    it("leaves a box that is already tall enough alone", () => {
        const positions = aligned(0.01, 0.005);
        const { height } = boxFrame(positions)!;
        expect(grownToHeight(positions, height - 1)).toBeNull();
    });

    it("grows to the height asked for", () => {
        const grown = grownToHeight(aligned(0.01, 0.005), 2000)!;
        expect(boxFrame(grown)!.height).toBeCloseTo(2000, 0);
    });

    it("leaves the width alone — it is the operator's", () => {
        const positions = aligned(0.01, 0.005);
        const grown = grownToHeight(positions, 2000)!;

        expect(grown.slice(0, 4)).toEqual(positions.slice(0, 4));
        expect(boxFrame(grown)!.width).toBeCloseTo(boxFrame(positions)!.width, -1);
    });

    it("grows away from the first edge, not towards it", () => {
        const positions = aligned(0.01, 0.005);
        const grown = grownToHeight(positions, 2000)!;
        expect(grown[5]).toBeGreaterThan(positions[5]);
        expect(grown[7]).toBeGreaterThan(positions[7]);
    });

    it("stays rectangular far from the equator", () => {
        // A screen rectangle high in the northern hemisphere. Growing it by
        // ground distance would shear it into a trapezoid, since a degree of
        // latitude is worth less mercator the further north it sits.
        const atFifty = [-10, 50, -9, 50, -9, 50.5, -10, 50.5];
        const grown = grownToHeight(atFifty, boxFrame(atFifty)!.height * 2)!;

        const topEdge = grown[2] - grown[0];
        const bottomEdge = grown[4] - grown[6];
        expect(bottomEdge).toBeCloseTo(topEdge, 6);
    });
});
