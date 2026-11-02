// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { cloneDeep, set as _set, unset as _unset } from "lodash";
import { create } from "zustand";

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

interface EventsEditingActions {
    /** Enter edit mode. Sets an empty overlay — panel reads element from store. */
    enterEditMode: () => void;
    /** Set a field in the sparse overlay. */
    setField: (path: string[], value: unknown) => void;
    /** Delete a field from the sparse overlay. */
    deleteField: (path: string[]) => void;
    /** Mark a form field as touched (controls when validation errors display). */
    markTouched: (field: string) => void;
    /** Remove fields from the touched set. */
    unmarkTouched: (fields: string[]) => void;
    /** Clear overlay and exit edit mode. */
    clearOverlay: () => void;
}

interface EventsEditingState {
    /**
     * Sparse overlay of user-changed properties. `null` = not editing.
     * Only fields the user explicitly changed are tracked here.
     * On save, the overlay is merged on top of the live element data.
     */
    overlay: Record<string, unknown> | null;
    /** Fields the user has interacted with — controls when validation errors display. */
    touchedFields: Set<string>;
    actions: EventsEditingActions;
}

// ---------------------------------------------------------------------------
// Store (global singleton — only one edit session at a time)
// ---------------------------------------------------------------------------

export const useEventsEditingStore = create<EventsEditingState>()((set) => ({
    overlay: null,
    touchedFields: new Set<string>(),

    actions: {
        enterEditMode: () => {
            set({ overlay: {} });
        },

        setField: (path, value) =>
            set((s) => {
                if (!s.overlay) return s;
                const newOverlay = cloneDeep(s.overlay);
                _set(newOverlay, path, value);
                return { overlay: newOverlay };
            }),

        deleteField: (path) =>
            set((s) => {
                if (!s.overlay) return s;
                const newOverlay = cloneDeep(s.overlay);
                _unset(newOverlay, path);
                return { overlay: newOverlay };
            }),

        markTouched: (field) =>
            set((s) => ({ touchedFields: new Set(s.touchedFields).add(field) })),

        unmarkTouched: (fields) =>
            set((s) => {
                const next = new Set(s.touchedFields);
                for (const f of fields) next.delete(f);
                return { touchedFields: next };
            }),

        clearOverlay: () => set({ overlay: null, touchedFields: new Set() }),
    },
}));

// ---------------------------------------------------------------------------
// Hooks
// ---------------------------------------------------------------------------

export function useEventsEditingActions(): EventsEditingActions {
    return useEventsEditingStore((s) => s.actions);
}
