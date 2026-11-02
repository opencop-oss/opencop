// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { VirtualList } from "@opencop/ui";
import { render, screen } from "~/test";
import { DEFAULT_VIRTUAL_WINDOW, virtualizerMock } from "./react-virtual-mock";

/**
 * Self-test for the global virtualizer stub (aliased over `@tanstack/react-virtual` in
 * vitest.config.ts). Sibling of `no-network.test.ts`: the harness's own guarantees need
 * coverage too, or a regression here silently changes what every consumer test sees.
 *
 * Drives the stub through a real `VirtualList` rather than calling `useVirtualizer`
 * directly, so the assertions are about what a test author actually observes.
 */
const ITEM_COUNT = DEFAULT_VIRTUAL_WINDOW * 2;
const ITEMS = Array.from({ length: ITEM_COUNT }, (_, index) => `item-${index}`);

function list() {
    return (
        <VirtualList
            items={ITEMS}
            estimateSize={() => 40}
            getItemKey={(item) => item}
            renderItem={(item) => <div data-testid="row">{item}</div>}
        />
    );
}

const renderedRows = () => screen.queryAllByTestId("row").length;

describe("virtualizer stub", () => {
    it("materializes only the default window, not every item", () => {
        render(list());

        expect(renderedRows()).toBe(DEFAULT_VIRTUAL_WINDOW);
        expect(renderedRows()).toBeLessThan(ITEM_COUNT);
    });

    it("reports the full item count the list received, not the rendered count", () => {
        render(list());

        // The reason a capped list is detectable at all while only a window renders.
        expect(virtualizerMock.itemCount).toHaveBeenLastCalledWith(ITEM_COUNT);
    });

    it("widens the window when setWindow is called before render", () => {
        virtualizerMock.setWindow(Infinity);

        render(list());

        expect(renderedRows()).toBe(ITEM_COUNT);
    });

    it("does not apply setWindow called after render until something re-renders", () => {
        const view = render(list());
        expect(renderedRows()).toBe(DEFAULT_VIRTUAL_WINDOW);

        virtualizerMock.setWindow(Infinity);

        // Documented sharp edge: the window is read during render, so this call alone is
        // inert — no error, just the old row count. Pinned so the JSDoc warning on
        // `setWindow` can't drift from the behaviour.
        expect(renderedRows()).toBe(DEFAULT_VIRTUAL_WINDOW);

        view.rerender(list());
        expect(renderedRows()).toBe(ITEM_COUNT);
    });

    it("resets the window between tests", () => {
        // Guards the `beforeEach` reset in setup.ts: without it the `Infinity` set by the
        // two tests above would leak here and quietly widen unrelated suites.
        expect(virtualizerMock.window).toBe(DEFAULT_VIRTUAL_WINDOW);

        render(list());

        expect(renderedRows()).toBe(DEFAULT_VIRTUAL_WINDOW);
    });
});
