// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { convertArea, convertLength } from "@turf/turf";
import { assertNever } from "assert-never";

/** The operator's unit-system preference. */
export type MeasurementSystem = "METRIC" | "IMPERIAL";

/** A unit a distance input is entered in. */
export type DistanceUnit = "m" | "km" | "mi" | "ft";

export const FEET_PER_MILE = 5280;
export const METERS_PER_KILOMETER = 1000;

export function metersToFeet(meters: number): number {
    return convertLength(meters, "meters", "feet");
}

export function feetToMeters(feet: number): number {
    return convertLength(feet, "feet", "meters");
}

/** Convert meters to `unit`. */
export function fromMeters(meters: number, unit: DistanceUnit): number {
    switch (unit) {
        case "m":
            return meters;
        case "km":
            return meters / METERS_PER_KILOMETER;
        case "mi":
            return convertLength(meters, "meters", "miles");
        case "ft":
            return metersToFeet(meters);
    }

    return assertNever(unit);
}

/** Convert a value in `unit` to meters. */
export function toMeters(value: number, unit: DistanceUnit): number {
    switch (unit) {
        case "m":
            return value;
        case "km":
            return value * METERS_PER_KILOMETER;
        case "mi":
            return convertLength(value, "miles", "meters");
        case "ft":
            return feetToMeters(value);
    }

    return assertNever(unit);
}

export function sqMetersToSqFeet(sqMeters: number): number {
    return convertArea(sqMeters, "meters", "feet");
}

export function sqMetersToAcres(sqMeters: number): number {
    return convertArea(sqMeters, "meters", "acres");
}
