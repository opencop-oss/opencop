// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { bearing } from "@turf/turf";
import type { AngleOrientation } from "../units/angle";

/**
 * The angle at `origin` from `arm0` to `arm1`, measured in `orientation`, in
 * degrees in `[0, 360)`.
 */
export function directionalAngle(
    origin: readonly [number, number],
    arm0: readonly [number, number],
    arm1: readonly [number, number],
    orientation: AngleOrientation,
): number {
    const b0 = bearing([origin[0], origin[1]], [arm0[0], arm0[1]]);
    const b1 = bearing([origin[0], origin[1]], [arm1[0], arm1[1]]);
    const angle = (b1 - b0 + 360) % 360;
    return orientation === "cw" ? angle : (360 - angle) % 360;
}
