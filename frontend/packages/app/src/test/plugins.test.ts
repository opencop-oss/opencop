// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { afterEach, describe, expect, it, vi } from "vitest";
import Logger from "js-logger";
import { nativeOwner } from "~/features/contributions/contribution-guard";
import { registerDetailsHeader } from "~/features/contributions/details-registry";
import { registerElementMapper } from "~/features/contributions/element-mapper-registry";
import { getAllTools } from "~/features/contributions/tool-registry";
import { getRegisteredFilterFields } from "~/features/contributions/filter-field-registry";
import { PluginManager } from "@opencop/contributions/host";
import { installPlugins, withPlugins } from "./plugins";

// Lets one test break the native uninstall, to show the cleanup check catches it, and
// another make it report a throw, to show the uninstall surfaces it.
const broken = vi.hoisted(() => ({
    skipNativeDispose: false,
    skipped: [] as Array<() => unknown>,
    nativeDisposeThrew: [] as unknown[],
}));
vi.mock("~/features/contributions/native-install", async (importOriginal) => {
    const actual = await importOriginal<typeof import("~/features/contributions/native-install")>();
    return {
        ...actual,
        installNativeSubjects: (...args: Parameters<typeof actual.installNativeSubjects>) => {
            const dispose = actual.installNativeSubjects(...args);
            return () => {
                if (!broken.skipNativeDispose) return [...dispose(), ...broken.nativeDisposeThrew];
                broken.skipped.push(dispose);
                return [];
            };
        },
    };
});

const disposers: Array<() => void> = [];
afterEach(() => {
    for (const dispose of disposers.splice(0)) dispose();
    vi.restoreAllMocks();
});

const quietPluginLog = () =>
    vi.spyOn(Logger.get("plugins"), "error").mockImplementation(() => undefined);

describe("installPlugins", () => {
    it("installs a native and a standard plugin by id, and removes both", () => {
        const before = getAllTools().length;
        const uninstall = installPlugins(["opencop.shapes", "opencop.symbology"]);

        expect(getAllTools().length).toBeGreaterThan(before);
        expect(
            getRegisteredFilterFields().some((field) => field.id.startsWith("opencop.symbology:")),
        ).toBe(true);

        uninstall();
        expect(getAllTools()).toHaveLength(before);
        expect(getRegisteredFilterFields()).toEqual([]);
    });

    it("refuses an id that names no plugin", () => {
        expect(() => installPlugins(["opencop.nowhere"])).toThrow(
            'no native or standard plugin "opencop.nowhere"',
        );
    });

    it("fails loudly when a native collides with a fake the test registered", () => {
        quietPluginLog();
        disposers.push(registerDetailsHeader(nativeOwner("opencop.test"), { type: "point" }));

        expect(() => installPlugins(["opencop.shapes"])).toThrow(
            /opencop\.shapes failed:.*"point" is already held/,
        );
    });

    it("fails loudly when a standard plugin collides with a fake the test registered", () => {
        const before = getAllTools().length;
        disposers.push(
            registerElementMapper(nativeOwner("opencop.test"), { type: "symbol", map: () => [] }),
        );

        expect(() => installPlugins(["opencop.symbology"])).toThrow(
            /opencop\.symbology failed:.*"symbol" is already held/,
        );
        expect(getAllTools()).toHaveLength(before);
    });

    it("blames only what it installed: a fake registered after install is not its leak", () => {
        const uninstall = installPlugins(["opencop.shapes"]);
        disposers.push(registerDetailsHeader(nativeOwner("opencop.test"), { type: "fixture" }));
        expect(() => uninstall()).not.toThrow();
    });

    it("catches an uninstall that leaves its entries behind", () => {
        const uninstall = installPlugins(["opencop.shapes"]);
        broken.skipNativeDispose = true;
        try {
            expect(() => uninstall()).toThrow(
                "opencop.shapes left entries registered after uninstall",
            );
        } finally {
            broken.skipNativeDispose = false;
            for (const dispose of broken.skipped.splice(0)) dispose();
        }
    });

    it("fails the uninstall with what a native disposer threw", () => {
        const uninstall = installPlugins(["opencop.shapes"]);
        const threw = new Error("native disposer threw");
        broken.nativeDisposeThrew.push(threw);
        try {
            expect(() => uninstall()).toThrow(threw);
        } finally {
            broken.nativeDisposeThrew.length = 0;
        }
    });

    it("fails the uninstall with what a plugin's uninstall threw", () => {
        const uninstall = installPlugins(["opencop.symbology"]);
        const threw = new Error("plugin uninstall threw");
        const unload = PluginManager.prototype.unload;
        vi.spyOn(PluginManager.prototype, "unload").mockImplementation(function (
            this: PluginManager,
            id,
        ) {
            return [...unload.call(this, id), threw];
        });
        expect(() => uninstall()).toThrow(threw);
    });
});

describe("withPlugins", () => {
    withPlugins(["opencop.symbology"]);

    it("installs before each test of its describe", () => {
        expect(getRegisteredFilterFields().length).toBeGreaterThan(0);
    });
});

describe("outside a withPlugins describe", () => {
    it("has nothing installed", () => {
        expect(getRegisteredFilterFields()).toEqual([]);
    });
});
