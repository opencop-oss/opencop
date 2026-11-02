// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { DateTime } from "luxon";
import { useCallback, useEffect, useRef, useState, type ReactElement } from "react";
import {
    formatAxisLabel,
    getAxisHeight,
    pixelXToTimestamp,
    PLOT_LEFT_X,
    plotRightX,
    timestampToPixelX,
} from "./histogram.util.ts";

const HIT_AREA_WIDTH = 8;
// Sized so the timestamp (up to "MMM d HH:mm", ~12 mono chars at 9px) has clear
// padding inside the pill rather than touching its edges.
const HANDLE_WIDTH = 84;
const HANDLE_HEIGHT = 18;
const PLAYHEAD_COLOR = "var(--chakra-colors-brand-solid)";
const PLAYHEAD_LABEL_COLOR = "var(--chakra-colors-brand-contrast)";

interface HistogramPlayheadProps {
    width: number;
    height: number;
    rangeStart: string;
    rangeEnd: string;
    position: string | null;
    onPositionChange: (iso: string) => void;
    timezone?: string;
    /** Right gutter reserved for the activity axis; plot right edge is `width - rightInset`. */
    rightInset?: number;
}

export function HistogramPlayhead({
    width,
    height,
    rangeStart,
    rangeEnd,
    position,
    onPositionChange,
    timezone = "utc",
    rightInset = 0,
}: HistogramPlayheadProps): ReactElement | null {
    const chartHeight = height - getAxisHeight();
    // Content bounds (inset from the axis lines by PLOT_INSET) so the playhead
    // never sits directly on an axis. `contentLeft` is the compile-time constant
    // PLOT_LEFT_X; `contentRight` derives from width + rightInset.
    const contentLeft = PLOT_LEFT_X;
    const contentRight = plotRightX(width, rightInset);
    const [dragging, setDragging] = useState(false);
    const gRef = useRef<SVGGElement>(null);

    // Position: right edge of the content if null, otherwise mapped from ISO
    const x = position
        ? timestampToPixelX(position, rangeStart, rangeEnd, width, rightInset)
        : contentRight;

    const rangeDurationMs =
        DateTime.fromISO(rangeEnd).toMillis() - DateTime.fromISO(rangeStart).toMillis();

    const label = position
        ? formatAxisLabel(position, rangeDurationMs, timezone)
        : formatAxisLabel(rangeEnd, rangeDurationMs, timezone);

    const handleMouseDown = useCallback((e: React.MouseEvent) => {
        e.stopPropagation();
        setDragging(true);
    }, []);

    useEffect(() => {
        if (!dragging) return;

        // Coalesce mousemove → onPositionChange via requestAnimationFrame so
        // we fire at most once per frame regardless of mousemove rate. Each
        // update fans out to a zustand store write + data-pipeline rebuild +
        // histogram SVG re-render; without rAF coalescing the unthrottled
        // ~60Hz mousemove rate pushes those past one frame and the drag feels
        // laggy, especially across chunk boundaries where the GPU filter set
        // actually changes.
        let pendingMouseX: number | null = null;
        let rafId: number | null = null;

        const flush = () => {
            rafId = null;
            if (pendingMouseX === null) return;
            const mouseX = pendingMouseX;
            pendingMouseX = null;
            const iso = pixelXToTimestamp(mouseX, rangeStart, rangeEnd, width, rightInset);
            onPositionChange(iso);
        };

        const handleMouseMove = (e: MouseEvent) => {
            const svg = gRef.current?.closest("svg");
            if (!svg) return;
            const rect = svg.getBoundingClientRect();
            // Reference the module-level helpers directly so the effect depends
            // only on width + rightInset (its true inputs), not on the derived
            // contentLeft/contentRight locals.
            pendingMouseX = Math.max(
                PLOT_LEFT_X,
                Math.min(e.clientX - rect.left, plotRightX(width, rightInset)),
            );
            if (rafId === null) rafId = requestAnimationFrame(flush);
        };

        const handleMouseUp = () => {
            // Final flush so the drag-end position isn't lost if mouseup
            // arrives before the queued frame fires.
            if (rafId !== null) {
                cancelAnimationFrame(rafId);
                rafId = null;
                flush();
            }
            setDragging(false);
        };

        window.addEventListener("mousemove", handleMouseMove);
        window.addEventListener("mouseup", handleMouseUp);
        return () => {
            window.removeEventListener("mousemove", handleMouseMove);
            window.removeEventListener("mouseup", handleMouseUp);
            if (rafId !== null) cancelAnimationFrame(rafId);
        };
    }, [dragging, rangeStart, rangeEnd, width, rightInset, onPositionChange]);

    if (chartHeight <= 0 || contentRight <= contentLeft) return null;

    // Clamp handle x so it stays within the chart area
    const handleLeft = Math.max(
        contentLeft,
        Math.min(x - HANDLE_WIDTH / 2, contentRight - HANDLE_WIDTH),
    );

    return (
        <g ref={gRef}>
            {/* Visible line below the handle */}
            <line
                x1={x}
                y1={HANDLE_HEIGHT}
                x2={x}
                y2={chartHeight}
                stroke={PLAYHEAD_COLOR}
                strokeWidth={1.5}
                pointerEvents="none"
            />
            {/* Grab handle pill at the top of the line with timestamp inside.
                Tagged so the histogram's column-hover handler can suppress the
                tooltip while the cursor is over the label (the label text is
                `pointerEvents="none"`, so its events fall through to this rect). */}
            <rect
                data-playhead-handle=""
                x={handleLeft}
                y={0}
                width={HANDLE_WIDTH}
                height={HANDLE_HEIGHT}
                rx={4}
                fill={PLAYHEAD_COLOR}
                cursor="ew-resize"
                onMouseDown={handleMouseDown}
            />
            <text
                x={handleLeft + HANDLE_WIDTH / 2}
                y={HANDLE_HEIGHT / 2 + 3}
                textAnchor="middle"
                fill={PLAYHEAD_LABEL_COLOR}
                fontSize={9}
                fontFamily="var(--chakra-fonts-mono)"
                fontWeight={600}
                pointerEvents="none"
            >
                {label}
            </text>
            {/* Invisible wider hit area along the full line for grabbing */}
            <rect
                x={x - HIT_AREA_WIDTH / 2}
                y={HANDLE_HEIGHT}
                width={HIT_AREA_WIDTH}
                height={chartHeight - HANDLE_HEIGHT}
                fill="transparent"
                cursor="ew-resize"
                onMouseDown={handleMouseDown}
            />
        </g>
    );
}
