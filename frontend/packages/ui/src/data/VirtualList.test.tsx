// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { render } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { OcThemeProvider } from "../theme/OcThemeProvider";
import { VirtualList } from "./VirtualList";

const ITEMS = ["alpha", "bravo", "charlie"];
const ITEM_SIZE = 40;

function list(onReachEnd: () => void, items: readonly string[] = ITEMS) {
    return (
        <OcThemeProvider>
            <VirtualList
                items={items}
                estimateSize={() => ITEM_SIZE}
                getItemKey={(item) => item}
                renderItem={(item) => item}
                onReachEnd={onReachEnd}
            />
        </OcThemeProvider>
    );
}

/**
 * Deliberately taller than these fixtures need: `onReachEnd` only fires once the
 * *last* item is rendered, so the virtualizer's window has to cover every item.
 * Keeping slack here means adding a fixture item can't silently stop the callback
 * from firing.
 */
const VIEWPORT_HEIGHT = ITEM_SIZE * 10;

/**
 * Gives the scroll container a height, and content that either overflows it or doesn't.
 *
 * Call this *before* `render`. `VirtualList` captures its scroll container and measures
 * it as it mounts, which is the point at which a browser already has real dimensions.
 * Faking the geometry afterwards is invisible to both React and the virtualizer —
 * nothing re-measures, so `onReachEnd` would never re-evaluate.
 *
 * Spied on `HTMLElement.prototype` for both properties even though happy-dom defines
 * `clientHeight` there and `scrollHeight` on `Element.prototype`; spying the subclass
 * shadows the inherited getter. `vi.restoreAllMocks()` below undoes both.
 */
function stubScrollContainer({ scrollable }: { scrollable: boolean }) {
    vi.spyOn(HTMLElement.prototype, "clientHeight", "get").mockReturnValue(VIEWPORT_HEIGHT);
    vi.spyOn(HTMLElement.prototype, "scrollHeight", "get").mockReturnValue(
        scrollable ? VIEWPORT_HEIGHT * 2 : VIEWPORT_HEIGHT,
    );
}

afterEach(() => {
    vi.restoreAllMocks();
});

describe("VirtualList onReachEnd", () => {
    it("fires when the final item is rendered in a scrollable list", () => {
        const onReachEnd = vi.fn();
        stubScrollContainer({ scrollable: true });

        render(list(onReachEnd));

        expect(onReachEnd).toHaveBeenCalledTimes(1);
    });

    it("does not fire when the list is not scrollable", () => {
        const onReachEnd = vi.fn();
        stubScrollContainer({ scrollable: false });

        render(list(onReachEnd));

        expect(onReachEnd).not.toHaveBeenCalled();
    });

    it("does not fire again when the parent re-renders with the same item count", () => {
        const onReachEnd = vi.fn();
        stubScrollContainer({ scrollable: true });
        // A fresh arrow per render is the common shape at call sites
        // (`onReachEnd={() => fetchNextPage()}`), and it changes the identity of one of
        // the effect's dependencies — so the effect genuinely re-runs here. Passing a
        // stable `onReachEnd` instead would leave every dependency untouched, the effect
        // would never re-enter, and this test would pass without exercising the guard.
        const view = render(list(() => onReachEnd()));

        view.rerender(list(() => onReachEnd()));
        view.rerender(list(() => onReachEnd()));

        expect(onReachEnd).toHaveBeenCalledTimes(1);
    });

    it("fires again once the loaded page grows the item count", () => {
        const onReachEnd = vi.fn();
        stubScrollContainer({ scrollable: true });
        const view = render(list(onReachEnd));
        expect(onReachEnd).toHaveBeenCalledTimes(1);

        // The guard keys on the item count, not on "has fired before" — so the next
        // page arriving must re-arm it, or an infinite list stops after one fetch.
        view.rerender(list(onReachEnd, [...ITEMS, "delta"]));

        expect(onReachEnd).toHaveBeenCalledTimes(2);
    });
});
