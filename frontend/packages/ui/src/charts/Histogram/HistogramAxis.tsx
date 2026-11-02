// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { memo, type ReactElement } from "react";
import { useTranslation } from "react-i18next";
import type { AxisTick, YAxisTick } from "./histogram.util.ts";
import { Y_AXIS_WIDTH } from "./histogram.util.ts";

/**
 * Minimum plot height (px) below which the rotated, vertical axis titles are
 * dropped. "Active events" (~78px wide at 10px font) is centered on the axis and
 * rotated 90°, so it needs roughly this much vertical room; below it the label
 * would overflow the SVG's top edge and clip. The panel is user-resizable down to
 * 60px, so this guard hides the titles cleanly on a shrunk panel.
 */
const AXIS_TITLE_MIN_HEIGHT = 80;

/**
 * Minimum baseline (px from the SVG top) for the topmost y-axis number. The SVG
 * clips its viewport (`overflow: hidden`), and the 9px monospace glyph box rises
 * ~11px above its baseline, so a label whose tick sits at y≈0 (the max value)
 * needs its baseline pushed down to here or its top is sliced off. Measured: at
 * baseline 8 the glyph box top landed 3px above the SVG edge.
 */
const Y_LABEL_MIN_BASELINE = 13;

interface HistogramAxisProps {
    ticks: AxisTick[];
    yTicks: YAxisTick[];
    width: number;
    y: number;
    /**
     * Right edge of the plot area. When the activity axis is shown this is
     * `width - RIGHT_AXIS_WIDTH` so the X baseline / gridlines stop short of the
     * right-axis labels; defaults to `width` (no right axis).
     */
    plotRight?: number;
    /**
     * Ticks for the right-hand activity (`UPDATED`) axis. When present, a second
     * Y axis is drawn at `plotRight` with these labels to its right.
     */
    rightYTicks?: YAxisTick[];
}

export const HistogramAxis = memo(function HistogramAxis({
    ticks,
    yTicks,
    width,
    y,
    plotRight,
    rightYTicks,
}: HistogramAxisProps): ReactElement {
    const { t } = useTranslation();
    const right = plotRight ?? width;
    return (
        <g>
            {/* X-axis baseline */}
            <line
                x1={Y_AXIS_WIDTH}
                y1={y}
                x2={right}
                y2={y}
                stroke="var(--chakra-colors-gray-600)"
                strokeWidth={1}
            />

            {/* X-axis tick marks and labels */}
            {ticks.map((tick) => (
                <g key={tick.x}>
                    <line
                        x1={tick.x}
                        y1={y}
                        x2={tick.x}
                        y2={y + 4}
                        stroke="var(--chakra-colors-gray-600)"
                        strokeWidth={1}
                    />
                    <text
                        x={tick.x}
                        y={y + 16}
                        textAnchor="middle"
                        fill="var(--chakra-colors-gray-400)"
                        fontSize={10}
                        fontFamily="monospace"
                    >
                        {tick.label}
                    </text>
                </g>
            ))}

            {/* Y-axis line */}
            <line
                x1={Y_AXIS_WIDTH}
                y1={0}
                x2={Y_AXIS_WIDTH}
                y2={y}
                stroke="var(--chakra-colors-gray-600)"
                strokeWidth={1}
            />

            {/* Left (events) axis title — rotated along the axis, left of the
                numbers. Hidden on short panels where it would clip (see
                AXIS_TITLE_MIN_HEIGHT). */}
            {y >= AXIS_TITLE_MIN_HEIGHT && (
                <text
                    x={11}
                    y={y / 2}
                    transform={`rotate(-90 11 ${y / 2})`}
                    textAnchor="middle"
                    dominantBaseline="central"
                    fill="var(--chakra-colors-gray-400)"
                    fontSize={10}
                    fontWeight={500}
                >
                    {t("historical.tooltipEvents", "Active events")}
                </text>
            )}

            {/* Y-axis gridlines (behind bars — render first). Driven by the left
                (events) axis; stop at the plot's right edge so they don't run
                under the activity-axis labels. */}
            {yTicks.map((tick) => (
                <line
                    key={`grid-${tick.y}`}
                    x1={Y_AXIS_WIDTH}
                    y1={tick.y}
                    x2={right}
                    y2={tick.y}
                    stroke="var(--chakra-colors-gray-700)"
                    strokeWidth={0.5}
                    strokeDasharray="4 4"
                />
            ))}

            {/* Y-axis tick marks and labels.
                Labels sit at tick.y + 3 by default (baseline a few px below the
                tick line, so the text visually centers on the line), clamped to
                Y_LABEL_MIN_BASELINE so the topmost label doesn't get its top
                sliced off by the SVG's clipped viewport. */}
            {yTicks.map((tick) => (
                <g key={tick.y}>
                    <line
                        x1={Y_AXIS_WIDTH - 4}
                        y1={tick.y}
                        x2={Y_AXIS_WIDTH}
                        y2={tick.y}
                        stroke="var(--chakra-colors-gray-600)"
                        strokeWidth={1}
                    />
                    <text
                        x={Y_AXIS_WIDTH - 6}
                        y={Math.max(tick.y + 3, Y_LABEL_MIN_BASELINE)}
                        textAnchor="end"
                        fill="var(--chakra-colors-gray-400)"
                        fontSize={9}
                        fontFamily="monospace"
                    >
                        {tick.label}
                    </text>
                </g>
            ))}

            {/* Right-hand activity (UPDATED) axis — own scale, labels to the
                right of the plot. Drawn only when the activity overlay is on. */}
            {rightYTicks && rightYTicks.length > 0 && (
                <g data-testid="histogram-activity-axis">
                    <line
                        x1={right}
                        y1={0}
                        x2={right}
                        y2={y}
                        stroke="var(--chakra-colors-gray-600)"
                        strokeWidth={1}
                    />

                    {/* Right (updates) axis title — rotated, right of the
                        numbers. Hidden on short panels where it would clip. */}
                    {y >= AXIS_TITLE_MIN_HEIGHT && (
                        <text
                            x={width - 11}
                            y={y / 2}
                            transform={`rotate(90 ${width - 11} ${y / 2})`}
                            textAnchor="middle"
                            dominantBaseline="central"
                            fill="var(--chakra-colors-gray-400)"
                            fontSize={10}
                            fontWeight={500}
                        >
                            {t("historical.tooltipActivity", "Updates")}
                        </text>
                    )}
                    {rightYTicks.map((tick) => (
                        <g key={`right-${tick.y}`}>
                            <line
                                x1={right}
                                y1={tick.y}
                                x2={right + 4}
                                y2={tick.y}
                                stroke="var(--chakra-colors-gray-600)"
                                strokeWidth={1}
                            />
                            <text
                                x={right + 6}
                                y={Math.max(tick.y + 3, Y_LABEL_MIN_BASELINE)}
                                textAnchor="start"
                                fill="var(--chakra-colors-gray-400)"
                                fontSize={9}
                                fontFamily="monospace"
                            >
                                {tick.label}
                            </text>
                        </g>
                    ))}
                </g>
            )}
        </g>
    );
});
