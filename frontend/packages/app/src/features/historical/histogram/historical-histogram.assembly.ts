// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * Combine N per-source histogram responses into a single index-aligned,
 * per-source/per-bucket model for the multi-source histogram.
 *
 * The backend histogram endpoint is per-source (`GET /v1/events/{sourceId}/histogram`),
 * so multi-source requires one fetch per source. Because every source is fetched
 * with **identical `from`/`to`/`bars`**, the returned buckets share the same
 * boundaries and align by index — assembly is a per-index gather, not a
 * re-bucketing problem.
 *
 * - `active` (the `ACTIVE` metric) drives the grayscale stacked expected-active-events
 *   skeleton; the per-bucket `totalActive` is the stacked column height (y-axis max input).
 * - `updated` (the `UPDATED` metric) drives the per-source active-event-updates line.
 */
import Logger from "js-logger";

import { HISTOGRAM_METRIC, type HistogramBucket, type HistogramResponse } from "@opencop/api";

const logger = Logger.get("historical");

export interface SourceBucketDatum {
    sourceId: string;
    /** Entities active in the bucket — expected-active-events skeleton segment height. */
    active: number;
    /** Update volume in the bucket — active-event-updates line value. */
    updated: number;
}

export interface MultiSourceBucket {
    start: string;
    end: string;
    /** Per-source data in the provided source order (stable stack position). */
    sources: SourceBucketDatum[];
    /** Sum of `active` across all sources — the stacked column total. */
    totalActive: number;
}

function sumMetric(bucket: HistogramBucket | undefined, kind: "active" | "updated"): number {
    if (!bucket) return 0;
    const metric = kind === "active" ? HISTOGRAM_METRIC.Active : HISTOGRAM_METRIC.Updated;
    let total = 0;
    for (const v of bucket.values) {
        if (v.metric === metric) total += v.count;
    }
    return total;
}

/**
 * Assemble the index-aligned model. The reference axis (bucket count + each
 * bucket's start/end) is taken from whichever source response currently has the
 * most buckets, so a still-loading or errored source (missing or short response)
 * contributes zeros rather than truncating the axis. Returns `[]` when no source
 * has any buckets yet.
 *
 * Index-alignment is a *precondition*, not something this gather enforces: every
 * source must be fetched with identical `from`/`to`/`bars` so bucket `i` means
 * the same time slot across sources. A DEV-only check warns if a present bucket's
 * boundary diverges from the axis (the tell that a caller broke that invariant);
 * in production the gather trusts the index.
 */
export function assembleMultiSourceBuckets(
    sourceIds: readonly string[],
    responses: ReadonlyMap<string, HistogramResponse | undefined>,
): MultiSourceBucket[] {
    let axis: HistogramBucket[] = [];
    for (const id of sourceIds) {
        const buckets = responses.get(id)?.buckets;
        if (buckets && buckets.length > axis.length) axis = buckets;
    }
    if (axis.length === 0) return [];

    let warnedMisalignment = false;
    return axis.map((refBucket, i) => {
        let totalActive = 0;
        const sources = sourceIds.map((sourceId) => {
            const bucket = responses.get(sourceId)?.buckets[i];
            if (
                import.meta.env.DEV &&
                !warnedMisalignment &&
                bucket &&
                bucket.start !== refBucket.start
            ) {
                warnedMisalignment = true;
                logger.warn(
                    `[assembly] histogram bucket boundary mismatch at index ${i} for ` +
                        `source ${sourceId}: axis=${refBucket.start} source=${bucket.start}. ` +
                        `All sources must be fetched with identical from/to/bars for ` +
                        `index-alignment to hold; the assembled model will be wrong.`,
                );
            }
            const active = sumMetric(bucket, "active");
            const updated = sumMetric(bucket, "updated");
            totalActive += active;
            return { sourceId, active, updated };
        });
        return { start: refBucket.start, end: refBucket.end, sources, totalActive };
    });
}

/** Max stacked column height across all buckets — the expected-active-events y-axis maximum. */
export function maxStackedActive(buckets: readonly MultiSourceBucket[]): number {
    let max = 0;
    for (const b of buckets) if (b.totalActive > max) max = b.totalActive;
    return max;
}
