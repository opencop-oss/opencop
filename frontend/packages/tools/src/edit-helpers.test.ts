// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import type { ControlPoint } from "./edit-model";
import { hitControlPoint } from "./edit-helpers";

const identity = (lngLat: [number, number]): [number, number] => lngLat;

const vertex = (position: [number, number]): ControlPoint => ({ kind: "vertex", position });
const rotate = (position: [number, number]): ControlPoint => ({ kind: "rotate", position });

describe("hitControlPoint", () => {
    it("picks the nearer of two handles both inside the tolerance, whatever their order", () => {
        const corner = vertex([0, 0]);
        const dot = rotate([10, 0]);

        expect(hitControlPoint([corner, dot], [8, 0], identity, 15)).toBe(dot);
        expect(hitControlPoint([dot, corner], [8, 0], identity, 15)).toBe(dot);
    });

    it("ignores a handle beyond the tolerance", () => {
        expect(hitControlPoint([vertex([0, 0])], [30, 0], identity, 15)).toBeUndefined();
    });
});
