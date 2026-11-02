// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useQueries, type UseQueryResult } from "@tanstack/react-query";
import { useCallback } from "react";
import type { HistogramResponse } from "@opencop/api";

import { HISTOGRAM_MAX_ATTEMPTS, historicalQueries } from "../data-plane/historical.queries";

export interface MultiSourceHistograms {
    /**
     * Each selected source's histogram response (undefined while loading/errored).
     * Read-only: `combine` hands back the same reference across renders while the
     * data is unchanged, and consumers depend on that identity being stable.
     */
    responses: ReadonlyMap<string, HistogramResponse | undefined>;
    /** True while any selected source's histogram is still loading. */
    isLoading: boolean;
    /**
     * Count of sources with a histogram to draw — a fresh success, or a
     * same-key refetch failure that still retains the prior successful data.
     */
    loadedCount: number;
    /**
     * Count of sources that failed with nothing to draw. Deliberately
     * excludes a same-key refetch failure that retains prior data: that
     * source is counted in {@link loadedCount} instead, since the chart still
     * has something to show for it. Without this split, TanStack Query's
     * `isRefetchError = isError && hasData` would let a single source push
     * both `allResolved` and `allFailed` true at once.
     */
    errorCount: number;
    /** Total selected sources. */
    total: number;
    /**
     * Every selected source's chart has resolved with data for the *current*
     * query params. False when nothing is selected.
     *
     * Changing the range, zoom, or interval is a new query key, and
     * `useQueries` carries no previous data across one — its `placeholderData`
     * is never handed a `previousData` argument, so a pending replacement
     * reports no data rather than the prior range's. That is what lets this
     * stay a claim about the range on screen without a placeholder guard.
     */
    allResolved: boolean;
    /** Every selected source's chart gave up. False when nothing is selected. */
    allFailed: boolean;
    /**
     * 1-based attempt number of the in-flight request that has failed the most
     * times so far, or 0 when nothing is in flight. A value above 1 means at
     * least one source's histogram is being retried — which is what separates a
     * slow query from a failing one on screen.
     */
    attempt: number;
    /** Attempts each query makes before it is declared failed. */
    totalAttempts: number;
}

/**
 * Fan out one histogram fetch per selected source (the endpoint is per-source).
 * Every query uses identical `from`/`to`/`bars`, so the returned buckets are
 * index-aligned across sources — see {@link assembleMultiSourceBuckets}.
 *
 * The returned `responses` map preserves the caller's source order via the keys'
 * insertion order; assembly and palette assignment both key off the same
 * `sourceIds` order, so stack position stays stable.
 */
export function useMultiSourceHistograms(
    sourceIds: readonly string[],
    params: { from: string; to: string; bars: number },
    activated: boolean,
): MultiSourceHistograms {
    // `combine` (memoized via `useCallback`) lets React Query reuse the same
    // assembled result reference across renders while the underlying query data
    // is unchanged — so the `responses` map keeps a stable identity and consumers
    // that depend on it (e.g. the status model's `histogramHasEvents` memo) skip
    // their bucket scan until the data actually changes.
    const combine = useCallback(
        (results: UseQueryResult<HistogramResponse>[]): MultiSourceHistograms => {
            const responses = new Map<string, HistogramResponse | undefined>();
            let isLoading = false;
            let loadedCount = 0;
            let errorCount = 0;
            let attempt = 0;
            sourceIds.forEach((sourceId, i) => {
                const r = results[i];
                const hasData = r?.data !== undefined;
                responses.set(sourceId, r?.data);
                if (r?.isLoading) {
                    isLoading = true;
                    // `failureCount` counts attempts that have already failed and
                    // resets on success, so the attempt now running is one past it.
                    // Only in-flight queries contribute: a settled failure is
                    // reported as an error, not as an attempt still being made.
                    attempt = Math.max(attempt, r.failureCount + 1);
                }
                // A same-key refetch failure keeps the prior data (`isError` and
                // `hasData` both true) — see `errorCount`'s doc comment. Only a
                // failure with nothing retained counts as "given up".
                if (r?.isError && !hasData) errorCount += 1;
                if (hasData) loadedCount += 1;
            });
            const total = sourceIds.length;
            return {
                responses,
                isLoading,
                loadedCount,
                errorCount,
                total,
                allResolved: total > 0 && loadedCount === total,
                allFailed: total > 0 && errorCount === total,
                attempt,
                totalAttempts: HISTOGRAM_MAX_ATTEMPTS,
            };
        },
        [sourceIds],
    );

    return useQueries({
        queries: sourceIds.map((sourceId) =>
            historicalQueries.histogram(sourceId, params, activated),
        ),
        combine,
    });
}
