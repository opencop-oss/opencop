// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { Feature } from "geojson";
import type { MapSliceCreator, Partializer } from "../../map.store";

export interface SelectionState {
    selected: string | null;
    selectionFeatures?: Feature[];
    /** Bumped on every empty-map click so panel listeners can dismiss even when selection was already null. */
    dismissTick: number;
}

export interface SelectionActions {
    select(key: string, features?: Feature[]): void;
    resetSelection(): void;
    bumpDismiss(): void;
}

export type SelectionSlice = SelectionState & SelectionActions;

const initialState: SelectionState = {
    selected: null,
    selectionFeatures: undefined,
    dismissTick: 0,
};

export const createSelectionSlice: MapSliceCreator<SelectionSlice> = (set) => ({
    ...initialState,
    select: (key, features) => set({ selected: key, selectionFeatures: features }),
    resetSelection: () => set({ selected: null, selectionFeatures: undefined }),
    bumpDismiss: () => set((s) => ({ dismissTick: s.dismissTick + 1 })),
});

export const partializeSelectionSlice: Partializer = (state) => ({
    selected: state.selected?.startsWith("local") // ignore local
        ? null
        : state.selected,
});
