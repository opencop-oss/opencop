// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act, renderHook } from "~/test";
import { getElementKey, PointGeometry, type Element } from "@opencop/elements";
import type { MapManager } from "~/features/map/MapManager";
import { useMapbarStore } from "~/features/mapbar/mapbar.store";
import { useMapbar, useMapbarSync } from "~/features/mapbar/mapbar.hook";
import { useStackedObjectSelectionStore } from "~/features/map/stacked-object-selection.store";
import { useAreaSelectionStore } from "~/features/selection/area-selection.store";
import { useMapStore } from "~/features/map/core/map.store";
import { resetInteractionForTest } from "~/test/interaction";
import { focusElementFromList, useEventsNavigationStore } from "./events-navigation.store";
import { useEventsNavigationSync } from "./use-events-navigation.hook";

const { mockClearSelection } = vi.hoisted(() => ({
    mockClearSelection: vi.fn(),
}));

vi.mock("~/features/map/MapManagerContext", () => ({
    useMapManager: () => ({ clearSelection: mockClearSelection }),
    useHasAuxiliarySource: () => false,
}));

vi.mock("@opencop/ingest", () => ({
    useElement: () => undefined,
}));

vi.mock("~/features/historical/data-plane/HistoricalDataContext", () => ({
    useHistoricalElement: () => undefined,
}));

vi.mock("~/features/admin/runtime-features/use-feature-flag.hook", () => ({
    useFeatureFlagEnabled: () => false,
    useBackendFeatureEnabled: () => false,
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
    useAreaSelectionStore.getState().select(elements as unknown as Element[], []);
}

function setSelectedElement() {
    useMapStore.setState({ selected: SELECTED_KEY });
}

// Mirrors MapManager.selectElement's real effect on the selection slice
// (restoreSelection -> useMapStore.getState().select(key)) without pulling in
// the full MapManager class, which needs far more app context than this test
// wires up.
function fakeMapManager(): MapManager {
    return {
        selectElement: (element: Element) => {
            useMapStore.getState().select(getElementKey(element));
        },
    } as unknown as MapManager;
}

function fakeElementWithGeometry(objectId: string): Element {
    return {
        objectId,
        source: "test",
        key: getElementKey({ source: "test", objectId }),
        geometry: new PointGeometry([5, 6]),
    } as unknown as Element;
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
        listFocusTarget: null,
    });
    mockClearSelection.mockReset();
}

beforeEach(() => {
    act(() => resetAll());
});

afterEach(() => {
    act(() => resetAll());
});

// ---------------------------------------------------------------------------
// useEventsNavigationSync — reacting to a fresh pick
// ---------------------------------------------------------------------------
//
// selectFromList calls events-navigation's `select()` synchronously before
// triggering the `selected` change — the list-pick tests below simulate that
// exact ordering (not just the resulting store shape) via select()/
// setSelectedElement. A *raw* map pick (PickingHandler, MapContextMenu, etc.)
// never calls select() at all — MapManager.selectElement stays unaware of
// Events — so those tests simulate a raw pick by only calling
// setSelectedElement(), exactly as production code does.

describe("useEventsNavigationSync — a fresh pick lands", () => {
    it("keeps events open and preserves listOrigin/slideDirection for a lasso-driven selection", () => {
        act(() => useMapbarStore.getState().openPanel("events"));
        renderHook(() => useEventsNavigationSync());

        act(() => {
            useEventsNavigationStore.getState().select("selection");
            setSelectedElement();
        });

        expect(useMapbarStore.getState().sidebarPanel).toBe("events");
        const { listOrigin, slideDirection, mode } = useEventsNavigationStore.getState();
        expect(listOrigin).toBe("selection");
        expect(slideDirection).toBe("forward");
        expect(mode).toBe("detail");
    });

    it("opens detail with no slide and no breadcrumb for a raw pick that never called select()", () => {
        act(() => useMapbarStore.getState().openPanel("filters"));
        renderHook(() => useEventsNavigationSync());

        // No select() call here — this is what a real raw map pick does.
        act(() => setSelectedElement());

        expect(useMapbarStore.getState().sidebarPanel).toBe("");
        const { mode, listOrigin, slideDirection } = useEventsNavigationStore.getState();
        expect(mode).toBe("detail");
        expect(listOrigin).toBeNull();
        expect(slideDirection).toBeNull();
    });

    it("a raw pick clears a stale listOrigin/slideDirection left over from an earlier list-driven detail", () => {
        renderHook(() => useEventsNavigationSync());

        // A previous list click left the store mid-breadcrumb.
        act(() => {
            useEventsNavigationStore.getState().select("search");
            setSelectedElement();
        });
        expect(useEventsNavigationStore.getState().listOrigin).toBe("search");

        // Now a raw pick lands on a different element, with no select() call.
        act(() =>
            useMapStore.setState({
                selected: getElementKey({ source: "test", objectId: "obj-1" }),
            }),
        );

        const { mode, listOrigin, slideDirection } = useEventsNavigationStore.getState();
        expect(mode).toBe("detail");
        expect(listOrigin).toBeNull();
        expect(slideDirection).toBeNull();
    });
});

