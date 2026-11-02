// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { Feature } from "geojson";
import type { MapSliceCreator } from "../../map.store";

export interface HoverState {
    hovered: string | null;
    hoveredFeatures?: Feature[];
}

export interface HoverActions {
    hover(key: string, features?: Feature[]): void;
    resetHover(): void;
}

export type HoverSlice = HoverState & HoverActions;

const initialState: HoverState = {
    hovered: null,
};

export const createHoverSlice: MapSliceCreator<HoverSlice> = (set) => ({
    ...initialState,
    hover: (key, features) => set({ hovered: key, hoveredFeatures: features }),
    resetHover: () => set({ hovered: null, hoveredFeatures: undefined }),
});
