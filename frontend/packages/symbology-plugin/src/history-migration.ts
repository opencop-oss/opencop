// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { PluginStorage } from "@opencop/contributions/plugin";
import type { HistoryState } from "./ui/history.store";
import {
    LEGACY_KEY_SYMBOL_HISTORY,
    LEGACY_STORAGE_KEY_FAVORITES,
    LEGACY_STORAGE_KEY_RECENTS,
    type HistoryEntry,
} from "./ui/history.util";
import { useSymbolHistoryStore } from "./ui/symbol-history.store";
import { AUTO_HIDE_STORAGE_KEY, LEGACY_KEY_AUTO_HIDE } from "./ui/ToolEditorShell";

/**
 * One-release migration from the pre-plugin-storage locations.
 *
 * History and the editor's auto-hide preference used to live in raw
 * `localStorage` under unprefixed keys; they now live in the plugin's
 * `PluginStorage`. This module adopts existing operator data into the new
 * location the first time the plugin installs with it empty, then removes the
 * old keys — the same adopt-then-clear shape the previous in-store legacy
 * migration used.
 *
 * **This module reads raw `localStorage` deliberately** — that is where the old
 * data lives — and is the only sanctioned raw-localStorage touch in the plugin.
 *
 * **Back-out plan:** once the migration window closes, delete this module, its
 * call in `plugin.tsx`, and the `LEGACY_*` key constants it reads. Nothing else
 * references any of them.
 */
export function migrateLegacyStorage(storage: PluginStorage): void {
    migrateHistory(
        useSymbolHistoryStore,
        [
            () => readPersistEnvelope(LEGACY_KEY_SYMBOL_HISTORY),
            // Older still: the pre-envelope favorites/recents pair the previous
            // in-store migration adopted from.
            readLegacyPair,
        ],
        [LEGACY_KEY_SYMBOL_HISTORY, LEGACY_STORAGE_KEY_FAVORITES, LEGACY_STORAGE_KEY_RECENTS],
    );
    migrateAutoHide(storage);
}

interface HistoryStoreLike {
    getState(): HistoryState;
    setState(state: Partial<HistoryState>): void;
}

/**
 * Adopt the first legacy source that holds data — but only into an empty store,
 * so data already in the new location always wins. `setState` goes through the
 * persist middleware, which writes the adopted state to the new storage. Only
 * *this store's* legacy keys are cleared, and only after an adoption (an
 * already-migrated install cleared them last time; the sibling store's keys are
 * its own migration's to read and clear).
 */
function migrateHistory(
    store: HistoryStoreLike,
    sources: Array<() => HistoryState | null>,
    clearKeys: readonly string[],
): void {
    const { favorites, recents } = store.getState();
    if (favorites.length > 0 || recents.length > 0) return;

    for (const read of sources) {
        const legacy = read();
        if (!legacy) continue;
        store.setState(legacy);
        for (const key of clearKeys) removeRaw(key);
        return;
    }
}

function migrateAutoHide(storage: PluginStorage): void {
    if (storage.get<boolean>(AUTO_HIDE_STORAGE_KEY) !== undefined) return;
    const raw = readRaw(LEGACY_KEY_AUTO_HIDE);
    if (raw === null) return;
    storage.set(AUTO_HIDE_STORAGE_KEY, raw === "true");
    removeRaw(LEGACY_KEY_AUTO_HIDE);
}

/** The zustand `persist` envelope the previous stores wrote: `{ state, version }`. */
function readPersistEnvelope(key: string): HistoryState | null {
    const envelope = readJson<{ state?: Partial<HistoryState> }>(key);
    return toHistoryState(envelope?.state?.favorites, envelope?.state?.recents);
}

function readLegacyPair(): HistoryState | null {
    return toHistoryState(
        readJson<HistoryEntry[]>(LEGACY_STORAGE_KEY_FAVORITES) ?? undefined,
        readJson<HistoryEntry[]>(LEGACY_STORAGE_KEY_RECENTS) ?? undefined,
    );
}

function toHistoryState(
    favorites: HistoryEntry[] | undefined,
    recents: HistoryEntry[] | undefined,
): HistoryState | null {
    const safeFavorites = Array.isArray(favorites) ? favorites : [];
    const safeRecents = Array.isArray(recents) ? recents : [];
    if (safeFavorites.length === 0 && safeRecents.length === 0) return null;
    return { favorites: safeFavorites, recents: safeRecents };
}

function readJson<T>(key: string): T | null {
    const raw = readRaw(key);
    if (raw === null) return null;
    try {
        return JSON.parse(raw) as T;
    } catch {
        return null;
    }
}

function readRaw(key: string): string | null {
    try {
        return localStorage.getItem(key);
    } catch {
        return null;
    }
}

function removeRaw(key: string): void {
    try {
        localStorage.removeItem(key);
    } catch {
        // localStorage may be unavailable; the adoption itself already succeeded.
    }
}
