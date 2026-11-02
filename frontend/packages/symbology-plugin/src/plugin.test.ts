// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { PluginManager, type PluginHostServices } from "@opencop/contributions/host";
import { Symbol } from "@opencop/elements";
import type { ElementMapper } from "@opencop/contributions";
import symbologyPlugin from "./plugin.tsx";
import { noop } from "lodash";

/**
 * Integration proof that the real symbology plugin runs on the runtime plugin
 * rails: loaded through the same {@link PluginManager} the host uses, it becomes
 * active and only the symbol mapper is registered in the mapper registry. The
 * live-map render is verified separately in the browser.
 */

// The app owns the mapper registry, so this stands in for it: the plugin
// contributes through the same `registerElementMapper` sink the app supplies.
let injectedMappers: ElementMapper[] = [];
let injectedFilterFields: string[] = [];

const services: PluginHostServices = {
    reservedOwnerIds: [],
    // Only the plugin's own bundles count, so its keys must resolve in them.
    translationExists: () => false,
    registerTool: () => noop,
    i18n: { addResourceBundle: noop, removeResourceBundle: noop },
    registerDetailsHeader: () => noop,
    registerFilterField: (_pluginId, field) => {
        injectedFilterFields.push(field.id);
        return () => {
            injectedFilterFields = injectedFilterFields.filter((id) => id !== field.id);
        };
    },
    registerElementMapper: (_pluginId, mapper) => {
        injectedMappers.push(mapper);
        return () => {
            injectedMappers = injectedMappers.filter((m) => m !== mapper);
        };
    },
    createContext: () => ({
        storage: { get: () => undefined, set: () => undefined, remove: () => undefined },
    }),
};

let manager: PluginManager;

beforeEach(() => {
    manager = new PluginManager(services);
});

afterEach(() => {
    for (const record of manager.list()) manager.unload(record.manifest.id);
    // Guard against a leaked registration bleeding into the next test.
    expect(injectedMappers).toHaveLength(0);
    expect(injectedFilterFields).toHaveLength(0);
});

/** The mapper this plugin contributed for an element type. */
const registered = (elementType: string) => injectedMappers.find((m) => m.type === elementType);

describe("symbology plugin", () => {
    it("contributes the SIDC filter fields through the plugin path, and unload removes them", () => {
        manager.load(symbologyPlugin);

        expect(injectedFilterFields).toEqual([
            "opencop.symbology:sidc",
            "opencop.symbology:standardIdentity",
            "opencop.symbology:symbolSet",
        ]);
    });

    it("loads as a well-formed plugin and contributes only the symbol mapper", () => {
        const record = manager.load(symbologyPlugin);

        expect(record.status).toBe("active");
        expect(record.manifest.id).toBe("opencop.symbology");
        expect(injectedMappers.map((m) => m.type)).toEqual(["symbol"]);
    });

    it("draws a symbol at the selected icon size", () => {
        manager.load(symbologyPlugin);
        const mapper = registered("symbol")!;
        const element = Symbol.create({ position: [0, 0], sidc: "10031000001211000000" });
        const size = mapper.selector!.select({ icons: { size: 40 } } as never);

        const [icon] = mapper.map(element, size);

        expect(icon.style).toMatchObject({ type: "icon", size: 40 });
    });

    it("unloads cleanly, removing its mapper from the registry", () => {
        manager.load(symbologyPlugin);
        expect(registered("symbol")).toBeDefined();
        expect(registered("graphic")).toBeUndefined();

        manager.unload("opencop.symbology");

        expect(registered("symbol")).toBeUndefined();
        expect(registered("graphic")).toBeUndefined();
    });
});
