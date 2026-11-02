// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * `@opencop/contributions/plugin` — the plugin protocol: how a plugin declares itself
 * ({@link definePlugin}, `PluginManifest`, `SDK_API_VERSION`), the context the host hands
 * it (`PluginContext`), and the React binding its UI reads that context through. The host
 * and plugins import the same declarations; the manifest's rules are in `/validation`.
 */
export { SDK_API_VERSION, definePlugin } from "./define-plugin.ts";
export type { PluginModule } from "./define-plugin.ts";
export type { PluginManifest } from "./manifest.ts";
export type { PluginContext, PluginInstallContext, PluginStorage } from "./context.ts";
export {
    PluginContextProvider,
    bindToPluginContext,
    usePluginContext,
} from "./PluginRuntimeContext.tsx";
