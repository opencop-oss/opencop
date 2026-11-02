// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { SIDC } from "@opencop/symbology";
import { v4 as uuid } from "uuid";

export interface HistoryEntry {
    id: string;
    sidc: string;
    modifiers: Record<string, string>;
    label?: string;
}

// History persists through the plugin's `PluginStorage` — host-namespaced under the
// plugin id and durable across uninstall (a contract guarantee), so operator data
// survives an entitlement change or reinstall. These are the storage-relative key
// names; the host prefixes them with the plugin's namespace.
export const SYMBOL_HISTORY_STORAGE_KEY = "symbol-history";

// Pre-plugin raw-localStorage locations, read ONLY by history-migration.ts to
// adopt existing operator data into the storage above. Delete alongside that
// module when the migration window closes.
export const LEGACY_KEY_SYMBOL_HISTORY = "opencop:symbol-history";
export const LEGACY_STORAGE_KEY_FAVORITES = "opencop:symbol-favorites";
export const LEGACY_STORAGE_KEY_RECENTS = "opencop:symbol-recents";

export function createHistoryEntry(
    sidc: SIDC | string,
    modifiers: Record<string, string>,
    label?: string,
): HistoryEntry {
    const sidcString = typeof sidc === "string" ? sidc : sidc.toString();

    return {
        id: uuid(),
        sidc: sidcString,
        modifiers: { ...modifiers },
        label,
    };
}

export function parseSIDC(sidcString: string): SIDC | null {
    return SIDC.tryFromString(sidcString);
}

export function filterHistoryEntries(entries: HistoryEntry[], searchTerm: string): HistoryEntry[] {
    if (!searchTerm) return entries;
    const lowerSearch = searchTerm.toLowerCase();
    return entries.filter((entry) => {
        const label = entry.label ?? entry.sidc;
        return label.toLowerCase().includes(lowerSearch);
    });
}
