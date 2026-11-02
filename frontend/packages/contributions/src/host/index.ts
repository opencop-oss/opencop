// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * `@opencop/contributions/host` — what a host runs to hold contributions and load
 * plugins: a registry factory per contribution type, the per-owner contribution context,
 * the native installer, the plugin manager and its registrars, and the registry and journal
 * primitives beneath them. None of it holds state outside an instance and none of it
 * imports a plugin, so every host — the app, a test host — builds the same code on its own
 * services.
 */
export { Registry } from "./registry.ts";
export { createJournal } from "./journal.ts";
export type { Journal } from "./journal.ts";
export { PluginManager } from "./PluginManager.ts";
export type { PluginHostServices, PluginRecord, PluginStatus } from "./PluginManager.ts";
export type {
    RegisterDetailsHeaderFn,
    RegisterFilterFieldFn,
    RegisterElementMapperFn,
    RegisterToolFn,
    TranslationTarget,
    RegistrarHost,
} from "./contribution-registrars.ts";
export { ContributionRefusal, refuseProblems } from "./refusal.ts";
export { createFilterFieldRegistry } from "./filter-fields.ts";
export type {
    FilterFieldEntry,
    FilterFieldRegistry,
    FilterFieldRegistryOptions,
} from "./filter-fields.ts";
export { createDetailsRegistry } from "./details.ts";
export type {
    DetailsEntry,
    DetailsRegistry,
    DetailsSnapshot,
    ResolvedDetailsHeader,
} from "./details.ts";
export { createNativeInstaller } from "./native-install.ts";
export type { NativeInstaller, NativeSubject, NativeSubjectRecord } from "./native-install.ts";
export { createToolRegistry } from "./tools.ts";
export type { ToolRegistry, ToolRegistryOptions } from "./tools.ts";
export { createElementMapperRegistry } from "./element-mappers.ts";
export type { ElementMapperEntry, ElementMapperRegistry } from "./element-mappers.ts";
export { createContextMenuRegistry } from "./context-menu.ts";
export type { ContextMenuEntry, ContextMenuOpenedOn, ContextMenuRegistry } from "./context-menu.ts";
export { createPickResponseRegistry } from "./pick-responses.ts";
export type { PickResponseRegistry } from "./pick-responses.ts";
export { contributionContextOn } from "./contribution-context.ts";
export type { HostRegistries } from "./contribution-context.ts";
