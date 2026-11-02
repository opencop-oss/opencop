// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { act, fireEvent, render, screen, userEvent, within } from "~/test";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { useStackedObjectSelectionStore } from "~/features/map/stacked-object-selection.store";
import { useAreaSelectionStore } from "~/features/selection/area-selection.store";
import { StackedElements } from "./StackedElements";
import type { Element } from "@opencop/elements";

vi.mock("~/features/map/MapManagerContext", () => ({
    useMapManager: vi.fn(),
}));

// ElementPreviewCanvas renders to a canvas — skip in happy-dom.
vi.mock("./ElementPreviewCanvas", () => ({
    ElementPreviewCanvas: () => null,
}));

vi.mock("~/features/preferences/use-user-timezone.hook", () => ({
    useUserTimezone: () => "UTC",
}));

import { useMapManager } from "~/features/map/MapManagerContext";

function makeTestMapManager() {
    return {
        onMap: vi.fn(() => ({ unsub: vi.fn() })),
        selectElement: vi.fn(),
        pickMoreAt: vi.fn(() => []),
        setHover: vi.fn(),
    };
}

function makeElement(name: string): Element {
    return {
        key: `test ${name}`,
        objectId: name,
        source: "test",
        getDisplayName: () => name,
        time: null,
    } as unknown as Element;
}

const POSITION = { x: 100, y: 200 };

function renderComponent() {
    // Theme + i18n come from the harness; MapManagerContext is mocked above.
    return act(async () => {
        render(<StackedElements />);
        await Promise.resolve();
        await Promise.resolve();
    });
}

function resetStore() {
    useStackedObjectSelectionStore.getState().dismiss();
    useAreaSelectionStore.getState().clear();
}

beforeEach(() => {
    act(() => {
        resetStore();
    });
    vi.mocked(useMapManager).mockReturnValue(makeTestMapManager() as never);
});

afterEach(() => {
    act(() => {
        resetStore();
    });
});

function setCompletedLassoResult(elements: Element[]) {
    useAreaSelectionStore.getState().select(elements, []);
}

describe("StackedElements — with only a lasso selection active", () => {
    it("renders nothing: the lasso selection is not this component's surface", async () => {
        const elements = [makeElement("Alpha"), makeElement("Bravo"), makeElement("Charlie")];

        act(() => {
            setCompletedLassoResult(elements);
        });

        await renderComponent();

        expect(screen.queryByText(/events selected/i)).toBeNull();
        expect(document.body.querySelector("[data-state='open']")).toBeNull();
    });

    it("shows the floating popup in point-pick mode", async () => {
        const elements = [makeElement("Alpha"), makeElement("Bravo")];

        act(() => {
            useStackedObjectSelectionStore.getState().open(elements, POSITION, false);
        });

        await renderComponent();

        expect(screen.queryByText(/events selected/i)).toBeNull();
        expect(await screen.findByLabelText("Dismiss")).toBeTruthy();
    });

    it("does not intercept Escape — the lasso selection survives it", async () => {
        act(() => {
            setCompletedLassoResult([makeElement("Alpha")]);
        });

        await renderComponent();
        fireEvent.keyDown(document, { key: "Escape" });

        // This component's Escape listener only attaches when the popover is
        // open, and a lasso selection no longer opens it. They are separate
        // stores, so the isolation is structural rather than a `source === "selection"`
        // guard. The keyboard router's rung 7 owns this
        // press instead; nothing here consumes it.
        expect(useStackedObjectSelectionStore.getState().elements).toBeNull();
        expect(useAreaSelectionStore.getState().elements).toHaveLength(1);
    });
});

describe("StackedElements — point-pick behavior", () => {
    it("clicking a row resets the store and calls selectElement", async () => {
        const mapManager = makeTestMapManager();
        vi.mocked(useMapManager).mockReturnValue(mapManager as never);

        const elements = [makeElement("Alpha"), makeElement("Bravo")];
        act(() => {
            useStackedObjectSelectionStore.getState().open(elements, POSITION, false);
        });

        await renderComponent();

        await act(async () => {
            screen.getByText("Alpha").click();
        });

        // The component's job is the store write; clearing the map hover
        // belongs to the surface withdrawal, which the host attaches and
        // `commands.test.ts` pins.
        expect(useStackedObjectSelectionStore.getState().elements).toBeNull();
        expect(mapManager.selectElement).toHaveBeenCalledWith(
            expect.objectContaining({ objectId: "Alpha" }),
        );
    });

    it("highlight changes route to mapManager.setHover", async () => {
        const mapManager = makeTestMapManager();
        vi.mocked(useMapManager).mockReturnValue(mapManager as never);

        const elements = [makeElement("Alpha")];
        act(() => {
            useStackedObjectSelectionStore.getState().open(elements, POSITION, false);
        });

        await renderComponent();

        // Real Ark Menu fires onHighlightChange on pointer highlight; hovering the
        // row routes to setHover with that element, and leaving routes to null.
        const menu = await screen.findByRole("menu");
        const row = within(menu).getByText("Alpha");

        await userEvent.hover(row);
        expect(mapManager.setHover).toHaveBeenCalledWith(
            expect.objectContaining({ objectId: "Alpha" }),
        );

        await userEvent.unhover(row);
        expect(mapManager.setHover).toHaveBeenLastCalledWith(null);
    });

    it("dismisses the picker when the map starts moving", async () => {
        const mapManager = makeTestMapManager();
        vi.mocked(useMapManager).mockReturnValue(mapManager as never);

        act(() => {
            useStackedObjectSelectionStore.getState().open([makeElement("Alpha")], POSITION, false);
        });

        await renderComponent();

        // Capture and fire the movestart callback registered via onMap.
        const calls = mapManager.onMap.mock.calls as unknown as [string, () => void][];
        const moveStartCall = calls.find(([event]) => event === "movestart");
        expect(moveStartCall).toBeDefined();
        const moveStartHandler = moveStartCall![1];

        act(() => {
            moveStartHandler();
        });

        expect(useStackedObjectSelectionStore.getState().elements).toBeNull();
    });

    it("does not subscribe to movestart for lasso source", async () => {
        const mapManager = makeTestMapManager();
        vi.mocked(useMapManager).mockReturnValue(mapManager as never);

        act(() => {
            useAreaSelectionStore.getState().select([makeElement("Alpha")], []);
        });

        await renderComponent();

        const calls = mapManager.onMap.mock.calls as unknown as [string, () => void][];
        const moveStartCall = calls.find(([event]) => event === "movestart");
        expect(moveStartCall).toBeUndefined();
    });
});
