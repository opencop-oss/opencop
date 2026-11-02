// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { MapViewState } from "@deck.gl/core";
import type { MapSliceCreator, Partializer } from "../../map.store";

// Use Deck.GL ViewState for impending change to use Deck.GL as root provider. Add on some props to support current MapLibre implementation.
export type ViewState = MapViewState &
    // Make MapLibre reactive to changes in these props.
    {
        bounds?: [left: number, bottom: number, right: number, top: number];
        flyToTarget?: ViewTarget;
        jumpToTarget?: ViewTarget;
        fitBoundsTarget?: {
            bounds: [[number, number], [number, number]];
            options?: FitBoundsOptions;
        };
    };

export interface ViewActions {
    setViewState: (view: Partial<ViewState>) => void;
    flyTo: (target: ViewTarget) => void;
    jumpTo: (target: ViewTarget) => void;
    fitBounds: (bounds: [[number, number], [number, number]], options?: FitBoundsOptions) => void;
}

export type ViewSlice = ViewState & ViewActions;

export interface ViewTarget {
    center?: [number, number] | { lng: number; lat: number };
    zoom?: number;
    pitch?: number;
    bearing?: number;
    speed?: number;
    duration?: number;
}

export interface FitBoundsOptions {
    padding?: number | { top: number; bottom: number; left: number; right: number };
    speed?: number;
    duration?: number;
    maxZoom?: number;
    canvasWidth?: number;
    canvasHeight?: number;
}

/** Dummy initial state when no storage exists and waiting for user preferences */
export const dummyState: ViewState = {
    bounds: [0, 0, 0, 0],
    longitude: Infinity,
    latitude: Infinity,
    zoom: 0,
    pitch: 0,
    bearing: 0,
};

/** Holds all view related state. Currently only used on initial map mount, Map component is uncontrolled. */
export const createViewSlice: MapSliceCreator<ViewSlice> = (set) => ({
    ...dummyState,
    setViewState: (view) => set(view),
    flyTo: (target) => set({ flyToTarget: target }),
    jumpTo: (target) => set({ jumpToTarget: target }),
    fitBounds: (bounds, options) => set({ fitBoundsTarget: { bounds, options } }),
});

export const partializeViewSlice: Partializer = (state) => {
    return state.longitude === Infinity || state.latitude === Infinity
        ? {}
        : {
              longitude: state.longitude,
              latitude: state.latitude,
              zoom: state.zoom,
              pitch: state.pitch,
              bearing: state.bearing,
          };
};
