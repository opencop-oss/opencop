// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { chunk } from "lodash";
import { length, lineString } from "@turf/turf";
import type { MeasurementSystem } from "../units/measurement-system";
import { formatArea, formatDistance } from "./distance";

/** A geometry with a radius in meters — a circle's. */
export interface RadiusGeometry {
    getRadius(): number;
}

/** A geometry with a path of flat `[lng, lat, lng, lat, …]` positions. */
export interface PathGeometry {
    getAllPos(): readonly number[];
}

/** A closed path with an area in square meters. */
export interface AreaGeometry extends PathGeometry {
    getArea(): number;
}

function toPairs(geometry: PathGeometry): [number, number][] {
    return chunk(geometry.getAllPos(), 2) as [number, number][];
}

/** A circle's `[area, circumference]` as display text. */
export function summarizeCircle(
    geometry: RadiusGeometry,
    system: MeasurementSystem,
): [string, string] {
    const radius = geometry.getRadius();
    return [
        formatArea(Math.PI * radius * radius, system).display,
        formatDistance(2 * Math.PI * radius, system).display,
    ];
}

/** A path's `[length]` as display text, or `null` with fewer than two positions. */
export function summarizeLine(geometry: PathGeometry, system: MeasurementSystem): [string] | null {
    const pairs = toPairs(geometry);
    if (pairs.length < 2) return null;
    return [formatDistance(length(lineString(pairs), { units: "meters" }), system).display];
}

/** A polygon's `[area, perimeter]` as display text, or `null` with fewer than three positions. */
export function summarizePolygon(
    geometry: AreaGeometry,
    system: MeasurementSystem,
): [string, string] | null {
    const pairs = toPairs(geometry);
    if (pairs.length < 3) return null;
    const ring = [...pairs, pairs[0]];
    return [
        formatArea(geometry.getArea(), system).display,
        formatDistance(length(lineString(ring), { units: "meters" }), system).display,
    ];
}
