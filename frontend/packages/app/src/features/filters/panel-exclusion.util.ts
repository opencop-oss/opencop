// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useFilterPanelStore } from "./filter-panel.store";

/**
 * Whether a layer or data source belongs in a picker: not removed from the map panel, unless it
 * is one of the `keep` ids (a value already chosen, which must stay listed so it keeps its name).
 */
export function isOffered(
    excluded: ReadonlySet<string>,
    id: string,
    keep: readonly string[] = [],
): boolean {
    return !excluded.has(id) || keep.includes(id);
}

/** Whether a source should start enabled: neither toggled off nor removed from the panel. */
export function shouldEnableSource(id: string): boolean {
    const { hiddenSources, excludedIds } = useFilterPanelStore.getState();
    return !hiddenSources.includes(id) && !excludedIds.includes(id);
}

/** Whether a data source's display name (its id when unnamed) contains the lower-cased search term. */
export function dataSourceMatchesSearch(
    ds: { serverId: string; serverName: string | null },
    lowerSearch: string,
): boolean {
    return (ds.serverName ?? ds.serverId).toLowerCase().includes(lowerSearch);
}
