// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { round } from "lodash";
import { fromMeters, toMeters, type DistanceUnit, type MeasurementSystem } from "@opencop/utils";
import { MAX_TEXT_SIZE_METERS } from "./box-text.util";

/** The unit a text size is shown and typed in, for the operator's system. */
export function sizeUnit(system: MeasurementSystem): DistanceUnit {
    return system === "IMPERIAL" ? "ft" : "m";
}

/**
 * Sizes offered in the list, in the displayed unit.
 *
 * Roughly geometric rather than even steps: the useful size depends on the
 * ground scale being annotated, which spans five decades between a city block
 * and a continent, so the stops have to grow with the scale to stay evenly
 * spaced to the eye. Round numbers win over an exact ratio where the two
 * disagree.
 *
 * Round in each system rather than converted from one another — an operator in
 * feet should see `500 ft`, not `1,640 ft`. The list stops below the ceiling in
 * imperial because no round number of feet lands on it; typing still reaches it.
 */
const PRESETS: Record<MeasurementSystem, readonly number[]> = {
    METRIC: [
        1,
        2,
        5,
        10,
        25,
        50,
        100,
        250,
        500,
        1_000,
        2_500,
        5_000,
        10_000,
        25_000,
        50_000,
        100_000,
        MAX_TEXT_SIZE_METERS,
    ],
    IMPERIAL: [
        5, 10, 25, 50, 100, 250, 500, 1_000, 2_500, 5_000, 10_000, 25_000, 50_000, 100_000, 250_000,
        500_000, 900_000,
    ],
};

export function sizePresets(system: MeasurementSystem): readonly number[] {
    return PRESETS[system];
}

/** A size in metres, as a number in the displayed unit, rounded to read cleanly. */
export function toDisplaySize(meters: number, system: MeasurementSystem): number {
    const converted = fromMeters(meters, sizeUnit(system));
    // Auto-sizing lands on fractions; below ten a whole number is too coarse.
    return converted >= 10 ? Math.round(converted) : round(converted, 1);
}

/** Inverse of {@link toDisplaySize}: a typed number back to the stored metres. */
export function fromDisplaySize(value: number, system: MeasurementSystem): number {
    return toMeters(value, sizeUnit(system));
}
