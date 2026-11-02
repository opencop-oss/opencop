// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { create } from "zustand";
import type { Element } from "@opencop/elements";
import { pruneDeletedElements } from "./prune-picked.util";
import { openSurface } from "~/features/map/interaction-runtime/arbitration/surface";

const DEPTH_INITIAL = 20;

/** Screen point the popover is anchored at. */
export interface PickPosition {
    x: number;
    y: number;
}

/**
 * The elements under one click, shown as the `StackedElements` popover anchored
 * at the click so the operator can say which one they meant.
 *
 * A transient disambiguation surface: it opens on an ambient pick, pages deeper
 * on demand, and dismisses on almost anything — a row choice, a click outside,
 * Escape, the map moving, or a session starting. Nothing here outlives the
 * question it was asked to answer.
 *
 * Unrelated to the area selection (`features/selection/area-selection.store`),
 * which is a working set the operator builds on purpose and keeps. The two used
 * to share one store and a `source` discriminator; they share nothing but the
 * pruning rule now.
 */
interface StackedObjectSelectionState {
    /** The elements under the click, or null when the popover is closed. */
    elements: Element[] | null;
    /** Where to anchor the popover. */
    position: PickPosition | null;
    /** Whether the last pick hit the depth ceiling, so "load more" is offered. */
    hitDepthLimit: boolean;
    /** How deep the current pick has paged. */
    currentDepth: number;

    /** Open the popover on the elements under a click. */
    open(elements: Element[], position: PickPosition, hitDepthLimit: boolean): void;
    /** Page deeper, keeping the already-listed elements. */
    advanceDepth(depth: number, elements: Element[], hitDepthLimit: boolean): void;
    /** Close the popover. */
    dismiss(): void;
    /** Drop elements whose exact store keys were deleted; close if none remain. */
    removeDeleted(deletedKeys: ReadonlySet<string>): void;
}

export const useStackedObjectSelectionStore = create<StackedObjectSelectionState>()((set, get) => ({
    elements: null,
    position: null,
    hitDepthLimit: false,
    currentDepth: DEPTH_INITIAL,

    open(elements, position, hitDepthLimit) {
        set({ elements, position, hitDepthLimit, currentDepth: DEPTH_INITIAL });
    },

    advanceDepth(depth, elements, hitDepthLimit) {
        if (get().elements === null) return;
        set({ currentDepth: depth, elements, hitDepthLimit });
    },

    dismiss() {
        set({ elements: null, position: null, hitDepthLimit: false, currentDepth: DEPTH_INITIAL });
    },

    removeDeleted(deletedKeys) {
        const elements = get().elements;
        if (!elements) return;
        const remaining = pruneDeletedElements(elements, deletedKeys);
        if (!remaining) return;
        if (remaining.length === 0) {
            get().dismiss();
            return;
        }
        set({ elements: remaining });
    },
}));

/** Whether the popover is open. */
export function useIsStackedObjectSelectionOpen(): boolean {
    return useStackedObjectSelectionStore((s) => s.elements !== null);
}

/** Whether the popover is open, outside React. */
export function isStackedObjectSelectionOpen(): boolean {
    return useStackedObjectSelectionStore.getState().elements !== null;
}

/** Close the popover, outside React. */
export function dismissStackedObjectSelection(): void {
    useStackedObjectSelectionStore.getState().dismiss();
}

/**
 * Keep the interaction runtime's surface slot in step with this store.
 *
 * **Declared from the store, not from `StackedElements`' mount.** A surface must
 * be declared in the same synchronous step that opens it — a passive effect
 * runs a macrotask later than the event that opened the surface, so a keypress
 * can land in between and start a session that finds nothing to close. The
 * store subscription is how *this* surface meets that rule; a surface whose only
 * truth is component state meets it with `useLayoutEffect` instead (see
 * `MapContextMenu`). The second reason applies only here: the store already
 * answers "am I open", so registering from a mount would answer it twice.
 *
 * The hover the popover holds while open (deck fires hover-on-nothing under
 * its DOM) is the runtime's to withhold and release — its hover policy follows
 * who owns the pointer — so nothing here touches hover.
 */
export function attachStackedObjectSelectionSurface(): () => void {
    let release: (() => void) | undefined;
    const sync = (open: boolean) => {
        if (open && !release) {
            release = openSurface({
                id: "stacked-object-selection",
                close: dismissStackedObjectSelection,
            });
        } else if (!open && release) {
            release();
            release = undefined;
        }
    };
    sync(isStackedObjectSelectionOpen());
    const unsubscribe = useStackedObjectSelectionStore.subscribe((state) =>
        sync(state.elements !== null),
    );
    return () => {
        unsubscribe();
        release?.();
    };
}
