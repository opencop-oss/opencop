// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { createElement } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
    SDK_API_VERSION,
    definePlugin,
    type PluginInstallContext,
    type PluginModule,
} from "../plugin/index.ts";
import type { PluginScopedId } from "../index.ts";
import type { ContributedFilterField } from "@opencop/ui";
import type { ElementMapper } from "@opencop/contributions";
import { createSequentialGrammar, type DrawKit } from "@opencop/tools";
import { PluginManager, type PluginHostServices } from "./PluginManager.ts";
import { noop } from "lodash";

// Stands in for the app-owned tool registry: tools are injected via the host
// `registerTool` service, so the test observes injection through this sink and
// rollback through the disposers.
let injectedTools: Array<{ pluginId: string; toolId: string }> = [];
let injectedNamespaces: string[] = [];
// Element mappers are injected the same way — the app owns their registry too —
// so the test observes them through a sink of the same shape the app records.
let injectedMappers: Array<{ pluginId: string; mapper: ElementMapper }> = [];
// Filter fields too: the app's filter-field registry refuses an id another owner holds.
let injectedFilterFields: Array<{ pluginId: string; fieldId: string }> = [];
let takenFilterFieldIds: string[] = [];
let injectedDetailsHeaders: Array<{ pluginId: string; contribution: unknown }> = [];
/** A mapper type whose disposer removes its entry and then throws. */
const THROWS_ON_UNREGISTER = "throws-on-unregister";

/** An owner id the app's native plugins hold, so no plugin may load under it. */
const NATIVE_OWNER_ID = "opencop.native-fixture";

const services: PluginHostServices = {
    reservedOwnerIds: [NATIVE_OWNER_ID],
    // Every key resolves: these tests drive the manager, not the host's translations.
    translationExists: () => true,
    createContext: () => ({
        storage: { get: () => undefined, set: () => undefined, remove: () => undefined },
    }),
    registerTool: (pluginId, tool) => {
        const entry = { pluginId, toolId: tool.id };
        injectedTools.push(entry);
        return () => {
            injectedTools = injectedTools.filter((e) => e !== entry);
        };
    },
    registerFilterField: (pluginId, field) => {
        if (takenFilterFieldIds.includes(field.id)) throw new Error("filter field id taken");
        const entry = { pluginId, fieldId: field.id };
        injectedFilterFields.push(entry);
        return () => {
            injectedFilterFields = injectedFilterFields.filter((e) => e !== entry);
        };
    },
    i18n: {
        addResourceBundle: (language, namespace) =>
            injectedNamespaces.push(`${language}/${namespace}`),
        removeResourceBundle: (language, namespace) => {
            injectedNamespaces = injectedNamespaces.filter((e) => e !== `${language}/${namespace}`);
        },
    },
    registerElementMapper: (pluginId, mapper) => {
        const entry = { pluginId, mapper };
        injectedMappers.push(entry);
        return () => {
            injectedMappers = injectedMappers.filter((e) => e !== entry);
            if (mapper.type === THROWS_ON_UNREGISTER) throw new Error("unregister threw");
        };
    },
    registerDetailsHeader: (pluginId, contribution) => {
        const entry = { pluginId, contribution };
        injectedDetailsHeaders.push(entry);
        return () => {
            injectedDetailsHeaders = injectedDetailsHeaders.filter((e) => e !== entry);
        };
    },
};

/**
 * The helpers below take any string, and assert the protocol's scoped-id type, because
 * these tests drive the host's *runtime* check: a plugin built without the protocol's
 * types, or one that casts, reaches it with an id the compile-time check would
 * have refused.
 */
const filterField = (
    id = "acme.test-plugin:sector",
    overrides: Partial<ContributedFilterField> = {},
) =>
    ({
        id,
        labelKey: "acme.test-plugin:sector",
        path: "properties.opencop.element.symbol",
        order: 60,
        ...overrides,
    }) as ContributedFilterField & { readonly id: PluginScopedId };

