// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import {
    type QueryClient,
    infiniteQueryOptions,
    keepPreviousData,
    mutationOptions,
    queryOptions,
} from "@tanstack/react-query";
import {
    getUserById,
    getUsersPage,
    getAssignedLayers,
    getAssignedDataSources,
    updateUserAdmin,
} from "@opencop/api";
import type { Page, User } from "@opencop/api";
import { userDirectoryQueries } from "~/features/user-directory/user-directory.queries";

const USER_PICKER_PAGE_SIZE = 25;

export const userQueries = {
    all: ["users"] as const,
    details: () => [...userQueries.all, "detail"] as const,
    detail: (id: string) => [...userQueries.details(), id] as const,
    searches: () => [...userQueries.all, "search"] as const,
    /**
     * Server-paged, searchable user pool for the drawer assign pickers.
     * Loads a bounded page and narrows by `search` server-side; scroll fetches the next page.
     */
    infiniteSearch: (search: string) =>
        infiniteQueryOptions({
            queryKey: [...userQueries.searches(), search] as const,
            queryFn: ({ pageParam, signal }) =>
                getUsersPage(
                    {
                        search: search || undefined,
                        page: pageParam,
                        size: USER_PICKER_PAGE_SIZE,
                        sort: ["username,asc"],
                    },
                    { signal },
                ),
            initialPageParam: 0,
            getNextPageParam: (lastPage: Page<User>) => {
                const next = lastPage.page + 1;
                return next < lastPage.totalPages ? next : undefined;
            },
            // Keep the current results visible while a new search page loads, so the
            // picker list doesn't flash its skeleton on every debounced keystroke.
            placeholderData: keepPreviousData,
        }),
    byId: (id: string) =>
        queryOptions({
            queryKey: [...userQueries.detail(id)] as const,
            queryFn: ({ signal }) => getUserById(id, { signal }),
            enabled: !!id,
        }),
    assignedLayers: (id: string) =>
        queryOptions({
            queryKey: [...userQueries.detail(id), "layers"] as const,
            queryFn: ({ signal }) => getAssignedLayers(id, { signal }),
            enabled: !!id,
        }),
    assignedDataSources: (id: string) =>
        queryOptions({
            queryKey: [...userQueries.detail(id), "dataSources"] as const,
            queryFn: ({ signal }) => getAssignedDataSources(id, { signal }),
            enabled: !!id,
        }),
};

export const userMutations = {
    updateAdmin: (queryClient: QueryClient) =>
        mutationOptions({
            mutationKey: [...userQueries.all, "updateAdmin"],
            mutationFn: ({ userId, isAdmin }: { userId: string; isAdmin: boolean }) =>
                updateUserAdmin(userId, isAdmin),
            onSuccess: (_data, { userId }) => {
                // Keep every cache of this user consistent: the picker pools (searches), the
                // drawer's by-id record, and the admin Users grid. searches() replaces the
                // former lists() key, which no live query subscribed to.
                queryClient.invalidateQueries({ queryKey: userQueries.searches() });
                queryClient.invalidateQueries({ queryKey: userQueries.detail(userId) });
                queryClient.invalidateQueries({ queryKey: userDirectoryQueries.all });
            },
        }),
};
