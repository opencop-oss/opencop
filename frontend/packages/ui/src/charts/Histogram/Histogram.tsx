// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, Spinner, Text } from "@chakra-ui/react";
import { DateTime } from "luxon";
import { useCallback, useMemo, useRef, useState, type ReactElement, type MouseEvent } from "react";
import { useTranslation } from "react-i18next";
import { HistogramAxis } from "./HistogramAxis.tsx";
import { HistogramBrush } from "./HistogramBrush.tsx";
import { HistogramPlayhead } from "./HistogramPlayhead.tsx";
import { HistogramSparkline } from "./HistogramSparkline.tsx";
import { HistogramTooltip } from "./HistogramTooltip.tsx";
import { CHUNK_STATUS, HISTOGRAM_METRIC } from "./histogram.constants.ts";
import {
    computeActiveEventStacks,
    computeActivityHoverMarkers,
    computeExpectedActiveEventStacks,
    computeHoverInfo,
    computeSourceActiveEventUpdates,
} from "./histogram.layout.ts";
import type { HistogramProps } from "./histogram.types.ts";
import { useElementWidth } from "./use-element-width.hook.ts";
import { useHistogramBrush } from "./use-histogram-brush.hook.ts";
import {
    computeAxisTicks,
    computeBarLayout,
    computeYAxisTicks,
    getAxisHeight,
    RIGHT_AXIS_WIDTH,
} from "./histogram.util.ts";

