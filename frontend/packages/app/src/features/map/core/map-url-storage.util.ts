// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { PersistStorage, StorageValue } from "zustand/middleware";
import {
    MAP_URL_KEYS,
    decodeMapUrl,
    encodeMapUrl,
    type PersistedMapUrlState,
} from "./map-url-codec.util";

const MAP_PATH = "/map";

const getSearchParams = () => new URL(window.location.href).searchParams;

const writeSearchParams = (params: URLSearchParams) => {
    window.history.pushState({}, "", `${window.location.pathname}?${params.toString()}`);
};

export interface MapUrlStorage extends PersistStorage<Partial<PersistedMapUrlState>> {
    /** Remove window/document listeners and clear pending timers. Tests must call this in afterEach. */
    dispose(): void;
}

/**
 * `PersistStorage` adapter that serializes the map view+selection slice into
 * flat URL search params instead of a JSON blob. Writes are debounced and
 * batched into a single `pushState` per flush so rapid pans don't pile up
 * history entries. Foreign search params are preserved on read and write.
 *
 * Operates only on the `/map` pathname — calls on other routes are no-ops.
 *
 * **Debounce semantics:** the timer coalesces writes within a `timeout`-ms
 * window of *each other*. A single user gesture (e.g., a pan with inertia)
 * may still produce more than one `pushState` if its move events straddle
 * the window — see `e2e/map-url.spec.ts` for how the back-button test
 * accommodates this.
 *
 * **Version:** the version returned from `getItem` is hardcoded to whatever
 * is passed in. If `map.store.ts` ever bumps the persist version to trigger
 * a migration, pass the new version here so URL-loaded state isn't treated
 * as stale and run through `migrate` against already-current data.
 */
export function createMapUrlStorage(timeout: number = 250, version: number = 0): MapUrlStorage {
    let pending: Partial<PersistedMapUrlState> | null = null;
    let timer: ReturnType<typeof setTimeout> | undefined;

    const applyPending = () => {
        const next = pending;
        pending = null;
        if (!next) return;
        if (location.pathname !== MAP_PATH) return;

        const before = getSearchParams();
        const after = new URLSearchParams(before);
        const encoded = encodeMapUrl(next);

        for (const key of MAP_URL_KEYS) {
            const v = encoded[key];
            if (v === undefined) continue;
            if (v === null) after.delete(key);
            else after.set(key, v);
        }

        // Skip identical writes so rehydrate echoes don't push fake history entries.
        if (after.toString() === before.toString()) return;
        writeSearchParams(after);
    };

    const flush = () => {
        if (timer !== undefined) {
            clearTimeout(timer);
            timer = undefined;
        }
        applyPending();
    };

    const onPagehide = flush;
    const onVisibilityChange = () => {
        if (document.visibilityState === "hidden") flush();
    };

    window.addEventListener("pagehide", onPagehide);
    document.addEventListener("visibilitychange", onVisibilityChange);

    return {
        getItem: () => {
            const state = decodeMapUrl(getSearchParams());
            return { state, version } satisfies StorageValue<Partial<PersistedMapUrlState>>;
        },
        setItem: (_name, value) => {
            pending = { ...pending, ...value.state };
            if (timer !== undefined) clearTimeout(timer);
            timer = setTimeout(() => {
                timer = undefined;
                applyPending();
            }, timeout);
        },
        removeItem: () => {
            pending = null;
            if (timer !== undefined) {
                clearTimeout(timer);
                timer = undefined;
            }
            if (location.pathname !== MAP_PATH) return;
            const params = getSearchParams();
            let changed = false;
            for (const key of MAP_URL_KEYS) {
                if (params.has(key)) {
                    params.delete(key);
                    changed = true;
                }
            }
            if (changed) writeSearchParams(params);
        },
        dispose: () => {
            window.removeEventListener("pagehide", onPagehide);
            document.removeEventListener("visibilitychange", onVisibilityChange);
            if (timer !== undefined) {
                clearTimeout(timer);
                timer = undefined;
            }
            pending = null;
        },
    };
}
