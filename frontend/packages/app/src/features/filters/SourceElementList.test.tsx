// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { beforeEach, describe, expect, it, vi } from "vitest";
import { API_BASE, fireEvent, http, HttpResponse, render, screen, server } from "~/test";
import { SourceElementList } from "./SourceElementList";

vi.mock("@opencop/ingest", () => ({
    useElementsBySource: vi.fn(),
    useSourceCount: vi.fn(),
}));

vi.mock("~/features/map/MapManagerContext", () => ({
    useMapManager: vi.fn(),
}));

vi.mock("../map/core/slices/view/view.hook", () => ({
    useViewActions: vi.fn(),
}));

import { useElementsBySource, useSourceCount } from "@opencop/ingest";
import { useMapManager } from "~/features/map/MapManagerContext";
import { useViewActions } from "../map/core/slices/view/view.hook";

function makeElement(name: string) {
    return {
        key: name,
        objectId: `object-${name}`,
        source: "test-source",
        getDisplayName: () => name,
        geometry: {
            toGeoJSONGeometry: () => {
                throw new Error("no geom");
            },
            getAllPos: () => [],
        },
    };
}

const TEST_ELEMENTS = [
    makeElement("Alpha"),
    makeElement("Bravo"),
    makeElement("Charlie"),
] as unknown as import("@opencop/elements").Element[];

function renderComponent() {
    return render(<SourceElementList sourceId="test-source" />);
}

describe("SourceElementList — element search lifecycle", () => {
    beforeEach(() => {
        // Alpha has a named author, Bravo an author without a username; Charlie came from a data source.
        server.use(
            http.get(
                `${API_BASE}/events/test-source/object/:objectId/details/latest`,
                ({ params }) =>
                    HttpResponse.json({
                        objectId: params.objectId,
                        ...(params.objectId === "object-Alpha" && {
                            author: { username: "jsmith" },
                        }),
                        ...(params.objectId === "object-Bravo" && {
                            author: { firstName: "Jo", lastName: "Doe" },
                        }),
                    }),
            ),
        );
        // Factory: calling it always returns a fresh iterator over TEST_ELEMENTS
        vi.mocked(useElementsBySource).mockReturnValue(() => TEST_ELEMENTS.values());
        vi.mocked(useSourceCount).mockReturnValue(TEST_ELEMENTS.length);
        vi.mocked(useMapManager).mockReturnValue({ selectElement: vi.fn() } as never);
        vi.mocked(useViewActions).mockReturnValue({ fitBounds: vi.fn() } as never);
    });

    it("shows all elements with an empty search field", () => {
        renderComponent();
        expect(screen.queryByText("Alpha")).not.toBeNull();
        expect(screen.queryByText("Bravo")).not.toBeNull();
        expect(screen.queryByText("Charlie")).not.toBeNull();
    });

    it("shows only matching elements when a term is typed", () => {
        renderComponent();

        fireEvent.change(screen.getByPlaceholderText("Search..."), { target: { value: "lph" } });

        expect(screen.queryByText("Alpha")).not.toBeNull();
        expect(screen.queryByText("Bravo")).toBeNull();
        expect(screen.queryByText("Charlie")).toBeNull();
    });

    it("shows no results when no element matches the search term", () => {
        renderComponent();

        fireEvent.change(screen.getByPlaceholderText("Search..."), {
            target: { value: "zzznomatch" },
        });

        expect(screen.queryByText("Alpha")).toBeNull();
        expect(screen.queryByText(/no results/i)).not.toBeNull();
    });

    it("restores all elements after clearing the search", () => {
        renderComponent();

        const input = screen.getByPlaceholderText("Search...");
        fireEvent.change(input, { target: { value: "zzznomatch" } });
        expect(screen.queryByText(/no results/i)).not.toBeNull();

        fireEvent.change(input, { target: { value: "" } });

        expect(screen.queryByText("Alpha")).not.toBeNull();
        expect(screen.queryByText("Bravo")).not.toBeNull();
        expect(screen.queryByText("Charlie")).not.toBeNull();
    });

    it("shows the author on the right, Unknown without a username, System for data-source objects", async () => {
        renderComponent();

        expect(await screen.findByText("jsmith")).toBeInTheDocument();
        expect(await screen.findByText("Unknown")).toBeInTheDocument();
        expect(await screen.findAllByText("System")).toHaveLength(1);
    });
});
