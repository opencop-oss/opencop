// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { memo, type ReactElement } from "react";

interface HistogramSparklineProps {
    points: string;
    stroke?: string;
    strokeWidth?: number;
}

export const HistogramSparkline = memo(function HistogramSparkline({
    points,
    stroke = "var(--chakra-colors-cyan-300)",
    strokeWidth = 1.5,
}: HistogramSparklineProps): ReactElement | null {
    if (!points) return null;
    return (
        <polyline
            data-testid="histogram-sparkline"
            points={points}
            fill="none"
            stroke={stroke}
            strokeWidth={strokeWidth}
            strokeLinecap="round"
            strokeLinejoin="round"
            pointerEvents="none"
        />
    );
});
