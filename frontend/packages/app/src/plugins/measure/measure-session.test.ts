// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { DEFAULT_MEASURE_SHAPE, resolveMeasureShape } from "./measure-session";

describe("resolveMeasureShape", () => {
    it("arms the default shape when the params name none (a plain toolbar activation)", () => {
        expect(resolveMeasureShape({})).toBe(DEFAULT_MEASURE_SHAPE);
        expect(resolveMeasureShape({ shape: undefined })).toBe(DEFAULT_MEASURE_SHAPE);
    });

    it("keeps an explicit shape", () => {
        expect(resolveMeasureShape({ shape: "circle" })).toBe("circle");
        expect(resolveMeasureShape({ shape: "polygon" })).toBe("polygon");
    });

    it("reads an explicit null as no shape — the editor's toggle deselected it", () => {
        expect(resolveMeasureShape({ shape: null })).toBeNull();
    });
});
