// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useSyncExternalStore } from "react";
import type { PluginRecord } from "@opencop/contributions/host";
import type { EnablementsState } from "./plugin-loader";
import { Registry } from "@opencop/contributions/host";

/**
 * Module-level singleton registry the host reads and the {@link PluginManager}
 * writes. Empty in a vanilla build, so host feature code reading it is
 * indistinguishable from there being no plugin system.
 */

/**
 * Reactive mirror of the manager's plugin records (admin view). Separate from the
 * manager instance so the admin surface stays reactive regardless of when/where
 * the manager is created — the manager `replace()`s it on every load/unload.
 */
export const pluginRecordRegistry = new Registry<PluginRecord>();

/** Reactive read of loaded plugin records + status for the admin view. */
export function usePluginRecords(): readonly PluginRecord[] {
    return useSyncExternalStore(
        pluginRecordRegistry.subscribe,
        pluginRecordRegistry.getAll,
        pluginRecordRegistry.getAll,
    );
}

/**
 * Reactive mirror of the enablements fetch's outcome, written by
 * {@link runPluginLoader}. A single-element registry rather than a second
 * primitive: the loader has exactly one current state, and reusing `Registry`
 * keeps the admin view on one subscription mechanism.
 *
 * Separate from {@link pluginRecordRegistry} because "no plugins loaded" and
 * "we could not find out which plugins to load" are different facts and the
 * record list cannot express the second — it is empty either way.
 */
export const enablementsStateRegistry = new Registry<EnablementsState>();

const PENDING: EnablementsState = { status: "pending" };

/** Reactive read of the last enablements outcome for the admin view. */
export function usePluginEnablementsState(): EnablementsState {
    const states = useSyncExternalStore(
        enablementsStateRegistry.subscribe,
        enablementsStateRegistry.getAll,
        enablementsStateRegistry.getAll,
    );
    return states[0] ?? PENDING;
}
