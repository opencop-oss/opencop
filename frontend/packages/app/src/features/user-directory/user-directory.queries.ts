// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { infiniteQueryOptions, keepPreviousData } from "@tanstack/react-query";
import {
    getUserDirectoryPage,
    type UserDirectoryEntry,
    type FilterParam,
    type Page,
    type SortParam,
} from "@opencop/api";

const USER_DIRECTORY_PAGE_SIZE = 25;
export interface UserDirectoryListParams {
    search?: string;
    sort?: SortParam[];
    /** Per-column filter clauses; see `toUserDirectoryFilterParams`. Each one narrows the result. */
    filter?: FilterParam[];
    /** Viewer's IANA zone, so Created filters match the dates the grid renders. */
    timezone?: string;
}

export const userDirectoryQueries = {
    all: ["user-directory"] as const,
    lists: () => [...userDirectoryQueries.all, "list"] as const,
    /**
     * Server-paged combined users+groups grid. Filters/sorts/paginates in the DB so the
     * admin Users view loads a bounded page; scroll fetches the next page.
     *
     * `params` is part of the query key, so a changed search, sort, or filter set restarts at page 0
     * rather than appending to the previous results.
     */
    infiniteList: (params: UserDirectoryListParams = {}) =>
        infiniteQueryOptions({
            queryKey: [...userDirectoryQueries.lists(), params] as const,
            queryFn: ({ pageParam, signal }) =>
                getUserDirectoryPage(
                    { ...params, page: pageParam, size: USER_DIRECTORY_PAGE_SIZE },
                    { signal },
                ),
            initialPageParam: 0,
            getNextPageParam: (lastPage: Page<UserDirectoryEntry>) => {
                const next = lastPage.page + 1;
                return next < lastPage.totalPages ? next : undefined;
            },
            placeholderData: keepPreviousData,
        }),
};
