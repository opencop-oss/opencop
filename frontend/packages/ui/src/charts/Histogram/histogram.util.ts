// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { DateTime } from "luxon";

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

export interface BarLayout {
    x: number;
    y: number;
    width: number;
    height: number;
    index: number;
}

export interface ScaleResult {
    bars: BarLayout[];
    maxCount: number;
    barWidth: number;
}

export interface AxisTick {
    x: number;
    label: string;
}

export interface YAxisTick {
    y: number;
    label: string;
}

// ---------------------------------------------------------------------------
// Layout constants
// ---------------------------------------------------------------------------

/** Pixel gap between adjacent bar columns (density bars, and matched by the chunk bars). */
export const BAR_GAP = 1;
const AXIS_HEIGHT = 24;
/** Left gutter: holds the events-axis numbers plus the vertical axis title. */
export const Y_AXIS_WIDTH = 48;
/**
 * Width reserved on the RIGHT for the activity (`UPDATED`) axis numbers plus its
 * vertical title, mirroring {@link Y_AXIS_WIDTH} on the left. Only reserved when
 * the activity overlay is shown — the caller passes this as `rightInset` to the
 * layout/pixel functions so bars, chunk overlay, brush, and playhead all share
 * the same plot width. When the overlay is hidden the inset is 0 and the plot
 * spans to the full width as before.
 */
export const RIGHT_AXIS_WIDTH = 52;

/**
 * Small gap between the plotted content (bars, activity line, playhead, brush)
 * and each y-axis line, so the content — especially the playhead at the right
 * edge — doesn't sit directly on top of an axis. The axis lines stay at the
 * gutter edges ({@link Y_AXIS_WIDTH} / `width - rightInset`); the content is
 * inset by this much on both sides.
 */
const PLOT_INSET = 6;

/**
 * Left x where plotted content begins (inset from the left axis line). A
 * compile-time constant — the left gutter never varies at runtime, unlike
 * {@link plotRightX}, which depends on the container width and the right gutter.
 */
export const PLOT_LEFT_X = Y_AXIS_WIDTH + PLOT_INSET;

/** Right x where plotted content ends (inset from the right axis / chart edge). */
export function plotRightX(containerWidth: number, rightInset = 0): number {
    return containerWidth - rightInset - PLOT_INSET;
}

/**
 * Headroom (px) reserved at the TOP of the plot, above the tallest bar/line. The
 * max-value y-axis tick lands here instead of at y=0, so its number label (drawn
 * a few px below the tick) clears the SVG's clipped top edge instead of being
 * sliced off, and the peak of every series gets a little breathing room. Applied
 * consistently to bars, the density skeleton, chunk bars, the activity line, and
 * the y-axis ticks (every value→y mapping) so all series stay aligned.
 */
export const AXIS_TOP_PAD = 10;

// ---------------------------------------------------------------------------
// Scale calculations
// ---------------------------------------------------------------------------

/**
 * Compute pixel positions for each histogram bar.
 * Reserves AXIS_HEIGHT at the bottom for X-axis labels
 * and Y_AXIS_WIDTH on the left for Y-axis labels.
 */
export function computeBarLayout(
    buckets: { values: { count: number }[] }[],
    containerWidth: number,
    containerHeight: number,
    /**
     * Optional scale override used to normalize bar heights. When the histogram
     * shares its Y-axis with an overlay (e.g. chunk bars) the caller can pass
     * the maximum of all series so density bars stay visually consistent with
     * the axis. Falls back to the intrinsic density max when not provided.
     */
    scaleOverride?: number,
    /** Right gutter reserved for the activity axis (see {@link RIGHT_AXIS_WIDTH}). */
    rightInset = 0,
): ScaleResult {
    const count = buckets.length;
    if (count === 0) return { bars: [], maxCount: 0, barWidth: 0 };

    const chartWidth = plotRightX(containerWidth, rightInset) - PLOT_LEFT_X;
    const chartHeight = containerHeight - AXIS_HEIGHT;
    // Reserve AXIS_TOP_PAD of headroom: a full-height bar tops out here, not at
    // y=0, so the max y-axis label clears the SVG's clipped top edge.
    const drawableHeight = Math.max(0, chartHeight - AXIS_TOP_PAD);
    const totalGap = (count - 1) * BAR_GAP;
    const barWidth = Math.max(1, (chartWidth - totalGap) / count);

    const maxCount = buckets.reduce((max, bucket) => {
        const total = bucket.values.reduce((sum, v) => sum + v.count, 0);
        return Math.max(max, total);
    }, 0);

    const scale = scaleOverride && scaleOverride > 0 ? scaleOverride : maxCount;

    const bars: BarLayout[] = buckets.map((bucket, i) => {
        const total = bucket.values.reduce((sum, v) => sum + v.count, 0);
        const height = scale > 0 ? (total / scale) * drawableHeight : 0;
        return {
            x: PLOT_LEFT_X + i * (barWidth + BAR_GAP),
            y: chartHeight - height,
            width: barWidth,
            height,
            index: i,
        };
    });

    return { bars, maxCount, barWidth };
}

/**
 * Get the axis height constant for layout calculations.
 */
export function getAxisHeight(): number {
    return AXIS_HEIGHT;
}

// ---------------------------------------------------------------------------
// Y-axis ticks
// ---------------------------------------------------------------------------

