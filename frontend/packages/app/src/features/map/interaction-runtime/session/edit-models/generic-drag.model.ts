// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * The default edit model: per-vertex drag handles plus whole-element
 * translate. Applies to any element type that doesn't declare its own model.
 *
 * Assembled from the shared primitives in `@opencop/tools` — the same three a
 * subject or a plugin builds its own model from. What lives here is only the
 * host default's *assembly*; the primitives are contract.
 *
 * Hit-testing is geometric over the CURRENT viewport projection (the
 * caller-supplied projector), not a hover-captured one — which is what makes
 * EDIT drivable by plain synthetic mouse events.
 */
import type { PointGeometry } from "@opencop/elements";
import {
    hitBody,
    hitControlPoint,
    vertexControlPoints,
    type EditIntent,
    type EditModel,
} from "@opencop/tools";

export const genericDragModel: EditModel = {
    controlPoints: vertexControlPoints,

    applyHandle(element, controlPoint, lngLat) {
        const geo = element.geometry;
        if ("setPosition" in geo) {
            (geo as PointGeometry).setPosition([lngLat[0], lngLat[1]]);
        } else {
            geo.setPos([lngLat[0], lngLat[1]], controlPoint.index ?? 0);
        }
    },

    hitTest(element, screen, lngLat, project, tolerancePx): EditIntent {
        const cp = hitControlPoint(vertexControlPoints(element), screen, project, tolerancePx);
        if (cp) return { kind: "handle", controlPoint: cp };
        if (hitBody(element, screen, lngLat, project, tolerancePx)) return { kind: "body" };
        return { kind: "none" };
    },

    invalidation: "vertex",
};
