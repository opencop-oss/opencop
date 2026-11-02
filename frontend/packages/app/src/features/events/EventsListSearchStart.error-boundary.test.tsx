// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it, vi } from "vitest";
import { getElementKey, type Element } from "@opencop/elements";
import { useState } from "react";
import { render, screen, userEvent } from "~/test";
import { EventsListSearchStart } from "./EventsListSearchStart";

const { mockUseAllElements } = vi.hoisted(() => ({
    mockUseAllElements: vi.fn((): Element[] => []),
}));

vi.mock("@opencop/ingest", async () => {
    const actual = await vi.importActual<typeof import("@opencop/ingest")>("@opencop/ingest");
    return { ...actual, useAllElements: mockUseAllElements };
});

vi.mock("~/features/map/MapManagerContext", () => ({
    useMapManager: () => ({ selectElement: vi.fn() }),
}));

vi.mock("~/features/historical/data-plane/historical.store", () => ({
    useHistoricalMode: () => "live",
}));

// Represents malformed websocket-sourced element data: a real element whose
// accessor throws when `searchElements` reads it during matching, rather than
// a mocked-out results component.
function makeMalformedElement(): Element {
    return {
        key: getElementKey({ source: "source-1", objectId: "malformed-1" }),
        id: "version-malformed-1",
        objectId: "malformed-1",
        source: "source-1",
        type: "point",
        getDisplayName: () => {
            throw new Error("malformed element data");
        },
        getCode: () => undefined,
        getModifiers: () => ({}),
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

describe("EventsListSearchStart — EventSearchResults error boundary", () => {
    it("shows a scoped fallback instead of crashing the whole Events panel when a malformed element's accessor throws during matching", async () => {
        const consoleError = vi.spyOn(console, "error").mockImplementation(() => {});
        mockUseAllElements.mockReturnValue([makeMalformedElement()]);
        const user = userEvent.setup();
        renderSearchStart();

        // `searchElements` (not just result rendering) must run inside the
        // boundary — typing a query is what actually invokes it.
        await user.type(screen.getByRole("searchbox", { name: "Search events" }), "malformed");

        expect(await screen.findByText("EventSearchResults Error")).toBeTruthy();
        // Only the results area should be replaced by the fallback — the rest
        // of the panel (heading, search input) must survive the crash.
        expect(screen.getByRole("heading", { name: /events list/i })).toBeTruthy();
        expect(screen.getByRole("searchbox", { name: "Search events" })).toBeTruthy();

        consoleError.mockRestore();
    });
});
