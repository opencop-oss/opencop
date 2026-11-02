// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useEffect, useMemo, useState, type RefObject } from "react";
import { useElementWidth } from "../charts/Histogram/use-element-width.hook.ts";

/** Measured row geometry: item border-box width and the container's column gap. */
interface ItemMetrics {
    width: number;
    gap: number;
}

/**
 * Returns how many uniformly-sized items fit in a flex container, reserving
 * one slot for an overflow trigger when not everything fits.
 *
 * The first rendered child is measured after layout — no pixel sizes need to be
 * hardcoded, so the count stays correct across breakpoints. Container width comes
 * from {@link useElementWidth}, so the count also tracks changes to the container's
 * flex allocation rather than only viewport breakpoints.
 *
 * Assumes every item is the same width and that the overflow trigger occupies one
 * item slot. `column-gap` is read off the container, so gapped rows are counted
 * correctly: `n` items occupy `n * width + (n - 1) * gap`.
 *
 * @param containerRef - Ref to the flex container element.
 * @param itemCount    - Total number of items in the list.
 * @returns The number of items to render, and the measured item width — `undefined`
 *          until the first measurement — for callers needing a width floor.
 */
export function useOverflowCount(
    containerRef: RefObject<HTMLElement | null>,
    itemCount: number,
): { visibleCount: number; itemWidth?: number } {
    const containerWidth = useElementWidth(containerRef);
    const [metrics, setMetrics] = useState<ItemMetrics>();

    useEffect(() => {
        const container = containerRef.current;
        const first = container?.firstElementChild;
        if (!container || !first) return;

        const width = first.getBoundingClientRect().width;
        if (width === 0) return;
        // Flex defaults column-gap to `normal`, which parses to NaN.
        const gap = Number.parseFloat(getComputedStyle(container).columnGap) || 0;

        // eslint-disable-next-line react-hooks/set-state-in-effect, @eslint-react/hooks-extra/no-direct-set-state-in-use-effect -- measurement must follow layout; the identity bail keeps a re-measure from looping
        setMetrics((prev) => (prev?.width === width && prev.gap === gap ? prev : { width, gap }));
    }, [containerRef, containerWidth, itemCount]);

    const visibleCount = useMemo(() => {
        if (!containerWidth || !metrics) return itemCount;
        const { width, gap } = metrics;
        if (itemCount * width + (itemCount - 1) * gap <= containerWidth) return itemCount;
        // Reserve one item-slot for the overflow trigger.
        return Math.max(0, Math.floor((containerWidth - width) / (width + gap)));
    }, [containerWidth, metrics, itemCount]);

    return { visibleCount: Math.min(visibleCount, itemCount), itemWidth: metrics?.width };
}
