// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { i18n as I18n } from "i18next";

/**
 * The formatters the host's i18n instance has, for strings that interpolate with a
 * format.
 *
 * `lowercase`: entity nouns ("Layer", "Data Source") are authored capitalized because
 * they head panel titles, but the same noun has to read as prose mid-sentence in the
 * descriptions below them. i18next ships this formatter's name but resolves it to a
 * no-op until one is registered. A translation that needs different casing rules than
 * `toLowerCase` can always write the noun inline instead of interpolating it.
 */
export function addHostFormatters(i18n: I18n): void {
    i18n.services.formatter?.add("lowercase", (value: string, lng?: string) =>
        value.toLocaleLowerCase(lng),
    );
}
