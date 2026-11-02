// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { DraftObject } from "@opencop/elements";
import { interleave, type MarchingAntsPrimitive } from "@opencop/scene";
import type { LassoTrace } from "./lasso-trace.model";

/**
 * How a lasso trace looks while it is laid down: a marching-ants path, the same
 * style as the boundary of the selection it makes. A trace with fewer than two
 * points draws nothing.
 */
export function lassoPrimitives(trace: LassoTrace): MarchingAntsPrimitive[] {
    const positions = trace.geometry.getAllPos() as number[];
    if (positions.length < 4) return [];
    return [
        {
            id: "trace",
            geometry: { type: "path", positions: interleave(positions) },
            style: { type: "marching-ants" },
        },
    ];
}

/** A lasso session's product: the trace being laid down. */
export function lassoProductPrimitives(draft: DraftObject): MarchingAntsPrimitive[] {
    return lassoPrimitives(draft as LassoTrace);
}
