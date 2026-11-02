// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ConnectionExtractor, IntegrationCatalogEntry } from "@opencop/api";
import type { TFunction } from "i18next";

/**
 * Translation of `key` in the user's language, else `text` (Core's English), shown as-is.
 *
 * Keep `returnObjects`: without it, a key that lands on a group of keys comes back as the string
 * "key '…' returned an object instead of string.", which would get past the string check.
 */
export function integrationText(text: string, key: string | undefined, t: TFunction): string {
    if (!key) return text;
    const resolved: unknown = t(key as never, {
        defaultValue: text,
        returnObjects: true,
        skipInterpolation: true,
    });
    // i18next can return an object or a function here (e.g. `constructor`). Neither falls back.
    return typeof resolved === "string" && resolved !== "" ? resolved : text;
}

/** An integration's display name. */
export function integrationDisplayName(
    entry: Pick<IntegrationCatalogEntry, "name" | "nameKey">,
    t: TFunction,
): string {
    return integrationText(entry.name, entry.nameKey, t);
}

/** A connection method's display label. */
export function extractorDisplayLabel(
    extractor: Pick<ConnectionExtractor, "label" | "labelKey">,
    t: TFunction,
): string {
    return integrationText(extractor.label, extractor.labelKey, t);
}
