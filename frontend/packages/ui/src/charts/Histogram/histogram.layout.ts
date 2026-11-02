// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { DateTime } from "luxon";
import {
    CHUNK_STATUS,
    DEFAULT_SOURCE_COLOR,
    GAP_COLOR,
    type HistogramChunkStatus,
} from "./histogram.constants.ts";
import { AXIS_TOP_PAD, BAR_GAP, timestampToPixelX, type BarLayout } from "./histogram.util.ts";
import type {
    HistogramBucket,
    HistogramChunkRange,
    HistogramHoverInfo,
    HistogramSourceSeries,
} from "./histogram.types.ts";

/** One stacked, per-source colored chunk-overlay rect. */
interface ActiveEventStackSegment {
    key: string;
    x: number;
    y: number;
    w: number;
    h: number;
    fill: string;
    status: HistogramChunkStatus;
}

/** One stacked, per-source grayscale density-skeleton rect. */
interface ExpectedActiveEventStackSegment {
    key: string;
    x: number;
    y: number;
    w: number;
    h: number;
    fill: string;
}

/** One per-source activity polyline (points string + stroke color). */
interface SourceActiveEventUpdateLine {
    sourceId: string;
    color: string;
    points: string;
}

/**
 * Multi-source: stacked, per-source colored chunk bars. Group ranges by time
 * slot (sources share a slot start), stack each source's element count in
 * `sourceColors` key order, and scale to `expectedActiveEventMax` — the SAME
 * stable Y axis as the density skeleton — so a source that loads before the
 * others renders at its final expected height (not full-height against a partial
 * chunk max that shrinks as more sources arrive). Returns `null` to fall back to
 * the legacy single-color overlay.
 */
export function computeActiveEventStacks({
    showActiveEvents,
    sourceColors,
    chunkRanges,
    rangeStart,
    rangeEnd,
    width,
    axisY,
    expectedActiveEventMax,
    rightInset = 0,
}: {
    showActiveEvents: boolean;
    sourceColors: ReadonlyMap<string, string> | undefined;
    chunkRanges: readonly HistogramChunkRange[] | undefined;
    rangeStart: string | undefined;
    rangeEnd: string | undefined;
    width: number;
    axisY: number;
    expectedActiveEventMax: number;
    /** Right gutter reserved for the activity axis; keeps chunk bars aligned
     * with the density bars (which already account for it via `bars`). */
    rightInset?: number;
}): ActiveEventStackSegment[] | null {
    if (
        !showActiveEvents ||
        !sourceColors ||
        sourceColors.size === 0 ||
        !chunkRanges ||
        chunkRanges.length === 0 ||
        !rangeStart ||
        !rangeEnd ||
        width === 0
    ) {
        return null;
    }
    const bySlot = new Map<string, HistogramChunkRange[]>();
    for (const r of chunkRanges) {
        if (r.sourceId === undefined) continue;
        const list = bySlot.get(r.start);
        if (list) list.push(r);
        else bySlot.set(r.start, [r]);
    }
    if (bySlot.size === 0) return null;
    let stackMax = 0;
    for (const list of bySlot.values()) {
        const total = list.reduce((s, r) => s + r.count, 0);
        if (total > stackMax) stackMax = total;
    }
    if (stackMax <= 0) return null;
    // Scale segment heights to the density skeleton's stable max so a source
    // that loads early renders at its final height (and doesn't shrink as the
    // others arrive). Fall back to the streamed chunk max if density is hidden
    // or empty, so the bars still fill the chart.
    const scale = expectedActiveEventMax > 0 ? expectedActiveEventMax : stackMax;
    const order = [...sourceColors.keys()];
    // Stack grows up from the baseline (axisY); heights scale to the drawable
    // height that reserves AXIS_TOP_PAD of headroom, matching the density bars.
    const drawableHeight = axisY - AXIS_TOP_PAD;
    const segs: ActiveEventStackSegment[] = [];
    for (const [start, list] of bySlot) {
        const x1 = timestampToPixelX(start, rangeStart, rangeEnd, width, rightInset);
        const x2 = timestampToPixelX(list[0].end, rangeStart, rangeEnd, width, rightInset);
        // Trim the right edge by BAR_GAP so adjacent (contiguous) chunk
        // columns show the same 1px gap as the density bars.
        const w = Math.max(1, x2 - x1 - BAR_GAP);
        let yCursor = axisY;
        for (const sourceId of order) {
            const r = list.find((x) => x.sourceId === sourceId);
            if (!r) continue;
            const isGap =
                r.status === CHUNK_STATUS.Missing || r.status === CHUNK_STATUS.IncompleteParts;
            const h = (r.count / scale) * drawableHeight;
            // A missing chunk has count 0; show a thin red marker so the gap
            // is still visible in the stack.
            const drawH = isGap ? Math.max(2, h) : h;
            if (drawH <= 0) continue;
            yCursor -= drawH;
            segs.push({
                key: `${sourceId} ${start}`,
                x: x1,
                y: yCursor,
                w,
                h: drawH,
                fill: isGap ? GAP_COLOR : (sourceColors.get(sourceId) ?? DEFAULT_SOURCE_COLOR),
                status: r.status ?? CHUNK_STATUS.Ok,
            });
        }
    }
    return segs;
}

