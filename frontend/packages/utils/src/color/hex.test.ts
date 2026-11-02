// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { hexToRgba } from "./hex";

describe("hexToRgba", () => {
    it.each([
        ["#ffa200", [255, 162, 0, 255]],
        ["ffa200", [255, 162, 0, 255]],
        ["#ffa20080", [255, 162, 0, 128]],
        ["#FFA200", [255, 162, 0, 255]],
        ["#fa0", [255, 170, 0, 255]],
        ["#fa08", [255, 170, 0, 136]],
    ])("parses %s", (hex, rgba) => {
        expect(hexToRgba(hex)).toEqual(rgba);
    });

    it("scales the alpha by opacity", () => {
        expect(hexToRgba("#ffa200", 0.5)).toEqual([255, 162, 0, 127.5]);
        expect(hexToRgba("#ffa20080", 0.5)).toEqual([255, 162, 0, 64]);
    });

    it.each(["", "#", "#ff", "#ffa20", "#ffa2008", "#gggggg", "red", "#ffa200801"])(
        "reads malformed %j as transparent, never NaN",
        (hex) => {
            expect(hexToRgba(hex)).toEqual([0, 0, 0, 0]);
        },
    );
});
