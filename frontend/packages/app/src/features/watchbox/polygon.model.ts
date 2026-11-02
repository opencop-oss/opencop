// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import * as turf from "@turf/turf";
import { isEqual } from "lodash";
import type { WatchboxPolygon } from "./watchbox.model";

/**
 * Normalizes world wrapped longitude coordinate to (-180, 180]
 *
 * @param lng The longitude.
 * @returns The normalized coordinate.
 */
function normalizeLongitude(lng: number): number {
    return 180 - ((((180 - lng) % 360) + 360) % 360);
}

/**
 * Determine whether two longititude's shortest path crosses the anti-meridian.
 *
 * @param lng0 First longitude.
 * @param lng1 Second longitude
 * @returns Whether the shortest line between them crosses the anti-meridian.
 */
function crossesAntimeridian(lng0: number, lng1: number): boolean {
    return Math.abs(normalizeLongitude(lng0) - normalizeLongitude(lng1)) > 180;
}

/**
 * Determine whether a polygon ring crosses the anti-meridian.
 *
 * @param ring The polygon ring.
 * @returns Whether the ring cross the anti-meridian.
 */
function ringCrossesAntimeridian(ring: GeoJSON.Position[]): boolean {
    for (let i = 1; i < ring.length; i++) {
        const prev = ring[i - 1];
        const curr = ring[i];
        if (crossesAntimeridian(prev[0], curr[0])) return true;
    }
    return false;
}

/**
 * Deterines if `ring` is closed.
 *
 * @param ring The ring to check.
 * @returns Whether `ring` is closed.
 */
function isClosed(ring: GeoJSON.Position[]): boolean {
    return ring[0][0] === ring[ring.length - 1][0] && ring[0][1] === ring[ring.length - 1][1];
}

/**
 * Builds a {@link WatchboxPolygon} from a flat [lng, lat, lng, lat, ...] vertex
 * list (the shape `PolygonGeometry.getAllPos()` returns), closing the ring.
 *
 * @param positions Flat vertex positions.
 * @returns The polygon.
 */
export function polygonFromPositions(positions: readonly number[]): WatchboxPolygon {
    const ring: GeoJSON.Position[] = [];
    for (let i = 0; i + 1 < positions.length; i += 2) {
        ring.push([positions[i], positions[i + 1]]);
    }
    if (ring.length > 0) ring.push([...ring[0]]);
    return { type: "Polygon", coordinates: [ring] };
}

/** First self-intersection point of a closed ring, or null if it doesn't cross itself. */
export function ringSelfIntersection(ring: GeoJSON.Position[]): GeoJSON.Position | null {
    // A repeated vertex (double-click while drawing) is a zero-length segment that turf
    // reports as a kink but JTS accepts server-side; dropping them keeps the two agreed.
    const deduped = ring.filter((pos, i) => i === 0 || !isEqual(pos, ring[i - 1]));
    if (deduped.length < 4) return null;
    const kinks = turf.kinks(turf.lineString(deduped)).features;
    return kinks.length > 0 ? kinks[0].geometry.coordinates : null;
}

/** Enum of errors for a {@link WatchboxPolygon} */
export type WatchboxPolygonError =
    | "empty"
    | "has-holes"
    | "not-closed"
    | "crosses-antimeridian"
    | "too-few-vertices"
    | "self-intersects";

export type ParsePolygonResult =
    | {
          ok: true;
          result: WatchboxPolygon;
      }
    | {
          ok: false;
          errors: WatchboxPolygonError[];
      };

/**
 * Validates a {@link WatchboxPolygon} before transport.
 *
 * @param polygon The polygon.
 * @returns The original polygon if valid, a {@link WatchboxPolygonIssue} if the polygon is invalid.
 */
export function parsePolygon(polygon: WatchboxPolygon): ParsePolygonResult {
    const errors: WatchboxPolygonError[] = [];
    if (polygon.coordinates.length === 0) errors.push("empty");
    if (polygon.coordinates.length > 1) errors.push("has-holes");
    const ring = polygon.coordinates[0];
    if (ring) {
        if (ring.length < 4) errors.push("too-few-vertices");
        else {
            if (ring && !isClosed(ring)) errors.push("not-closed");
            if (ring && ringCrossesAntimeridian(ring)) errors.push("crosses-antimeridian");
            if (ring && isClosed(ring) && ringSelfIntersection(ring))
                errors.push("self-intersects");
        }
    }
    if (errors.length > 0) return { ok: false, errors };
    return {
        ok: true,
        result: {
            ...polygon,
            coordinates: [
                polygon.coordinates[0].map(([lng, lat]) => [normalizeLongitude(lng), lat]),
            ],
        },
    };
}
