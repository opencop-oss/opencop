// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { create } from "zustand";
import { getElementKey, type Element } from "@opencop/elements";
import type { MapManager } from "~/features/map/MapManager";
import { useMapStore } from "~/features/map/core/map.store";
import { focusOnElement, type FocusCameraActions } from "~/features/map/element-focus.util";

/** Which Events sub-view is (or, when the panel isn't open, was last) showing. */
export type EventsMode = "search" | "selection" | "detail";

export type ListOrigin = "search" | "selection" | null;

interface EventsNavigationStore {
    mode: EventsMode;
    /**
     * Which list the currently-viewed detail was opened from, if any. Drives
     * the back-to-list breadcrumb and which list `mode` falls back to when
     * the selection clears. `null` for a raw map-feature pick — there's no
     * list to go back to, so no breadcrumb.
     */
    listOrigin: ListOrigin;
    /** Direction for EventsPanel's (non-remounting) slide transition. */
    slideDirection: "forward" | "back" | null;
    /** Dismissed the auto-open triggered by a point-pick. */
    autoOpenDismissed: boolean;
    /** Dismissed the auto-open triggered by an area selection landing. */
    selectionAutoDismissed: boolean;
    /**
     * True for the one effect pass immediately after select() runs — lets
     * useEventsNavigationSync tell "the selection that just landed was a
     * list-driven pick, already fully handled by select()" apart from "a raw
     * map pick landed with no list involved," without MapManager needing to
     * say which. Consumed (set back to false) as soon as that pass reads it.
     */
    listPickPending: boolean;
    /**
     * Element key expected from focusFromList(). useEventsNavigationSync only
     * suppresses navigation when the selected key matches this target, so an
     * intervening raw pick cannot consume another element's pending focus.
     */
    listFocusTarget: string | null;

    /** Called by selectFromList for a pick made from an Events list row. */
    select: (origin: ListOrigin) => void;
    /** Called by focusElementFromList before it hands off to MapManager — records the selection change that is a non-navigating focus. */
    focusFromList: (target: string) => void;
    /** Called when a pick lands that didn't go through select() — always detail, never a slide, no breadcrumb. */
    rawPickLanded: () => void;
    /** Called when a fresh area selection lands. */
    areaSelected: () => void;
    /**
     * Called when the area selection is dismissed — drops every reference to
     * the list that is gone: a detail's back-to-list origin, and the list mode
     * itself (search is the canonical list to fall back to). Nothing else moves.
     */
    areaSelectionCleared: () => void;
    /** Called when the active detail's selection clears — falls back to whichever list it came from. */
    selectionCleared: () => void;
    /**
     * Explicit navigation to a list (back-to-list breadcrumb/button, or the
     * toolbar toggle used as a back gesture). Only slides if it's actually
     * leaving a detail view — opening/revealing a list from closed never
     * slides.
     */
    showList: (which: "search" | "selection") => void;
    /** Reopen a still-selected detail with no fresh pick (listOrigin already set) — a reveal, never a slide. */
    showDetail: () => void;
    dismissAutoOpen: () => void;
    dismissSelectionAutoOpen: () => void;
    /** Dismiss every channel that can auto-open Events after an explicit close. */
    dismissAllAutoOpen: () => void;
}

