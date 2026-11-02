// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import fs from "node:fs";
import { describe, expect, it } from "vitest";

function read(fileName: string): string {
    return fs.readFileSync(fileName, "utf8");
}

function normalizeWhitespace(value: string): string {
    return value.replace(/\s+/g, " ").trim();
}

function normalizeDocumentation(value: string): string {
    return normalizeWhitespace(
        value.replace(/^\s*(?:\/\*\*?|\/\/|\*)\s?/gm, " ").replace(/\*\//g, " "),
    );
}

describe("public API documentation contracts", () => {
    it("states the storage durability guarantee everywhere storage is declared or implemented", () => {
        // The `PluginStorage` contract's load-bearing sentence: the host never
        // clears a plugin's keys on unload or uninstall. Plugin data migrations
        // and the operator-data promise both rest on it, so every site that
        // declares or implements storage must state it. (The element-view case
        // that used to live here was removed with the capability it documented;
        // the plugin manager is deliberately absent — it names no individual
        // capability, so it has no storage prose to keep honest.)
        const readme = normalizeWhitespace(read("packages/contributions/README.md"));
        expect(readme).toContain("durable across the plugin lifecycle");

        for (const [fileName, documentedSymbol] of [
            ["packages/contributions/src/plugin/context.ts", "export interface PluginStorage"],
            ["packages/app/src/features/plugin-manager/plugin-services.ts", "function createStorage"],
        ]) {
            const source = read(fileName);
            const symbolIndex = source.indexOf(documentedSymbol);
            expect(symbolIndex, `${fileName} should contain ${documentedSymbol}`).toBeGreaterThan(
                0,
            );
            const documentation = normalizeDocumentation(
                source.slice(Math.max(0, symbolIndex - 700), symbolIndex),
            );
            expect(
                documentation,
                `${fileName} should tie durability to the plugin lifecycle`,
            ).toContain("unload or uninstall");
            expect(documentation, `${fileName} should state the durability guarantee`).toMatch(
                /durab/i,
            );
        }
    });

    it("documents LRU as a public symbology utility", () => {
        const readme = read("packages/symbology/README.md");
        const barrel = read("packages/symbology/src/index.ts");
        const barrelHeader = barrel.slice(0, barrel.indexOf("*/") + 2);

        expect(normalizeWhitespace(readme)).toContain("general-purpose `LRU` helper");
        expect(barrelHeader).toContain("`LRU`");
        expect(barrelHeader).toContain("general-purpose");
        expect(normalizeDocumentation(barrelHeader)).not.toMatch(
            /`lru\.ts`.*deliberately not re-exported/i,
        );
        expect(barrel).toContain('export { LRU } from "./lru.ts";');
    });
});
