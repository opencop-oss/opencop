// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import Logger from "js-logger";
import { bindToPluginContext, type PluginContext } from "../plugin/index.ts";
import type { DetailsHeaderContribution, ElementMapper } from "../index.ts";
import type { ContributionKind, Contributions } from "../validation/index.ts";
import type { ContributedFilterField } from "@opencop/ui";
import { createJournal } from "./journal.ts";
import { hostedPluginTool } from "../validation/index.ts";
import type { ToolDescriptor } from "@opencop/tools";

/**
 * App-supplied sink for injecting a plugin's toolbar tool into the host's
 * (app-owned) tool registry — the "registry-in-app, loader-injects" model every
 * contribution kind follows: the manager keeps no contribution registry of its own,
 * so a plugin tool becomes the same kind of entry as a native tool. The
 * manager binds the plugin context into the tool's components before calling this
 * and keeps the returned disposer for rollback / unload.
 */
export type RegisterToolFn = (pluginId: string, tool: ToolDescriptor) => () => void;

/**
 * The i18n instance a plugin's namespaces are added to.
 * The translation registrar adds and removes them itself, so every host adds them the
 * same way.
 */
export interface TranslationTarget {
    addResourceBundle(
        language: string,
        namespace: string,
        resources: object,
        deep: boolean,
        overwrite: boolean,
    ): unknown;
    removeResourceBundle(language: string, namespace: string): unknown;
}

/**
 * App-supplied sink for adding a plugin's details-panel header presentation to
 * the host's registry, returning a disposer that removes exactly it. The app
 * registry validates it and refuses a kind another owner holds, and the manager
 * rolls the load back.
 */
export type RegisterDetailsHeaderFn = (
    pluginId: string,
    contribution: DetailsHeaderContribution,
) => () => void;

/**
 * App-supplied sink for adding a plugin's filter field to the host's registry,
 * returning a disposer that removes exactly it. The app registry validates the field —
 * its scoped id, label keys and path — and refuses an id another owner holds, and the
 * manager rolls the load back.
 */
export type RegisterFilterFieldFn = (pluginId: string, field: ContributedFilterField) => () => void;

/**
 * App-supplied sink for adding a plugin's element mapper to the host's registry,
 * returning a disposer that removes exactly it. The app registry contains the
 * mapper and refuses a type another owner draws, and the manager rolls the load
 * back.
 */
export type RegisterElementMapperFn = (pluginId: string, mapper: ElementMapper) => () => void;

/** Host capabilities a registrar may need. Supplied by the app via the manager. */
export interface RegistrarHost {
    readonly registerTool: RegisterToolFn;
    readonly i18n: TranslationTarget;
    readonly registerElementMapper: RegisterElementMapperFn;
    readonly registerDetailsHeader: RegisterDetailsHeaderFn;
    readonly registerFilterField: RegisterFilterFieldFn;
}

/**
 * A per-kind contribution registrar. The {@link PluginManager} owns the plugin
 * *lifecycle* (load / validate-atomically / register / initialize / dispose /
 * rollback) but delegates the *per-contribution-kind* wiring here — so it never
 * grows a `for (tools) … for (renderers) … for (nth) …` body, and never learns
 * UI-framework details (e.g. binding a tool editor's React context). Adding a
 * contribution kind = adding a registrar to {@link contributionRegistrars}, with
 * no change to the manager.
 *
 * Each registrar reads only its own slice of `contributes`, keeping the manager
 * entirely kind-agnostic. The rules a contribution must meet are not here: they are
 * the shared validators in `@opencop/contributions/validation`, which the manager runs
 * for every kind before any registrar runs, so a load stays all-or-nothing.
 * `register` performs the registration and returns undo thunks the manager collects
 * for rollback / unload; a `register` that throws has already undone its own earlier
 * registrations (see {@link registerEach}).
 */
export interface ContributionRegistrar {
    /** The contribution kind this registrar owns. */
    readonly kind: ContributionKind;
    register(
        pluginId: string,
        contributes: Contributions,
        context: PluginContext,
        host: RegistrarHost,
    ): Array<() => void>;
}

/**
 * Register each item through `register`, returning every disposer. If a
 * registration throws — a sink refusing an identity another owner holds, which
 * validation cannot see — the ones this call already made are undone, newest
 * first, before the error propagates. The manager only receives the disposers of
 * a registrar that returned, so without this a failed load would strand the
 * earlier items of the same kind in the live registries.
 */
function registerEach<T>(
    items: readonly T[] | undefined,
    register: (item: T) => () => void,
): Array<() => void> {
    const undos: Array<() => void> = [];
    try {
        for (const item of items ?? []) undos.push(register(item));
    } catch (error) {
        // Contained, so an undo that throws neither strands the others nor
        // replaces the refusal the caller reports.
        const journal = createJournal();
        for (const undo of undos) journal.add(undo);
        for (const undoError of journal.rollback()) {
            Logger.get("plugin-host").error(
                "undo threw while rolling back a refused registration:",
                undoError,
            );
        }
        throw error;
    }
    return undos;
}

