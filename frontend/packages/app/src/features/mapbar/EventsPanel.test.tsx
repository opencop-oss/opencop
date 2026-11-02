// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { beforeEach, describe, expect, it, vi } from "vitest";
import { getElementKey, type Element } from "@opencop/elements";
import { act, fireEvent, render, screen } from "~/test";
import { useMapStore } from "~/features/map/core/map.store";
import { useEventsNavigationStore } from "~/features/events/events-navigation.store";
import { resetInteractionForTest } from "~/test/interaction";
import { useStackedObjectSelectionStore } from "~/features/map/stacked-object-selection.store";
import { useAreaSelectionStore } from "~/features/selection/area-selection.store";
import { layerQueries } from "~/features/layers/layers.queries";
import { dataSourceQueries } from "~/features/data-sources/data-sources.queries";
import { useMapbarStore } from "./mapbar.store";
import { EventsPanel } from "./EventsPanel";

vi.mock("~/features/events/EventDetail", () => ({
    EventDetail: () => <div data-testid="event-detail" />,
}));

vi.mock("~/features/events/EventsListSearchStart", () => ({
    EventsListSearchStart: () => <div data-testid="event-list" tabIndex={0} />,
}));

vi.mock("~/features/map/MapManagerContext", () => ({
    useMapManager: () => ({
        selectElement: vi.fn(),
        clearSelection: vi.fn(),
        enablePan: vi.fn(),
    }),
}));

vi.mock("~/features/events/ElementPreviewCanvas", () => ({
    ElementPreviewCanvas: () => null,
}));

vi.mock("~/features/notifications/use-toast.hook", () => ({
    useToast: () => ({ showToast: vi.fn() }),
}));

vi.mock("~/features/historical/data-plane/historical.store", () => ({
    useHistoricalMode: () => "live",
}));

const SELECTED_KEY = getElementKey({ source: "test", objectId: "obj-0" });

function resetState() {
    useMapStore.getState().resetSelection();
    useStackedObjectSelectionStore.getState().dismiss();
    useAreaSelectionStore.getState().clear();
    resetInteractionForTest();
    useMapbarStore.setState({ sidebarPanel: "events" });
    useEventsNavigationStore.setState({
        mode: "search",
        listOrigin: null,
        slideDirection: null,
        autoOpenDismissed: false,
        selectionAutoDismissed: false,
        listPickPending: false,
    });
}

function makeElement(): Element {
    return {
        key: "lasso-alpha",
        id: "alpha-id",
        objectId: "alpha-object",
        source: "test",
        getDisplayName: () => "Alpha",
        time: null,
    } as unknown as Element;
}

function renderPanel() {
    return render(<EventsPanel />, {
        queryData: {
            [JSON.stringify(layerQueries.myList().queryKey)]: [],
            [JSON.stringify(dataSourceQueries.myList().queryKey)]: [],
        },
    });
}

beforeEach(resetState);

// The theme provider injects a <script> before the panel content, so query by
// the mocked child's testid and check its parent (the animated Flex) instead
// of relying on container.firstElementChild.
function panelAnimation(container: HTMLElement): string {
    const child = container.querySelector(
        '[data-testid="event-list"], [data-testid="event-detail"]',
    );
    return getComputedStyle(child!.parentElement!).animation;
}

describe("EventsPanel — slide animation scoping", () => {
    it("applies no animation for the default list view", () => {
        const { container } = renderPanel();

        expect(panelAnimation(container)).toBe("");
    });

    it("slides forward only on the transition into detail from a list", () => {
        const { container } = renderPanel();

        act(() => {
            useEventsNavigationStore.getState().select("search");
            useMapStore.setState({ selected: SELECTED_KEY });
        });

        expect(panelAnimation(container)).toContain("slideInFromRight");
    });

    it("slides back only on the transition from detail back to a list", () => {
        const { container } = renderPanel();

        act(() => {
            useEventsNavigationStore.getState().select("search");
            useMapStore.setState({ selected: SELECTED_KEY });
        });
        expect(panelAnimation(container)).toContain("slideInFromRight");

        act(() => {
            useEventsNavigationStore.getState().showList("search");
            useMapStore.setState({ selected: null });
        });

        expect(panelAnimation(container)).toContain("slideInFromLeft");
    });
});

describe("EventsPanel — Escape belongs to the interaction runtime", () => {
    // The panel used to close itself on Escape. The runtime's ladder — one
    // document-capture listener — silently preempted that handler for a lasso
    // result, so it was deleted rather than re-ranked: the panel
    // is the presentation of a selection and follows it through `autoOpen`,
    // closing when the selection opened it and returning to its list when the
    // operator did. `interaction-rules.md` Q8; `keyboard-ladder.test.ts` pins
    // what Escape does instead.
    it("leaves Escape over the working set to the ladder — no store moves, the panel stays", () => {
        act(() => {
            useAreaSelectionStore.getState().select(
                [makeElement()],
                [
                    [0, 0],
                    [1, 0],
                    [1, 1],
                ],
            );
            useEventsNavigationStore.getState().areaSelected();
        });
        renderPanel();

        fireEvent.keyDown(screen.getByRole("toolbar", { name: "Events List" }), {
            key: "Escape",
        });

        expect(useAreaSelectionStore.getState().elements).not.toBeNull();
        expect(useAreaSelectionStore.getState().boundary).not.toBeNull();
        expect(useMapbarStore.getState().sidebarPanel).toBe("events");
        expect(useEventsNavigationStore.getState().autoOpenDismissed).toBe(false);
        expect(useEventsNavigationStore.getState().selectionAutoDismissed).toBe(false);
    });

    it("leaves Escape over the search list to the ladder as well", () => {
        const { getByTestId } = renderPanel();
        const list = getByTestId("event-list");
        list.focus();

        fireEvent.keyDown(list, { key: "Escape" });

        expect(useMapbarStore.getState().sidebarPanel).toBe("events");
        expect(useEventsNavigationStore.getState().autoOpenDismissed).toBe(false);
    });

    it("leaves Escape in the detail view to the ladder", () => {
        act(() => {
            useEventsNavigationStore.setState({ mode: "detail" });
            useMapStore.setState({ selected: SELECTED_KEY });
        });
        renderPanel();

        fireEvent.keyDown(document, { key: "Escape" });

        expect(useMapbarStore.getState().sidebarPanel).toBe("events");
        expect(useMapStore.getState().selected).toBe(SELECTED_KEY);
    });
});
