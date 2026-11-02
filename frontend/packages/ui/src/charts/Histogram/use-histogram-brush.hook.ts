// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useCallback, useEffect, useRef, useState, type MouseEvent, type RefObject } from "react";
import { pixelXToTimestamp, PLOT_LEFT_X, plotRightX } from "./histogram.util.ts";

/** Minimum brush width (px) below which a gesture is treated as a click, not a zoom. */
const MIN_BRUSH_PX = 5;

export interface HistogramBrush {
    /** True while a drag is in progress — drives the brush rect's visibility. */
    brushing: boolean;
    /** Gesture anchor X (px, SVG-relative). */
    brushStartX: number;
    /** Current cursor X (px, SVG-relative), clamped to the plot area. */
    brushCurrentX: number;
    /** Attach to the SVG's `onMouseDown` to begin a brush gesture. */
    onMouseDown: (e: MouseEvent<SVGSVGElement>) => void;
}

/**
 * Brush-to-zoom gesture for the histogram. Tracks the drag at the SVG level (no
 * transparent overlay rect, so bar hover still works) via window listeners that
 * live only while dragging, and fires `onBrush(from, to)` on mouseup when the
 * selection clears {@link MIN_BRUSH_PX}. A no-op when `canBrush` is false.
 */
export function useHistogramBrush({
    svgRef,
    width,
    canBrush,
    rangeStart,
    rangeEnd,
    onBrush,
    rightInset = 0,
}: {
    svgRef: RefObject<SVGSVGElement | null>;
    width: number;
    canBrush: boolean;
    rangeStart: string | undefined;
    rangeEnd: string | undefined;
    onBrush: ((from: string, to: string) => void) | undefined;
    /** Right gutter reserved for the activity axis; brush clamps to `width - rightInset`. */
    rightInset?: number;
}): HistogramBrush {
    const [brushing, setBrushing] = useState(false);
    const [brushStartX, setBrushStartX] = useState(0);
    const [brushCurrentX, setBrushCurrentX] = useState(0);
    const brushStartXRef = useRef(0);
    const brushCurrentXRef = useRef(0);

    const onMouseDown = useCallback(
        (e: MouseEvent<SVGSVGElement>) => {
            if (!canBrush) return;
            const svg = svgRef.current;
            if (!svg) return;
            const rect = svg.getBoundingClientRect();
            const x = e.clientX - rect.left;
            brushStartXRef.current = x;
            brushCurrentXRef.current = x;
            setBrushStartX(x);
            setBrushCurrentX(x);
            setBrushing(true);
        },
        [canBrush, svgRef],
    );

    useEffect(() => {
        if (!brushing) return;
        const handleMove = (e: globalThis.MouseEvent) => {
            const svg = svgRef.current;
            if (!svg) return;
            const rect = svg.getBoundingClientRect();
            const x = Math.max(
                PLOT_LEFT_X,
                Math.min(e.clientX - rect.left, plotRightX(width, rightInset)),
            );
            brushCurrentXRef.current = x;
            setBrushCurrentX(x);
        };
        const handleUp = () => {
            setBrushing(false);
            const x1 = Math.min(brushStartXRef.current, brushCurrentXRef.current);
            const x2 = Math.max(brushStartXRef.current, brushCurrentXRef.current);
            if (x2 - x1 < MIN_BRUSH_PX) return;
            if (!onBrush || !rangeStart || !rangeEnd) return;
            const from = pixelXToTimestamp(x1, rangeStart, rangeEnd, width, rightInset);
            const to = pixelXToTimestamp(x2, rangeStart, rangeEnd, width, rightInset);
            onBrush(from, to);
        };
        window.addEventListener("mousemove", handleMove);
        window.addEventListener("mouseup", handleUp);
        return () => {
            window.removeEventListener("mousemove", handleMove);
            window.removeEventListener("mouseup", handleUp);
        };
    }, [brushing, width, rightInset, onBrush, rangeStart, rangeEnd, svgRef]);

    return { brushing, brushStartX, brushCurrentX, onMouseDown };
}
