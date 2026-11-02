// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import i18n from "i18next";
import { initReactI18next } from "react-i18next";
import { addHostFormatters } from "@opencop/ui";
import en from "~/locales/en/translation.json";
import tw from "~/locales/tw/translation.json";
import fr from "~/locales/fr/translation.json";

i18n.use(initReactI18next).init({
    // Only the host's own bundle. A plugin contributes its own namespace at
    // runtime through `ctx.registerTranslations`, which is why none appear here —
    // the host cannot import a plugin.
    resources: {
        en: { translation: en },
        tw: { translation: tw },
        fr: { translation: fr },
    },
    lng: localStorage.getItem("language") ?? "en",
    fallbackLng: "en",
    interpolation: {
        escapeValue: false,
    },
});

addHostFormatters(i18n);

export { i18n };
