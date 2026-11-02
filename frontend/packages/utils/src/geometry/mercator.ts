// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { MercatorCoordinate } from "maplibre-gl";

/**
 * The midpoint of two positions in Web Mercator space, as `[lng, lat]` — where
 * the middle of a straight segment on the map appears, rather than its great-
 * circle midpoint.
 */
export function mercatorMidpoint(
    start: readonly [number, number] | readonly [number, number, number],
    end: readonly [number, number] | readonly [number, number, number],
): [number, number] {
    const a = MercatorCoordinate.fromLngLat([start[0], start[1]]);
    const b = MercatorCoordinate.fromLngLat([end[0], end[1]]);
    const midpoint = new MercatorCoordinate((a.x + b.x) / 2, (a.y + b.y) / 2).toLngLat();
    return [midpoint.lng, midpoint.lat];
}