/**
 * Multi-source density skeleton: grayscale, per-source stacked segments on the
 * ACTIVE scale. The skeleton is an informational reference of expected activity
 * shape that the colored chunk bars populate — so it's non-interactive (plain
 * rects, no hover treatment) and scaled to its own `expectedActiveEventMax`
 * (independent of the chunk layer's scale). Returns `null` to fall back to the
 * legacy single summed gray bar.
 */
export function computeExpectedActiveEventStacks({
    showExpectedActiveEvents,
    sourceSeries,
    bars,
    expectedActiveEventMax,
    axisY,
}: {
    showExpectedActiveEvents: boolean;
    sourceSeries: readonly HistogramSourceSeries[] | undefined;
    bars: BarLayout[];
    expectedActiveEventMax: number;
    axisY: number;
}): ExpectedActiveEventStackSegment[] | null {
    if (
        !showExpectedActiveEvents ||
        !sourceSeries ||
        sourceSeries.length === 0 ||
        bars.length === 0 ||
        expectedActiveEventMax <= 0
    ) {
        return null;
    }
    // Heights scale to the drawable height that reserves AXIS_TOP_PAD of
    // headroom (matching the colored bars), while the stack grows up from axisY.
    const drawableHeight = axisY - AXIS_TOP_PAD;
    const segs: ExpectedActiveEventStackSegment[] = [];
    for (const bar of bars) {
        let yCursor = axisY;
        for (const s of sourceSeries) {
            const active = s.active[bar.index] ?? 0;
            if (active <= 0) continue;
            const h = (active / expectedActiveEventMax) * drawableHeight;
            if (h <= 0) continue;
            yCursor -= h;
            segs.push({
                key: `${s.sourceId}:${bar.index}`,
                x: bar.x,
                y: yCursor,
                w: bar.width,
                h,
                fill: s.shade,
            });
        }
    }
    return segs.length > 0 ? segs : null;
}

/**
 * Map an activity (`UPDATED`) count to a y-pixel. The activity series has its
 * own labeled right-hand axis (see the right `yTicks` in {@link HistogramAxis}),
 * so it uses the **full** chart height like every other series — `max` maps to
 * the top (`y = 0`), `0` to the baseline (`y = axisY`). There is no longer a
 * fractional height cap; the labeled axis is what keeps the scale honest.
 * Reserves {@link AXIS_TOP_PAD} of headroom at the top (matching the bars and the
 * y-axis ticks), so `max` lands at `AXIS_TOP_PAD` rather than `y = 0`.
 */
function activityToY(count: number, max: number, axisY: number): number {
    return axisY - (count / max) * (axisY - AXIS_TOP_PAD);
}

/**
 * Multi-source activity overlay: one sparkline per source, drawn in the source's
 * categorical color, all sharing a single y-scale (`max` = the max `UPDATED`
 * across every source, supplied by the caller so the same value drives the
 * right-hand axis ticks) so heights are comparable. Each point is anchored at
 * the **start (left edge)** of its bucket, not the midpoint, so its horizontal
 * position reflects when the bucket begins. Returns `null` when there is no
 * positive activity to draw.
 */
