// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { SymbologyTFunction } from "./symbology-i18n";

export function translateByKey(localeKey: string, fallback: string, t: SymbologyTFunction): string {
    return t(`symbology:${localeKey}`, { defaultValue: fallback });
}
