// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { PluginHostServices } from "@opencop/contributions/host";
import { pluginRecordRegistry } from "./plugin-records";
import type { PluginContext, PluginStorage } from "@opencop/contributions/plugin";
import { registerTool } from "~/features/contributions/tool-registry";
import { pluginOwner } from "~/features/contributions/contribution-guard";
import { registerElementMapper } from "~/features/contributions/element-mapper-registry";
import { registerDetailsHeader } from "~/features/contributions/details-registry";
import { registerFilterField } from "~/features/contributions/filter-field-registry";
import { i18n } from "~/i18n/config";
import { NATIVE_SUBJECT_IDS } from "~/features/contributions/install-built-in-subjects";

/**
 * Build the host capabilities the {@link PluginManager} injects into plugins.
 * This lives in the app layer, which can reach the app-owned registries and the
 * host's i18n instance; the manager itself imports no host source.
 */
export function createPluginHostServices(): PluginHostServices {
    return {
        // From the composition root, the one file that names the native subjects;
        // a second list of their ids would drift from it.
        reservedOwnerIds: NATIVE_SUBJECT_IDS,
        translationExists: (key) => i18n.exists(key),
        // Mirrored for `/admin/plugins`; the manager itself keeps no global state.
        onRecordsChange: (records) => pluginRecordRegistry.replace(records),
        // Inject a plugin's tool into the app-owned tool registry. It is the SAME
        // `ToolDescriptor` a native feature registers — there is no narrow→rich
        // bridge — so the app only decorates the plugin's entry: it namespaces the
        // id (so two plugins, or a plugin and a native tool, can't collide),
        // resolves the label key, and groups it apart from native tools.
        // `content`/`editor` were already bound to the plugin context by the host
        // registrar. The disposer removes it on unload.
        registerTool: (_pluginId, tool) => registerTool(tool),
        // Inject a plugin's element mapper into the app-owned mapper registry,
        // owned by the plugin. The registry contains it and refuses a type another
        // owner draws, and the manager rolls the load back. The disposer removes it
        // on unload.
        registerElementMapper: (pluginId, mapper) =>
            registerElementMapper(pluginOwner(pluginId), mapper),
        // Inject a plugin's details-panel header presentation; the registry
        // validates it and refuses a kind another owner holds.
        registerDetailsHeader: (pluginId, contribution) =>
            registerDetailsHeader(pluginOwner(pluginId), contribution),
        // Inject a plugin's filter field; the registry validates it and refuses an id
        // another owner holds.
        registerFilterField: (pluginId, field) => registerFilterField(pluginOwner(pluginId), field),
        // Add the plugin's namespace to the host's i18next instance. A namespace,
        // not keys merged into `translation`, so a plugin cannot collide with host
        // keys. Consumers address them with the ordinary namespace-prefixed key
        // form (namespace, colon, key) — spelled out rather than shown as a call,
        // because the i18n extractor scrapes example calls out of comments.
        //
        // **Namespace ownership is not enforced.** Validation checks only
        // that `namespace` is a non-empty string, so two plugins may claim one
        // namespace; i18next then shallow-merges the second over the first, and
        // unloading either removes the whole namespace rather than one plugin's
        // keys. Not reachable with a single in-repo plugin; a declared-owner check
        // belongs with the translations validator before a second plugin ships.
        i18n,
        // The whole context for one plugin, assembled here. The manager passes it
        // through without naming a member, so a new capability is declared on
        // `PluginContext` and supplied here — nothing in the manager changes.
        createContext: (pluginId): PluginContext => ({
            storage: createStorage(pluginId),
        }),
    };
}

/**
 * Durable storage for one plugin, namespaced so two plugins cannot collide.
 *
 * Nothing here (or anywhere in the host) clears a plugin's keys on unload or
 * uninstall — that durability is part of the {@link PluginStorage} contract:
 * operator data must survive an entitlement change, a version bump, or a
 * reinstall.
 */
function createStorage(pluginId: string): PluginStorage {
    const prefix = `opencop.plugin.${pluginId}.`;
    return {
        get<T>(key: string): T | undefined {
            const raw = localStorage.getItem(prefix + key);
            if (raw === null) return undefined;
            // Contract is `T | undefined`, so a corrupt / schema-stale entry (e.g.
            // after a plugin version bump changed its shape) must not throw a
            // SyntaxError at the plugin author — degrade to undefined.
            try {
                return JSON.parse(raw) as T;
            } catch {
                return undefined;
            }
        },
        set<T>(key: string, value: T): void {
            localStorage.setItem(prefix + key, JSON.stringify(value));
        },
        remove(key: string): void {
            localStorage.removeItem(prefix + key);
        },
    };
}
