// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { HistogramChunkStatus, HistogramMetric } from "./histogram.constants.ts";

// Re-exported here so callers can pull the chart's wire-token unions from the
// same module as its prop types.
export type { HistogramChunkStatus, HistogramMetric };

/**
 * Backend may split each bucket into multiple entries, one per metric (see
 * {@link HistogramMetric}). The histogram density bars visualize `ACTIVE`
 * only; `UPDATED` counts can be 10–100× larger and dominate the shared Y axis.
 * When a bucket value has no `metric` field (legacy single-series shape), it is
 * treated as the sole series and passes through the filter unchanged.
 */
export interface HistogramBucket {
    start: string;
    end: string;
    values: { key: string; metric?: HistogramMetric; count: number }[];
}

/** A range to visualize as a translucent band overlaying the density bars. */
export interface HistogramChunkRange {
    /**
     * Owning source id. When `sourceColors` is provided, chunk bars are stacked
     * per source and colored by it (multi-source mode). Omitted for the legacy
     * single-source overlay.
     */
    sourceId?: string;
    /** Range start (ISO). */
    start: string;
    /** Range end (ISO). */
    end: string;
    /** Element count for this range (shown in the tooltip). */
    count: number;
    /**
     * Delivery status. Omitted = `"ok"`. `"missing"` / `"incomplete-parts"`
     * render as full-height muted-red bars to signal a gap; tooltip text
     * adapts to match.
     */
    status?: HistogramChunkStatus;
    /** Populated only when `status === "incomplete-parts"`. */
    parts?: { seen: number; expected: number };
}

/**
 * Per-source metadata + series for the multi-source histogram. One entry per
 * selected source, in stack order. Both `active` and `activity` are
 * index-aligned with `buckets`:
 *   - `active`   — the `ACTIVE` metric; segment height in the grayscale density
 *     skeleton (stacked per source using `shade`).
 *   - `activity` — the `UPDATED` metric; the per-source activity sparkline
 *     (drawn in `color`) and the hover tooltip's activity column.
 * The per-source unique-event `count` in the tooltip comes from `chunkRanges`
 * (matched by `sourceId` at the hovered time), not from here.
 */
export interface HistogramSourceSeries {
    sourceId: string;
    name: string;
    /** Categorical color — colored chunk bars, activity sparkline, legend. */
    color: string;
    /** Grayscale shade — this source's segment in the density skeleton. */
    shade: string;
    /** `ACTIVE` per bucket — density skeleton segment heights. */
    active: number[];
    /** `UPDATED` per bucket — activity sparkline + tooltip activity column. */
    activity: number[];
}

export interface HistogramProps {
    buckets: HistogramBucket[];
    /** Height in pixels (default 100) */
    height?: number;
    /** Show loading skeleton */
    isLoading?: boolean;
    /**
     * Overrides the default empty-state text shown when there are no buckets
     * and not loading. Use to differentiate "no data for range" from
     * "user hasn't requested data yet".
     */
    emptyText?: string;
    /** IANA timezone for axis/tooltip labels (default "utc") */
    timezone?: string;
    /** Called when user completes a brush selection */
    onBrush?: (from: string, to: string) => void;
    /** Controlled playhead position as ISO timestamp; null = right edge */
    playheadPosition?: string | null;
    /** Called when user drags the playhead */
    onPlayheadChange?: (iso: string) => void;
    /** Enable brush interaction (default true when onBrush is provided) */
    brushEnabled?: boolean;
    /** Full time range start ISO — for pixel↔time mapping */
    rangeStart?: string;
    /** Full time range end ISO — for pixel↔time mapping */
    rangeEnd?: string;
    /**
     * Optional coverage ranges rendered as a translucent band at the top of
     * the chart area, marking which time windows have data coverage.
     */
    chunkRanges?: readonly HistogramChunkRange[];
    /**
     * Show the gray ACTIVE density bars. Defaults to true (the pre-existing
     * rendering).
     */
    showExpectedActiveEvents?: boolean;
    /**
     * Show the brand-color per-chunk overlay bars. Defaults to true. Has no
     * effect when `chunkRanges` is undefined/empty.
     */
    showActiveEvents?: boolean;
    /**
     * Show the cyan event-activity sparkline derived from `UPDATED` metric
     * values in `buckets`. Defaults to false — opt-in for historical mode.
     */
    showActiveEventUpdates?: boolean;
    /**
     * Per-source color map (selection order). When provided AND `chunkRanges`
     * carry `sourceId`, the chunk overlay renders as **stacked per-source
     * colored bars** on their own element-count scale (multi-source mode),
     * instead of the single translucent brand-color band. Sources stack in this
     * map's key order so a source keeps a stable vertical position.
     */
    sourceColors?: ReadonlyMap<string, string>;
    /**
     * Per-source series (name, color, activity). When provided, the hover
     * tooltip shows one row per source — swatch + name + unique-event count +
     * activity — instead of the default expected-active-events /
     * active-events / active-event-updates rows.
     */
    sourceSeries?: readonly HistogramSourceSeries[];
}

/** One per-source row in the multi-source hover tooltip. */
export interface HistogramHoverRow {
    sourceId: string;
    name: string;
    color: string;
    /** Unique-event count from the chunk covering the hovered time. */
    count: number;
    /** `UPDATED` at the hovered bucket. */
    activity: number;
}

/** Assembled hover-tooltip model for a single bucket: one row per visible source. */
export interface HistogramHoverInfo {
    start: string;
    end: string;
    perSource: HistogramHoverRow[];
}
