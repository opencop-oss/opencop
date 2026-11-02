// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { PluginStorage } from "@opencop/contributions/plugin";
import type { PersistStorage, StorageValue } from "zustand/middleware";

let bound: PluginStorage | null = null;

/**
 * Bind the host-provided storage capability. Called once from the plugin's
 * `install(ctx)` — the only place a `PluginContext` exists at module scope's
 * lifetime. Everything in this plugin that persists goes through this binding,
 * so the plugin has exactly one storage idiom: host-namespaced, durable across
 * uninstall (a {@link PluginStorage} contract guarantee).
 */
export function bindPluginStorage(storage: PluginStorage): void {
    bound = storage;
}

/** The bound storage, or null before install (tests, pre-install module init). */
export function getPluginStorage(): PluginStorage | null {
    return bound;
}

/** Test seam: return to the pre-install state. */
export function unbindPluginStorage(): void {
    bound = null;
}

/**
 * A zustand `PersistStorage` over the host-provided storage.
 *
 * The persisted stores are module-level, created at import time — before any
 * `PluginContext` exists — so they declare `skipHydration: true` and `install(ctx)`
 * hydrates them explicitly (`store.persist.rehydrate()`) right after
 * {@link bindPluginStorage}. Unbound (tests, a store touched before install) it
 * reads empty and drops writes rather than throwing.
 *
 * No `createJSONStorage` wrapper: {@link PluginStorage} already speaks JSON values,
 * so this stores zustand's `StorageValue` envelope directly instead of
 * double-encoding it as a string.
 */
export function createPluginPersistStorage<S>(): PersistStorage<S> {
    return {
        getItem: (name) => bound?.get<StorageValue<S>>(name) ?? null,
        setItem: (name, value) => bound?.set(name, value),
        removeItem: (name) => bound?.remove(name),
    };
}
