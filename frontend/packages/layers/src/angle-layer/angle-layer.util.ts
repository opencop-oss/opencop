// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { MILS_PER_DEGREE, type AngleOrientation } from "@opencop/utils";

/** How a layer writes an angle label (not the operator preference `AngleUnit` in `@opencop/utils`). */
export type AngleUnit = "degrees" | "mils" | "degrees+mils";
export type { AngleOrientation };

export function formatAngle(degrees: number, unit: AngleUnit): string {
    const formattedDegrees = degrees.toFixed(2) + "°";
    const formattedMils = (degrees * MILS_PER_DEGREE).toFixed(2) + "mils";
    switch (unit) {
        case "degrees":
            return formattedDegrees;
        case "mils":
            return formattedMils;
        case "degrees+mils":
            return formattedDegrees + " | " + formattedMils;
    }
}
