// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { TranslationContribution } from "@opencop/contributions";
import en from "~/locales/en/translation.json";
import tw from "~/locales/tw/translation.json";
import fr from "~/locales/fr/translation.json";

/**
 * The host's own bundle, as a translation contribution. Native plugins read their strings
 * from it today, so a native's test host loads it — `createTestHost({ translations:
 * [HOST_TRANSLATIONS] })` — to render them as the app does.
 */
export const HOST_TRANSLATIONS: TranslationContribution = {
    namespace: "translation",
    resources: { en, tw, fr },
};
