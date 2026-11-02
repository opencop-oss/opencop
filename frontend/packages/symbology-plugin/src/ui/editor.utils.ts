// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { SymbologyTFunction } from "./symbology-i18n";
import type { SIDCLabel } from "@opencop/symbology";
import type { SelectItem } from "@opencop/ui";
import { translateByKey } from "./translate-sidc.util";

export const valueTextProps = {
    color: "fg.muted",
    textStyle: "xs",
    fontWeight: "medium",
    truncate: true,
};

export const textProps = {
    color: "fg.muted",
    textStyle: "xs",
};

export const selectProps = {
    valueTextProps,
    itemTextProps: textProps,
    controlProps: {
        h: "8",
        minH: "0",
        bg: "bg.elevated",
    },
    triggerProps: {
        minH: "0",
    },
    size: "xs" as const,
    borderRadius: "2px",
} as const;

export function items(entries: [string, SIDCLabel][], t?: SymbologyTFunction): SelectItem[] {
    return entries.map(([code, entry]) => ({
        value: code,
        label: t ? translateByKey(entry.localeKey, entry.label, t) : entry.label,
    }));
}
