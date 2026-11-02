// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { defineConfig } from "i18next-cli";

export default defineConfig({
    locales: ["en", "fr", "tw"],
    extract: {
        input: [
            "packages/app/src/**/*.{ts,tsx}",
            "packages/ui/src/**/*.{ts,tsx}",
        ],
        output: "packages/app/src/locales/{{language}}/{{namespace}}.json",
        defaultNS: "translation",
        keySeparator: ".",
        // `":"`, not `false`: with separation disabled the extractor treated
        // `t("symbology:some.key")` as a *literal* key and wrote it into the host's
        // `translation` bundle, so a plugin's namespaced keys silently accumulated
        // there as empty-valued junk. Nothing under `input` addresses another
        // namespace today, so this routes to no new file.
        nsSeparator: ":",
        functions: ["t", "*.t"],
        transComponents: [],
        useTranslationNames: ["useTranslation"],
        primaryLanguage: "en",
        secondaryLanguages: ["fr", "tw"],
        defaultValue: "",
        sort: true,
        indentation: 4,
        // Disabled: too many false positives from indirect key references
        // (keys stored as string literals in objects/arrays, then passed to t() dynamically).
        // Examples: route handle.subtitleKey, labelKey arrays, STATUS_CONFIG objects.
        // Stale key removal should be done manually.
        removeUnusedKeys: false,
    },
});
