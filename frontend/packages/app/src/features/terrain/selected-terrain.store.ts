// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { create } from "zustand";
import { persist } from "zustand/middleware";

export interface SelectedTerrainState {
    terrainId?: string;
    textureId?: string;
}

export interface SelectedTerrainActions {
    setTerrainId(id: string): void;
    setTextureId(id: string): void;
    /**
     * Drops persisted selections whose tile is no longer offered. An admin can disable a tile long
     * after it was selected, and these ids outlive the browser session.
     */
    reconcile(availableIds: string[]): void;
    reset(): void;
}

export interface SelectedTerrainStore extends SelectedTerrainState {
    actions: SelectedTerrainActions;
}

const initialState: SelectedTerrainState = {};

export const SELECTED_TERRAIN_STORAGE = "opencop:selected-terrain";

export const useSelectedTerrainStore = create<SelectedTerrainStore>()(
    persist(
        (set) => ({
            ...initialState,
            actions: {
                setTerrainId: (id) => set({ terrainId: id }),
                setTextureId: (id) => set({ textureId: id }),
                reconcile: (availableIds) =>
                    set((state) => {
                        const available = new Set(availableIds);
                        const terrainId =
                            state.terrainId && available.has(state.terrainId)
                                ? state.terrainId
                                : undefined;
                        const textureId =
                            state.textureId && available.has(state.textureId)
                                ? state.textureId
                                : undefined;
                        if (terrainId === state.terrainId && textureId === state.textureId) {
                            return state;
                        }
                        return { terrainId, textureId };
                    }),
                reset: () => set({ terrainId: undefined, textureId: undefined }),
            },
        }),
        {
            name: SELECTED_TERRAIN_STORAGE,
            partialize: (state) => ({
                terrainId: state.terrainId,
                textureId: state.textureId,
            }),
        },
    ),
);

export function useSelectedTerrainState<T>(selector: (state: SelectedTerrainState) => T): T {
    return useSelectedTerrainStore(selector);
}

export function useSelectedTerrainActions(): SelectedTerrainActions {
    return useSelectedTerrainStore((s) => s.actions);
}