const toolRegistrar: ContributionRegistrar = {
    kind: "tools",
    // Tools are injected into the host's tool registry via `host.registerTool`,
    // which returns the disposer.
    // Plugin UI (`content`/`editor`) is bound to the plugin context here, once, so
    // the host renders a stable component; everything else passes through as-is.
    register(pluginId, contributes, context, host) {
        return registerEach(contributes.tools, (tool) => {
            const bound: ToolDescriptor = {
                ...tool,
                // Icon is a component (like content/editor); bind it to the plugin
                // context so a plugin icon can use theme / usePluginContext.
                icon: bindToPluginContext(
                    tool.icon,
                    context,
                    `Plugin(${pluginId}):${tool.id}:icon`,
                ),
                ...(tool.content
                    ? {
                          content: bindToPluginContext(
                              tool.content,
                              context,
                              `Plugin(${pluginId}):${tool.id}`,
                          ),
                      }
                    : null),
                ...(tool.editor
                    ? {
                          editor: bindToPluginContext(
                              tool.editor,
                              context,
                              `Plugin(${pluginId}):${tool.id}:editor`,
                          ),
                      }
                    : null),
            };
            return host.registerTool(pluginId, hostedPluginTool(pluginId, bound));
        });
    },
};

const elementMapperRegistrar: ContributionRegistrar = {
    kind: "elementMappers",
    // A mapper is data, not plugin UI — it registers as-is. Injected into the
    // app-owned mapper registry via `host.registerElementMapper`, which contains it.
    register(pluginId, contributes, _context, host) {
        return registerEach(contributes.elementMappers, (mapper) =>
            host.registerElementMapper(pluginId, mapper),
        );
    },
};

const translationRegistrar: ContributionRegistrar = {
    kind: "translations",
    // A namespace, not keys merged into `translation`, so a plugin cannot collide with
    // host keys. Namespace ownership is not enforced yet: a second owner of a namespace
    // is shallow-merged over the first, and unloading either removes the whole namespace.
    register(_pluginId, contributes, _context, host) {
        return registerEach(contributes.translations, ({ namespace, resources }) => {
            const languages = Object.keys(resources);
            // `deep: false` — a flat per-language bundle needs no deep merge.
            for (const language of languages)
                host.i18n.addResourceBundle(language, namespace, resources[language], false, false);
            return () => {
                for (const language of languages)
                    host.i18n.removeResourceBundle(language, namespace);
            };
        });
    },
};

const detailsHeaderRegistrar: ContributionRegistrar = {
    kind: "detailsHeaders",
    // The app registry the sink writes to checks the header the way it checks a
    // native one, so a refusal there rolls the load back.
    register(pluginId, contributes, context, host) {
        return registerEach(contributes.detailsHeaders, (header) =>
            host.registerDetailsHeader(pluginId, {
                ...header,
                ...(header.typeIcon
                    ? {
                          typeIcon: bindToPluginContext(
                              header.typeIcon,
                              context,
                              `${pluginId}:detailsHeader.typeIcon`,
                          ),
                      }
                    : null),
            }),
        );
    },
};

const filterFieldRegistrar: ContributionRegistrar = {
    kind: "filterFields",
    // What only the app can see — a path its evaluators accept, keys that resolve, an
    // id another owner holds — is checked by the app registry the sink writes to, the
    // same check a native field gets.
    register(pluginId, contributes, context, host) {
        return registerEach(contributes.filterFields, (field) =>
            host.registerFilterField(pluginId, {
                ...field,
                ...(field.ValueEditor
                    ? {
                          ValueEditor: bindToPluginContext(
                              field.ValueEditor,
                              context,
                              `${pluginId}:filterField.ValueEditor`,
                          ),
                      }
                    : null),
            }),
        );
    },
};

/**
 * The closed set of contribution registrars, in registration order. Extend this
 * — add a registrar, and its validator in `@opencop/contributions/validation` — to
 * support a new contribution kind; the manager needs no change.
 *
 * Keyed by kind and `satisfies`-checked against `Contributions` so a kind added
 * there without a registrar here is a **compile** error. Without that check the
 * manager, which iterates only this list, would silently never register the new
 * kind — a contribution that vanishes with no error at all.
 */
const registrarsByKind = {
    tools: toolRegistrar,
    translations: translationRegistrar,
    // After translations: a mapper's text-setting label key must resolve.
    elementMappers: elementMapperRegistrar,
    detailsHeaders: detailsHeaderRegistrar,
    // After translations: a field's label keys must resolve when it registers.
    filterFields: filterFieldRegistrar,
} satisfies Record<ContributionKind, ContributionRegistrar>;

export const contributionRegistrars: readonly ContributionRegistrar[] =
    Object.values(registrarsByKind);
