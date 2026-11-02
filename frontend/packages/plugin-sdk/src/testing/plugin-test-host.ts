// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { createElement } from "react";
import { noop } from "lodash";
import {
    createNativeTestHost,
    createTestHostCore,
    type CapabilityOverrides,
    type TestHost,
    type TestHostOptions,
} from "./test-host.tsx";
import {
    NATIVE_OWNER_IDS,
    withContributedTranslations,
    type ManifestProblem,
    type Problem,
} from "@opencop/contributions/validation";
import { pluginOwner } from "@opencop/contributions/containment";
import {
    PluginManager,
    type PluginHostServices,
    type PluginRecord,
    type RegistrarHost,
} from "@opencop/contributions/host";
import type { ContributionContext, TranslationContribution } from "@opencop/contributions";
import {
    PluginContextProvider,
    type PluginContext,
    type PluginModule,
    type PluginStorage,
} from "@opencop/contributions/plugin";

/** Everything the host would refuse a plugin for: its manifest, and its contributions. */
export type PluginProblem = ManifestProblem | Problem;

export interface PluginCheckOptions {
    /**
     * The host's own bundles the plugin reads (shared strings), beside the ones it
     * contributes itself.
     */
    readonly translations?: readonly TranslationContribution[];
}

export interface PluginTestHostOptions extends PluginCheckOptions {
    readonly plugin: PluginModule;
    /** Capability values for every render, over the test values. */
    readonly capabilities?: CapabilityOverrides;
    /** What the plugin's storage holds when it installs. */
    readonly storage?: Readonly<Record<string, unknown>>;
}

/**
 * A test host with a plugin loaded by the host's own plugin manager. `problems` is
 * everything the host refused it for — so `expect(host.problems).toEqual([])` is the
 * plugin's whole self-check — and a refused plugin registers nothing, as on the host.
 */
export interface PluginTestHost extends Omit<TestHost, "problems"> {
    readonly problems: readonly PluginProblem[];
    /** The plugin's storage: in memory, starting from the options' `storage`. */
    readonly storage: PluginStorage;
    /** The manager's record of the load: its status and, when it failed, why. */
    readonly record: PluginRecord;
}

function memoryStorage(initial: Readonly<Record<string, unknown>> = {}): PluginStorage {
    const values = new Map<string, unknown>(Object.entries(initial));
    return {
        get: <T>(key: string) => values.get(key) as T | undefined,
        set: (key, value) => void values.set(key, value),
        remove: (key) => void values.delete(key),
    };
}

/** The sinks of the host services, onto `sinks`: what the app wires to its registries. */
function sinksOnto(sinks: ContributionContext): Omit<RegistrarHost, "i18n"> {
    return {
        registerTool: (_pluginId, tool) => sinks.registerTool(tool),
        registerElementMapper: (_pluginId, mapper) => sinks.registerElementMapper(mapper),
        registerDetailsHeader: (_pluginId, header) => sinks.registerDetailsHeader(header),
        registerFilterField: (_pluginId, field) => sinks.registerFilterField(field),
    };
}

/** A load the manager refused for no rule — an `install` that threw, a version mismatch. */
function refusedWithoutProblem(record: PluginRecord): boolean {
    return (
        record.status !== "active" &&
        !record.problems.some((problem) => problem.severity === "error")
    );
}

/**
 * Every problem the host would refuse `plugin` for, without rendering anything: the
 * plugin's own CI check, run by the host's plugin manager. A plugin with none is one
 * the host loads. Throws for a load refused for no rule (an `install` that threw), and
 * for an uninstall that threw — the plugin's `uninstall` or an undo.
 */
export function validatePlugin(
    plugin: PluginModule,
    { translations = [] }: PluginCheckOptions = {},
): PluginProblem[] {
    const sink = () => noop;
    const manager = new PluginManager({
        reservedOwnerIds: NATIVE_OWNER_IDS,
        translationExists: withContributedTranslations(
            { translationExists: () => false },
            translations,
        ).translationExists,
        createContext: () => ({ storage: memoryStorage() }),
        i18n: { addResourceBundle: noop, removeResourceBundle: noop },
        registerTool: sink,
        registerElementMapper: sink,
        registerDetailsHeader: sink,
        registerFilterField: sink,
    });
    const record = manager.load(plugin);
    const undone = manager.unload(plugin.manifest.id);
    if (refusedWithoutProblem(record)) throw new Error(`${plugin.manifest.id}: ${record.error}`);
    if (undone.length > 0)
        throw new AggregateError(undone, `${plugin.manifest.id}: uninstall threw`);
    return [...record.problems];
}

/**
 * Load `plugin` into a fresh test host through the host's own plugin manager: its
 * manifest and version checked, `install` recorded with an in-memory storage, every
 * contribution checked as a set, and — only if nothing was refused — registered by the
 * host's registrars into the test host's instances of the host's registries, bound to
 * the plugin's context and contained. Call it inside a test or a `beforeEach`.
 */
function createPluginTestHost(options: PluginTestHostOptions): PluginTestHost {
    const { plugin, capabilities, translations = [] } = options;
    const pluginId = plugin.manifest.id;
    const storage = memoryStorage(options.storage);
    const context: PluginContext = { storage };
    let record!: PluginRecord;

    const core = createTestHostCore({
        id: pluginId,
        ownerKind: "plugin",
        translations,
        capabilities,
        // Under the plugin's own context, as all of its UI renders in the host.
        wrap: (children) => createElement(PluginContextProvider, { value: context, children }),
        attach: ({ contextFor, i18n, fail }) => {
            const services: PluginHostServices = {
                reservedOwnerIds: NATIVE_OWNER_IDS,
                translationExists: (key) => i18n.exists(key),
                createContext: () => context,
                i18n,
                ...sinksOnto(contextFor(pluginOwner(pluginId))),
            };
            const manager = new PluginManager(services);
            record = manager.load(plugin);
            if (refusedWithoutProblem(record)) fail(`${pluginId}:load`, new Error(record.error));
            return { dispose: () => manager.unload(pluginId), problems: () => [] };
        },
    });

    return {
        get problems() {
            return [...record.problems, ...core.problems];
        },
        get record() {
            return record;
        },
        storage,
        contributions: core.contributions,
        get capabilities() {
            return core.capabilities;
        },
        get failures() {
            return core.failures;
        },
        takeFailures: core.takeFailures,
        i18n: core.i18n,
        setLocale: core.setLocale,
        update: core.update,
        render: core.render,
        renderHook: core.renderHook,
        uninstall: core.uninstall,
    };
}

/**
 * A fresh test host, the host's own code on test instances: `{ plugin }` loads a plugin
 * through the host's plugin manager; `{ id, install }` installs a native subject through
 * the host's native installer. Call it inside a test or a `beforeEach`.
 */
export function createTestHost(options: PluginTestHostOptions): PluginTestHost;
export function createTestHost(options: TestHostOptions): TestHost;
export function createTestHost(
    options: PluginTestHostOptions | TestHostOptions,
): PluginTestHost | TestHost {
    return "plugin" in options ? createPluginTestHost(options) : createNativeTestHost(options);
}
