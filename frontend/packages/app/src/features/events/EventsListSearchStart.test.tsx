// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { beforeEach, describe, expect, it, vi } from "vitest";
import { getElementKey, type Element } from "@opencop/elements";
import { useState } from "react";
import { fireEvent, render, screen, userEvent, waitFor } from "~/test";
import { virtualizerMock } from "~/test/react-virtual-mock";
import { EventsListSearchStart } from "./EventsListSearchStart";
import { useEventsNavigationStore } from "./events-navigation.store";

const { mockUseAllElements, mockSelectElement, mockUseHistoricalMode } = vi.hoisted(() => ({
    mockUseAllElements: vi.fn((): Element[] => []),
    mockSelectElement: vi.fn(),
    mockUseHistoricalMode: vi.fn<() => "live" | "historical">(() => "live"),
}));

const OBJECT_ID = "049ed46e-7328-4f52-a751-3ac54ca359b0";

vi.mock("@opencop/ingest", async () => {
    const actual = await vi.importActual<typeof import("@opencop/ingest")>("@opencop/ingest");
    return { ...actual, useAllElements: mockUseAllElements };
});

vi.mock("~/features/map/MapManagerContext", () => ({
    useMapManager: () => ({ selectElement: mockSelectElement }),
}));

vi.mock("~/features/historical/data-plane/historical.store", () => ({
    useHistoricalMode: mockUseHistoricalMode,
}));

function makeElement(): Element {
    return {
        key: getElementKey({ source: "source-1", objectId: OBJECT_ID }),
        id: "version-uuid-1",
        objectId: OBJECT_ID,
        source: "source-1",
        type: "point",
        getDisplayName: () => "Searchable Alpha",
        getCode: () => undefined,
        getModifiers: () => ({ callsign: "Raven" }),
        getAttributes: () => ({}),
    } as unknown as Element;
}

function renderSearchStart() {
    function StatefulSearchStart() {
        const [query, setQuery] = useState("");
        return <EventsListSearchStart query={query} onQueryChange={setQuery} />;
    }

    return render(<StatefulSearchStart />);
}

beforeEach(() => {
    mockUseAllElements.mockReset().mockReturnValue([]);
    mockSelectElement.mockReset();
    mockUseHistoricalMode.mockReset().mockReturnValue("live");
    useEventsNavigationStore.setState({
        mode: "search",
        listOrigin: null,
        slideDirection: null,
        autoOpenDismissed: false,
        selectionAutoDismissed: false,
        listPickPending: false,
    });
});

