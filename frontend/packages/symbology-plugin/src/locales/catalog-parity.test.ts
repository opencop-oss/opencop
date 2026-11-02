// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Catalog } from "@opencop/symbology";

import en from "./en.json";
import fr from "./fr.json";
import tw from "./tw.json";

function collectLocaleKeys(value: unknown, keys: Set<string>): void {
    if (Array.isArray(value)) {
        value.forEach((entry) => collectLocaleKeys(entry, keys));
        return;
    }

    if (!value || typeof value !== "object") return;

    const record = value as Record<string, unknown>;
    if (typeof record.localeKey === "string") keys.add(record.localeKey);
    Object.values(record).forEach((entry) => collectLocaleKeys(entry, keys));
}

function activeCatalogLocaleKeys(): string[] {
    const keys = new Set<string>();
    collectLocaleKeys(Catalog.AMPLIFIERS.record, keys);
    collectLocaleKeys(Catalog.POINT_SYMBOL_SETS.record, keys);
    return [...keys].sort();
}

/**
 * Every leaf key path, sorted, with i18next's plural suffix removed so a locale's
 * own plural forms (fr has `_many`) compare equal to en's.
 */
function leafKeys(value: unknown, path = "", out = new Set<string>()): string[] {
    if (value && typeof value === "object") {
        for (const [key, child] of Object.entries(value)) {
            leafKeys(child, path ? `${path}.${key}` : key, out);
        }
    } else {
        out.add(path.replace(/_(zero|one|two|few|many|other)$/, ""));
    }
    return [...out].sort();
}

describe("symbology catalog locale parity", () => {
    const locales = [
        ["en", en],
        ["fr", fr],
        ["tw", tw],
    ] as const;

    it("keeps the same exact key set in every retained locale", () => {
        const englishKeys = Object.keys(en).sort();
        expect(Object.keys(fr).sort()).toEqual(englishKeys);
        expect(Object.keys(tw).sort()).toEqual(englishKeys);
    });

    it.each([
        ["fr", fr],
        ["tw", tw],
    ] as const)("has exactly en's nested leaf keys in %s", (_locale, translations) => {
        expect(leafKeys(translations)).toEqual(leafKeys(en));
    });

    it.each(locales)(
        "contains every translatable active catalog key in %s",
        (_locale, translations) => {
            const translationKeys = new Set(Object.keys(translations));
            const missingKeys = activeCatalogLocaleKeys().filter(
                (key) => !translationKeys.has(key),
            );
            expect(missingKeys).toEqual([]);
        },
    );

    it.each(locales)("has no empty catalog translation in %s", (_locale, translations) => {
        // Recurses because the namespace holds the wizard's nested strings beside
        // the flat catalog labels — every leaf gets checked, not just the top level.
        const emptyLeaves: string[] = [];
        const walk = (value: unknown, path: string): void => {
            if (typeof value === "string") {
                if (value.trim().length === 0) emptyLeaves.push(path);
                return;
            }
            if (!value || typeof value !== "object") return;
            for (const [key, child] of Object.entries(value)) {
                walk(child, path ? `${path}.${key}` : key);
            }
        };
        walk(translations, "");

        expect(emptyLeaves).toEqual([]);
    });
});
