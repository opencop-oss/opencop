// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { DateTime, Duration } from "luxon";
import { sortedIndex, uniq } from "lodash";

export const MAX_SCRUBBER_STEPS = 100;

/**
 * Builds the ordered array of ISO date strings for the WMS date scrubber.
 *
 * Steps `step` from `timeMin` to `timeMax`, capping at `maxSteps`. When the
 * natural step count exceeds the cap, samples `maxSteps` dates evenly across
 * the span and deduplicates adjacent values that land on the same calendar day.
 */
export function buildScrubberDates(
    timeMin: DateTime,
    timeMax: DateTime,
    step: string,
    maxSteps = MAX_SCRUBBER_STEPS,
): string[] {
    const duration = Duration.fromISO(step);
    if (!duration.isValid) return [];
    const start = timeMin.startOf("day");
    const end = timeMax.startOf("day");

    const result: string[] = [];
    let current = start;
    while (current <= end && result.length < maxSteps) {
        const iso = current.toISODate();
        if (iso) result.push(iso);
        current = current.plus(duration);
    }
    if (current > end) return result;

    // More than maxSteps natural steps — sample evenly across the span.
    const spanMs = end.toMillis() - start.toMillis();
    const sampled: string[] = [];
    for (let i = 0; i < maxSteps; i++) {
        const frac = i / (maxSteps - 1);
        const iso = DateTime.fromMillis(start.toMillis() + frac * spanMs)
            .startOf("day")
            .toISODate();
        if (iso) sampled.push(iso);
    }
    return uniq(sampled);
}

/**
 * Maps a selected date string to a 0-based slider index within `dates`.
 *
 * Returns the last index when nothing is selected, 0 for an empty array, the
 * exact position when found, or the nearest insertion point when the date falls
 * between steps.
 */
export function findSliderValue(dates: string[], selectedDate: string | null): number {
    if (!dates.length) return 0;
    if (!selectedDate) return dates.length - 1;
    const exact = dates.indexOf(selectedDate);
    if (exact !== -1) return exact;
    return Math.min(sortedIndex(dates, selectedDate), dates.length - 1);
}