describe("EventsListSearchStart", () => {
    it("renders the unavailable state without mounting live search in historical mode", () => {
        mockUseHistoricalMode.mockReturnValue("historical");

        renderSearchStart();

        expect(screen.getByText("Event search is unavailable in History mode")).toBeTruthy();
        expect(screen.queryByRole("searchbox", { name: "Search events" })).toBeNull();
        expect(mockUseAllElements).not.toHaveBeenCalled();
    });

    it("renders the Events List heading and an accessible search input", () => {
        renderSearchStart();

        expect(mockUseAllElements).toHaveBeenCalledWith({ enabled: false });
        expect(screen.getByRole("heading", { name: /events list/i })).toBeTruthy();
        expect(screen.getByRole("searchbox", { name: "Search events" })).toBeTruthy();
        expect(
            screen.getByText(
                "Search for an event, or select one or more events on the map to view details here.",
            ),
        ).toBeTruthy();
        expect(screen.queryByText("No Feature Selected")).toBeNull();
    });

    it("updates and clears the controlled query", async () => {
        const user = userEvent.setup();
        renderSearchStart();
        const input = screen.getByRole("searchbox", { name: "Search events" });

        await user.type(input, "event-uuid");
        expect(mockUseAllElements).toHaveBeenLastCalledWith({ enabled: true });
        expect((input as HTMLInputElement).value).toBe("event-uuid");
        expect(await screen.findByText("No matching loaded events")).toBeTruthy();
        expect(screen.queryByText(/Search for an event, or select one or more events/)).toBeNull();

        await user.click(screen.getByRole("button", { name: "Clear event search" }));
        expect(mockUseAllElements).toHaveBeenLastCalledWith({ enabled: false });
        expect((input as HTMLInputElement).value).toBe("");
        expect(screen.getByText(/Search for an event, or select one or more events/)).toBeTruthy();
    });

    it("returns keyboard focus to the input after clearing", async () => {
        const user = userEvent.setup();
        renderSearchStart();
        const input = screen.getByRole("searchbox", { name: "Search events" });

        await user.type(input, "z");
        await user.click(screen.getByRole("button", { name: "Clear event search" }));

        expect(document.activeElement).toBe(input);
    });

    it("does not flash the previous query's results after clearing and immediately retyping", async () => {
        const user = userEvent.setup();
        mockUseAllElements.mockReturnValue([makeElement()]);
        renderSearchStart();
        const input = screen.getByRole("searchbox", { name: "Search events" });

        await user.type(input, "Raven");
        expect(await screen.findByText("Searchable Alpha")).toBeTruthy();

        await user.click(screen.getByRole("button", { name: "Clear event search" }));
        // A direct change event (rather than `user.type`'s per-keystroke delay)
        // simulates retyping before the previous debounce window would have
        // settled on its own.
        fireEvent.change(input, { target: { value: "zzz" } });

        expect(screen.queryByText("Searchable Alpha")).toBeNull();
        expect(screen.getByText(/Search for an event, or select one or more events/)).toBeTruthy();

        expect(await screen.findByText("No matching loaded events")).toBeTruthy();
        expect(screen.queryByText("Searchable Alpha")).toBeNull();
    });

    it("does not show a false no-results state before the query is debounced", async () => {
        const user = userEvent.setup();
        renderSearchStart();

        await user.type(screen.getByRole("searchbox", { name: "Search events" }), "zzz");

        expect(mockUseAllElements).toHaveBeenLastCalledWith({ enabled: true });
        expect(screen.queryByText("No matching loaded events")).toBeNull();
        expect(screen.queryByText("0 matching loaded events")).toBeNull();
        expect(screen.getByText(/Search for an event, or select one or more events/)).toBeTruthy();

        expect(await screen.findByText("No matching loaded events")).toBeTruthy();
        expect(screen.getByText("0 matching loaded events")).toBeTruthy();
    });

    it("does not enable the elements subscription below the minimum query length", async () => {
        const user = userEvent.setup();
        renderSearchStart();

        await user.type(screen.getByRole("searchbox", { name: "Search events" }), "zz");

        expect(mockUseAllElements).toHaveBeenLastCalledWith({ enabled: false });
        expect(screen.getByText("Type at least 3 characters to search")).toHaveAttribute(
            "aria-live",
            "polite",
        );
        expect(screen.queryByText(/Search for an event, or select one or more events/)).toBeNull();
    });

    it("enables the elements subscription once the minimum query length is reached", async () => {
        const user = userEvent.setup();
        renderSearchStart();
        const input = screen.getByRole("searchbox", { name: "Search events" });

        await user.type(input, "zz");
        expect(mockUseAllElements).toHaveBeenLastCalledWith({ enabled: false });

        await user.type(input, "z");
        expect(mockUseAllElements).toHaveBeenLastCalledWith({ enabled: true });
        expect(await screen.findByText("No matching loaded events")).toBeTruthy();
    });

    it("disables the elements subscription when deleting back below the minimum length", async () => {
        const user = userEvent.setup();
        renderSearchStart();
        const input = screen.getByRole("searchbox", { name: "Search events" });

        await user.type(input, "zzz");
        expect(mockUseAllElements).toHaveBeenLastCalledWith({ enabled: true });

        await user.type(input, "{backspace}");
        expect(mockUseAllElements).toHaveBeenLastCalledWith({ enabled: false });
        expect(screen.getByText("Type at least 3 characters to search")).toBeTruthy();
    });

    it("finds a loaded event and opens it through MapManager", async () => {
        const user = userEvent.setup();
        const element = makeElement();
        mockUseAllElements.mockReturnValue([element]);
        renderSearchStart();

        await user.type(screen.getByRole("searchbox", { name: "Search events" }), "Raven");

        expect(await screen.findByText("Searchable Alpha")).toBeTruthy();
        expect(screen.getByRole("button", { name: "Copy ID for Searchable Alpha" })).toBeTruthy();
        await user.click(screen.getByRole("button", { name: "Searchable Alpha" }));
        // MapManager itself stays unaware of the "search" origin — selectFromList
        // records it in events-navigation.store before delegating the raw pick.
        expect(mockSelectElement).toHaveBeenCalledWith(element);
        expect(useEventsNavigationStore.getState().listOrigin).toBe("search");
    });

    it("shows why a result matched and highlights the current query", async () => {
        const user = userEvent.setup();
        mockUseAllElements.mockReturnValue([makeElement()]);
        const { container } = renderSearchStart();

        await user.type(screen.getByRole("searchbox", { name: "Search events" }), "uuid-1");

        expect(await screen.findByText("source-1")).toBeTruthy();
        expect(container.textContent).toContain("id:");
        expect(container.textContent).toContain("version-uuid-1");
        expect(
            Array.from(container.querySelectorAll("mark"), (mark) => mark.textContent),
        ).toContain("uuid-1");
    });

    it("shows how many additional matched fields are omitted", async () => {
        const user = userEvent.setup();
        const element = {
            ...makeElement(),
            getModifiers: () => ({
                "match-one": "value",
                "match-two": "value",
                "match-three": "value",
                "match-four": "value",
            }),
        } as unknown as Element;
        mockUseAllElements.mockReturnValue([element]);
        renderSearchStart();

        await user.type(screen.getByRole("searchbox", { name: "Search events" }), "match");

        expect(await screen.findByText("... +1 more")).toBeTruthy();
    });

    it("highlights a display-name match in the card title", async () => {
        const user = userEvent.setup();
        mockUseAllElements.mockReturnValue([makeElement()]);
        const { container } = renderSearchStart();

        await user.type(screen.getByRole("searchbox", { name: "Search events" }), "Alpha");

        await waitFor(() =>
            expect(
                Array.from(container.querySelectorAll("mark"), (mark) => mark.textContent),
            ).toContain("Alpha"),
        );
        expect(screen.getByRole("button", { name: "Searchable Alpha" })).toBeTruthy();
    });

    it("shows the full object ID as the match reason when searching by it", async () => {
        const user = userEvent.setup();
        mockUseAllElements.mockReturnValue([makeElement()]);
        const { container } = renderSearchStart();

        await user.type(screen.getByRole("searchbox", { name: "Search events" }), "3ac54ca359b0");

        expect(await screen.findByText("Searchable Alpha")).toBeTruthy();
        expect(container.textContent).toContain(`objectId:${OBJECT_ID}`);
    });

    it("does not cap matching results at 100 items", async () => {
        const user = userEvent.setup();
        const elements = Array.from({ length: 101 }, (_, index) => {
            const number = index + 1;
            return {
                ...makeElement(),
                key: getElementKey({ source: "source-1", objectId: `object-${number}` }),
                objectId: `object-${number}`,
                getDisplayName: () => `Search result ${number}`,
            } as Element;
        });
        mockUseAllElements.mockReturnValue(elements);
        renderSearchStart();

        await user.type(screen.getByRole("searchbox", { name: "Search events" }), "Raven");

        expect(await screen.findByText("101 matching loaded events")).toBeTruthy();
        // Assert what the list was handed, not which rows rendered: only the
        // virtualizer's window materializes. Probing for one row by name would also
        // miss a cap — results sort lexicographically, so "Search result 101" lands
        // 4th and survives a `slice(0, 100)`.
        expect(virtualizerMock.itemCount).toHaveBeenLastCalledWith(101);
    });
});
