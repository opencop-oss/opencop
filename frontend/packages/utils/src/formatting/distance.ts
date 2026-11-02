// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import {
    FEET_PER_MILE,
    METERS_PER_KILOMETER,
    feetToMeters,
    metersToFeet,
    sqMetersToAcres,
    sqMetersToSqFeet,
    type MeasurementSystem,
} from "../units/measurement-system";

// ============================================================================
// SMART FORMATTING (with automatic unit selection)
// ============================================================================

export interface FormattedDistance {
    value: number;
    unit: string;
    display: string;
}

/**
 * Format a distance in meters to the appropriate unit based on measurement system
 * Automatically selects between large/small units (km/m or mi/ft)
 *
 * @param meters - Distance in meters
 * @param system - Measurement system preference
 * @param decimals - Number of decimal places (default: 2)
 * @returns Formatted distance with value, unit, and display string
 */
export function formatDistance(
    meters: number,
    system: MeasurementSystem,
    decimals = 2,
): FormattedDistance {
    if (system === "IMPERIAL") {
        const feet = metersToFeet(meters);
        if (feet >= FEET_PER_MILE) {
            const miles = feet / FEET_PER_MILE;
            return {
                value: miles,
                unit: "mi",
                display: `${miles.toFixed(decimals)}mi`,
            };
        }
        return {
            value: feet,
            unit: "ft",
            display: `${feet.toFixed(decimals)}ft`,
        };
    }

    // Metric
    if (meters >= METERS_PER_KILOMETER) {
        const km = meters / METERS_PER_KILOMETER;
        return {
            value: km,
            unit: "km",
            display: `${km.toFixed(decimals)}km`,
        };
    }
    return {
        value: meters,
        unit: "m",
        display: `${meters.toFixed(decimals)}m`,
    };
}

/**
 * Format an area in square meters to the appropriate unit based on measurement system
 * Automatically selects between large/small units (km²/m² or mi²/acres/ft²)
 *
 * @param sqMeters - Area in square meters
 * @param system - Measurement system preference
 * @param decimals - Number of decimal places (default: 2)
 * @returns Formatted area with value, unit, and display string
 */
export function formatArea(
    sqMeters: number,
    system: MeasurementSystem,
    decimals = 2,
): FormattedDistance {
    if (system === "IMPERIAL") {
        const acres = sqMetersToAcres(sqMeters);

        // Use square miles for very large areas (>640 acres = 1 sq mi)
        if (acres >= 640) {
            const sqMiles = acres / 640;
            return {
                value: sqMiles,
                unit: "mi²",
                display: `${sqMiles.toFixed(decimals)}mi²`,
            };
        }

        // Use acres for medium areas (>1 acre)
        if (acres >= 1) {
            return {
                value: acres,
                unit: "ac",
                display: `${acres.toFixed(decimals)}ac`,
            };
        }

        // Use square feet for small areas
        const sqFeet = sqMetersToSqFeet(sqMeters);
        return {
            value: sqFeet,
            unit: "ft²",
            display: `${sqFeet.toFixed(decimals)}ft²`,
        };
    }

    // Metric
    const sqKm = sqMeters / (METERS_PER_KILOMETER * METERS_PER_KILOMETER);
    if (sqKm >= 1) {
        return {
            value: sqKm,
            unit: "km²",
            display: `${sqKm.toFixed(decimals)}km²`,
        };
    }
    return {
        value: sqMeters,
        unit: "m²",
        display: `${sqMeters.toFixed(decimals)}m²`,
    };
}

// ============================================================================
// BREAKDOWN FORMATTING (for input fields with separate large/small units)
// ============================================================================

interface DistanceBreakdownValues {
    large: number;
    small: number;
}

interface ImperialBreakdown extends DistanceBreakdownValues {
    largeUnit: "mi";
    smallUnit: "ft";
}

interface MetricBreakdown extends DistanceBreakdownValues {
    largeUnit: "km";
    smallUnit: "m";
}

export type DistanceBreakdown = ImperialBreakdown | MetricBreakdown;

/**
 * Break down meters into km+m or mi+ft components for input fields
 * Used in DistanceThresholdInput where users can input both units separately
 *
 * @param meters - Distance in meters
 * @param system - Measurement system preference
 * @returns Breakdown with large and small unit values and labels
 */
export function breakdownDistance(meters: number, system: MeasurementSystem): DistanceBreakdown {
    if (system === "IMPERIAL") {
        const totalFeet = metersToFeet(meters);
        const miles = Math.floor(totalFeet / FEET_PER_MILE);
        const feet = Math.floor(totalFeet % FEET_PER_MILE);
        return {
            large: miles,
            small: feet,
            largeUnit: "mi",
            smallUnit: "ft",
        };
    }

    // Metric
    const km = Math.floor(meters / METERS_PER_KILOMETER);
    const m = Math.floor(meters % METERS_PER_KILOMETER);
    return {
        large: km,
        small: m,
        largeUnit: "km",
        smallUnit: "m",
    };
}

/**
 * Combine separate large+small unit inputs back into meters
 *
 * @param large - Large unit value (km or mi)
 * @param small - Small unit value (m or ft)
 * @param system - Measurement system preference
 * @returns Total distance in meters
 */
export function combineDistance(large: number, small: number, system: MeasurementSystem): number {
    if (system === "IMPERIAL") {
        const totalFeet = large * FEET_PER_MILE + small;
        return feetToMeters(totalFeet);
    }

    // Metric
    return large * METERS_PER_KILOMETER + small;
}
