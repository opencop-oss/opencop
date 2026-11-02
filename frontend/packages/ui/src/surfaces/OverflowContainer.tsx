// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, type BoxProps } from "@chakra-ui/react";
import { useRef, type ReactNode } from "react";
import { useOverflowCount } from "./use-overflow-count.hook";

interface OverflowContainerProps<T> extends Omit<BoxProps, "children"> {
    /** Full ordered list of items to render. */
    items: readonly T[];
    /** Renders a single visible item. Must include a React `key`. */
    renderItem: (item: T) => ReactNode;
    /**
     * Renders the overflow trigger (e.g. a "…" button) when not all items fit.
     * Receives the items that could not be shown in the container.
     */
    renderOverflow: (hidden: readonly T[]) => ReactNode;
    /**
     * An item that must not end up in the overflow. When it would fold, it is shown
     * at the end of the row instead and one other item folds in its place, so the
     * row never holds more than it did before.
     */
    pinned?: T;
}

/**
 * A horizontal flex container that measures how many uniformly-sized items fit
 * and calls `renderOverflow` with the hidden remainder. One item-slot is
 * reserved for the overflow trigger when not everything fits.
 *
 * Container width is tracked via ResizeObserver so overflow adjusts correctly
 * when the container's flex allocation changes — not just on viewport breakpoints.
 *
 * `display`, `flexDir` and `minW` are layout invariants of the measurement and are
 * applied after `...rest`, so they cannot be overridden by a caller. `minW` is held
 * at one item width (two when a `pinned` item needs its own slot): without a floor,
 * the flexbox automatic-minimum-size rule lets an `overflow`-clipped container shrink
 * to zero and clip away its own overflow trigger, making the hidden items unreachable.
 * `overflow` stays configurable.
 */
export function OverflowContainer<T>({
    items,
    renderItem,
    renderOverflow,
    pinned,
    overflow = "hidden",
    ...rest
}: OverflowContainerProps<T>): ReactNode {
    const containerRef = useRef<HTMLDivElement>(null);
    const { visibleCount, itemWidth } = useOverflowCount(containerRef, items.length);

    // The width floor below reserves a slot for the pinned item, so it is shown
    // whenever it would otherwise fold, even when no other item fits beside it.
    const pinIndex = pinned === undefined ? -1 : items.indexOf(pinned);
    const foldedPin = pinned !== undefined && pinIndex >= visibleCount ? pinned : undefined;
    const floorSlots = pinned !== undefined && items.length > 1 ? 2 : 1;

    let shown: readonly T[];
    let hidden: readonly T[];
    if (foldedPin !== undefined) {
        const others = items.filter((_, i) => i !== pinIndex);
        shown = others.slice(0, Math.max(0, visibleCount - 1));
        hidden = others.slice(Math.max(0, visibleCount - 1));
    } else {
        shown = items.slice(0, visibleCount);
        hidden = items.slice(visibleCount);
    }

    return (
        <Box
            {...rest}
            ref={containerRef}
            display="flex"
            flexDir="row"
            overflow={overflow}
            minW={itemWidth ? `${itemWidth * floorSlots}px` : rest.minW}
        >
            {shown.map(renderItem)}
            {foldedPin !== undefined && renderItem(foldedPin)}
            {hidden.length > 0 && renderOverflow(hidden)}
        </Box>
    );
}
