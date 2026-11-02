// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * The shared control-point primitives an {@link EditModel} is built from:
 * the default handle set, the handle hit test, and the body hit test.
 *
 * Pure over a draft's geometry plus a caller-supplied screen projector — no
 * host handle, no store, no React — so a subject or a plugin writing its own
 * model reuses them instead of reimplementing vertex and body hit-testing. The
 * host's own default model (`genericDragModel`) is assembled from exactly
 * these three.
 */
import * as turf from "@turf/turf";
import type { DraftObject } from "@opencop/elements";
import type { ControlPoint, EditIntent, ScreenProjector } from "./edit-model";
import { dist, pointToSegmentDistance, positionPairs } from "./screen-math";

/**
 * Pixel radius that counts as "on the body" for a point geometry (an icon),
 * which has no area to test against.
 */
const POINT_BODY_TOLERANCE_PX = 20;

/** One `vertex` control point per position — the handle set of every plain geometry. */
export function vertexControlPoints(draft: DraftObject): ControlPoint[] {
    return positionPairs(draft.geometry.getAllPos()).map((position, index) => ({
        kind: "vertex",
        position,
        index,
    }));
}

/** Nearest vertex control point within the host's grab tolerance, else undefined. */
export function hitControlPoint(
    controlPoints: readonly ControlPoint[],
    screen: readonly [number, number],
    project: ScreenProjector,
    tolerance: number,
): ControlPoint | undefined {
    let nearest: ControlPoint | undefined;
    let nearestDist = tolerance;
    for (const cp of controlPoints) {
        const projected = project(cp.position);
        if (!projected) continue;
        const d = dist(projected, screen);
        if (d <= nearestDist) {
            nearest = cp;
            nearestDist = d;
        }
    }
    return nearest;
}

/** Geometric body test for translate: inside a polygon, near a line, on a point. */
export function hitBody(
    draft: DraftObject,
    screen: readonly [number, number],
    lngLat: readonly [number, number],
    project: ScreenProjector,
    tolerancePx: number,
): boolean {
    let geo: GeoJSON.Geometry;
    try {
        geo = draft.geometry.toGeoJSONGeometry();
    } catch {
        return false;
    }
    if (geo.type === "Polygon" || geo.type === "MultiPolygon") {
        return turf.booleanPointInPolygon(turf.point([lngLat[0], lngLat[1]]), geo);
    }
    if (geo.type === "LineString") {
        const screenVertices = geo.coordinates
            .map((c) => project([c[0], c[1]]))
            .filter((p): p is [number, number] => p !== undefined);
        for (let i = 0; i + 1 < screenVertices.length; i++) {
            if (
                pointToSegmentDistance(screen, screenVertices[i], screenVertices[i + 1]) <=
                tolerancePx
            ) {
                return true;
            }
        }
        return false;
    }
    if (geo.type === "Point") {
        const projected = project([geo.coordinates[0], geo.coordinates[1]]);
        return projected !== undefined && dist(projected, screen) <= POINT_BODY_TOLERANCE_PX;
    }
    return false;
}

/** Re-exported for models that build a custom `hitTest` around the same intents. */
export type { EditIntent };
