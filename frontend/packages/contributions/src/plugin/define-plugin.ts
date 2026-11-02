// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { PluginInstallContext } from "./context.ts";
import type { PluginManifest } from "./manifest.ts";

/**
 * The SDK contract version. A plugin's `manifest.apiVersion` is validated against
 * this at load time; a mismatch marks the plugin `version-mismatch` and registers
 * nothing.
 *
 * While every plugin is statically imported and compiled with the host, TypeScript
 * rejects breaking source-contract changes before runtime, so this value remains
 * stable. Once plugins are independently built and dynamically loaded, increment it
 * for every breaking SDK contract change so the runtime gate rejects incompatible
 * bundles.
 */
export const SDK_API_VERSION = 1;

/** The default export shape a plugin module produces via {@link definePlugin}. */
export interface PluginModule {
    manifest: PluginManifest;
    /**
     * Register everything this plugin contributes, and do any setup it needs.
     * Called once per load with a {@link PluginInstallContext} — the plugin's
     * scoped capabilities (element reads, durable storage) plus the register
     * methods. Because registration is a call rather than static data, a plugin
     * can decide what to contribute from what it finds at install time.
     *
     * Registrations are buffered and validated as a set: if this throws or
     * contributes something malformed, nothing is registered.
     */
    install(ctx: PluginInstallContext): void;
    /**
     * Called on unload to release resources the plugin itself allocated. The host
     * unregisters the plugin's contributions on its own, so this is only for
     * things the plugin owns (timers, subscriptions, caches).
     */
    uninstall?(): void;
}

/**
 * Identity envelope authors wrap their plugin in. Checks the shape at the
 * definition site and returns a stable, recognizable default-export object the
 * host manager validates and drives.
 */
export function definePlugin(plugin: PluginModule): PluginModule {
    return plugin;
}
