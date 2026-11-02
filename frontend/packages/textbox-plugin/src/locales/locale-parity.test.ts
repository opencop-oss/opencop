// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import en from "./en.json";
import fr from "./fr.json";
import tw from "./tw.json";

/**
 * A missing key renders as the raw key string, which nothing else in the build
 * catches — typecheck and lint have nothing to say about a JSON file.
 */
function keyPaths(bundle: object, prefix = ""): string[] {
    return Object.entries(bundle)
        .flatMap(([key, value]) =>
            value && typeof value === "object"
                ? keyPaths(value as object, `${prefix}${key}.`)
                : [`${prefix}${key}`],
        )
        .sort();
}

describe("textbox locales", () => {
    it.each([
        ["fr", fr],
        ["tw", tw],
    ])("%s has exactly en's keys", (_language, bundle) => {
        expect(keyPaths(bundle)).toEqual(keyPaths(en));
    });

    it("leaves no value empty", () => {
        for (const [language, bundle] of [
            ["en", en],
            ["fr", fr],
            ["tw", tw],
        ] as const) {
            const blanks = JSON.stringify(bundle).match(/""/g);
            expect(blanks, `${language} has an untranslated value`).toBeNull();
        }
    });
});
