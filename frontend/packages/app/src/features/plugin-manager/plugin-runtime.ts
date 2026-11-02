// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { PluginManager } from "@opencop/contributions/host";
import { runPluginLoader, type EnablementsState } from "./plugin-loader";
import { createPluginHostServices } from "./plugin-services";

/**
 * The one {@link PluginManager} instance, and the two operations over it.
 *
 * Module-scoped, not component-scoped: it survives StrictMode's double-invoke and
 * any remount, so the module-level registries it writes keep a single owner. It
 * lives in its own module rather than inside `PluginLoader` because the admin
 * surface needs {@link retryPluginLoad} — and a component file that also exports
 * a function loses Fast Refresh.
 *
 * Nothing here exposes the manager itself: the admin view reads the reactive
 * record and enablements-state registries, never the instance.
 */
let manager: PluginManager | null = null;

/**
 * Reconcile the loaded plugin set against the enablements document, creating the
 * manager on first call. Never throws: `runPluginLoader` reports a failed fetch
 * as state rather than a rejection.
 */
export async function reconcilePlugins(isCancelled?: () => boolean): Promise<EnablementsState> {
    manager ??= new PluginManager(createPluginHostServices());
    return runPluginLoader(manager, undefined, isCancelled);
}

/**
 * Run the loader again against the current enablements document — the operator's
 * recovery from a fetch failure, without remounting the app.
 *
 * Safe to offer because reconciliation is not a reset: a retry that succeeds
 * loads what is missing and leaves everything already running untouched, and one
 * that fails again just republishes the failure.
 */
export async function retryPluginLoad(): Promise<EnablementsState> {
    return reconcilePlugins();
}

/** Unload every loaded plugin. The loader's own teardown, not a flag change. */
export function unloadAllPlugins(): void {
    for (const record of manager?.list() ?? []) manager?.unload(record.manifest.id);
}
