// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/** NATO standard: 6400 mils = 360 degrees. */
export const MILS_PER_DEGREE = 6400 / 360;

/** The operator's angle-readout preference. */
export type AngleUnit = "both" | "degrees" | "mils";

/** Which way an angle is measured from its first arm to its second. */
export type AngleOrientation = "ccw" | "cw";
