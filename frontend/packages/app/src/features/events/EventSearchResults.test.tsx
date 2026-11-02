// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, userEvent, within } from "~/test";
import { getElementKey, PointGeometry, type Element } from "@opencop/elements";
import { useMapStore } from "~/features/map/core/map.store";
import { useEventsNavigationStore } from "./events-navigation.store";
import { useEventsNavigationSync } from "./use-events-navigation.hook";
import { EventSearchResults } from "./EventSearchResults";
import type { SearchResult } from "./event-search.util";

const { mockSelectElement, mockFlyTo, mockFitBounds } = vi.hoisted(() => ({
    mockSelectElement: vi.fn(),
    mockFlyTo: vi.fn(),
    mockFitBounds: vi.fn(),
}));

vi.mock("~/features/map/MapManagerContext", () => ({
    useMapManager: () => ({
        // Mirrors MapManager.selectElement's real effect on the selection
        // slice (restoreSelection -> useMapStore.getState().select(key)), so
        // useEventsNavigationSync reacts exactly as it does in production —
        // a bare vi.fn() here would hide the exact regression this test
        // guards against (focusElementFromList navigating to Detail).
        selectElement: (element: Element) => {
            mockSelectElement(element);
            useMapStore.getState().select(getElementKey(element));
        },
    }),
}));

vi.mock("~/features/map/core/slices/view/view.hook", () => ({
    useViewActions: () => ({ flyTo: mockFlyTo, fitBounds: mockFitBounds }),
}));

function makeElement(name: string): Element {
    return {
        key: getElementKey({ source: "test", objectId: name }),
        objectId: name,
        source: "test",
        getDisplayName: () => name,
        geometry: new PointGeometry([5, 6]),
    } as unknown as Element;
}

function makeResult(name: string): SearchResult {
    return { element: makeElement(name), matchedFields: [] };
}

function EventSearchResultsWithSync(props: {
    results: SearchResult[];
    hasQuery: boolean;
    query: string;
}) {
    useEventsNavigationSync();
    return <EventSearchResults {...props} />;
}

beforeEach(() => {
    mockSelectElement.mockReset();
    mockFlyTo.mockReset();
    mockFitBounds.mockReset();
    useMapStore.setState({ selected: null });
    useEventsNavigationStore.setState({
        mode: "search",
        listOrigin: null,
        slideDirection: null,
        autoOpenDismissed: false,
        selectionAutoDismissed: false,
        listPickPending: false,
        listFocusTarget: null,
    });
});

describe("EventSearchResults", () => {
    it("clicking a result's location-pin button selects and pans the camera for that specific element, without leaving the search list", async () => {
        const user = userEvent.setup();
        const alpha = makeResult("Alpha");
        const bravo = makeResult("Bravo");

        render(<EventSearchResultsWithSync results={[alpha, bravo]} hasQuery query="test" />);

        // The pin is a sibling of the row button within the same card, not nested inside it.
        const bravoCard = screen
            .getByRole("button", { name: "Bravo" })
            .closest<HTMLElement>("[data-panel-item]");
        expect(bravoCard).not.toBeNull();
        await user.click(within(bravoCard!).getByRole("button", { name: "Show on map" }));

        expect(mockSelectElement).toHaveBeenCalledOnce();
        expect(mockSelectElement).toHaveBeenCalledWith(bravo.element);
        expect(useMapStore.getState().selected).toBe(bravo.element.key);
        expect(mockFlyTo).toHaveBeenCalledOnce();
        expect(mockFitBounds).not.toHaveBeenCalled();

        // The row's own onClick (selectFromList) is what navigates to
        // Detail — the pin button must not trigger that path.
        const { mode, listOrigin } = useEventsNavigationStore.getState();
        expect(mode).toBe("search");
        expect(listOrigin).toBeNull();
    });

    it("clicking a result's row (not the pin) still navigates to Detail", async () => {
        const user = userEvent.setup();
        const alpha = makeResult("Alpha");

        render(<EventSearchResultsWithSync results={[alpha]} hasQuery query="test" />);

        await user.click(screen.getByRole("button", { name: "Alpha" }));

        expect(mockSelectElement).toHaveBeenCalledWith(alpha.element);
        const { mode, listOrigin } = useEventsNavigationStore.getState();
        expect(mode).toBe("detail");
        expect(listOrigin).toBe("search");
    });
});