export const useEventsNavigationStore = create<EventsNavigationStore>((set) => ({
    mode: "search",
    listOrigin: null,
    slideDirection: null,
    autoOpenDismissed: false,
    selectionAutoDismissed: false,
    listPickPending: false,
    listFocusTarget: null,

    // Slides forward: this is the one place a real "click an item in a list"
    // navigation happens.
    select: (origin) =>
        set({
            mode: "detail",
            listOrigin: origin,
            slideDirection: origin ? "forward" : null,
            autoOpenDismissed: false,
            listPickPending: true,
        }),

    // Deliberately touches nothing but the marker — mode/listOrigin/
    // slideDirection stay exactly as they are so the list stays open.
    focusFromList: (target) => set({ listFocusTarget: target }),

    // Never slides: a raw map pick isn't a click inside the Events panel.
    rawPickLanded: () =>
        set({
            mode: "detail",
            listOrigin: null,
            slideDirection: null,
            autoOpenDismissed: false,
            listPickPending: false,
            listFocusTarget: null,
        }),

    areaSelected: () =>
        set({
            mode: "selection",
            listOrigin: null,
            slideDirection: null,
            selectionAutoDismissed: false,
        }),

    // The invariant `listOrigin` documents ("null — no list to go back to")
    // applied from the selection's side: a detail opened from the working set
    // keeps `listOrigin: "selection"` until told the list is gone. Written as
    // one step of `clearAreaSelection()` so the two stores move together.
    areaSelectionCleared: () =>
        set((s) => ({
            listOrigin: s.listOrigin === "selection" ? null : s.listOrigin,
            mode: s.mode === "selection" ? "search" : s.mode,
        })),

    selectionCleared: () =>
        set((s) => {
            // A passive clear (mode still "detail", e.g. an empty-map click)
            // falls back to a list and drops any in-flight slide. But if mode
            // already moved off "detail" earlier in this same synchronous tick
            // — EventDetail's back-to-list breadcrumb calls showList() (which
            // sets slideDirection: "back") and then clears the selection —
            // that call already chose the slide direction intentionally, so
            // leave it alone here instead of clobbering it back to null.
            if (s.mode !== "detail") {
                return { listOrigin: null, listPickPending: false, listFocusTarget: null };
            }
            return {
                mode: s.listOrigin === "selection" ? "selection" : "search",
                listOrigin: null,
                slideDirection: null,
                listPickPending: false,
                listFocusTarget: null,
            };
        }),

    // Slides back only when actually leaving a detail view. Opening/revealing
    // a list from closed (the toolbar button, auto-open) passes a `which`
    // too, but there's no detail on screen to slide away from, so no slide.
    showList: (which) =>
        set((s) => ({ mode: which, slideDirection: s.mode === "detail" ? "back" : null })),

    // Always a reveal, never a click inside the panel — never slides.
    showDetail: () => set({ mode: "detail", slideDirection: null }),

    dismissAutoOpen: () => set({ autoOpenDismissed: true }),

    dismissSelectionAutoOpen: () => set({ selectionAutoDismissed: true }),

    dismissAllAutoOpen: () =>
        set({
            autoOpenDismissed: true,
            selectionAutoDismissed: true,
        }),
}));

/**
 * Select an element picked from an Events list (search results or an area
 * result row), recording which list it came from before handing off to
 * MapManager's generic selection. List-driven picks route through here
 * instead of through a listOrigin parameter on MapManager.selectElement, so
 * MapManager itself stays unaware of Events' navigation concepts.
 *
 * Contrast with focusElementFromList below — the location-pin button also
 * selects an element from one of these same lists, but must not navigate to
 * Detail, so it records a listFocusTarget instead of calling select().
 */
export function selectFromList(
    mapManager: MapManager,
    element: Element,
    listOrigin: "search" | "selection",
): void {
    useEventsNavigationStore.getState().select(listOrigin);
    mapManager.selectElement(element);
}

/**
 * Select an element picked from an Events list and pan the camera to it,
 * without navigating the panel to Detail — the location-pin button on each
 * list row. When the element is not already selected, records its key as the
 * listFocusTarget before calling MapManager.selectElement() so
 * useEventsNavigationSync can tell this selection change apart from a real
 * pick and leave mode/listOrigin/slideDirection untouched. An already-selected
 * element only needs the camera movement; skipping the no-op selection also
 * prevents an unconsumed pending marker from leaking into the next real map
 * pick.
 */
export function focusElementFromList(
    mapManager: MapManager,
    element: Element,
    cameraActions: FocusCameraActions,
): void {
    const elementKey = getElementKey(element);
    if (useMapStore.getState().selected !== elementKey) {
        useEventsNavigationStore.getState().focusFromList(elementKey);
        mapManager.selectElement(element);
    }
    focusOnElement(element, cameraActions);
}
