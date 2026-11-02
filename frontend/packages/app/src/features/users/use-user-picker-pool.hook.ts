// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useMemo, useState } from "react";
import { useInfiniteQuery } from "@tanstack/react-query";
import { useDebouncedValue } from "@tanstack/react-pacer";
import { resolveInfiniteRetry, type User } from "@opencop/api";
import type { DrawerRelationServerPaging } from "~/features/drawers/drawer-relation.model";
import { userQueries } from "./users.queries";

/**
 * Server-paged, searchable user pool for the drawer assign pickers. Replaces the
 * old "load the whole user list, filter client-side" pattern: the picker fetches a bounded page,
 * narrows by a debounced server-side `search`, and loads more on scroll.
 *
 * Returns the pool plus a {@link DrawerRelationServerPaging} bundle, so a section can spread this
 * straight into {@link useRelationSection} to drive the picker in server mode.
 */
export interface UserPickerPool {
    pool: User[];
    poolLoading: boolean;
    serverPaging: DrawerRelationServerPaging;
}

export function useUserPickerPool(): UserPickerPool {
    const [search, setSearch] = useState("");
    const [debouncedSearch] = useDebouncedValue(search, { wait: 300 });
    const query = useInfiniteQuery(userQueries.infiniteSearch(debouncedSearch));

    // Memoize on query.data so `pool` is referentially stable across renders — it's spread into
    // every picker's `unassigned = useMemo(() => pool.filter(...), [pool, ...])`, which would
    // otherwise recompute a fresh array each render and defeat downstream memoization.
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
