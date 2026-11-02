// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act, renderHook } from "~/test";
import { useMapbarStore } from "./mapbar.store";
import { useStackedObjectSelectionStore } from "~/features/map/stacked-object-selection.store";
import { useAreaSelectionStore } from "~/features/selection/area-selection.store";
import { useMapStore } from "~/features/map/core/map.store";
import { resetInteractionForTest } from "~/test/interaction";
import { useMapbar, useMapbarSync } from "./mapbar.hook";
import { useEventsNavigationStore } from "~/features/events/events-navigation.store";
import { getElementKey } from "@opencop/elements";

const { mockClearSelection, mockWatchboxEnabled } = vi.hoisted(() => ({
    mockClearSelection: vi.fn(),
    mockWatchboxEnabled: { value: false },
}));

vi.mock("~/features/map/MapManagerContext", () => ({
    useMapManager: () => ({ clearSelection: mockClearSelection }),
    useHasAuxiliarySource: () => false,
}));

vi.mock("~/features/admin/runtime-features/use-feature-flag.hook", () => ({
    useFeatureFlagEnabled: () => false,
    useBackendFeatureEnabled: () => mockWatchboxEnabled.value,
}));

vi.mock("@opencop/ingest", () => ({
    useElement: () => undefined,
}));

vi.mock("~/features/historical/data-plane/HistoricalDataContext", () => ({
    useHistoricalElement: () => undefined,
}));

// Element keys use a NUL separator internally; use getElementKey to produce the correct format.
const SELECTED_KEY = getElementKey({ source: "test", objectId: "obj-0" });

function setLassoElements(count = 2) {
    const elements = Array.from({ length: count }, (_, i) => ({
        key: `test-el-${i}`,
        id: `id-${i}`,
        objectId: `obj-${i}`,
        source: "test",
        getDisplayName: () => `Element ${i}`,
        time: null,
    }));
    useAreaSelectionStore
        .getState()
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        .select(elements as any, []);
}

function setSelectedElement() {
    useMapStore.setState({ selected: SELECTED_KEY });
}

function resetAll() {
    useStackedObjectSelectionStore.getState().dismiss();
    useAreaSelectionStore.getState().clear();
    useMapStore.setState({ selected: null, dismissTick: 0 });
    resetInteractionForTest();
    useMapbarStore.setState({ sidebarPanel: "" });
    useEventsNavigationStore.setState({
        mode: "search",
        listOrigin: null,
        slideDirection: null,
        autoOpenDismissed: false,
        selectionAutoDismissed: false,
        listPickPending: false,
    });
    mockClearSelection.mockReset();
    mockWatchboxEnabled.value = false;
}

beforeEach(() => {
    act(() => resetAll());
});

afterEach(() => {
    act(() => resetAll());
});

// ---------------------------------------------------------------------------
// useMapbar — derived `open`
// ---------------------------------------------------------------------------

describe("useMapbar — lasso vs point-pick priority", () => {
    it("returns 'events' when a lasso result is active and not dismissed", () => {
        act(() => setLassoElements());

        const { result } = renderHook(() => useMapbar());

        expect(result.current.open).toBe("events");
    });

    it("returns 'events' for a point-pick selection, ignoring an active lasso", () => {
        act(() => {
            setLassoElements();
            setSelectedElement();
        });

        const { result } = renderHook(() => useMapbar());

        // point-pick and lasso both auto-open "events" — either way the result is the same panel.
        expect(result.current.open).toBe("events");
    });

    it("returns '' when lasso auto-panel is dismissed", () => {
        act(() => {
            setLassoElements();
            useEventsNavigationStore.setState({ selectionAutoDismissed: true });
        });

        const { result } = renderHook(() => useMapbar());

        expect(result.current.open).toBe("");
    });

    it("returns an explicit sidebarPanel over lasso auto-open", () => {
        act(() => {
            setLassoElements();
            useMapbarStore.setState({ sidebarPanel: "filters" });
        });

        const { result } = renderHook(() => useMapbar());

        expect(result.current.open).toBe("filters");
    });
});

// ---------------------------------------------------------------------------
// useMapbarSync — generic panel dismissal (non-events panels)
// ---------------------------------------------------------------------------

describe("useMapbarSync — dismissing a picked element leaves a non-events panel alone", () => {
    it("closes an open watchbox panel when the backend feature is disabled", () => {
        act(() => useMapbarStore.getState().openPanel("watchbox"));

        renderHook(() => useMapbarSync());

        expect(useMapbarStore.getState().sidebarPanel).toBe("");
    });

    it("keeps a non-events panel open after an earlier empty-map click", () => {
        const { result } = renderHook(() => {
            useMapbarSync();
            return useMapbar();
        });

        act(() => useMapStore.getState().bumpDismiss());
        act(() => result.current.toggleOpen("imagery"));

        expect(useMapbarStore.getState().sidebarPanel).toBe("imagery");
        expect(result.current.open).toBe("imagery");
    });

    it("keeps another panel open when leaving the search list without a selection", () => {
        act(() => useMapbarStore.getState().openPanel("events"));

        const { result } = renderHook(() => {
            useMapbarSync();
            return useMapbar();
        });

        act(() => result.current.toggleOpen("imagery"));

        expect(useMapbarStore.getState().sidebarPanel).toBe("imagery");
        expect(result.current.open).toBe("imagery");
    });

    it("keeps another panel open while the selected event is unchanged", () => {
        act(() => setSelectedElement());

        const { result } = renderHook(() => {
            useMapbarSync();
            return useMapbar();
        });

        act(() => result.current.toggleOpen("imagery"));

        expect(useMapbarStore.getState().sidebarPanel).toBe("imagery");
        expect(result.current.open).toBe("imagery");
    });
});
