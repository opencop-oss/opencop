// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { Row } from "@tanstack/react-table";
import { DateTime } from "luxon";

/** Epoch millis, or `null` if missing or unparseable. */
function isoToMillis(value: unknown): number | null {
    if (typeof value !== "string") return null;
    const millis = DateTime.fromISO(value, { zone: "utc" }).toMillis();
    return Number.isNaN(millis) ? null : millis;
}

/**
 * Sorts ISO timestamps by instant. The default comparator misorders fractional seconds of
 * different widths (`.500999Z` after `.501Z`).
 *
 * Empty values sort last only ascending, because TanStack inverts the result for descending;
 * give the column `sortUndefined: "last"` to keep them last in both directions.
 */
export function sortByIsoTimestamp<T>(rowA: Row<T>, rowB: Row<T>, columnId: string) {
    const a = isoToMillis(rowA.getValue(columnId));
    const b = isoToMillis(rowB.getValue(columnId));
    if (a === null) return b === null ? 0 : 1;
    if (b === null) return -1;
    return a - b;
}
