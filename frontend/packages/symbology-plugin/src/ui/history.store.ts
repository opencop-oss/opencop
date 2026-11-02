// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { create } from "zustand";
import { persist } from "zustand/middleware";
import type { StateCreator } from "zustand";
import type { UseBoundStore, StoreApi } from "zustand";
import { createHistoryEntry, type HistoryEntry } from "./history.util";
import { createPluginPersistStorage } from "../plugin-storage";
import type { SIDC } from "@opencop/symbology";

export interface HistoryState {
    favorites: HistoryEntry[];
    recents: HistoryEntry[];
}

export interface HistoryActions {
    addFavorite: (sidc: SIDC, modifiers: Record<string, string>, label?: string) => void;
    removeFavorite: (id: string) => void;
    removeFavoriteByCode: (sidc: string) => void;
    addRecent: (sidc: SIDC | string, modifiers: Record<string, string>, label?: string) => void;
    promoteRecentToFavorite: (id: string, label?: string) => void;
    updateRecentLabel: (sidc: string, label?: string) => void;
}

export type HistoryStore = HistoryState & HistoryActions;

const MAX_RECENTS = 100;

export const buildActions: StateCreator<HistoryStore, [], [], HistoryActions> = (set, get) => ({
    addFavorite: (sidc, modifiers, label) => {
        const sidcString = sidc.toString();
        const favorites = get().favorites;
        if (favorites.some((f) => f.sidc === sidcString)) return;
        const config = createHistoryEntry(sidc, modifiers, label);
        set({ favorites: [...favorites, config] });
    },

    removeFavorite: (id) => {
        set({ favorites: get().favorites.filter((f) => f.id !== id) });
    },

    removeFavoriteByCode: (sidcString) => {
        set({ favorites: get().favorites.filter((f) => f.sidc !== sidcString) });
    },

    addRecent: (sidc, modifiers, label) => {
        const sidcString = sidc.toString();
        const favorites = get().favorites;

        const existing = get().recents.find((r) => r.sidc === sidcString);
        const favorite = favorites.find((f) => f.sidc === sidcString);
        const preservedLabel = label || existing?.label || favorite?.label;

        const filtered = get().recents.filter((r) => r.sidc !== sidcString);
        const config = createHistoryEntry(sidc, modifiers, preservedLabel);
        set({ recents: [config, ...filtered].slice(0, MAX_RECENTS) });
    },

    promoteRecentToFavorite: (id, label) => {
        const recent = get().recents.find((r) => r.id === id);
        if (!recent) return;
        const favorites = get().favorites;
        if (favorites.some((f) => f.sidc === recent.sidc)) return;
        const config = { ...recent, label: label || recent.label };
        set({ favorites: [...favorites, config] });
    },

    updateRecentLabel: (sidcString, label) => {
        const recents = get().recents;
        const index = recents.findIndex((r) => r.sidc === sidcString);
        if (index !== -1) {
            const updated = [...recents];
            updated[index] = { ...updated[index], label };
            set({ recents: updated });
        }
    },
});

/**
 * Shared persist options for the two history stores.
 *
 * Persistence goes through the plugin's host-provided storage (see
 * `plugin-storage.ts`), which does not exist yet when these module-level stores
 * are created — so hydration is skipped here and `install(ctx)` triggers it
 * explicitly with `store.persist.rehydrate()` after binding the storage.
 */
export const historyPersistOptions = (name: string) => ({
    name,
    storage: createPluginPersistStorage<HistoryState>(),
    skipHydration: true,
    partialize: ({ favorites, recents }: HistoryStore) => ({ favorites, recents }),
});

export function createHistoryStore(name: string) {
    return create<HistoryStore>()(
        persist(
            (...a) => ({ favorites: [], recents: [], ...buildActions(...a) }),
            historyPersistOptions(name),
        ),
    );
}

export function useHistory(useStore: UseBoundStore<StoreApi<HistoryStore>>) {
    return useStore((s) => s);
}