// ---------------------------------------------------------------------------
// useEventsNavigationSync — the location-pin button (focusElementFromList)
// must select and pan the camera without leaving the current list. This is
// a regression check: focusElementFromList's selection change goes through
// the exact same MapManager.selectElement -> useMapStore `selected` path a
// real pick does, so without the correlated listFocusTarget,
// useEventsNavigationSync can't tell it apart from a raw pick and would call
// rawPickLanded(), flipping mode to "detail".
// ---------------------------------------------------------------------------

describe("useEventsNavigationSync — the location-pin button stays on the current list", () => {
    it("selects and pans the camera without leaving the search list", () => {
        renderHook(() => useEventsNavigationSync());
        expect(useEventsNavigationStore.getState().mode).toBe("search");

        const element = fakeElementWithGeometry("pin-1");
        const flyTo = vi.fn();
        const fitBounds = vi.fn();

        act(() => focusElementFromList(fakeMapManager(), element, { flyTo, fitBounds }));

        expect(useMapStore.getState().selected).toBe(element.key);
        expect(flyTo).toHaveBeenCalledOnce();
        expect(fitBounds).not.toHaveBeenCalled();
        const { mode, listOrigin, slideDirection } = useEventsNavigationStore.getState();
        expect(mode).toBe("search");
        expect(listOrigin).toBeNull();
        expect(slideDirection).toBeNull();
    });

    it("does not let a repeated focus on the selected element swallow the next raw map pick", () => {
        renderHook(() => useEventsNavigationSync());
        const mapManager = fakeMapManager();
        const alpha = fakeElementWithGeometry("pin-repeat-alpha");
        const bravo = fakeElementWithGeometry("pin-repeat-bravo");
        const flyTo = vi.fn();
        const fitBounds = vi.fn();

        act(() => focusElementFromList(mapManager, alpha, { flyTo, fitBounds }));
        expect(useEventsNavigationStore.getState().listFocusTarget).toBeNull();

        // Re-centering an already-selected element does not change the
        // selection value, so there is no sync-effect pass to consume a
        // marker. The helper must therefore avoid creating one.
        act(() => focusElementFromList(mapManager, alpha, { flyTo, fitBounds }));
        expect(useEventsNavigationStore.getState().listFocusTarget).toBeNull();
        expect(flyTo).toHaveBeenCalledTimes(2);
        expect(fitBounds).not.toHaveBeenCalled();

        // A subsequent selection with no list action is a genuine map pick
        // and must navigate to Detail rather than consuming stale state.
        act(() => useMapStore.getState().select(getElementKey(bravo)));

        const { mode, listOrigin, slideDirection } = useEventsNavigationStore.getState();
        expect(mode).toBe("detail");
        expect(listOrigin).toBeNull();
        expect(slideDirection).toBeNull();
    });

    it("does not let a pending list focus swallow a different raw map pick in the same act", () => {
        renderHook(() => useEventsNavigationSync());
        const mapManager = fakeMapManager();
        const alpha = fakeElementWithGeometry("pin-race-alpha");
        const bravo = fakeElementWithGeometry("pin-race-bravo");

        act(() => {
            focusElementFromList(mapManager, alpha, { flyTo: vi.fn(), fitBounds: vi.fn() });
            useMapStore.getState().select(getElementKey(bravo));
        });

        expect(useMapStore.getState().selected).toBe(getElementKey(bravo));
        const { mode, listOrigin, slideDirection, listFocusTarget } =
            useEventsNavigationStore.getState();
        expect(mode).toBe("detail");
        expect(listOrigin).toBeNull();
        expect(slideDirection).toBeNull();
        expect(listFocusTarget).toBeNull();
    });

    it("clears a pending list focus when selection clears before consumption", () => {
        const previouslySelected = fakeElementWithGeometry("pin-clear-previous");
        act(() => useMapStore.getState().select(getElementKey(previouslySelected)));
        renderHook(() => useEventsNavigationSync());
        const alpha = fakeElementWithGeometry("pin-clear-alpha");

        act(() => {
            focusElementFromList(fakeMapManager(), alpha, { flyTo: vi.fn(), fitBounds: vi.fn() });
            useMapStore.setState({ selected: null });
        });

        expect(useMapStore.getState().selected).toBeNull();
        expect(useEventsNavigationStore.getState().listFocusTarget).toBeNull();
    });

    it("selects and pans the camera without leaving the lasso list", () => {
        act(() => {
            setLassoElements();
            useEventsNavigationStore.setState({ mode: "selection" });
        });
        renderHook(() => useEventsNavigationSync());

        const element = fakeElementWithGeometry("pin-2");
        const flyTo = vi.fn();
        const fitBounds = vi.fn();

        act(() => focusElementFromList(fakeMapManager(), element, { flyTo, fitBounds }));

        expect(useMapStore.getState().selected).toBe(element.key);
        expect(flyTo).toHaveBeenCalledOnce();
        expect(fitBounds).not.toHaveBeenCalled();
        const { mode, listOrigin, slideDirection } = useEventsNavigationStore.getState();
        expect(mode).toBe("selection");
        expect(listOrigin).toBeNull();
        expect(slideDirection).toBeNull();
    });
});

