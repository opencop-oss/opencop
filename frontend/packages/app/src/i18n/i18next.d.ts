// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import "i18next";
import en from "../locales/en/translation.json";

declare module "i18next" {
    interface CustomTypeOptions {
        defaultNS: "translation";
        resources: {
            translation: typeof en;
            /**
             * A namespace contributed at runtime by the plugin that owns it, so its
             * keys cannot be known here — the host must not import a plugin, and a
             * runtime-loaded namespace has no compile-time shape from this side.
             *
             * Declared permissively so keys in that namespace resolve; the plugin's own
             * key safety comes from its bundle, not from this augmentation. Values are
             * `string`, so translating a key in this namespace returns a string (with
             * `unknown` it returns `never`, which hides misuse). Follow-up: give the
             * plugin a locally-typed `t` so its keys are checked again.
             */
            symbology: Record<string, string>;
            /** As `symbology` above — contributed at runtime by the textbox plugin. */
            textbox: Record<string, string>;
        };
        returnNull: false;
    }
}
