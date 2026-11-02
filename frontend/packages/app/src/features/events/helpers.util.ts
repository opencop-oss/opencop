// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { DateTime } from "luxon";

/* export const EXCLUDED_KEYS = new Set([
    "element",
    "timestampz",
    "attributes",
    "contacts",
    "extensions",
    "remarks",
]); */

export function formatKey(key: string): string {
    return key
        .replace(/([A-Z])/g, " $1")
        .replace(/^./, (str) => str.toUpperCase())
        .trim();
}

export function formatNumber(num: number): string {
    if (Number.isInteger(num)) return num.toString();
    return num
        .toFixed(6)
        .replace(/(\.[1-9]*)0+$/, "$1") // match trailing decimal 0s
        .replace(/\.$/, ""); // match a trailing decimal point.
}

export function isColorValue(value: string): boolean {
    return /^#([0-9A-Fa-f]{3,8})$/.test(value) || /^rgba?\(/.test(value) || /^hsla?\(/.test(value);
}

export function formatTimestamp(ms: number, timezone?: string): string {
    const dt = DateTime.fromMillis(ms);
    return (timezone ? dt.setZone(timezone) : dt).toLocaleString(DateTime.DATETIME_SHORT);
}

/*export function getUngroupedProperties(details: EventDetails): DisplayProperty[] {
    const groupedKeys = new Set([
        ...PROPERTY_GROUPS.identity.keys,
        ...EXCLUDED_KEYS,
    ]);

    return Object.entries(details)
        .filter(([key, value]) => {
            if (groupedKeys.has(key)) return false;
            if (typeof value === "function") return false;
            if (Array.isArray(value) && value.length === 0) return false;
            return true;
        })
        .map(([key, value]) => ({
            key,
            value,
            formatted: formatKey(key),
        }));
}*/