const mapper = (type = "test-type") => ({ type, map: () => [] });

const tool = (id = "test-tool", label = "Test Tool") => ({
    id,
    label,
    icon: () => createElement("span"),
    content: () => null,
});

/** A plugin whose `install` contributes one mapper, unless overridden. */
function makePlugin(overrides: Partial<PluginModule> = {}): PluginModule {
    return definePlugin({
        manifest: {
            id: "acme.test-plugin",
            name: "Test Plugin",
            version: "1.0.0",
            apiVersion: SDK_API_VERSION,
        },
        install: (ctx) => ctx.registerElementMapper(mapper()),
        ...overrides,
    });
}

let manager: PluginManager;

beforeEach(() => {
    injectedTools = [];
    injectedNamespaces = [];
    injectedMappers = [];
    injectedFilterFields = [];
    takenFilterFieldIds = [];
    injectedDetailsHeaders = [];
    manager = new PluginManager(services);
});

afterEach(() => {
    for (const record of manager.list()) manager.unload(record.manifest.id);
    // Guard against a leaked registration bleeding into the next test.
    expect(injectedTools).toHaveLength(0);
    expect(injectedNamespaces).toHaveLength(0);
    expect(injectedMappers).toHaveLength(0);
    expect(injectedDetailsHeaders).toHaveLength(0);
});

