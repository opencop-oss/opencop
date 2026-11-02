// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useMemo } from "react";
import { formatRangeBounds } from "./range-format";

/**
 * Returns minute-precision timestamps for the current range, formatted in the
 * user's timezone. Wraps the shared `formatRangeBounds` so the status bar and
 * the range-picker trigger display the same string for the same ISO range.
 */
export function useRangeTimestamps(
    from: string | null,
    to: string | null,
    timezone: string,
): { startLabel: string; endLabel: string } {
    return useMemo(() => formatRangeBounds(from, to, timezone), [from, to, timezone]);
}
