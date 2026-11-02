// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import i18next, { type Resource } from "i18next";
import type { TranslationContribution } from "../translation.ts";

/**
 * What a rule needs from the host that a contribution cannot say about itself.
 */
export interface ValidationEnvironment {
    /** Whether a translation key (`namespace:key`, or a key in the default namespace) resolves. */
    readonly translationExists: (key: string) => boolean;
}

/**
 * `translations` as i18next resources: by language, then namespace. A bundle with no
 * resources contributes nothing — a problem its own rule reports.
 */
export function toI18nResources(translations: readonly TranslationContribution[]): Resource {
    const resources: Record<string, Record<string, object>> = {};
    for (const { namespace, resources: byLanguage } of translations)
        for (const [language, bundle] of Object.entries(byLanguage ?? {}))
            (resources[language] ??= {})[namespace] = bundle;
    return resources as Resource;
}

/** The host's default namespace: an unprefixed key is in no contributed bundle unless one is this. */
const DEFAULT_NAMESPACE = "translation";

/**
 * `environment`, with `translations` counted as already registered. A plugin's
 * contributions are validated before any of them register, so its own label keys
 * resolve only against the bundles it contributes alongside them: a key resolves if
 * i18next finds a string for it in any language they carry. A key naming a subtree does
 * not — as a label it renders i18next's "returned an object" message.
 */
export function withContributedTranslations(
    environment: ValidationEnvironment,
    translations: readonly TranslationContribution[] = [],
): ValidationEnvironment {
    if (translations.length === 0) return environment;
    const resources = toI18nResources(translations);
    const languages = Object.keys(resources);
    const probe = i18next.createInstance();
    void probe.init({
        lng: languages[0],
        fallbackLng: false,
        ns: translations.map(({ namespace }) => namespace),
        defaultNS: DEFAULT_NAMESPACE,
        resources,
        initAsync: false,
    });
    return {
        translationExists: (key) =>
            environment.translationExists(key) ||
            languages.some(
                (lng) =>
                    probe.exists(key, { lng }) &&
                    typeof probe.getResource(lng, ...namespaced(key)) === "string",
            ),
    };
}

/** `key` as `[namespace, path]`, as i18next splits it: an unprefixed key is in the default namespace. */
function namespaced(key: string): [string, string] {
    const separator = key.indexOf(":");
    return separator > 0
        ? [key.slice(0, separator), key.slice(separator + 1)]
        : [DEFAULT_NAMESPACE, key];
}
