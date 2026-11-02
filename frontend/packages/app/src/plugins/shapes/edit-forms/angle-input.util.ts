// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { Position2D } from "@opencop/elements";
import type { AngleOrientation } from "@opencop/utils";
import * as turf from "@turf/turf";

/**
 * Normalize a bearing in degrees to the half-open [-180, 180) range.
 *
 * Deliberately not `turf.azimuthToBearing`: that uses the half-closed (-180, 180] convention
 * (180 stays 180), whereas this always folds the boundary down to -180. The two agree everywhere
 * else, and the only consumer (`turf.destination`'s bearing parameter) treats them as the same
 * direction regardless, so the difference is cosmetic — kept for a stable, side-consistent range.
 */
export function normalizeBearing(bearing: number): number {
    return ((((bearing + 180) % 360) + 360) % 360) - 180;
}

/** Compass bearing (degrees clockwise from true north, in [0, 360)) from `origin` to `pos`. */
export function compassBearing(origin: Position2D, pos: Position2D): number {
    return turf.bearingToAzimuth(turf.bearing(origin, pos));
}

/**
 * Bearing (degrees, normalized to [-180, 180)) that the second arm must point along from the
 * vertex so the vertex angle between the two arms reads `targetDegrees` under `orientation`.
 *
 * This is the inverse of `Angle.getDirectionalAngle` (`@opencop/elements`): that derives the angle
 * from the two arm bearings; this solves for the second arm's bearing given the first arm's
 * (`arm0Bearing` — the signed value from `turf.bearing`) and the desired angle. There is no
 * turf primitive for it: `turf.angle` computes the forward direction only.
 */
export function bearingForAngle(
    arm0Bearing: number,
    targetDegrees: number,
    orientation: AngleOrientation,
): number {
    const sweep = orientation === "cw" ? targetDegrees : (360 - targetDegrees) % 360;
    return normalizeBearing(arm0Bearing + sweep);
}
