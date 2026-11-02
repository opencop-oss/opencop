// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { apiFetch, type FetchOptions } from "../client.ts";

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

/**
 * Metric discriminator on histogram bucket values. Backend (core's
 * `HistogramBucketValue` record) splits each bucket into one entry per metric:
 * - `ACTIVE`  — entities alive in the bucket (stable population count)
 * - `UPDATED` — entities that emitted a sample in the bucket (traffic volume)
 *
 * Named members so callers never hand-type the wire tokens. `@opencop/ui` is a
 * leaf package and can't import this; it keeps its own mirror (`HISTOGRAM_METRIC`
 * in `Histogram`), so keep the two in sync.
 */
export const HISTOGRAM_METRIC = {
    Active: "ACTIVE",
    Updated: "UPDATED",
} as const;
export type HistogramMetric = (typeof HISTOGRAM_METRIC)[keyof typeof HISTOGRAM_METRIC];

export interface HistogramBucketValue {
    key: string;
    metric: HistogramMetric;
    count: number;
}

export interface HistogramBucket {
    start: string;
    end: string;
    values: HistogramBucketValue[];
}

export interface HistogramResponse {
    buckets: HistogramBucket[];
    /**
     * The raw window the client asked for (echoes the request `from`/`to`).
     * Optional because the perf bridge and older backends only return `buckets`.
     */
    requestedFrom?: string;
    requestedTo?: string;
    /**
     * The window the backend actually bucketed, after the planner snapped the
     * requested range to resolution/chunk boundaries. The buckets tile exactly
     * `[effectiveFrom, effectiveTo)`, and the historical chunk slots use the same
     * planner grid — so this is the time domain that lines the histogram bars up
     * with the chunk-overlay bars. Identical across sources for a given request.
     */
    effectiveFrom?: string;
    effectiveTo?: string;
    /** Resolution bucket the planner chose (e.g. `MINUTE_1`, `HOUR_1`). */
    resolution?: string;
    /** Bucket/chunk count for the effective range. */
    chunks?: number;
    /** Width of each bucket in milliseconds. */
    bucketWidthMillis?: number;
}

export interface HistogramParams {
    from: string;
    to: string;
    bars: number;
}

// ---------------------------------------------------------------------------
// Historical Tracks
// ---------------------------------------------------------------------------

export interface HistoricalSubjectRequest {
    subjectType: "SOURCE" | "LAYER";
    subjectId: string;
}

/**
 * Mirrors core's `HistoricalFilterRequest(String type, Map<String, Object> params)`.
 *
 * The shape is intentionally open: the backend accepts any `type`/`params` pair and
 * incorporates them into the chunk cache key (see `HistoricalCacheIdentity`), but no
 * planner or query builder switches on `type` yet — filters are a forward-compat hook
 * for future features (e.g. bbox, attribute predicates). Tighten to a discriminated
 * union once concrete filter types are defined on both sides.
 */
export interface HistoricalFilterRequest {
    type: string;
    params: Record<string, unknown>;
}

export interface HistoricalTracksRequest {
    start: string;
    end: string;
    filters: HistoricalFilterRequest[];
    sources: HistoricalSubjectRequest[];
}

export interface HistoricalSubjectAck {
    subjectType: "SOURCE" | "LAYER";
    subjectId: string;
    /**
     * Job id for this subject's historical query. Binary chunk frames are stamped
     * with this id (the `history-job-id` OCMP header) so a listener can accept only
     * the chunks for its own job — see `HistoricalSession.handleBinaryChunk`.
     */
    jobId: string;
}

export interface HistoricalTracksAck {
    requestId: string;
    status: "ACCEPTED";
    subjects: HistoricalSubjectAck[];
}

export async function submitHistoricalTracks(
    request: HistoricalTracksRequest,
    options?: FetchOptions,
): Promise<HistoricalTracksAck> {
    return apiFetch<HistoricalTracksAck>("/history/tracks", {
        method: "POST",
        body: JSON.stringify(request),
        signal: options?.signal,
    });
}

// ---------------------------------------------------------------------------
// Fetch
// ---------------------------------------------------------------------------

export async function fetchSourceHistogram(
    sourceId: string,
    params: HistogramParams,
    options?: FetchOptions,
): Promise<HistogramResponse> {
    const searchParams = new URLSearchParams({
        from: params.from,
        to: params.to,
        bars: String(params.bars),
    });
    return apiFetch<HistogramResponse>(`/events/${sourceId}/histogram?${searchParams}`, {
        signal: options?.signal,
    });
}
