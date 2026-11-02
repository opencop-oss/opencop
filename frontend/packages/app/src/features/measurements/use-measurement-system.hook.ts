// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useCallback } from "react";
import { useQuery } from "@tanstack/react-query";
import { preferencesQueries } from "~/features/preferences/preferences.queries";
import * as measurementUtils from "@opencop/utils";
import type { MeasurementSystem } from "@opencop/utils";

export interface UseMeasurementSystemReturn {
    /** The user's current measurement system preference */
    system: MeasurementSystem;

    /**
     * Format a distance in meters to the appropriate unit (auto-selects km/m or mi/ft)
     * Note: When using turf.js, specify `{ units: "meters" }` to get meters directly
     */
    formatDistance: (meters: number, decimals?: number) => measurementUtils.FormattedDistance;

    /** Format an area in square meters to the appropriate unit (auto-selects km²/m² or mi²/ac/ft²) */
    formatArea: (sqMeters: number, decimals?: number) => measurementUtils.FormattedDistance;

    /** Break down meters into km+m or mi+ft components for input fields */
    breakdownDistance: (meters: number) => measurementUtils.DistanceBreakdown;

    /** Combine separate large+small unit inputs back into meters */
    combineDistance: (large: number, small: number) => number;
}

/**
 * Hook to get the user's preferred measurement system and formatting utilities.
 * Returns bound formatting functions that automatically use the current system preference.
 *
 * Defaults to IMPERIAL if preferences haven't loaded or aren't set.
 *
 * All formatting functions are memoized and only recreate when the system changes.
 *
 * @example
 * ```tsx
 * function MyComponent() {
 *   const { system, formatDistance } = useMeasurementSystem();
 *   const formatted = formatDistance(1000); // "1.00km" or "3280.84ft"
 *
 *   // When using turf.js, specify units: "meters"
 *   const dist = turf.distance(pointA, pointB, { units: "meters" });
 *   const formatted = formatDistance(dist);
 * }
 * ```
 *
 * @returns Object containing system preference and bound formatting functions
 */
export function useMeasurementSystem(): UseMeasurementSystemReturn {
    const { data: preferences } = useQuery(preferencesQueries.current());
    const system: MeasurementSystem = preferences?.measurementSystem ?? "IMPERIAL";

    // Bind formatting functions to current system
    const formatDistance = useCallback(
        (meters: number, decimals = 2) => measurementUtils.formatDistance(meters, system, decimals),
        [system],
    );

    const formatArea = useCallback(
        (sqMeters: number, decimals = 2) => measurementUtils.formatArea(sqMeters, system, decimals),
        [system],
    );

    const breakdownDistance = useCallback(
        (meters: number) => measurementUtils.breakdownDistance(meters, system),
        [system],
    );

    const combineDistance = useCallback(
        (large: number, small: number) => measurementUtils.combineDistance(large, small, system),
        [system],
    );

    return {
        system,
        formatDistance,
        formatArea,
        breakdownDistance,
        combineDistance,
    };
}
