// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { pixelsPerMeter, textAngle } from "./TextBoxElementMapper";

describe("pixelsPerMeter", () => {
    /**
     * Web Mercator calculations commonly use either deck's 512-pixel tile or
     * a 256-pixel tile. They differ by a factor of two, and only the 512 one
     * matches what is drawn — picking the other would draw every textbox's
     * content at half size while every ratio-based test still passed.
     */
    it("uses deck's 512-pixel tile, not the 256 of the OGC scale denominator", () => {
        // One pixel covers the equator's circumference over 512 at zoom 0.
        expect(1 / pixelsPerMeter(0, 0)).toBeCloseTo(40_075_016.686 / 512, 1);
        // The 256 convention would be this, and is what we must not be using.
        expect(1 / pixelsPerMeter(0, 0)).not.toBeCloseTo(40_075_016.686 / 256, 1);
    });

    it("halves the pixels a metre covers for each zoom level out", () => {
        expect(pixelsPerMeter(10, 0) / pixelsPerMeter(9, 0)).toBeCloseTo(2, 10);
    });

    it("covers more pixels per metre further from the equator", () => {
        // Mercator stretches by 1/cos(latitude), so a metre is drawn larger.
        expect(pixelsPerMeter(10, 60) / pixelsPerMeter(10, 0)).toBeCloseTo(2, 6);
    });

    it("clamps at the poles rather than running away", () => {
        // `Math.cos` of 90° is 6e-17, not zero, so an unclamped formula hands
        // back ~1e14 pixels per metre instead of failing visibly.
        expect(pixelsPerMeter(10, 90)).toBe(pixelsPerMeter(10, 85.051129));
        expect(pixelsPerMeter(10, -90)).toBe(pixelsPerMeter(10, -85.051129));
        expect(pixelsPerMeter(10, 90)).toBeLessThan(pixelsPerMeter(10, 0) * 15);
    });
});

describe("textAngle", () => {
    it("lays text along an eastward first edge at zero", () => {
        expect(textAngle([0, 0, 0.02, 0, 0.02, 0.01, 0, 0.01])).toBeCloseTo(0, 6);
    });

    it("turns with the box past a quarter turn instead of flipping upright", () => {
        expect(Math.abs(textAngle([0.02, 0.01, 0, 0.01, 0, 0, 0.02, 0]))).toBeCloseTo(180, 6);
    });
});
