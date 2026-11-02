// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useMemo, useState } from "react";
import { useInfiniteQuery } from "@tanstack/react-query";
import { useDebouncedValue } from "@tanstack/react-pacer";
import { resolveInfiniteRetry, type UserGroupSearchResult } from "@opencop/api";
import type { DrawerRelationServerPaging } from "~/features/drawers/drawer-relation.model";
import { groupQueries } from "./groups.queries";

/**
 * Server-paged, searchable user-group pool for the drawer assign pickers. Fetches a bounded page,
 * narrows it by a debounced server-side `search`, and loads more on scroll.
 *
 * Returns the pool plus a {@link DrawerRelationServerPaging} bundle, so a section can spread this
 * straight into {@link useRelationSection} to drive the picker in server mode.
 */
export interface UserGroupPickerPool {
    pool: UserGroupSearchResult[];
    poolLoading: boolean;
    serverPaging: DrawerRelationServerPaging;
}

export interface UserGroupPickerPoolOptions {
    /** A read-only section never reaches the assign picker, so its pool would be dead weight. */
    enabled?: boolean;
}

export function useUserGroupPickerPool({
    enabled = true,
}: UserGroupPickerPoolOptions = {}): UserGroupPickerPool {
    const [search, setSearch] = useState("");
    const [debouncedSearch] = useDebouncedValue(search, { wait: 300 });
    const query = useInfiniteQuery({ ...groupQueries.infiniteSearch(debouncedSearch), enabled });

    // Memoize on query.data so `pool` stays referentially stable across renders. It is spread
    // into every picker's `unassigned = useMemo(() => pool.filter(...), [pool, ...])`, which would
    // otherwise rebuild a fresh array each render and defeat that memoization.
    const pool = useMemo(
        () => query.data?.pages.flatMap((page) => page.content) ?? [],
        [query.data],
    );

    return {
        pool,
        poolLoading: query.isLoading,
        serverPaging: {
            onSearchChange: setSearch,
            onLoadMore: query.fetchNextPage,
            hasMore: query.hasNextPage,
            isFetchingMore: query.isFetchingNextPage,
            isError: query.isError,
            onRetry: () =>
                resolveInfiniteRetry(pool.length > 0, query.fetchNextPage, query.refetch),
        },
    };
}
