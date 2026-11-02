// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { create } from "zustand";
import { getElementKey, type Element } from "@opencop/elements";
import { pruneCheckedKeys, pruneDeletedElements } from "~/features/map/prune-picked.util";
import { useEventsNavigationStore } from "~/features/events/events-navigation.store";

/** A closed region in map coordinates — where a selection was gathered from. */
export type SelectionBoundary = readonly [number, number][];

/**
 * The elements an area-gathering tool selected, shown as the `SelectionPanel`
 * list inside the Events panel, with the boundary they came from kept so the
 * map can draw it.
 *
 * A working set: the operator builds it on purpose, ticks a subset, exports it,
 * and edits its members one at a time — so it deliberately survives an edit
 * session, and nothing dismisses it but the operator.
 *
 * **Named for the selection, not for the tool.** The lasso is the only producer
 * today, but it reaches this store through `CompletionHost.selectElements`,
 * which any tool may call — a box-select or a radius-select would hand over the
 * same way. Naming this after one producer is what would force the second one
 * to write to a store named after a different tool, or to open its own; the
 * second store is where the discriminator comes back.
 *
 * **Host-owned on purpose.** The gathering *tool* is a `ToolDescriptor` that
 * could live in a plugin; the selection it produces is the host's, because the
 * Events panel, export and element selection are host concerns. A tool hands
 * its result over and keeps nothing — no consumer of this store is the tool.
 *
 * Unrelated to `features/map/stacked-object-selection.store`, the transient
 * click-disambiguation popover. The two used to share one store and a `source`
 * discriminator.
 */
interface AreaSelectionState {
    /** The selected elements, or null when there is no selection. */
    elements: Element[] | null;
    /** The region the selection was gathered from, drawn for as long as it lasts. */
    boundary: SelectionBoundary | null;
    /** The ticked subset, keyed by `getElementKey`. */
    checkedKeys: Set<string>;

    /** Replace the selection with a newly gathered set. Everything starts ticked. */
    select(elements: Element[], boundary: SelectionBoundary): void;
    /** Drop the selection and its boundary together. */
    clear(): void;

    toggleChecked(key: string): void;
    /** Tick or untick just these keys, leaving the rest of the selection as it is. */
    setChecked(keys: Iterable<string>, checked: boolean): void;
    /** Drop elements whose exact store keys were deleted; clear if none remain. */
    removeDeleted(deletedKeys: ReadonlySet<string>): void;
}

export const useAreaSelectionStore = create<AreaSelectionState>()((set, get) => ({
    elements: null,
    boundary: null,
    checkedKeys: new Set(),

    select(elements, boundary) {
        set({ elements, boundary, checkedKeys: new Set(elements.map(getElementKey)) });
    },

    clear() {
        set({ elements: null, boundary: null, checkedKeys: new Set() });
    },

    toggleChecked(key) {
        const next = new Set(get().checkedKeys);
        if (next.has(key)) next.delete(key);
        else next.add(key);
        set({ checkedKeys: next });
    },

    setChecked(keys, checked) {
        const next = new Set(get().checkedKeys);
        for (const key of keys) {
            if (checked) next.add(key);
            else next.delete(key);
        }
        set({ checkedKeys: next });
    },

    removeDeleted(deletedKeys) {
        const { elements, checkedKeys } = get();
        if (!elements) return;
        const remaining = pruneDeletedElements(elements, deletedKeys);
        if (!remaining) return;
        if (remaining.length === 0) {
            get().clear();
            return;
        }
        set({ elements: remaining, checkedKeys: pruneCheckedKeys(checkedKeys, remaining) });
    },
}));

/** Whether an area selection is active. */
export function useHasAreaSelection(): boolean {
    return useAreaSelectionStore((s) => s.elements !== null);
}

/** Whether an area selection is active, outside React. */
export function hasAreaSelection(): boolean {
    return useAreaSelectionStore.getState().elements !== null;
}

/**
 * Dismiss the area selection — the one verb for it, whatever the channel
 * (Escape, a zero-match lasso, the panel's Cancel, a mode switch).
 *
 * Two stores move as one: the selection and its boundary are dropped, and the
 * events navigation forgets the list that is gone — a detail opened from this
 * selection loses its back-to-list origin, and the selection list mode falls
 * back to search. `events-navigation.store` documents `listOrigin: null` as
 * "no list to go back to"; this is where that rule is applied, so no
 * breadcrumb can outlive the list it names.
 */
export function clearAreaSelection(): void {
    useAreaSelectionStore.getState().clear();
    useEventsNavigationStore.getState().areaSelectionCleared();
}
