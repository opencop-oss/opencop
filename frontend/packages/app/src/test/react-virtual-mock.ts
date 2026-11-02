// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { VirtualItem } from "@tanstack/react-virtual";
import { useEffect, useMemo } from "react";
import { vi } from "vitest";

/**
 * Global stand-in for `@tanstack/react-virtual`, installed via `resolve.alias` in
 * `vitest.config.ts` (the same mechanism as the `@opencop/websocket` and `maplibre-gl`
 * stubs). Tests never need to `vi.mock` the virtualizer — mounting a `VirtualList` or
 * `ResourceTable` just works.
 *
 * **Why a stub is unavoidable.** happy-dom implements no layout, so the real
 * virtualizer measures a zero-height scroll element and renders **nothing** — verified
 * to stay at zero rows even with `clientHeight`, `scrollHeight`,
 * `getBoundingClientRect`, and a firing `ResizeObserver` all stubbed on the scroll
 * container. Component tests use this stub because the DOM simulator cannot measure
 * layout. Browser-mode tests (`*.browser.test.ts`) exercise real layout and deliberately
 * do **not** get this alias.
 *
 * **Windowed, not pass-through.** Only {@link DEFAULT_VIRTUAL_WINDOW} items are
 * materialized, mirroring the real virtualizer, so a large fixture stays cheap to
 * mount. `getTotalSize()` still reflects the full count, as the real one does.
 *
 * To assert a list received every item without mounting every row, use the
 * {@link virtualizerMock.itemCount} spy.
 */

/**
 * Items materialized per list unless a test raises it. Wide enough for current
 * fixtures; small enough that a thousand-row table stays cheap.
 */
export const DEFAULT_VIRTUAL_WINDOW = 25;

/**
 * Only the options this stub actually reads. Consumers pass more
 * (`getScrollElement`, `overscan`, …); those are ignored, exactly as an option the real
 * virtualizer doesn't need would be.
 *
 * Hand-written rather than derived from `VirtualizerOptions`, whose generics over the
 * scroll and item element types make a `Pick<…>` awkward to express here. The *item*
 * shape — where a silent divergence actually cost us, via the `end` field the old
 * per-file mocks dropped — is pinned to the library through {@link VirtualItem} below.
 */
interface UseVirtualizerArgs {
    count: number;
    estimateSize: () => number;
    getItemKey?: (index: number) => VirtualItem["key"];
}

let windowSize: number = DEFAULT_VIRTUAL_WINDOW;

/**
 * Test-facing controls for the stub. Reset automatically between tests by
 * `test/setup.ts`, so a test only touches this when it needs a non-default window or
 * wants to assert on the hand-off.
 */
export const virtualizerMock = {
    /**
     * Raise or lower how many items materialize.
     *
     * **Call this before `render`.** The window is read during render, so a call made
     * afterwards changes nothing until something re-renders — and a test that just calls
     * it and asserts gets no error, only the old row count. (A later `rerender` does pick
     * it up, which is what makes the failure mode quiet rather than obvious.)
     *
     * `Infinity` renders everything — only worth it when a test must query rows beyond
     * the window, and it mounts the whole list (101 `EventListItem` rows is ~2.4k nodes
     * vs ~570 windowed).
     */
    setWindow(size: number) {
        windowSize = size;
    },

    /** Items currently materialized per list. */
    get window() {
        return windowSize;
    },

    /**
     * Spy called with the item `count` each list receives. Assert with
     * `expect(virtualizerMock.itemCount).toHaveBeenLastCalledWith(101)`.
     *
     * This is how you catch a `.slice()` cap between a data layer and the list.
     * Counting DOM rows cannot: it either under-reports (only the window renders) or
     * forces the whole list to render. It also survives a sort-order change, where
     * probing for one row by name does not.
     */
    itemCount: vi.fn<(count: number) => void>(),

    /** Ref callback passed to consumers; assert on it directly when relevant. */
    measureElement: vi.fn(),

    /**
     * Spy for the imperative scroll `ResourceTable` uses to return a filtered grid to the first
     * row. A spy rather than a no-op because happy-dom has no layout: nothing moves, so the call
     * itself is the only observable behaviour.
     */
    scrollToOffset: vi.fn<(offset: number) => void>(),

    /** Called from `test/setup.ts` in a global `beforeEach`. */
    reset() {
        windowSize = DEFAULT_VIRTUAL_WINDOW;
        virtualizerMock.itemCount.mockReset();
        virtualizerMock.measureElement.mockReset();
        virtualizerMock.scrollToOffset.mockReset();
    },
};

/**
 * Stub of the real hook. Only the members consumers actually use are provided
 * (`getTotalSize`, `getVirtualItems`, `measureElement`, `scrollToOffset`) — add one here
 * when a consumer starts calling it, or that call throws only in tests.
 *
 * The window is a fixed count — it does **not** derive a range from the scroll element's
 * measured height. Nothing needs it to: `VirtualList` decides whether the list is
 * scrollable from its own `scrollHeight > clientHeight` check on the element, not from
 * how many items came back. Deriving a range would also behave differently for the two
 * consumers, since `ResourceTable` supplies its scroll element through a ref (read during
 * render, so a render stale) while `VirtualList` holds it in state.
 */
export function useVirtualizer({ count, estimateSize, getItemKey }: UseVirtualizerArgs) {
    const size = estimateSize();
    const rendered = Math.min(count, windowSize);

    // Memoized for the same reason the real virtualizer memoizes: consumers put
    // `getVirtualItems()` in effect dependency arrays (`VirtualList`'s onReachEnd
    // effect, `ResourceTable`'s infinite fetch). Returning a fresh array each render
    // would re-run those effects on every render — the stub would be *less* faithful
    // than the library and could mask or invent a render loop.
    //
    // Typed as VirtualItem[] so the shape is pinned to the library rather than to this
    // file: that is what makes a dropped field (`end`, which ResourceTable reads for
    // bottom padding) a typecheck failure instead of a silent divergence. `tsc` resolves
    // the real package here — the alias is Vitest-only — and a type-only import is erased
    // at runtime, so it cannot alias back onto this module.
    const virtualItems: VirtualItem[] = useMemo(
        () =>
            Array.from({ length: rendered }, (_, index) => ({
                index,
                start: index * size,
                end: (index + 1) * size,
                size,
                // Honour the consumer's key extractor, as the real virtualizer does;
                // returning `index` unconditionally was a divergence of its own.
                key: getItemKey?.(index) ?? index,
                lane: 0,
            })),
        [rendered, size, getItemKey],
    );

    // Reported from an effect, not during render: recording it inline would be a
    // render-phase side effect (and `react-hooks/globals` rightly rejects that).
    useEffect(() => {
        virtualizerMock.itemCount(count);
    }, [count]);

    return useMemo(
        () => ({
            // The full count, deliberately — not `rendered`. Matches the real
            // virtualizer, which sizes the scroll container for every item.
            getTotalSize: () => count * size,
            getVirtualItems: () => virtualItems,
            measureElement: virtualizerMock.measureElement,
            scrollToOffset: virtualizerMock.scrollToOffset,
        }),
        [count, size, virtualItems],
    );
}
