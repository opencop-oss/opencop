// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useCallback, useEffect } from "react";
import { useMapManager } from "~/features/map/MapManagerContext";
import { usePickedElementRef } from "~/features/map/use-picked-element.hook";
import { useSelectionState } from "~/features/map/core/slices/selection/selection.hook";
import {
    useHasAreaSelection,
    useAreaSelectionStore,
} from "~/features/selection/area-selection.store";
import { useMapbarStore } from "~/features/mapbar/mapbar.store";
import { useEventsNavigationStore, type EventsMode } from "./events-navigation.store";

export interface EventsNavigation {
    mode: EventsMode;
    slideDirection: "forward" | "back" | null;
    /** Whether Events should auto-open given the current pick/lasso state, absent an explicit non-events panel choice. */
    autoOpen: boolean;
    /** Handles a click on the Events toolbar button. `open` is the mapbar-derived currently-visible panel id. */
    toggleEvents: (open: string) => void;
}

/**
 * Owns Events' navigation state: which sub-view (search / lasso / detail) is
 * showing, whether it should auto-open for the current pick/lasso state, and
 * the toolbar toggle-button state machine. Callers only need `sidebarPanel`'s
 * generic open/closed identity from mapbar — everything about *which* Events
 * sub-view that resolves to lives here.
 */
export function useEventsNavigation(): EventsNavigation {
    const mode = useEventsNavigationStore((s) => s.mode);
    const slideDirection = useEventsNavigationStore((s) => s.slideDirection);
    const autoOpenDismissed = useEventsNavigationStore((s) => s.autoOpenDismissed);
    const selectionAutoDismissed = useEventsNavigationStore((s) => s.selectionAutoDismissed);
    const selectedRef = usePickedElementRef();
    const hasAreaSelection = useHasAreaSelection();
    const openPanel = useMapbarStore((s) => s.openPanel);
    const closePanel = useMapbarStore((s) => s.closePanel);
    const mapManager = useMapManager();
    const hasSelection = selectedRef !== null;

    const autoOpen =
        (!autoOpenDismissed && hasSelection) || (!selectionAutoDismissed && hasAreaSelection);

    const toggleEvents = useCallback(
        (open: string) => {
            const nav = useEventsNavigationStore.getState();

            if (open === "events") {
                if (nav.mode === "selection") {
                    // Lasso list showing -> close entirely.
                    nav.dismissAllAutoOpen();
                    closePanel();
                    return;
                }
                if (nav.mode === "detail" && nav.listOrigin === "selection") {
                    // Lasso-origin detail -> back to its list, keeping the map selection.
                    nav.showList("selection");
                    return;
                }
                // Search list, or a non-lasso detail -> close and clear the map selection.
                nav.dismissAllAutoOpen();
                mapManager.clearSelection();
                closePanel();
                return;
            }

            // Events isn't currently showing — decide what opening it should reveal.
            if (hasAreaSelection && hasSelection && nav.mode !== "selection") {
                // Lasso active + event selected, and the user didn't last
                // prefer the list -> reopen detail with its breadcrumb intact.
                nav.showDetail();
                openPanel("events");
                return;
            }

            if (hasAreaSelection) {
                // Lasso active, no selection (or the user last preferred the list) -> show it.
                nav.showList("selection");
                openPanel("events");
                return;
            }

            if (hasSelection) {
                nav.showDetail();
            } else {
                // Search is the canonical Events List. A stale list preference
                // must never reopen an empty lasso panel after its results are gone.
                nav.showList("search");
            }
            openPanel("events");
        },
        [closePanel, openPanel, mapManager, hasAreaSelection, hasSelection],
    );

    return { mode, slideDirection, autoOpen, toggleEvents };
}

/**
 * Reactive side of Events' navigation: reacts to selection/lasso changes that
 * don't originate from the toolbar toggle (a raw map pick, an empty-map
 * click, a fresh lasso completing). Call once alongside useMapbarSync.
 */
export function useEventsNavigationSync(): void {
    const selected = useSelectionState((s) => s.selected);

    useEffect(() => {
        const nav = useEventsNavigationStore.getState();
        if (selected === null) {
            // selectionCleared() also invalidates any pending list focus — a
            // cleared selection can never match the recorded target key.
            // The detail view is going away regardless of what runs next —
            // fall back to whichever list it came from so content is correct
            // whether or not Events stays the visible panel.
            nav.selectionCleared();
            if (useMapbarStore.getState().sidebarPanel === "events") return;
            nav.dismissAutoOpen();
            useMapbarStore.getState().closePanel();
            return;
        }
        // The location-pin button records the exact key it expects before
        // calling MapManager.selectElement. Only that selection may consume
        // the target; a different selection is a genuine pick and follows the
        // normal navigation path below.
        if (nav.listFocusTarget === selected) {
            useEventsNavigationStore.setState({ listFocusTarget: null });
            return;
        }
        if (nav.listFocusTarget !== null) {
            useEventsNavigationStore.setState({ listFocusTarget: null });
        }
        // A list-driven pick already set mode/listOrigin/slideDirection
        // synchronously via select() (selectFromList calls it right before
        // MapManager.selectElement) — listPickPending marks that hand-off so
        // this pass doesn't need to re-derive anything, just consume the
        // marker. Anything else reaching here is a raw map pick that never
        // went through select() at all — MapManager stays unaware of Events,
        // so this is the only place that turns it into a detail view.
        if (nav.listPickPending) {
            useEventsNavigationStore.setState({ listPickPending: false });
        } else {
            nav.rawPickLanded();
        }
        if (useMapbarStore.getState().sidebarPanel !== "events") {
            useMapbarStore.getState().closePanel();
        }
    }, [selected]);

    useEffect(() => {
        return useAreaSelectionStore.subscribe((state, prevState) => {
            // The transition into a selection. The old guard also had to exclude
            // "a point pick replaced the lasso", because both shared one slot —
            // which meant a lasso completed over an open popover never opened the
            // panel. Separate stores make the condition just "a selection appeared".
            if (state.elements === null || prevState.elements !== null) return;
            useEventsNavigationStore.getState().areaSelected();
            if (useMapbarStore.getState().sidebarPanel !== "events") {
                useMapbarStore.getState().closePanel();
            }
        });
    }, []);
}
