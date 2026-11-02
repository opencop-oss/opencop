// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { beforeEach, describe, expect, it } from "vitest";
import type { PluginStorage } from "@opencop/contributions/plugin";
import { bindPluginStorage, unbindPluginStorage } from "./plugin-storage";
import { migrateLegacyStorage } from "./history-migration";
import { useSymbolHistoryStore } from "./ui/symbol-history.store";
import {
    LEGACY_KEY_SYMBOL_HISTORY,
    LEGACY_STORAGE_KEY_FAVORITES,
    SYMBOL_HISTORY_STORAGE_KEY,
    type HistoryEntry,
} from "./ui/history.util";
import { AUTO_HIDE_STORAGE_KEY, LEGACY_KEY_AUTO_HIDE } from "./ui/ToolEditorShell";
import type { HistoryState } from "./ui/history.store";

const entry = (sidc: string): HistoryEntry => ({ id: sidc, sidc, modifiers: {} });

/** The envelope shape zustand `persist` wrote to the pre-plugin localStorage keys. */
const envelope = (state: Partial<HistoryState>) => JSON.stringify({ state, version: 0 });

/**
 * In-memory {@link PluginStorage} plus the install sequence `plugin.tsx` runs:
 * bind → rehydrate both stores → migrate. Returns the backing map so tests can
 * seed the "new location" and observe writes.
 */
function install(seed: Record<string, unknown> = {}) {
    const backing = new Map<string, unknown>(Object.entries(seed));
    const storage: PluginStorage = {
        get: <T>(key: string) => backing.get(key) as T | undefined,
        set: (key, value) => void backing.set(key, value),
        remove: (key) => void backing.delete(key),
    };
    bindPluginStorage(storage);
    void useSymbolHistoryStore.persist.rehydrate();
    migrateLegacyStorage(storage);
    return backing;
}

beforeEach(() => {
    localStorage.clear();
    unbindPluginStorage();
    useSymbolHistoryStore.setState({ favorites: [], recents: [] });
});

describe("plugin storage hydration", () => {
    it("hydrates the stores from the plugin storage on install", () => {
        install({
            [SYMBOL_HISTORY_STORAGE_KEY]: {
                state: { favorites: [entry("A")], recents: [] },
                version: 0,
            },
        });

        expect(useSymbolHistoryStore.getState().favorites).toHaveLength(1);
        expect(useSymbolHistoryStore.getState().favorites[0].sidc).toBe("A");
    });

    it("writes through to the plugin storage after install", () => {
        const backing = install();

        useSymbolHistoryStore.getState().addRecent("B", {});

        const stored = backing.get(SYMBOL_HISTORY_STORAGE_KEY) as {
            state: HistoryState;
        };
        expect(stored.state.recents[0].sidc).toBe("B");
    });

    it("drops writes and reads empty while unbound (tests, pre-install)", () => {
        useSymbolHistoryStore.getState().addRecent("C", {});
        // No throw, and a later install with empty storage sees no phantom data.
        useSymbolHistoryStore.setState({ favorites: [], recents: [] });
        install();
        expect(useSymbolHistoryStore.getState().recents).toHaveLength(0);
    });
});

describe("legacy history migration", () => {
    it("adopts the pre-plugin envelope into an empty store and clears the old key", () => {
        localStorage.setItem(LEGACY_KEY_SYMBOL_HISTORY, envelope({ favorites: [entry("A")] }));

        const backing = install();

        expect(useSymbolHistoryStore.getState().favorites[0].sidc).toBe("A");
        // The adoption persisted into the NEW location…
        expect(backing.has(SYMBOL_HISTORY_STORAGE_KEY)).toBe(true);
        // …and the old locations are gone.
        expect(localStorage.getItem(LEGACY_KEY_SYMBOL_HISTORY)).toBeNull();
    });

    it("never overwrites data already in the new location", () => {
        localStorage.setItem(LEGACY_KEY_SYMBOL_HISTORY, envelope({ favorites: [entry("OLD")] }));

        install({
            [SYMBOL_HISTORY_STORAGE_KEY]: {
                state: { favorites: [entry("NEW")], recents: [] },
                version: 0,
            },
        });

        expect(useSymbolHistoryStore.getState().favorites).toHaveLength(1);
        expect(useSymbolHistoryStore.getState().favorites[0].sidc).toBe("NEW");
    });

    it("falls back to the oldest favorites/recents pair for the symbol store", () => {
        localStorage.setItem(LEGACY_STORAGE_KEY_FAVORITES, JSON.stringify([entry("PAIR")]));

        install();

        expect(useSymbolHistoryStore.getState().favorites[0].sidc).toBe("PAIR");
        expect(localStorage.getItem(LEGACY_STORAGE_KEY_FAVORITES)).toBeNull();
    });

    it("adopts nothing when no legacy data exists", () => {
        install();

        expect(useSymbolHistoryStore.getState().favorites).toHaveLength(0);
    });

    it("survives a malformed legacy value", () => {
        localStorage.setItem(LEGACY_KEY_SYMBOL_HISTORY, "{not json");

        expect(() => install()).not.toThrow();
        expect(useSymbolHistoryStore.getState().favorites).toHaveLength(0);
    });
});

describe("auto-hide migration", () => {
    it("adopts the raw localStorage boolean and clears the old key", () => {
        localStorage.setItem(LEGACY_KEY_AUTO_HIDE, "true");

        const backing = install();

        expect(backing.get(AUTO_HIDE_STORAGE_KEY)).toBe(true);
        expect(localStorage.getItem(LEGACY_KEY_AUTO_HIDE)).toBeNull();
    });

    it("leaves an existing plugin-storage value alone", () => {
        localStorage.setItem(LEGACY_KEY_AUTO_HIDE, "true");

        const backing = install({ [AUTO_HIDE_STORAGE_KEY]: false });

        expect(backing.get(AUTO_HIDE_STORAGE_KEY)).toBe(false);
    });
});