// ---------------------------------------------------------------------------
// useEventsNavigationSync — reacting to a fresh lasso landing
// ---------------------------------------------------------------------------
//
// This exercises the useStackedObjectSelectionStore.subscribe callback itself: the
// hook must already be mounted (subscription registered) before
// setLassoElements() transitions stackedElements from null to non-null.
// Seeding the store before renderHook (as other tests in this file do for
// unrelated reasons) would never invoke this callback at all.

describe("useEventsNavigationSync — a fresh lasso lands", () => {
    it("switches mode to lasso when a lasso selection completes while mounted", () => {
        renderHook(() => useEventsNavigationSync());

        act(() => setLassoElements());

        const { mode, listOrigin, slideDirection, selectionAutoDismissed } =
            useEventsNavigationStore.getState();
        expect(mode).toBe("selection");
        expect(listOrigin).toBeNull();
        expect(slideDirection).toBeNull();
        expect(selectionAutoDismissed).toBe(false);
    });

    it("interrupts an unrelated explicit panel when a fresh lasso lands", () => {
        act(() => useMapbarStore.getState().openPanel("filters"));
        renderHook(() => useEventsNavigationSync());

        act(() => setLassoElements());

        expect(useMapbarStore.getState().sidebarPanel).toBe("");
        expect(useEventsNavigationStore.getState().mode).toBe("selection");
    });
});

// ---------------------------------------------------------------------------
// Self-recovery — the detail view goes away
// ---------------------------------------------------------------------------

