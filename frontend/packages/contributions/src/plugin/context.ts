// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { PluginContributionContext } from "../contribution-context.ts";

/**
 * Durable key/value storage scoped to a single plugin. The host owns persistence;
 * the plugin owns the schema. Keys are namespaced by the host under the owning
 * plugin's id, so two plugins cannot collide on the same key.
 *
 * **Durable across the plugin lifecycle by design.** The host never clears a
 * plugin's storage on unload or uninstall — operator data (favorites, preferences)
 * must survive an entitlement change, a version bump, or a reinstall. A plugin
 * that wants to discard its own data does so explicitly with {@link remove}.
 */
export interface PluginStorage {
    get<T>(key: string): T | undefined;
    set<T>(key: string, value: T): void;
    remove(key: string): void;
}

/**
 * Handle passed to `install(ctx)` and made available to plugin UI via
 * {@link usePluginContext}. Grows as contracts are added; today it exposes durable
 * storage. A tool editor's session is not here: the host opens it around the
 * editor, read with `useToolSession` from `@opencop/tools`. There is deliberately
 * no element read view: the snapshot-only one that existed was consumed by
 * nothing and could not notify a plugin of changes — element access returns as a
 * *reactive* contract, designed against its first real consumer.
 */
export interface PluginContext {
    readonly storage: PluginStorage;
}

/**
 * The context passed to a plugin's `install(ctx)`.
 *
 * It is a {@link PluginContext} — so a plugin can read its own storage while
 * deciding what to contribute — plus the register methods that *are* the
 * contribution API, declared in `@opencop/contributions` beside their native
 * twins. Registering is a thin call taking one contract-shaped object; the host
 * never hands a plugin a prop bag or a large host reference (no `MapManager`, no
 * stores), and registration is deliberately not the whole protocol — capabilities
 * arrive as contracts on this context.
 */
export interface PluginInstallContext extends PluginContext, PluginContributionContext {}
