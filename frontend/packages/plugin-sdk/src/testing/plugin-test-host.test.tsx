// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { screen } from "@testing-library/react";
import { definePlugin, SDK_API_VERSION, usePluginContext } from "@opencop/contributions/plugin";
import { createTestHost, validatePlugin } from "./plugin-test-host.ts";

const MANIFEST = { id: "acme.kit", name: "Kit", version: "1.0.0", apiVersion: SDK_API_VERSION };

const COLOR_KEY = "color";

function Editor() {
    const { storage } = usePluginContext();
    return <p>{`stored: ${String(storage.get(COLOR_KEY))}`}</p>;
}

const plugin = definePlugin({
    manifest: MANIFEST,
    install(ctx) {
        ctx.registerTranslations({
            namespace: "acme.kit",
            resources: { en: { sector: "Sector" } },
        });
        ctx.registerTool({
            id: "paint",
            label: "Paint",
            icon: () => null,
            content: () => null,
            editor: Editor,
        });
        ctx.registerFilterField({
            id: "acme.kit:sector",
            labelKey: "acme.kit:sector",
            path: "properties.opencop.element.symbol",
            order: 60,
        });
    },
});

describe("createTestHost({ plugin })", () => {
    it("loads a plugin the host would load: no problems, its tool under the host's id", () => {
        const host = createTestHost({ plugin, storage: { [COLOR_KEY]: "red" } });

        expect(host.problems).toEqual([]);
        host.render(host.contributions.tools.get("acme.kit:paint")!.editor!);
        expect(screen.getByText("stored: red")).toBeInTheDocument();
    });

    it("reports a bad manifest, and loads nothing", () => {
        const host = createTestHost({
            plugin: { ...plugin, manifest: { ...MANIFEST, id: "Acme Kit" } },
        });

        expect(host.problems.map((problem) => problem.rule)).toContain("manifest.id-malformed");
        expect(host.contributions.tools.getAll().length).toBe(0);
    });

    it("reports a bad tool, and loads nothing — a refused plugin registers nothing, as on the host", () => {
        const broken = definePlugin({
            manifest: MANIFEST,
            install(ctx) {
                ctx.registerTool({ id: "paint", label: "Paint", icon: () => null });
                ctx.registerFilterField({
                    id: "acme.kit:sector",
                    labelKey: "acme.kit:nowhere",
                    path: "properties.opencop.element.symbol",
                    order: 60,
                });
            },
        });
        const host = createTestHost({ plugin: broken });

        expect(host.problems.map((problem) => problem.rule)).toEqual([
            "tools.content-or-draw-missing",
            "filterFields.label-key-unresolved",
        ]);
        expect(
            host.contributions.tools.getAll().length +
                host.contributions.filterFields.getEntries().length,
        ).toBe(0);
    });

    it("attributes a contained throw to the plugin, as the host does", () => {
        const throwing = definePlugin({
            manifest: MANIFEST,
            install(ctx) {
                ctx.registerTool({
                    id: "paint",
                    label: "Paint",
                    icon: () => null,
                    content: () => null,
                    editor: () => {
                        throw new Error("editor broke");
                    },
                });
            },
        });
        const host = createTestHost({ plugin: throwing });
        host.render(host.contributions.tools.get("acme.kit:paint")!.editor!);

        expect(host.takeFailures().map((failure) => failure.origin)).toEqual([
            "acme.kit:acme.kit:paint:editor",
        ]);
    });

    it("runs the plugin's uninstall, then removes what it registered", () => {
        const calls: string[] = [];
        const host = createTestHost({
            plugin: { ...plugin, uninstall: () => calls.push("uninstall") },
        });
        host.uninstall();
        expect(calls).toEqual(["uninstall"]);
        expect(host.contributions.tools.getAll().length).toBe(0);
    });
});

describe("validatePlugin", () => {
    it("is the same check, without rendering: none for a plugin the host loads", () => {
        expect(validatePlugin(plugin)).toEqual([]);
    });

    // A manifest problem that is not about the plugin's identity or version does not stop
    // the load, so the contributions are checked in the same pass.
    it("lists the manifest's and the contributions' problems together", () => {
        const problems = validatePlugin({
            ...plugin,
            manifest: { ...MANIFEST, name: "" },
            install(ctx) {
                ctx.registerTool({ id: "", label: "Paint", icon: () => null, content: () => null });
            },
        });
        expect(problems.map((problem) => problem.rule)).toEqual([
            "manifest.name-missing",
            "tools.id-missing",
        ]);
    });
    it("throws for an uninstall that throws, as a test host does", () => {
        const threw = new Error("uninstall broke");
        expect(() =>
            validatePlugin({
                ...plugin,
                uninstall: () => {
                    throw threw;
                },
            }),
        ).toThrow("acme.kit: uninstall threw");
    });
});
