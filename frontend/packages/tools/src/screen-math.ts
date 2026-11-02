// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * Screen-space helpers for writing an {@link import("./edit-model").EditModel}.
 * Pure math, no host handles — a tool author's `hitTest` needs these and would
 * otherwise re-derive them.
 *
 * Deliberately no tolerance constant: grab tolerance is host policy and arrives
 * as `hitTest`'s `tolerancePx` argument.
 */

import type { Geometry, PointGeometry } from "@opencop/elements";

export function dist(v0: readonly [number, number], v1: readonly [number, number]): number {
    return Math.hypot(v1[0] - v0[0], v1[1] - v0[1]);
}

/** Distance from point p to segment a-b, all in screen pixels. */
export function pointToSegmentDistance(
    p: readonly [number, number],
    a: readonly [number, number],
    b: readonly [number, number],
): number {
    const abx = b[0] - a[0];
    const aby = b[1] - a[1];
    const lengthSq = abx * abx + aby * aby;
    if (lengthSq === 0) return dist(p, a);
    let t = ((p[0] - a[0]) * abx + (p[1] - a[1]) * aby) / lengthSq;
    t = Math.max(0, Math.min(1, t));
    return dist(p, [a[0] + t * abx, a[1] + t * aby]);
}

/** Iterate an element's flat [lng, lat, …] positions as pairs. */
export function positionPairs(flat: readonly number[]): [number, number][] {
    const pairs: [number, number][] = [];
    for (let i = 0; i + 1 < flat.length; i += 2) pairs.push([flat[i], flat[i + 1]]);
    return pairs;
}

/**
 * Move one vertex of any geometry to `lngLat`. A point geometry has one
 * position and its own setter; every other geometry indexes its vertices.
 */
export function setVertex(geometry: Geometry, lngLat: readonly number[], index: number): void {
    if ("setPosition" in geometry) {
        (geometry as PointGeometry).setPosition([lngLat[0], lngLat[1]]);
    } else {
        geometry.setPos([lngLat[0], lngLat[1]], index);
    }
}
