// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * Checks that all secondary locale files (fr.json, tw.json) have no empty
 * string values. Empty values indicate untranslated keys that were added by
 * i18next-cli extract but never filled in.
 *
 * Exit code 0: all translations complete
 * Exit code 1: missing translations found
 */

import { readFileSync } from "node:fs";
import { resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = dirname(fileURLToPath(import.meta.url));
const localesDir = resolve(__dirname, "../packages/app/src/locales");

const secondaryLocales = ["fr", "tw"];

function findEmptyKeys(obj, prefix = "") {
    const empty = [];
    for (const [key, value] of Object.entries(obj)) {
        const path = prefix ? `${prefix}.${key}` : key;
        if (typeof value === "object" && value !== null) {
            empty.push(...findEmptyKeys(value, path));
        } else if (value === "") {
            empty.push(path);
        }
    }
    return empty;
}

let hasErrors = false;

for (const locale of secondaryLocales) {
    const filePath = resolve(localesDir, locale, "translation.json");
    const content = JSON.parse(readFileSync(filePath, "utf-8"));
    const emptyKeys = findEmptyKeys(content);

    if (emptyKeys.length > 0) {
        hasErrors = true;
        console.error(`\n${locale}/translation.json: ${emptyKeys.length} untranslated key(s):`);
        for (const key of emptyKeys) {
            console.error(`  - ${key}`);
        }
    }
}

if (hasErrors) {
    console.error("\nFix: add translations for the keys listed above.");
    process.exit(1);
} else {
    console.log("All secondary locale translations are complete.");
}