describe("PluginManager", () => {
    it("installs a well-formed plugin, marks it active, and registers its contribution", () => {
        const record = manager.load(makePlugin());

        expect(record.status).toBe("active");
        expect(injectedMappers).toHaveLength(1);
        expect(injectedMappers[0]).toMatchObject({
            pluginId: "acme.test-plugin",
            mapper: { type: "test-type" },
        });
    });

    it("calls install exactly once, with the plugin's scoped context", () => {
        const install = vi.fn();
        manager.load(makePlugin({ install }));

        expect(install).toHaveBeenCalledOnce();
        const ctx = install.mock.calls[0][0];
        // Capabilities (DI) alongside the register methods — registration is not
        // the whole API.
        expect(ctx.storage).toBeDefined();
        expect(typeof ctx.registerTool).toBe("function");
        expect(typeof ctx.registerElementMapper).toBe("function");
    });

    it("fails an invalid module without registering anything", () => {
        const record = manager.load({ nonsense: true });

        expect(record.status).toBe("failed");
        expect(injectedMappers).toHaveLength(0);
    });

    it("fails a module with no install function", () => {
        const record = manager.load({
            manifest: { id: "acme.x", name: "X", version: "1.0.0", apiVersion: SDK_API_VERSION },
        });

        expect(record.status).toBe("failed");
    });

    it("stops at a manifest whose identity is wrong, without ever calling install", () => {
        const install = vi.fn();
        const record = manager.load(
            makePlugin({
                manifest: {
                    id: "Acme:Bad",
                    name: "Bad",
                    version: "1.0.0",
                    apiVersion: SDK_API_VERSION,
                },
                install,
            }),
        );

        expect(record.status).toBe("failed");
        expect(install).not.toHaveBeenCalled();
        expect(injectedMappers).toHaveLength(0);
    });

    it("goes on past any other manifest problem, reporting every problem and still refusing", () => {
        const install = vi.fn((ctx: PluginInstallContext) =>
            ctx.registerElementMapper({ type: "", map: () => [] }),
        );
        const record = manager.load(
            makePlugin({
                manifest: {
                    id: "acme.bad",
                    name: "",
                    version: "1.0.0",
                    apiVersion: SDK_API_VERSION,
                },
                install,
            }),
        );

        expect(record.status).toBe("failed");
        expect(install).toHaveBeenCalledOnce();
        expect(record.problems.map((problem) => problem.rule)).toEqual([
            "manifest.name-missing",
            "elementMappers.type-missing",
        ]);
        expect(injectedMappers).toHaveLength(0);
    });

    it.each([
        ["no namespace", "heatmap"],
        ["a colon", "acme:heatmap"],
        ["upper case", "Acme.Heatmap"],
        ["an empty segment", "acme..heatmap"],
    ])("refuses a manifest id with %s, without calling install", (_case, id) => {
        const install = vi.fn();
        const record = manager.load(
            makePlugin({
                manifest: { id, name: "Bad id", version: "1.0.0", apiVersion: SDK_API_VERSION },
                install,
            }),
        );

        expect(record.status).toBe("failed");
        expect(record.error).toBe(
            `manifest.id "${id}" must be "<namespace>.<name>": lower case, at least one ".", no ":"`,
        );
        expect(install).not.toHaveBeenCalled();
    });

    it("refuses a plugin that takes a native plugin's owner id", () => {
        const install = vi.fn();
        const record = manager.load(
            makePlugin({
                manifest: {
                    id: NATIVE_OWNER_ID,
                    name: "Impostor",
                    version: "1.0.0",
                    apiVersion: SDK_API_VERSION,
                },
                install,
            }),
        );

        expect(record.status).toBe("failed");
        expect(record.error).toBe(
            `manifest.id "${NATIVE_OWNER_ID}" is reserved by a native plugin`,
        );
        expect(install).not.toHaveBeenCalled();
    });

    it("accepts a namespaced id with hyphens and more than one dot", () => {
        const record = manager.load(
            makePlugin({
                manifest: {
                    id: "acme.geo.heat-map",
                    name: "Heat map",
                    version: "1.0.0",
                    apiVersion: SDK_API_VERSION,
                },
            }),
        );

        expect(record.status).toBe("active");
    });

    it("marks an apiVersion mismatch and never calls install", () => {
        const install = vi.fn();
        const record = manager.load(
            makePlugin({
                manifest: {
                    id: "acme.mismatch",
                    name: "Mismatch",
                    version: "1.0.0",
                    apiVersion: SDK_API_VERSION + 1,
                },
                install,
            }),
        );

        expect(record.status).toBe("version-mismatch");
        expect(install).not.toHaveBeenCalled();
        expect(injectedMappers).toHaveLength(0);
    });

    // The point of recording `install` instead of applying it: an imperative
    // register API stays all-or-nothing.
    it("registers NOTHING when install throws part-way through", () => {
        const record = manager.load(
            makePlugin({
                manifest: {
                    id: "acme.boom",
                    name: "Boom",
                    version: "1.0.0",
                    apiVersion: SDK_API_VERSION,
                },
                install: (ctx) => {
                    ctx.registerElementMapper(mapper("half-registered"));
                    ctx.registerTool(tool("half-tool"));
                    throw new Error("kaboom");
                },
            }),
        );

        expect(record.status).toBe("failed");
        // The mapper/tool it managed to register before throwing never took effect.
        expect(injectedMappers).toHaveLength(0);
        expect(injectedTools).toHaveLength(0);
    });

    it("isolates a throwing install and does not affect other plugins", () => {
        const good = manager.load(makePlugin());
        expect(good.status).toBe("active");

        const boom = manager.load(
            makePlugin({
                manifest: {
                    id: "acme.boom",
                    name: "Boom",
                    version: "1.0.0",
                    apiVersion: SDK_API_VERSION,
                },
                install: () => {
                    throw new Error("kaboom");
                },
            }),
        );

        expect(boom.status).toBe("failed");
        expect(injectedMappers).toHaveLength(1);
        expect(injectedMappers[0].pluginId).toBe("acme.test-plugin");
    });

    it("unloads a plugin: uninstalls it and unregisters its contributions", () => {
        const uninstall = vi.fn();
        manager.load(makePlugin({ uninstall }));
        expect(injectedMappers).toHaveLength(1);

        manager.unload("acme.test-plugin");

        expect(uninstall).toHaveBeenCalledOnce();
        expect(injectedMappers).toHaveLength(0);
        expect(manager.list()).toHaveLength(0);
    });

    it("reloads the same plugin id idempotently without duplicating its contributions", () => {
        manager.load(makePlugin());
        const record = manager.load(makePlugin());

        expect(record.status).toBe("active");
        // Second load replaces the first — one mapper + one record, not two.
        expect(injectedMappers).toHaveLength(1);
        expect(manager.list()).toHaveLength(1);
    });

    it("keeps the working instance when a reload fails validation", () => {
        expect(manager.load(makePlugin()).status).toBe("active");

        const record = manager.load(
            makePlugin({
                install: (ctx) => ctx.registerElementMapper({ ...mapper(), type: "" }),
            }),
        );

        expect(record.status).toBe("failed");
        // The previously-installed good instance is untouched — a bad reload
        // can't take down what already works.
        expect(injectedMappers).toHaveLength(1);
    });

    it("fails a plugin whose manifest is null without throwing, and keeps loading others", () => {
        const record = manager.load({ manifest: null, install: noop });

        expect(record.status).toBe("failed");
        expect(injectedMappers).toHaveLength(0);
        // A malformed module must not abort a subsequent load.
        expect(manager.load(makePlugin()).status).toBe("active");
    });

    it("injects a tool contribution via the host registerTool service", () => {
        const record = manager.load(makePlugin({ install: (ctx) => ctx.registerTool(tool()) }));

        expect(record.status).toBe("active");
        // Tools live in the app-owned registry, reached through the injected service.
        expect(injectedTools).toEqual([
            { pluginId: "acme.test-plugin", toolId: "acme.test-plugin:test-tool" },
        ]);
    });

    it("fails a tool missing content and injects nothing (atomic)", () => {
        const record = manager.load(
            makePlugin({
                install: (ctx) => {
                    ctx.registerElementMapper(mapper("r"));
                    // Missing content — the whole load must fail, mapper included.
                    ctx.registerTool({
                        id: "t",
                        label: "T",
                        icon: () => createElement("span"),
                    } as never);
                },
            }),
        );

        expect(record.status).toBe("failed");
        expect(injectedTools).toHaveLength(0);
        expect(injectedMappers).toHaveLength(0);
    });

    it("unloads a plugin and disposes its injected tool", () => {
        manager.load(makePlugin({ install: (ctx) => ctx.registerTool(tool()) }));
        expect(injectedTools).toHaveLength(1);

        manager.unload("acme.test-plugin");

        expect(injectedTools).toHaveLength(0);
    });

    it("registers a mapper contribution into the registry", () => {
        const map = vi.fn(() => []);
        const record = manager.load(
            makePlugin({
                install: (ctx) => ctx.registerElementMapper({ type: "symbol", map }),
            }),
        );

        expect(record.status).toBe("active");
        expect(injectedMappers).toHaveLength(1);
        expect(injectedMappers[0]).toMatchObject({
            pluginId: "acme.test-plugin",
            mapper: { type: "symbol" },
        });
    });

    it("fails a mapper missing map and registers nothing (atomic)", () => {
        const record = manager.load(
            makePlugin({
                install: (ctx) => {
                    ctx.registerTool(tool("t"));
                    // Missing map — the whole load must fail, tool included.
                    ctx.registerElementMapper({ type: "symbol" } as never);
                },
            }),
        );

        expect(record.status).toBe("failed");
        expect(injectedMappers).toHaveLength(0);
        expect(injectedTools).toHaveLength(0);
        expect(record.error).toBe('elementMappers: elementMapper "symbol" map missing');
    });

    it("fails a mapper with a malformed text setting and registers nothing", () => {
        const record = manager.load(
            makePlugin({
                install: (ctx) =>
                    ctx.registerElementMapper({ ...mapper("x"), textSetting: { labelKey: "" } }),
            }),
        );

        expect(record.status).toBe("failed");
        expect(record.error).toBe('elementMappers: elementMapper "x" textSetting.labelKey missing');
        expect(injectedMappers).toHaveLength(0);
    });

    it("unloads a plugin and unregisters its mapper", () => {
        manager.load(
            makePlugin({
                install: (ctx) => ctx.registerElementMapper(mapper("symbol")),
            }),
        );
        expect(injectedMappers).toHaveLength(1);

        manager.unload("acme.test-plugin");

        expect(injectedMappers).toHaveLength(0);
    });

    it("registers a filter field and removes it on unload", () => {
        const record = manager.load(
            makePlugin({ install: (ctx) => ctx.registerFilterField(filterField()) }),
        );

        expect(record.status).toBe("active");
        expect(injectedFilterFields).toEqual([
            { pluginId: "acme.test-plugin", fieldId: "acme.test-plugin:sector" },
        ]);

        manager.unload("acme.test-plugin");

        expect(injectedFilterFields).toHaveLength(0);
    });

    it.each([
        ["no id", filterField(""), "filterField.id missing"],
        [
            "an id outside the plugin's namespace",
            filterField("sector"),
            'filterField "sector" must be namespaced as "acme.test-plugin:<name>"',
        ],
        [
            "an id in another plugin's namespace",
            filterField("other:sector"),
            'filterField "other:sector" must be namespaced as "acme.test-plugin:<name>"',
        ],
        [
            "no label key",
            filterField(undefined, { labelKey: "" }),
            'filterField "acme.test-plugin:sector" labelKey missing',
        ],
        [
            "no path",
            filterField(undefined, { path: "" }),
            'filterField "acme.test-plugin:sector" path missing',
        ],
    ])("fails a filter field with %s and registers nothing (atomic)", (_, field, error) => {
        const record = manager.load(
            makePlugin({
                install: (ctx) => {
                    ctx.registerElementMapper(mapper("r"));
                    ctx.registerFilterField(field);
                },
            }),
        );

        expect(record.status).toBe("failed");
        expect(record.error).toBe(`filterFields: ${error}`);
        expect(injectedFilterFields).toHaveLength(0);
        expect(injectedMappers).toHaveLength(0);
    });

    it("fails a plugin that registers one filter field id twice", () => {
        const record = manager.load(
            makePlugin({
                install: (ctx) => {
                    ctx.registerFilterField(filterField());
                    ctx.registerFilterField(filterField());
                },
            }),
        );

        expect(record.error).toBe(
            'filterFields: filterField "acme.test-plugin:sector" registered twice',
        );
        expect(injectedFilterFields).toHaveLength(0);
    });

    it("has the protocol types refuse a filter field id with no namespace", () => {
        const install = (ctx: PluginInstallContext) => {
            ctx.registerFilterField({
                // @ts-expect-error — a filter field id must be "<pluginId>:<name>"
                id: "sector",
                labelKey: "acme.test-plugin:sector",
                path: "properties.opencop.element.symbol",
                order: 60,
            });
        };

        const record = manager.load(makePlugin({ install }));
        expect(record.status).toBe("failed");
        expect(record.error).toBe(
            'filterFields: filterField "sector" must be namespaced as "acme.test-plugin:<name>"',
        );
    });

    it("rolls the load back when the host refuses a filter field id another owner holds", () => {
        takenFilterFieldIds = ["acme.test-plugin:sector"];
        const record = manager.load(
            makePlugin({
                install: (ctx) => {
                    ctx.registerElementMapper(mapper("r"));
                    ctx.registerFilterField(filterField("acme.test-plugin:first"));
                    ctx.registerFilterField(filterField());
                },
            }),
        );

        expect(record.status).toBe("failed");
        expect(injectedFilterFields).toHaveLength(0);
        expect(injectedMappers).toHaveLength(0);
    });

    it("unloads every other contribution, and the plugin, when one unregister throws", () => {
        manager.load(
            makePlugin({
                install: (ctx) => {
                    ctx.registerTool(tool());
                    ctx.registerElementMapper(mapper(THROWS_ON_UNREGISTER));
                },
            }),
        );

        // The mapper registers last, so its disposer runs first and throws.
        expect(() => manager.unload("acme.test-plugin")).not.toThrow();
        expect(injectedTools).toHaveLength(0);
        expect(manager.list()).toEqual([]);
    });

    it.each([
        [
            "an empty element type",
            { produces: { store: "elements", type: "" } },
            'tools: tool "test-tool" type is empty',
        ],
        [
            "neither content nor a draw kit",
            { content: undefined },
            'tools: tool "test-tool" needs content or draw',
        ],
        [
            "a draw kit with no grammar",
            { content: undefined, draw: {} as DrawKit },
            'tools: tool "test-tool" draw.grammar missing',
        ],
        [
            "a static grammar with no bind",
            {
                content: undefined,
                draw: { grammar: { hints: () => [], create: () => ({}) as never } } as never,
            },
            'tools: tool "test-tool" draw.grammar.bind missing',
        ],
        [
            "a session product outside the plugin's namespace",
            { produces: { store: "session", type: "other:ruler" } },
            'tools: tool "test-tool" session type "other:ruler" must be namespaced as "acme.test-plugin:<name>"',
        ],
        [
            'location "external"',
            { location: "external" as const },
            'tools: tool "test-tool" location "external" is not available to plugins yet — only "topBarStart", "topBarCenter" and "topBarEnd" are',
        ],
        [
            'an unrecognized location (fails closed, not just on "external")',
            { location: "sidebar" as never },
            'tools: tool "test-tool" location "sidebar" is not available to plugins yet — only "topBarStart", "topBarCenter" and "topBarEnd" are',
        ],
    ])("fails a tool declaring %s", (_, fields, error) => {
        const record = manager.load(
            makePlugin({ install: (ctx) => ctx.registerTool({ ...tool(), ...fields }) }),
        );

        expect(record.status).toBe("failed");
        expect(record.error).toBe(error);
    });

    it("reports every problem across kinds, not the first, and registers nothing", () => {
        const record = manager.load(
            makePlugin({
                install: (ctx) => {
                    ctx.registerTool({ ...tool(), content: undefined });
                    ctx.registerFilterField(filterField(undefined, { path: "" }));
                },
            }),
        );

        expect(record.status).toBe("failed");
        expect(record.error).toBe(
            'tools: tool "test-tool" needs content or draw; ' +
                'filterFields: filterField "acme.test-plugin:sector" path missing',
        );
        expect(injectedTools).toHaveLength(0);
        expect(injectedFilterFields).toHaveLength(0);
    });

    it("refuses a filter field whose label key resolves nowhere, before anything registers", () => {
        const strict = new PluginManager({ ...services, translationExists: () => false });
        const record = strict.load(
            makePlugin({
                install: (ctx) => {
                    ctx.registerElementMapper(mapper("m"));
                    ctx.registerFilterField(filterField());
                },
            }),
        );

        expect(record.error).toBe(
            'filterFields: filterField "acme.test-plugin:sector" labelKey "acme.test-plugin:sector" does not resolve',
        );
        expect(injectedMappers).toHaveLength(0);
    });

    it("resolves a filter field's label key in the plugin's own bundle", () => {
        const strict = new PluginManager({ ...services, translationExists: () => false });
        const record = strict.load(
            makePlugin({
                install: (ctx) => {
                    ctx.registerTranslations({
                        namespace: "acme.test-plugin",
                        resources: { en: { sector: "Sector" } },
                    });
                    ctx.registerFilterField(filterField());
                },
            }),
        );

        expect(record.status).toBe("active");
        strict.unload("acme.test-plugin");
    });

    it("loads a tool whose session product is in the plugin's namespace", () => {
        const record = manager.load(
            makePlugin({
                install: (ctx) =>
                    ctx.registerTool({
                        ...tool(),
                        produces: { store: "session", type: "acme.test-plugin:ruler" },
                    }),
            }),
        );

        expect(record.status).toBe("active");
    });

    it("loads a draw tool whose kit carries its own grammar", () => {
        const grammar = createSequentialGrammar({ min: 2, create: () => ({}) as never });

        const record = manager.load(
            makePlugin({
                install: (ctx) =>
                    ctx.registerTool({ ...tool(), content: undefined, draw: { grammar } }),
            }),
        );

        expect(record.status).toBe("active");
        expect(injectedTools).toHaveLength(1);
    });

    it("injects a translations contribution and removes it on unload", () => {
        manager.load(
            makePlugin({
                install: (ctx) =>
                    ctx.registerTranslations({
                        namespace: "test-ns",
                        resources: { en: { greeting: "hi" }, fr: { greeting: "salut" } },
                    }),
            }),
        );

        expect(injectedNamespaces).toEqual(["en/test-ns", "fr/test-ns"]);

        manager.unload("acme.test-plugin");

        expect(injectedNamespaces).toHaveLength(0);
    });

    it("registers NO translations when install throws part-way through", () => {
        const record = manager.load(
            makePlugin({
                manifest: {
                    id: "acme.boom2",
                    name: "Boom",
                    version: "1.0.0",
                    apiVersion: SDK_API_VERSION,
                },
                install: (ctx) => {
                    ctx.registerTranslations({ namespace: "half", resources: { en: {} } });
                    throw new Error("kaboom");
                },
            }),
        );

        // Same all-or-nothing guarantee the other kinds get: `install` is recorded,
        // then applied, so a throw part-way leaves nothing behind.
        expect(record.status).toBe("failed");
        expect(injectedNamespaces).toHaveLength(0);
    });

    it("rejects a translations contribution with no namespace", () => {
        const record = manager.load(
            makePlugin({
                manifest: {
                    id: "acme.bad-ns",
                    name: "Bad",
                    version: "1.0.0",
                    apiVersion: SDK_API_VERSION,
                },
                install: (ctx) =>
                    ctx.registerTranslations({ namespace: "", resources: { en: {} } }),
            }),
        );

        expect(record.status).toBe("failed");
        expect(injectedNamespaces).toHaveLength(0);
    });

    it("keeps both mappers when two plugins claim the same type", () => {
        const first = mapper("symbol");
        const second = mapper("symbol");
        manager.load(
            makePlugin({
                manifest: {
                    id: "acme.a",
                    name: "A",
                    version: "1.0.0",
                    apiVersion: SDK_API_VERSION,
                },
                install: (ctx) => ctx.registerElementMapper(first),
            }),
        );
        manager.load(
            makePlugin({
                manifest: {
                    id: "acme.b",
                    name: "B",
                    version: "1.0.0",
                    apiVersion: SDK_API_VERSION,
                },
                install: (ctx) => ctx.registerElementMapper(second),
            }),
        );

        // The manager passes both through: refusing the second is the app registry's
        // call (one owner per type), which needs every owner's registrations.
        expect(injectedMappers).toHaveLength(2);
        expect(injectedMappers.map((r) => r.mapper)).toEqual([first, second]);
    });

    it("lets install decide what to contribute from its context", () => {
        // The capability an imperative install buys over a static list: the plugin
        // reads host/plugin state and contributes conditionally.
        const record = manager.load(
            makePlugin({
                install: (ctx) => {
                    if (ctx.storage.get("mode") === undefined)
                        ctx.registerElementMapper(mapper("empty-state"));
                    else ctx.registerElementMapper(mapper("populated"));
                },
            }),
        );

        expect(record.status).toBe("active");
        expect(injectedMappers[0].mapper.type).toBe("empty-state");
    });
});
