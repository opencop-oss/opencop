// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useCallback } from "react";
import { parseElementKey, type Element, type ElementRef } from "@opencop/elements";
import { useMapManager } from "./MapManagerContext";
import { useDraftElement, useElement } from "@opencop/ingest";
import { useSelectionState } from "./core/slices/selection/selection.hook";
import { useMapStore } from "./core/map.store";

/**
 * Stable ref of the currently selected event. Only changes on user action
 * (map click / clear), never on WS updates.
 */
export function usePickedElementRef(): ElementRef | null {
    return parseElementKey(useSelectionState((s) => s.selected) ?? "");
}

/**
 * Sync Element for the current selection, whatever plane produced it. Live and
 * historical elements share one store — distinguished by `elementClass`, not by
 * which store they live in — so a single subscription serves both: the result
 * updates on selection change, on WebSocket addBatch arrivals, and as streaming
 * historical chunks land.
 */
export function usePickedElement(): Element | undefined {
    const selected = useSelectionState((s) => s.selected);
    const draft = useDraftElement();
    const element = useElement(selected ?? "");
    return draft && draft.key === selected ? draft : element;
}

/**
 * Hook to get a stable callback for clearing the selection.
 */
export function useClearPickedElement(): () => void {
    const mapManager = useMapManager();
    return useCallback(() => mapManager.clearSelection(), [mapManager]);
}

/**
 * Clears the current map selection only when its key is in `elementKeys`.
 * The selected key is read when this runs so an async operation cannot
 * clear a different element the user focused while that operation was pending.
 */
export function clearPickedElementIfMatches(
    elementKeys: ReadonlySet<string>,
    clearPickedElement: () => void,
): void {
    const { selected } = useMapStore.getState();
    if (selected && elementKeys.has(selected)) {
        clearPickedElement();
    }
}