describe("useMapbarSync — self-recovery when a detail selection clears", () => {
    it("falls back to the search list when a detail selection clears while events is open", () => {
        act(() => {
            useMapbarStore.getState().openPanel("events");
            useEventsNavigationStore.getState().select(null);
            setSelectedElement();
        });

        renderHook(() => {
            useMapbarSync();
            return useMapbar();
        });

        act(() => useMapStore.setState({ selected: null }));

        expect(useMapbarStore.getState().sidebarPanel).toBe("events");
        expect(useEventsNavigationStore.getState().mode).toBe("search");
    });

    it("reveals the lasso list when the focused element clears while a stacked list is active", () => {
        act(() => {
            setLassoElements();
            useMapbarStore.getState().openPanel("events");
            useEventsNavigationStore.getState().select("selection");
            setSelectedElement();
        });

        const { result } = renderHook(() => {
            useMapbarSync();
            return useMapbar();
        });

        act(() => useMapStore.setState({ selected: null }));

        expect(result.current.open).toBe("events");
        expect(useEventsNavigationStore.getState().mode).toBe("selection");
        expect(useEventsNavigationStore.getState().selectionAutoDismissed).toBe(false);
    });

    it("falls back to the lasso list when an empty-map click clears a lasso-origin detail", () => {
        act(() => {
            setLassoElements();
            useMapbarStore.getState().openPanel("events");
            useEventsNavigationStore.getState().select("selection");
            setSelectedElement();
        });

        const { result } = renderHook(() => {
            useMapbarSync();
            return useMapbar();
        });

        act(() => {
            // Mirrors PickingHandler's empty-click sequence: the alternate
            // result set goes away, selection clears, and dismiss is signaled.
            useStackedObjectSelectionStore.getState().dismiss();
            useAreaSelectionStore.getState().clear();
            useMapStore.getState().resetSelection();
            useMapStore.getState().bumpDismiss();
        });

        expect(result.current.open).toBe("events");
        expect(useMapbarStore.getState().sidebarPanel).toBe("events");
        expect(useEventsNavigationStore.getState().listOrigin).toBeNull();
        expect(useEventsNavigationStore.getState().mode).toBe("selection");
    });
});

// ---------------------------------------------------------------------------
// toggleOpen('events') navigation state machine
// ---------------------------------------------------------------------------