export function Histogram({
    buckets,
    height = 100,
    isLoading = false,
    emptyText,
    timezone = "utc",
    onBrush,
    playheadPosition,
    onPlayheadChange,
    brushEnabled,
    rangeStart,
    rangeEnd,
    chunkRanges,
    showExpectedActiveEvents = true,
    showActiveEvents = true,
    showActiveEventUpdates = false,
    sourceColors,
    sourceSeries,
}: HistogramProps): ReactElement {
    const { t } = useTranslation();
    const containerRef = useRef<HTMLDivElement>(null);
    const svgRef = useRef<SVGSVGElement>(null);
    const width = useElementWidth(containerRef);
    const [hoveredBucketIndex, setHoveredBucketIndex] = useState<number | null>(null);

    // Track mouse position within the container (for the in-SVG crosshair) and in
    // viewport coordinates (for the portaled, viewport-clamped tooltip below).
    const [mouseX, setMouseX] = useState(0);
    const [mouseClient, setMouseClient] = useState({ x: 0, y: 0 });

    // Max `UPDATED` across all sources — the activity series' own scale, used
    // both for its (full-height) line geometry and its right-hand axis ticks.
    const activeEventUpdateMax = useMemo(() => {
        if (!showActiveEventUpdates || !sourceSeries) return 0;
        let m = 0;
        for (const s of sourceSeries) for (const v of s.activity) if (v > m) m = v;
        return m;
    }, [showActiveEventUpdates, sourceSeries]);

    // Reserve a right gutter for the activity axis only when it has something to
    // draw, so toggling the overlay (not other states) reflows the plot.
    const rightInset = activeEventUpdateMax > 0 ? RIGHT_AXIS_WIDTH : 0;
    const plotRight = width - rightInset;

    const canBrush = !!(onBrush && rangeStart && rangeEnd && (brushEnabled ?? true));
    // Brush-to-zoom is tracked at the SVG level (no overlay rect, so bar hover
    // still works); the hook owns the drag state + window listeners.
    const {
        brushing,
        brushStartX,
        brushCurrentX,
        onMouseDown: handleSvgMouseDown,
    } = useHistogramBrush({ svgRef, width, canBrush, rangeStart, rangeEnd, onBrush, rightInset });

    const isEmpty = !isLoading && buckets.length === 0;

    const rangeDurationMs = useMemo(
        () =>
            buckets.length >= 2
                ? DateTime.fromISO(buckets[buckets.length - 1].end).toMillis() -
                  DateTime.fromISO(buckets[0].start).toMillis()
                : 0,
        [buckets],
    );

    const ticks = useMemo(
        () =>
            width > 0
                ? computeAxisTicks(buckets, width, rangeDurationMs, 8, timezone, rightInset)
                : [],
        [buckets, width, rangeDurationMs, timezone, rightInset],
    );

    // Density bars render the ACTIVE metric only. `UPDATED` counts can be
    // an order of magnitude larger and would crush the chart if summed in.
    // Backends that haven't migrated to the metric-split shape emit values
    // without a `metric` field; those pass through unchanged as density.
    const expectedActiveEventBuckets = useMemo(
        () =>
            buckets.map((b) => ({
                ...b,
                values: b.values.filter((v) => !v.metric || v.metric === HISTOGRAM_METRIC.Active),
            })),
        [buckets],
    );

    // The Y-axis tracks the grayscale ACTIVE density skeleton — the expected
    // final shape, available from the REST histogram immediately. The colored
    // chunk bars scale to this same max (see `computeActiveEventStacks`) so each
    // source's segment loads in at its final height instead of rescaling as the
    // others stream in.
    const expectedActiveEventMax = useMemo(() => {
        if (!showExpectedActiveEvents) return 0;
        return expectedActiveEventBuckets.reduce((max, bucket) => {
            const total = bucket.values.reduce((sum, v) => sum + v.count, 0);
            return Math.max(max, total);
        }, 0);
    }, [showExpectedActiveEvents, expectedActiveEventBuckets]);

    const { bars } = useMemo(
        () =>
            width > 0
                ? computeBarLayout(
                      expectedActiveEventBuckets,
                      width,
                      height,
                      expectedActiveEventMax,
                      rightInset,
                  )
                : { bars: [] },
        [expectedActiveEventBuckets, width, height, expectedActiveEventMax, rightInset],
    );
    const yTicks = useMemo(
        () => (width > 0 ? computeYAxisTicks(expectedActiveEventMax, height) : []),
        [expectedActiveEventMax, height, width],
    );
    // Right-hand activity axis ticks, on the activity scale (same full-height
    // mapping the line uses, so a tick at value V lines up with the line at V).
    const activityYTicks = useMemo(
        () =>
            width > 0 && activeEventUpdateMax > 0
                ? computeYAxisTicks(activeEventUpdateMax, height)
                : [],
        [activeEventUpdateMax, height, width],
    );
    const axisY = height - getAxisHeight();

    // `isEmpty` suppresses the chart itself, not just an overlaid message: with
    // no density buckets the Y axis has no max to scale against, so drawing the
    // chunk overlay on its own produces a plausible-looking chart on a
    // fabricated scale. While a fetch is still in flight the overlay does
    // render, so streamed chunks stay visible as progress.
    const showChart = width > 0 && !isEmpty && (bars.length > 0 || (chunkRanges?.length ?? 0) > 0);

    // Per-source colored chunk bars, stacked on the stable density Y axis.
    const activeEventStacks = useMemo(
        () =>
            computeActiveEventStacks({
                showActiveEvents,
                sourceColors,
                chunkRanges,
                rangeStart,
                rangeEnd,
                width,
                axisY,
                expectedActiveEventMax,
                rightInset,
            }),
        [
            showActiveEvents,
            sourceColors,
            chunkRanges,
            rangeStart,
            rangeEnd,
            width,
            axisY,
            expectedActiveEventMax,
            rightInset,
        ],
    );

    // Per-source stacked density skeleton (grayscale, informational only).
    const expectedActiveEventStacks = useMemo(
        () =>
            computeExpectedActiveEventStacks({
                showExpectedActiveEvents,
                sourceSeries,
                bars,
                expectedActiveEventMax,
                axisY,
            }),
        [showExpectedActiveEvents, sourceSeries, bars, expectedActiveEventMax, axisY],
    );

    // One activity line per source, sharing a single y-scale.
    const sourceActiveEventUpdates = useMemo(
        () =>
            computeSourceActiveEventUpdates({
                showActiveEventUpdates,
                sourceSeries,
                bars,
                axisY,
                max: activeEventUpdateMax,
            }),
        [showActiveEventUpdates, sourceSeries, bars, axisY, activeEventUpdateMax],
    );

    // Column-wide hover: track which bucket the cursor is over (floor-divide
    // by computed bar pitch). Hovering the playhead handle, the axis area, or
    // empty space beyond the bars clears the hover. Mouse coordinates are
    // only written to state when the cursor is actually over a bucket so we
    // don't trigger a re-render on every pixel of cursor motion in dead
    // space.
    const handleContainerMouseMove = useCallback(
        (e: MouseEvent<HTMLDivElement>) => {
            const el = containerRef.current;
            if (!el) return;
            if (bars.length === 0) {
                setHoveredBucketIndex(null);
                return;
            }
            // Over the playhead label pill: the user is reading/grabbing the
            // playhead, not inspecting a bucket — suppress the hover tooltip.
            if ((e.target as Element).closest("[data-playhead-handle]")) {
                setHoveredBucketIndex(null);
                return;
            }
            const rect = el.getBoundingClientRect();
            const x = e.clientX - rect.left;
            const y = e.clientY - rect.top;
            const first = bars[0];
            const last = bars[bars.length - 1];
            if (x < first.x || x > last.x + last.width || y >= axisY) {
                setHoveredBucketIndex(null);
                return;
            }
            const pitch = bars.length > 1 ? bars[1].x - first.x : last.width + 1;
            const idx = Math.max(0, Math.min(bars.length - 1, Math.floor((x - first.x) / pitch)));
            setHoveredBucketIndex(idx);
            setMouseX(x);
            setMouseClient({ x: e.clientX, y: e.clientY });
        },
        [bars, axisY],
    );

    const handleContainerMouseLeave = useCallback(() => {
        setHoveredBucketIndex(null);
    }, []);

    // Per-source tooltip rows for the hovered bucket.
    const hoverInfo = useMemo(
        () => computeHoverInfo({ hoveredBucketIndex, buckets, chunkRanges, sourceSeries }),
        [hoveredBucketIndex, buckets, chunkRanges, sourceSeries],
    );

    // Per-source on-axis markers: where the hovered bucket's activity value sits
    // on the right-hand activity scale (color-matched dot + guide to the axis).
    const activityHoverMarkers = useMemo(
        () =>
            computeActivityHoverMarkers({
                hoveredBucketIndex,
                sourceSeries,
                bars,
                axisY,
                max: activeEventUpdateMax,
            }),
        [hoveredBucketIndex, sourceSeries, bars, axisY, activeEventUpdateMax],
    );

    // Crosshair follows the cursor's X smoothly rather than snapping to the
    // bar midpoint — feels more direct. `hoveredBucketIndex` still gates
    // visibility so the line disappears when the cursor leaves the chart
    // region (the same gate the tooltip uses).
    const crosshairX = hoveredBucketIndex !== null ? mouseX : null;

    // Compose the accessible name. When the chunk overlay contains
    // missing/incomplete entries, append a count so screen-reader users
    // know data didn't fully load — color + hover tooltip alone aren't
    // enough (WCAG 1.4.1).
    const gapCounts = useMemo(() => {
        if (!chunkRanges || chunkRanges.length === 0) return null;
        let missing = 0;
        let incomplete = 0;
        for (const r of chunkRanges) {
            if (r.status === CHUNK_STATUS.Missing) missing++;
            else if (r.status === CHUNK_STATUS.IncompleteParts) incomplete++;
        }
        return missing > 0 || incomplete > 0 ? { missing, incomplete } : null;
    }, [chunkRanges]);
    const baseLabel = t("historical.histogramLabel", "Event activity histogram");
    // Gated on `showChart`, not just `gapCounts`: with the chart suppressed the
    // gap bars aren't drawn, and `role="img"` makes the children presentational —
    // so an ungated label would be the *only* thing a screen reader gets, and it
    // would describe a chart that isn't there.
    const ariaLabel =
        showChart && gapCounts
            ? t(
                  "historical.histogramLabelWithGaps",
                  "Event activity histogram — {{missing}} missing, {{incomplete}} incomplete chunks",
                  gapCounts,
              )
            : baseLabel;

    return (
        <Box
            ref={containerRef}
            role="img"
            aria-label={ariaLabel}
            w="full"
            h={`${height}px`}
            position="relative"
            onMouseMove={handleContainerMouseMove}
            onMouseLeave={handleContainerMouseLeave}
        >
            {isLoading && (
                <Box
                    position="absolute"
                    inset="0"
                    display="flex"
                    alignItems="center"
                    justifyContent="center"
                >
                    <Spinner size="sm" color="brand.solid" />
                </Box>
            )}

            {isEmpty && (
                <Box
                    position="absolute"
                    inset="0"
                    display="flex"
                    alignItems="center"
                    justifyContent="center"
                >
                    <Text fontSize="xs" color="fg.muted">
                        {emptyText ?? t("historical.noData", "No data for this time range")}
                    </Text>
                </Box>
            )}

            {showChart && (
                <svg
                    ref={svgRef}
                    width={width}
                    height={height}
                    style={{
                        display: "block",
                        userSelect: "none",
                        cursor: canBrush ? "crosshair" : "default",
                    }}
                    onDragStart={(e) => e.preventDefault()}
                    onMouseDown={handleSvgMouseDown}
                >
                    <defs>
                        {/* Drop shadow lifts the per-source activity lines off
                                the stacked bars behind them so they stay legible. */}
                        <filter
                            id="histogram-sparkline-shadow"
                            x="-20%"
                            y="-40%"
                            width="140%"
                            height="180%"
                        >
                            <feDropShadow
                                dx="0"
                                dy="0.5"
                                stdDeviation="1.2"
                                floodColor="#000"
                                floodOpacity="0.85"
                            />
                        </filter>
                    </defs>
                    {/* Axis renders first so gridlines are behind bars */}
                    <HistogramAxis
                        ticks={ticks}
                        yTicks={yTicks}
                        width={width}
                        y={axisY}
                        plotRight={plotRight}
                        rightYTicks={activityYTicks}
                    />
                    {/* Multi-source: grayscale, per-source stacked density
                            skeleton — informational only, so non-interactive. */}
                    {showExpectedActiveEvents && expectedActiveEventStacks && (
                        <g pointerEvents="none">
                            {expectedActiveEventStacks.map((s) => (
                                <rect
                                    key={s.key}
                                    data-testid="histogram-density-bar"
                                    x={s.x}
                                    y={s.y}
                                    width={Math.max(0, s.w)}
                                    height={Math.max(0, s.h)}
                                    fill={s.fill}
                                    rx={1}
                                />
                            ))}
                        </g>
                    )}
                    {showActiveEvents && activeEventStacks && (
                        <g pointerEvents="none">
                            {activeEventStacks.map((s) => {
                                // Highlight the whole column the cursor is over
                                // (chunk bars are the interactive layer now —
                                // the density skeleton is not). Pixel test
                                // against the cursor X avoids time-bucket math.
                                const isHover =
                                    hoveredBucketIndex !== null &&
                                    mouseX >= s.x &&
                                    mouseX < s.x + s.w;
                                return (
                                    <rect
                                        key={s.key}
                                        data-testid="histogram-chunk-bar"
                                        data-status={s.status}
                                        x={s.x}
                                        y={s.y}
                                        width={s.w}
                                        height={s.h}
                                        fill={s.fill}
                                        fillOpacity={isHover ? 0.85 : 0.55}
                                        stroke={isHover ? s.fill : "none"}
                                        strokeWidth={isHover ? 1 : 0}
                                        rx={1}
                                    />
                                );
                            })}
                        </g>
                    )}
                    {/* One colored activity line per source, each lifted off the
                            bars with a drop shadow. */}
                    {showActiveEventUpdates && sourceActiveEventUpdates && (
                        <g filter="url(#histogram-sparkline-shadow)">
                            {sourceActiveEventUpdates.map((s) => (
                                <HistogramSparkline
                                    key={s.sourceId}
                                    points={s.points}
                                    stroke={s.color}
                                />
                            ))}
                        </g>
                    )}
                    {canBrush && (
                        <HistogramBrush
                            height={height}
                            brushing={brushing}
                            brushStartX={brushStartX}
                            brushCurrentX={brushCurrentX}
                        />
                    )}
                    {onPlayheadChange && rangeStart && rangeEnd && (
                        <HistogramPlayhead
                            width={width}
                            height={height}
                            rangeStart={rangeStart}
                            rangeEnd={rangeEnd}
                            position={playheadPosition ?? null}
                            onPositionChange={onPlayheadChange}
                            timezone={timezone}
                            rightInset={rightInset}
                        />
                    )}
                    {/* On-axis hover markers: per source, a color-matched dot on
                            the activity axis at the hovered value, a guide line to
                            it, and a ring on the line vertex. */}
                    {activityHoverMarkers && (
                        <g data-testid="histogram-activity-markers" pointerEvents="none">
                            {activityHoverMarkers.map((m) => (
                                <g key={m.sourceId}>
                                    <line
                                        x1={m.pointX}
                                        y1={m.y}
                                        x2={plotRight}
                                        y2={m.y}
                                        stroke={m.color}
                                        strokeWidth={1}
                                        strokeDasharray="2 3"
                                        opacity={0.8}
                                    />
                                    <circle
                                        cx={m.pointX}
                                        cy={m.y}
                                        r={3.5}
                                        fill="var(--chakra-colors-gray-900)"
                                        stroke={m.color}
                                        strokeWidth={2}
                                    />
                                    <circle
                                        cx={plotRight}
                                        cy={m.y}
                                        r={4}
                                        fill={m.color}
                                        stroke="var(--chakra-colors-gray-900)"
                                        strokeWidth={1.5}
                                    />
                                </g>
                            ))}
                        </g>
                    )}
                    {/* Crosshair renders last so the hover line sits above every
                            chart series (density, chunk bars, sparkline). */}
                    {crosshairX !== null && (
                        <line
                            data-testid="histogram-crosshair"
                            x1={crosshairX}
                            x2={crosshairX}
                            y1={0}
                            y2={axisY}
                            stroke="var(--chakra-colors-gray-400)"
                            strokeWidth={1}
                            strokeDasharray="4 4"
                            pointerEvents="none"
                        />
                    )}
                </svg>
            )}

            {hoverInfo && (
                <HistogramTooltip
                    hoverInfo={hoverInfo}
                    mouseClient={mouseClient}
                    timezone={timezone}
                />
            )}
        </Box>
    );
}
