// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useQuery } from "@tanstack/react-query";
import { layerQueries } from "~/features/layers/layers.queries";
import { dataSourceQueries } from "~/features/data-sources/data-sources.queries";
import { getEditability, type Editability } from "./editability";

const NOT_EDITABLE: Editability = {
    canEditGeometry: false,
    canEditProperties: false,
    canDelete: false,
    canBroadcast: false,
};

/**
 * Returns the editability flags for a feature with the given source.
 * Reads from the cached `myList` queries (layers + data sources) — no extra
 * network requests unless the cache is empty.
 */
export function useEditability(source: string | undefined): Editability {
    const { data: myLayers } = useQuery(layerQueries.myList());
    const { data: myDataSources } = useQuery(dataSourceQueries.myList());

    if (!source || !myLayers || !myDataSources) return NOT_EDITABLE;
    return getEditability(source, myLayers, myDataSources);
}
