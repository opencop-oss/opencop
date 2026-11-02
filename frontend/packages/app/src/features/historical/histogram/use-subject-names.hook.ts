// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useQuery } from "@tanstack/react-query";
import { useCallback, useMemo } from "react";

import { dataSourceQueries } from "~/features/data-sources/data-sources.queries";
import { layerQueries } from "~/features/layers/layers.queries";

export interface SubjectNames {
    /** `subjectId → display name`, resolved from active + archived lists. */
    nameById: ReadonlyMap<string, string>;
    /** Display name for a subject id, falling back to a short id prefix. */
    nameOf: (id: string) => string;
}

/**
 * Resolve display names for historical subjects. A selected subject can be a
 * data source or a layer, and either may be archived, so names come from all
 * four "my" lists (active/archived × source/layer). React Query dedupes these
 * by key, so multiple callers share one fetch.
 */
export function useSubjectNames(): SubjectNames {
    const { data: activeSources } = useQuery(dataSourceQueries.myList());
    const { data: archivedSources } = useQuery(dataSourceQueries.myList(true));
    const { data: activeLayers } = useQuery(layerQueries.myList());
    const { data: archivedLayers } = useQuery(layerQueries.myList(true));

    const nameById = useMemo(() => {
        const map = new Map<string, string>();
        for (const ds of [...(activeSources ?? []), ...(archivedSources ?? [])]) {
            map.set(ds.serverId, ds.serverName);
        }
        for (const l of [...(activeLayers ?? []), ...(archivedLayers ?? [])]) {
            map.set(l.layerId, l.layerName);
        }
        return map;
    }, [activeSources, archivedSources, activeLayers, archivedLayers]);

    const nameOf = useCallback((id: string) => nameById.get(id) ?? id.slice(0, 8), [nameById]);

    return { nameById, nameOf };
}