export function computeSourceActiveEventUpdates({
    showActiveEventUpdates,
    sourceSeries,
    bars,
    axisY,
    max,
}: {
    showActiveEventUpdates: boolean;
    sourceSeries: readonly HistogramSourceSeries[] | undefined;
    bars: BarLayout[];
    axisY: number;
    /** Max `UPDATED` across all sources (the activity axis scale). */
    max: number;
}): SourceActiveEventUpdateLine[] | null {
    if (
        !showActiveEventUpdates ||
        !sourceSeries ||
        sourceSeries.length === 0 ||
        bars.length === 0 ||
        max <= 0
    ) {
        return null;
    }
    const toY = (count: number) => activityToY(count, max, axisY);
    const last = bars[bars.length - 1];
    // Each bucket contributes one vertex at its left edge (its start), so the
    // loop's first iteration already covers the first bucket's left edge — no
    // separate lead-in point is needed. A trailing point extends the line to the
    // last bucket's right edge so it spans the full plotted width.
    const rightEdgeX = last.x + last.width;
    return sourceSeries.map((s) => {
        const pts: string[] = [];
        for (const bar of bars) {
            // Anchor at the bucket's left edge (its start), not the midpoint.
            const cx = bar.x;
            pts.push(`${cx.toFixed(2)},${toY(s.activity[bar.index] ?? 0).toFixed(2)}`);
        }
        pts.push(`${rightEdgeX.toFixed(2)},${toY(s.activity[last.index] ?? 0).toFixed(2)}`);
        return { sourceId: s.sourceId, color: s.color, points: pts.join(" ") };
    });
}

/** One per-source on-axis hover marker: where the hovered bucket's activity sits. */
export interface ActivityHoverMarker {
    sourceId: string;
    color: string;
    /** X of the line vertex for the hovered bucket (its left edge). */
    pointX: number;
    /** Y of the value on the activity scale (shared by the line vertex and axis dot). */
    y: number;
    /** The raw `UPDATED` value, for an optional inline label. */
    value: number;
}

/**
 * Per-source markers for the hovered bucket: the y-position of each source's
 * activity value on the (full-height) activity scale, plus the x of its line
 * vertex. The caller draws a color-matched dot on the right axis at `y`, a guide
 * line from `(pointX, y)` to the axis, and a ring on the line vertex. Returns
 * `null` when nothing is hovered or there is no activity to mark.
 */
export function computeActivityHoverMarkers({
    hoveredBucketIndex,
    sourceSeries,
    bars,
    axisY,
    max,
}: {
    hoveredBucketIndex: number | null;
    sourceSeries: readonly HistogramSourceSeries[] | undefined;
    bars: BarLayout[];
    axisY: number;
    max: number;
}): ActivityHoverMarker[] | null {
    if (hoveredBucketIndex === null || !sourceSeries || sourceSeries.length === 0 || max <= 0) {
        return null;
    }
    const bar = bars[hoveredBucketIndex];
    if (!bar) return null;
    return sourceSeries.map((s) => {
        const value = s.activity[hoveredBucketIndex] ?? 0;
        return {
            sourceId: s.sourceId,
            color: s.color,
            pointX: bar.x,
            y: activityToY(value, max, axisY),
            value,
        };
    });
}

/**
 * Assemble the hover-tooltip model for the bucket under the cursor: one row per
 * source, each carrying its unique-event count (from the chunk whose [start,
 * end) covers the bucket midpoint — linear scan, `chunkRanges` is small) and its
 * activity (UPDATED at the bucket). Returns `null` when no bucket is hovered.
 */
export function computeHoverInfo({
    hoveredBucketIndex,
    buckets,
    chunkRanges,
    sourceSeries,
}: {
    hoveredBucketIndex: number | null;
    buckets: HistogramBucket[];
    chunkRanges: readonly HistogramChunkRange[] | undefined;
    sourceSeries: readonly HistogramSourceSeries[] | undefined;
}): HistogramHoverInfo | null {
    if (hoveredBucketIndex === null) return null;
    const bucket = buckets[hoveredBucketIndex];
    if (!bucket) return null;
    const midMs =
        (DateTime.fromISO(bucket.start).toMillis() + DateTime.fromISO(bucket.end).toMillis()) / 2;

    const perSource = (sourceSeries ?? []).map((s) => {
        let count = 0;
        if (chunkRanges) {
            for (const r of chunkRanges) {
                if (r.sourceId !== s.sourceId) continue;
                const rs = DateTime.fromISO(r.start).toMillis();
                const re = DateTime.fromISO(r.end).toMillis();
                if (midMs >= rs && midMs < re) {
                    count = r.count;
                    break;
                }
            }
        }
        return {
            sourceId: s.sourceId,
            name: s.name,
            color: s.color,
            count,
            activity: s.activity[hoveredBucketIndex] ?? 0,
        };
    });

    return { start: bucket.start, end: bucket.end, perSource };
}
