// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { infiniteQueryOptions, keepPreviousData } from "@tanstack/react-query";
import {
    getLayerDirectoryPage,
    type FilterParam,
    type LayerDirectoryEntry,
    type Page,
    type RecordVisibility,
    type SortParam,
} from "@opencop/api";

/** Page size for the server-paged admin Layers (layer-directory) grid. */
const LAYER_DIRECTORY_PAGE_SIZE = 25;

export interface LayerDirectoryListParams {
    /** Free-text substring search across the entry name. */
    search?: string;
    /** ACTIVE → active layers + groups; ARCHIVED → only archived layers. */
    recordVisibility?: RecordVisibility;
    /** Spring sort tokens, e.g. `["name,asc"]`. */
    sort?: SortParam[];
    /** Per-column filter clauses, e.g. `["type,equals,LAYER"]`. */
    filter?: FilterParam[];
    /** Viewer's IANA zone, so Created filters match the dates the grid renders. */
    timezone?: string;
}

export const layerDirectoryQueries = {
    all: ["layer-directory"] as const,
    lists: () => [...layerDirectoryQueries.all, "list"] as const,
    /**
     * Server-paged combined layers+layer-groups grid. Filters/sorts/paginates in the DB so the
     * admin Layers view loads a bounded page; scroll fetches the next page.
     */
    infiniteList: (params: LayerDirectoryListParams = {}) =>
        infiniteQueryOptions({
            queryKey: [...layerDirectoryQueries.lists(), params] as const,
            queryFn: ({ pageParam, signal }) =>
                getLayerDirectoryPage(
                    { ...params, page: pageParam, size: LAYER_DIRECTORY_PAGE_SIZE },
                    { signal },
                ),
            initialPageParam: 0,
            getNextPageParam: (lastPage: Page<LayerDirectoryEntry>) => {
                const next = lastPage.page + 1;
                return next < lastPage.totalPages ? next : undefined;
            },
            // Keep the current rows visible while a new filter/search/sort page loads,
            // so the grid doesn't flash its skeleton on every debounced change.
            placeholderData: keepPreviousData,
        }),
};
