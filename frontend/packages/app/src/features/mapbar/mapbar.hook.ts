// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useCallback, useEffect } from "react";
import { useIsDrawing, useIsEditing } from "~/features/map/interaction-runtime/session/selectors";
import { useMapbarStore } from "./mapbar.store";
import {
    useEventsNavigation,
    useEventsNavigationSync,
} from "~/features/events/use-events-navigation.hook";
import { useSelectionState } from "../map/core/slices/selection/selection.hook";
import { useVisiblePanels } from "./use-visible-panels.hook";

export type MapbarContextType = {
    open: string;
    onOpen: (tab: string) => void;
    onClose: () => void;
    toggleOpen: (tab: string) => void;
};

/**
 * Synchronises MapManager events with the Zustand mapbar store.
 * Call once inside the MapManager provider tree.
 */
export function useMapbarSync() {
    useEventsNavigationSync();

    const closePanel = useMapbarStore((s) => s.closePanel);
    const sidebarPanel = useMapbarStore((s) => s.sidebarPanel);
    const dismissTick = useSelectionState((s) => s.dismissTick);
    const isDrawing = useIsDrawing();

    // Dismiss whatever explicit panel is open on every empty-map click
    // (covers the case where selection was already null, so
    // useEventsNavigationSync's own selection effect wouldn't fire). Events
    // recovers on its own by re-deriving its content from the now-cleared
    // selection/lasso state, so it's the one panel left alone here.
    useEffect(() => {
        if (dismissTick === 0) return;
        if (useMapbarStore.getState().sidebarPanel === "events") return;
        closePanel();
    }, [dismissTick, closePanel]);

    // Starting a custody draw dismisses any open panel — the draw owns the
    // map surface, panels would compete for focus / clicks, and its completion
    // pins the events panel anyway. A custody-less draw (a measurement, a
    // watchbox polygon) leaves the panels alone: nothing it does competes.
    useEffect(() => {
        if (isDrawing) closePanel();
    }, [isDrawing, closePanel]);

    const visiblePanels = useVisiblePanels();
    useEffect(() => {
        if (sidebarPanel && !visiblePanels.some((panel) => panel.id === sidebarPanel)) {
            closePanel();
        }
    }, [closePanel, sidebarPanel, visiblePanels]);
}

export function useMapbar(): MapbarContextType {
    // Both custody rules: a custody draw suppresses the events panel's
    // auto-open (its product is not selected until it completes); a custody
    // edit pins the panel open (Save/Cancel live there). A custody-less
    // session (a measurement, a lasso, a watchbox polygon) does neither — its
    // own panel, if any, opens itself.
    const isDrawing = useIsDrawing();
    const isEditing = useIsEditing();
    const sidebarPanel = useMapbarStore((s) => s.sidebarPanel);
    const storeToggle = useMapbarStore((s) => s.togglePanel);
    const onOpen = useMapbarStore((s) => s.openPanel);
    const onClose = useMapbarStore((s) => s.closePanel);
    const { autoOpen, toggleEvents } = useEventsNavigation();

    const open = isEditing
        ? "events"
        : isDrawing
          ? sidebarPanel || ""
          : sidebarPanel || (autoOpen ? "events" : "");

    const toggleOpen = useCallback(
        (tab: string) => {
            if (tab === "events") {
                toggleEvents(open);
                return;
            }
            storeToggle(tab);
        },
        [open, toggleEvents, storeToggle],
    );

    return { open, onOpen, onClose, toggleOpen };
}
