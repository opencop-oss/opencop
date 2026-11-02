// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { queryOptions } from "@tanstack/react-query";
import { fetchSourceHistogram, isNonRetryableAuthError, withRequestTimeout } from "@opencop/api";

/**
 * Retries a histogram request makes after its first attempt fails.
 *
 * Deliberately kept — a large multi-source histogram has been observed to fail
 * on a server-side timeout and succeed on a later attempt, so giving up early
 * would discard requests that were about to return.
 */
const HISTOGRAM_RETRY_COUNT = 3;

/** Attempts a histogram query makes in total before it is declared failed. */
export const HISTOGRAM_MAX_ATTEMPTS = HISTOGRAM_RETRY_COUNT + 1;

/**
 * Per-*attempt* backstop for a connection that never answers at all.
 *
 * Set *above* the backend's own failure point on purpose: it is not a way to
 * fail faster (successful responses have been observed to take nearly as long
 * as the server's own timeout), only a bound on a genuinely hung request,
 * which otherwise spins forever because `queryClient.ts` sets no timeout on
 * data fetches.
 *
 * A timeout abort is an ordinary error, so it is retried like any other. The
 * bound on the whole query is therefore roughly
 * `HISTOGRAM_MAX_ATTEMPTS * HISTOGRAM_REQUEST_TIMEOUT_MS` plus React Query's
 * backoff — about three minutes, not 45 seconds. That is deliberate: a hang is
 * usually transient, and the evidence behind {@link HISTOGRAM_RETRY_COUNT} (a
 * real success on a later attempt) argues against treating any single failure
 * as terminal. Make the timeout non-retryable only if a hung histogram turns
 * out to be a persistent state rather than a blip.
 */
const HISTOGRAM_REQUEST_TIMEOUT_MS = 45_000;

export const historicalQueries = {
    all: ["historical"] as const,
    histograms: () => [...historicalQueries.all, "histogram"] as const,
    histogram: (
        sourceId: string,
        params: { from: string; to: string; bars: number },
        activated: boolean,
    ) =>
        queryOptions({
            queryKey: [...historicalQueries.histograms(), sourceId, params] as const,
            // Composing the timeout with React Query's own `signal` keeps
            // cancellation intact: an unmount still aborts through `signal` and
            // is treated as a cancellation, while a timeout abort surfaces as an
            // ordinary (retryable, `isError`-visible) query failure.
            queryFn: ({ signal }) =>
                fetchSourceHistogram(sourceId, params, {
                    signal: withRequestTimeout(signal, HISTOGRAM_REQUEST_TIMEOUT_MS),
                }),
            // Declared here rather than inherited from the global default so
            // `HISTOGRAM_MAX_ATTEMPTS` — which the status bar counts against
            // when it narrates a retry — is a contract of this query, not an
            // assumption about an unrelated app-wide setting.
            retry: (failureCount, error) =>
                !isNonRetryableAuthError(error) && failureCount < HISTOGRAM_RETRY_COUNT,
            enabled: activated && !!sourceId && !!params.from && !!params.to && params.bars > 0,
            staleTime: 30_000,
        }),
};
