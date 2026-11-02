// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { MapSliceCreator, Partializer } from "../../map.store";

export interface TerrainState {
    terrainConfig: TerrainConfig | null;
    drape: boolean;

    // Depth compare determines the underlying deck.gl depth test function (https://developer.mozilla.org/en-US/docs/Web/API/WebGLRenderingContext/depthFunc)
    // for the z-buffer (https://en.wikipedia.org/wiki/Z-buffering). This depth compare option would be impacting all layers,
    // and causes performance implications due to the z-buffer being completely ignored under "always". This belongs more so in the map settings.
    depthCompare: "lessequal" | "always";
}

export interface ElevationDecoder {
    rScaler: number;
    gScaler: number;
    bScaler: number;
    offset: number;
}

export interface TerrainConfig {
    elevationUrl: string;
    textureUrl: string;
    meshMaxError: number;
    elevationDecoder: ElevationDecoder;
}

export interface TerrainActions {
    isTerrainEnabled(): boolean;
    enableTerrain(config: TerrainConfig): void;
    disableTerrain(): void;
    setDrape(enabled: boolean): void;
    setDepthCompare(depthCompare: TerrainState["depthCompare"]): void;
    resetTerrain(): void;
}

export type TerrainSlice = TerrainState & TerrainActions;

const initialState: TerrainState = {
    drape: true,
    depthCompare: "lessequal",
    terrainConfig: null,
};

export const createTerrainSlice: MapSliceCreator<TerrainSlice> = (set, get) => ({
    ...initialState,
    enableTerrain: (terrainConfig) => set({ terrainConfig }),
    disableTerrain: () => set({ terrainConfig: null }),
    isTerrainEnabled: () => !!get().terrainConfig,
    setDrape: (drape) => set({ drape }),
    setDepthCompare: (depthCompare) => set({ depthCompare }),
    resetTerrain: () =>
        set({
            terrainConfig: null,
            drape: true,
            depthCompare: "lessequal",
        }),
});

export const partializeTerrainSlice: Partializer = (state) => ({
    drape: state.drape,
    depthCompare: state.depthCompare,
});
