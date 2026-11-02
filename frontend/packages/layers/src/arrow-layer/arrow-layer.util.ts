// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { formatDistance, type MeasurementSystem } from "@opencop/utils";

/** Metres as a display string, in the unit the operator reads. */
export function formatLength(meters: number, system: MeasurementSystem, decimals = 2): string {
    return formatDistance(meters, system, decimals).display;
}
