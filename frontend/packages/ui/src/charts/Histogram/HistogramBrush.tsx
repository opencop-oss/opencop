// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { type ReactElement } from "react";
import { getAxisHeight } from "./histogram.util.ts";

interface HistogramBrushProps {
    height: number;
    /** True when a brush drag is in progress (managed by parent SVG handler). */
    brushing: boolean;
    /** SVG-local X where the brush started. */
    brushStartX: number;
    /** SVG-local X of the current cursor position during a brush drag. */
    brushCurrentX: number;
}

/**
 * Renders only the visible brush selection overlay. All interaction —
 * including detecting brush completion and firing the onBrush callback —
 * is handled by the parent `Histogram` component at the SVG level.
 */
export function HistogramBrush({
    height,
    brushing,
    brushStartX,
    brushCurrentX,
}: HistogramBrushProps): ReactElement | null {
    const chartHeight = height - getAxisHeight();
    if (chartHeight <= 0) return null;

    const selX = Math.min(brushStartX, brushCurrentX);
    const selW = Math.abs(brushCurrentX - brushStartX);

    if (!brushing || selW === 0) return null;

    return (
        <rect
            x={selX}
            y={0}
            width={selW}
            height={chartHeight}
            fill="var(--chakra-colors-brand-solid)"
            opacity={0.3}
            pointerEvents="none"
        />
    );
}
