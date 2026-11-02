// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { lassoPrimitives } from "./lasso.mapper";
import { lassoTrace } from "./lasso-trace.model";

describe("lassoPrimitives", () => {
    it("draws nothing for a trace of one point", () => {
        expect(lassoPrimitives(lassoTrace([0, 0]))).toEqual([]);
    });

    it("draws the trace as a marching-ants path", () => {
        const trace = lassoTrace([0, 0]);
        trace.geometry.setAllPos([0, 0, 1, 0, 1, 1]);

        expect(lassoPrimitives(trace)).toEqual([
            {
                id: "trace",
                geometry: { type: "path", positions: [0, 0, 0, 1, 0, 0, 1, 1, 0] },
                style: { type: "marching-ants" },
            },
        ]);
    });
});
