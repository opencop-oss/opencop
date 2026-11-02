// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { DateTime } from "luxon";

/**
 * Canonical bound format used by the historical range UI — sortable
 * minute-precision "yyyy-MM-dd HH:mm". This is also the format
 * `OcDateTimePicker`'s typeable mode parses, so the trigger display and the
 * popover inputs stay in sync.
 */
export const RANGE_BOUND_FORMAT = "yyyy-MM-dd HH:mm";

/**
 * Format an ISO range as a pair of minute-precision strings in the user's
 * timezone. Returns empty strings if either bound is missing or unparseable
 * so a partial UI state renders cleanly rather than "Invalid DateTime".
 */
export function formatRangeBounds(
    from: string | null,
    to: string | null,
    timezone: string,
): { startLabel: string; endLabel: string } {
    if (!from || !to) return { startLabel: "", endLabel: "" };
    const start = DateTime.fromISO(from).setZone(timezone);
    const end = DateTime.fromISO(to).setZone(timezone);
    return {
        startLabel: start.isValid ? start.toFormat(RANGE_BOUND_FORMAT) : "",
        endLabel: end.isValid ? end.toFormat(RANGE_BOUND_FORMAT) : "",
    };
}
