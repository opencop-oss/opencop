// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act, render, screen } from "~/test";
import type { Element } from "@opencop/elements";
import { useStackedObjectSelectionStore } from "~/features/map/stacked-object-selection.store";
import { useAreaSelectionStore } from "~/features/selection/area-selection.store";
import { useMapbarStore } from "~/features/mapbar/mapbar.store";
import { layerQueries } from "~/features/layers/layers.queries";
import { dataSourceQueries } from "~/features/data-sources/data-sources.queries";
import { useEventsNavigationStore } from "./events-navigation.store";
import { EventList } from "./EventList";

const { mockSelectElement } = vi.hoisted(() => ({
    mockSelectElement: vi.fn(),
}));

vi.mock("~/features/map/MapManagerContext", () => ({
    useMapManager: () => ({ selectElement: mockSelectElement, clearSelection: vi.fn() }),
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

vi.mock("@opencop/ingest", async () => {
    const actual = await vi.importActual<typeof import("@opencop/ingest")>("@opencop/ingest");
    return { ...actual, useAllElements: () => [] };
});

function makeElement(name: string): Element {
    return {
        key: `evt-${name}`,
        id: `${name.toLowerCase()}-id`,
        objectId: `${name.toLowerCase()}-object`,
        source: "test",
        getDisplayName: () => name,
        time: null,
    } as unknown as Element;
}

function resetStores() {
    useStackedObjectSelectionStore.getState().dismiss();
    useAreaSelectionStore.getState().clear();
    useMapbarStore.setState({ sidebarPanel: "events" });
    useEventsNavigationStore.setState({
        mode: "search",
        listOrigin: null,
        slideDirection: null,
        autoOpenDismissed: false,
        selectionAutoDismissed: false,
        listPickPending: false,
    });
    mockSelectElement.mockReset();
}

beforeEach(() => act(resetStores));
afterEach(() => act(resetStores));

function renderList() {
    return render(<EventList query="" onQueryChange={() => {}} />, {
        queryData: {
            [JSON.stringify(layerQueries.myList().queryKey)]: [],
            [JSON.stringify(dataSourceQueries.myList().queryKey)]: [],
        },
    });
}

describe("EventList — dispatches between the search list and the lasso list", () => {
    it("shows the search list when there is no active lasso selection", () => {
        renderList();

        expect(screen.getByRole("searchbox", { name: "Search events" })).toBeTruthy();
        expect(screen.queryByRole("toolbar", { name: "Events List" })).toBeNull();
    });

    it("shows the lasso result list when a lasso selection is active", () => {
        act(() => {
            useAreaSelectionStore.getState().select([makeElement("Alpha")], []);
        });

        renderList();

        expect(screen.getByRole("toolbar", { name: "Events List" })).toBeTruthy();
        expect(screen.queryByRole("searchbox", { name: "Search events" })).toBeNull();
    });

    it("switches from the search list to the lasso list as soon as a lasso selection lands", () => {
        const { rerender } = renderList();
        expect(screen.getByRole("searchbox", { name: "Search events" })).toBeTruthy();

        act(() => {
            useAreaSelectionStore.getState().select([makeElement("Alpha")], []);
        });
        rerender(<EventList query="" onQueryChange={() => {}} />);

        expect(screen.getByRole("toolbar", { name: "Events List" })).toBeTruthy();
    });

    it("wires a lasso row's onFocus through selectFromList: MapManager gets the raw pick, events-navigation.store records the origin", () => {
        const alpha = makeElement("Alpha");
        act(() => {
            useAreaSelectionStore.getState().select([alpha], []);
        });

        renderList();

        act(() => {
            screen.getByRole("button", { name: "Alpha" }).click();
        });

        expect(mockSelectElement).toHaveBeenCalledWith(alpha);
        expect(useEventsNavigationStore.getState().listOrigin).toBe("selection");
    });

    it("wires Cancel through to the mapbar store's closePanel", () => {
        act(() => {
            useAreaSelectionStore.getState().select([makeElement("Alpha")], []);
            useMapbarStore.setState({ sidebarPanel: "events" });
        });

        renderList();

        act(() => {
            screen.getByRole("button", { name: "Cancel" }).click();
        });

        expect(useMapbarStore.getState().sidebarPanel).toBe("");
    });
});