describe("toggleOpen('events') navigation state machine", () => {
    it("opens the lasso list when lasso is active and no event is selected", () => {
        act(() => {
            setLassoElements();
            // Both auto-open channels dismissed so open === "" — simulates the
            // user having closed both and now clicking the events button again.
            useEventsNavigationStore.setState({
                selectionAutoDismissed: true,
                autoOpenDismissed: true,
            });
        });

        const { result } = renderHook(() => useMapbar());

        act(() => result.current.toggleOpen("events"));

        expect(useMapbarStore.getState().sidebarPanel).toBe("events");
        expect(useEventsNavigationStore.getState().mode).toBe("selection");
    });

    it("reopens the lasso-origin detail, preserving listOrigin, when lasso is active and an event is selected", () => {
        act(() => {
            setLassoElements();
            setSelectedElement();
            // This element was already selected via a lasso-list click before
            // this test starts (setSelectedElement bypasses the real
            // MapManager.selectElement, so listOrigin/mode are seeded
            // directly to simulate that prior click).
            useEventsNavigationStore.setState({
                listOrigin: "selection",
                mode: "search",
                autoOpenDismissed: true,
                selectionAutoDismissed: true,
            });
            useMapbarStore.setState({ sidebarPanel: "" });
        });

        const { result } = renderHook(() => useMapbar());

        act(() => result.current.toggleOpen("events"));

        expect(useMapbarStore.getState().sidebarPanel).toBe("events");
        expect(useEventsNavigationStore.getState().mode).toBe("detail");
        expect(useEventsNavigationStore.getState().listOrigin).toBe("selection");
    });

    it("returns to the lasso list when the button is pressed while in lasso-origin detail", () => {
        act(() => {
            setLassoElements();
            useMapbarStore.setState({ sidebarPanel: "events" });
            useEventsNavigationStore.setState({ mode: "detail", listOrigin: "selection" });
        });

        const { result } = renderHook(() => useMapbar());

        act(() => result.current.toggleOpen("events"));

        expect(useMapbarStore.getState().sidebarPanel).toBe("events");
        expect(useEventsNavigationStore.getState().mode).toBe("selection");
        // listOrigin is deliberately left as "selection" here — nothing reads it
        // while the list (not detail) is showing, and the next real pick
        // (MapManager.selectElement) always sets it fresh regardless.
        expect(useEventsNavigationStore.getState().listOrigin).toBe("selection");
    });

    it("closes the events panel and calls clearSelection when no lasso is active", () => {
        act(() => {
            setSelectedElement();
            useMapbarStore.setState({ sidebarPanel: "events" });
            useEventsNavigationStore.setState({ mode: "detail", listOrigin: null });
        });

        const { result } = renderHook(() => useMapbar());

        act(() => result.current.toggleOpen("events"));

        expect(useMapbarStore.getState().sidebarPanel).toBe("");
        expect(mockClearSelection).toHaveBeenCalledOnce();
    });

    it("closes the lasso list without clearSelection when it's the one open", () => {
        act(() => {
            setLassoElements();
            useMapbarStore.setState({ sidebarPanel: "events" });
            useEventsNavigationStore.setState({ mode: "selection" });
        });

        const { result } = renderHook(() => useMapbar());

        act(() => result.current.toggleOpen("events"));

        expect(useMapbarStore.getState().sidebarPanel).toBe("");
        expect(mockClearSelection).not.toHaveBeenCalled();
    });

    it("closes the lasso list when a point selection could also auto-open events", () => {
        act(() => {
            setLassoElements();
            setSelectedElement();
            useMapbarStore.setState({ sidebarPanel: "events" });
            useEventsNavigationStore.setState({
                mode: "selection",
                autoOpenDismissed: false,
                selectionAutoDismissed: false,
            });
        });

        const { result } = renderHook(() => useMapbar());

        act(() => result.current.toggleOpen("events"));

        expect(result.current.open).toBe("");
        expect(useMapbarStore.getState().sidebarPanel).toBe("");
        expect(useEventsNavigationStore.getState().autoOpenDismissed).toBe(true);
        expect(useEventsNavigationStore.getState().selectionAutoDismissed).toBe(true);
        expect(mockClearSelection).not.toHaveBeenCalled();
    });

    it("closes a non-lasso detail when a lasso selection could also auto-open events", () => {
        act(() => {
            setLassoElements();
            setSelectedElement();
            useMapbarStore.setState({ sidebarPanel: "events" });
            useEventsNavigationStore.setState({
                mode: "detail",
                listOrigin: null,
                autoOpenDismissed: false,
                selectionAutoDismissed: false,
            });
        });

        const { result } = renderHook(() => useMapbar());

        act(() => result.current.toggleOpen("events"));

        expect(result.current.open).toBe("");
        expect(useMapbarStore.getState().sidebarPanel).toBe("");
        expect(useEventsNavigationStore.getState().autoOpenDismissed).toBe(true);
        expect(useEventsNavigationStore.getState().selectionAutoDismissed).toBe(true);
        expect(mockClearSelection).toHaveBeenCalledOnce();
    });

    it("opens the lasso list when lasso is active, event selected, but the user last viewed the list", () => {
        act(() => {
            setLassoElements();
            setSelectedElement();
            // User navigated: lasso list -> event detail -> back to list -> filters
            useMapbarStore.setState({ sidebarPanel: "filters" });
            useEventsNavigationStore.setState({
                mode: "selection",
                autoOpenDismissed: true,
                selectionAutoDismissed: true,
            });
        });

        const { result } = renderHook(() => useMapbar());

        act(() => result.current.toggleOpen("events"));

        expect(useMapbarStore.getState().sidebarPanel).toBe("events");
        expect(useEventsNavigationStore.getState().mode).toBe("selection");
        expect(useEventsNavigationStore.getState().listOrigin).toBeNull();
    });

    it("restores the detail view when returning from another panel after viewing it", () => {
        act(() => {
            setSelectedElement();
            // User was on events detail, navigated to filters
            useMapbarStore.setState({ sidebarPanel: "filters" });
            useEventsNavigationStore.setState({ mode: "detail", autoOpenDismissed: true });
        });

        const { result } = renderHook(() => useMapbar());

        act(() => result.current.toggleOpen("events"));

        expect(useMapbarStore.getState().sidebarPanel).toBe("events");
        expect(useEventsNavigationStore.getState().mode).toBe("detail");
    });

    it("shows detail rather than a stale lasso list when a selection exists but its results are gone", () => {
        // Under the old two-panel design this fell back to the search list
        // purely as a side effect of panel-ID bookkeeping — the content
        // itself always rendered detail whenever a selection existed. Now
        // that `mode` is the single source of truth for content, the
        // fallback branch checks the real selection directly, so this case
        // resolves to detail explicitly rather than by accident.
        act(() => {
            setSelectedElement();
            // User was on the lasso list, navigated to filters; that lasso's
            // results are gone by the time they come back.
            useMapbarStore.setState({ sidebarPanel: "filters" });
            useEventsNavigationStore.setState({ mode: "selection", autoOpenDismissed: true });
        });

        const { result } = renderHook(() => useMapbar());

        act(() => result.current.toggleOpen("events"));

        expect(useMapbarStore.getState().sidebarPanel).toBe("events");
        expect(useEventsNavigationStore.getState().mode).toBe("detail");
    });
});
