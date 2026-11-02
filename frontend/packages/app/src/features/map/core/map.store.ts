// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { create, type StateCreator } from "zustand";
import { createViewSlice, partializeViewSlice, type ViewSlice } from "./slices/view/view.slice";
import { createJSONStorage, subscribeWithSelector } from "zustand/middleware";
import { multiPersist } from "zustand-multi-persist";
import { createMapUrlStorage } from "./map-url-storage.util";
import { merge } from "lodash";
import {
    createSelectionSlice,
    partializeSelectionSlice,
    type SelectionSlice,
} from "./slices/selection/selection.slice";
import { createHoverSlice, type HoverSlice } from "./slices/hover/hover.slice";
import {
    createTerrainSlice,
    partializeTerrainSlice,
    type TerrainSlice,
} from "./slices/terrain/terrain.slice";

export type MapState = ViewSlice & TerrainSlice & SelectionSlice & HoverSlice;

export type MapSliceCreator<T> = StateCreator<MapState, [], [], T>;

export type Partializer = (state: MapState) => Partial<MapState>;

export const MAPSTORE_URL_STORAGE = "map-url";
export const MAPSTORE_LOCAL_STORAGE = "opencop:mapstore-local";

/**
 * Persist version for the URL slice. Bump only when the URL-persisted shape
 * changes in a way that requires migration; the same number is passed to
 * `createMapUrlStorage()` so it surfaces in the `getItem` envelope. If the two
 * ever drift, zustand will run the migrate callback against already-current
 * data on every load.
 */
const MAP_URL_VERSION = 0;

export const useMapStore = create<MapState>()(
    subscribeWithSelector(
        multiPersist(
            (...a) => ({
                ...createViewSlice(...a),
                ...createSelectionSlice(...a),
                ...createHoverSlice(...a),
                ...createTerrainSlice(...a),
            }),
            {
                [MAPSTORE_URL_STORAGE]: {
                    storage: createMapUrlStorage(undefined, MAP_URL_VERSION),
                    version: MAP_URL_VERSION,
                    partialize: (state) => ({
                        ...partializeViewSlice(state),
                        ...partializeSelectionSlice(state),
                    }),
                    merge: (persisted, current) => merge(current, persisted),
                },
                [MAPSTORE_LOCAL_STORAGE]: {
                    storage: createJSONStorage(() => localStorage),
                    partialize: (state) => ({
                        ...partializeTerrainSlice(state),
                    }),
                    merge: (persisted, current) => merge(current, persisted),
                },
            },
        ),
    ),
);