const countFormatter = new Intl.NumberFormat(undefined, {
    notation: "compact",
    maximumFractionDigits: 1,
});

/**
 * Compute Y-axis tick positions and labels.
 * Picks ~4 nice round values between 0 and maxCount.
 */
export function computeYAxisTicks(maxCount: number, containerHeight: number): YAxisTick[] {
    const chartHeight = containerHeight - AXIS_HEIGHT;
    if (maxCount === 0 || chartHeight <= 0) return [];

    const targetTicks = 4;
    const rawStep = maxCount / targetTicks;

    // Round to a nice step: 1, 2, 5, 10, 20, 50, 100, ...
    const magnitude = Math.pow(10, Math.floor(Math.log10(rawStep)));
    const normalized = rawStep / magnitude;
    let niceStep: number;
    if (normalized <= 1.5) niceStep = 1;
    else if (normalized <= 3.5) niceStep = 2;
    else if (normalized <= 7.5) niceStep = 5;
    else niceStep = 10;
    niceStep *= magnitude;
    niceStep = Math.max(1, Math.round(niceStep));

    // Match the AXIS_TOP_PAD headroom the bars/series use, so a tick at value V
    // lands at the same y as a bar/line at V (gridlines stay aligned with data).
    const drawableHeight = Math.max(0, chartHeight - AXIS_TOP_PAD);
    const ticks: YAxisTick[] = [];
    for (let value = niceStep; value <= maxCount; value += niceStep) {
        const y = chartHeight - (value / maxCount) * drawableHeight;
        ticks.push({ y, label: countFormatter.format(value) });
    }

    return ticks;
}

// ---------------------------------------------------------------------------
// X-axis ticks
// ---------------------------------------------------------------------------

/**
 * Select evenly-spaced bucket indices for axis labels.
 * Avoids overlapping labels by limiting to maxLabels.
 */
export function computeAxisTicks(
    buckets: { start: string; end: string }[],
    containerWidth: number,
    rangeDurationMs: number,
    maxLabels = 8,
    timezone = "utc",
    rightInset = 0,
): AxisTick[] {
    const count = buckets.length;
    if (count === 0) return [];

    const chartWidth = plotRightX(containerWidth, rightInset) - PLOT_LEFT_X;
    const totalGap = (count - 1) * BAR_GAP;
    const barWidth = Math.max(1, (chartWidth - totalGap) / count);

    const labelCount = Math.min(count, maxLabels);
    const step = Math.max(1, Math.floor(count / labelCount));

    const ticks: AxisTick[] = [];
    for (let i = 0; i < count; i += step) {
        ticks.push({
            x: PLOT_LEFT_X + i * (barWidth + BAR_GAP) + barWidth / 2,
            label: formatAxisLabel(buckets[i].start, rangeDurationMs, timezone),
        });
    }

    return ticks;
}

// ---------------------------------------------------------------------------
// Formatting
// ---------------------------------------------------------------------------

/**
 * Format an ISO timestamp for the axis.
 * - Ranges < 24h: time only (HH:mm)
 * - Ranges 1–7 days: short date + time (MMM d HH:mm)
 * - Ranges > 7 days: date only (MMM d)
 */
export function formatAxisLabel(
    isoString: string,
    rangeDurationMs: number,
    timezone = "utc",
): string {
    const dt = DateTime.fromISO(isoString, { zone: timezone });
    const hours = rangeDurationMs / (1000 * 60 * 60);

    if (hours <= 24) return dt.toFormat("HH:mm");
    if (hours <= 168) return dt.toFormat("MMM d HH:mm");
    return dt.toFormat("MMM d");
}

// ---------------------------------------------------------------------------
// Pixel ↔ Time conversion (for brush-to-zoom and playhead)
// ---------------------------------------------------------------------------

/**
 * Convert an SVG x-coordinate to an ISO timestamp.
 * Linear interpolation over the full time range, accounting for Y_AXIS_WIDTH.
 */
export function pixelXToTimestamp(
    x: number,
    rangeStartIso: string,
    rangeEndIso: string,
    containerWidth: number,
    rightInset = 0,
): string {
    const left = PLOT_LEFT_X;
    const chartWidth = plotRightX(containerWidth, rightInset) - left;
    const ratio = Math.max(0, Math.min(1, (x - left) / chartWidth));
    const startMs = DateTime.fromISO(rangeStartIso).toMillis();
    const endMs = DateTime.fromISO(rangeEndIso).toMillis();
    const ms = startMs + ratio * (endMs - startMs);
    return DateTime.fromMillis(ms, { zone: "utc" }).toISO()!;
}

/**
 * Convert an ISO timestamp to an SVG x-coordinate.
 * Inverse of pixelXToTimestamp.
 */
export function timestampToPixelX(
    iso: string,
    rangeStartIso: string,
    rangeEndIso: string,
    containerWidth: number,
    rightInset = 0,
): number {
    const startMs = DateTime.fromISO(rangeStartIso).toMillis();
    const endMs = DateTime.fromISO(rangeEndIso).toMillis();
    const ms = DateTime.fromISO(iso).toMillis();
    const durationMs = endMs - startMs;
    const left = PLOT_LEFT_X;
    if (durationMs <= 0) return left;
    const ratio = Math.max(0, Math.min(1, (ms - startMs) / durationMs));
    const chartWidth = plotRightX(containerWidth, rightInset) - left;
    return left + ratio * chartWidth;
}
