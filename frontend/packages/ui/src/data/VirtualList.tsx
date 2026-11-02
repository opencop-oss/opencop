// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, type BoxProps } from "@chakra-ui/react";
import { useVirtualizer } from "@tanstack/react-virtual";
import { useEffect, useRef, useState, type Key, type ReactNode } from "react";

export interface VirtualListProps<T> {
    items: readonly T[];
    estimateSize: () => number;
    getItemKey: (item: T) => Key;
    renderItem: (item: T, index: number) => ReactNode;
    onReachEnd?: () => void;
    fitContent?: boolean;
    containerProps?: BoxProps;
    /** Vertical space reserved below each item, in pixels. Include it in `estimateSize` — it is measured as part of the item's row. */
    gap?: number;
}

/**
 * Shared virtualized list for potentially large collections.
 *
 * `fitContent` lets floating lists shrink to their contents while still respecting a max height.
 * Full-height lists should instead size the scroll container through `containerProps`.
 */
export function VirtualList<T>({
    items,
    estimateSize,
    getItemKey,
    renderItem,
    onReachEnd,
    fitContent = false,
    containerProps,
    gap = 0,
}: VirtualListProps<T>) {
    "use no memo";
    const [scrollElement, setScrollElement] = useState<HTMLDivElement | null>(null);
    const lastLoadedAtRef = useRef(-1);

    // eslint-disable-next-line react-hooks/incompatible-library -- acknowledged via "use no memo" above
    const virtualizer = useVirtualizer({
        count: items.length,
        getScrollElement: () => scrollElement,
        estimateSize,
        getItemKey: (index) => getItemKey(items[index]),
    });
    const virtualItems = virtualizer.getVirtualItems();
    const totalSize = virtualizer.getTotalSize();

    useEffect(() => {
        const lastItem = virtualItems[virtualItems.length - 1];
        if (!lastItem) return;

        const isAtEnd = lastItem.index === items.length - 1;
        const isScrollable =
            scrollElement && scrollElement.scrollHeight > scrollElement.clientHeight;
        const alreadyTriggered = lastLoadedAtRef.current === items.length;

        if (isAtEnd && isScrollable && !alreadyTriggered) {
            lastLoadedAtRef.current = items.length;
            onReachEnd?.();
        }
    }, [virtualItems, items.length, onReachEnd, scrollElement]);

    return (
        <Box
            {...containerProps}
            ref={setScrollElement}
            overflowY="auto"
            position="relative"
            height={fitContent ? `${totalSize}px` : containerProps?.height}
        >
            <Box position="relative" height={`${totalSize}px`}>
                {virtualItems.map((virtualItem) => {
                    const item = items[virtualItem.index];
                    return (
                        <Box
                            key={getItemKey(item)}
                            ref={virtualizer.measureElement}
                            data-index={virtualItem.index}
                            position="absolute"
                            top={0}
                            left={0}
                            width="100%"
                            pb={gap ? `${gap}px` : undefined}
                            transform={`translateY(${virtualItem.start}px)`}
                        >
                            {renderItem(item, virtualItem.index)}
                        </Box>
                    );
                })}
            </Box>
        </Box>
    );
}
