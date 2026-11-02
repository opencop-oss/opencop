// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import {
    getAuditHistory,
    type AuditEntityType,
    type AuditHistoryItem,
    type Page,
} from "@opencop/api";
import { infiniteQueryOptions } from "@tanstack/react-query";

const AUDIT_HISTORY_PAGE_SIZE = 50;

export const auditQueries = {
    all: ["audit-history"] as const,
    /**
     * One resource's history, newest first.
     *
     * Keyed by type as well as id so two resources that somehow shared an id could not read each
     * other's rows, and so invalidating one type's histories is possible without touching another.
     *
     * Refetches on every mount: only the active drawer section is mounted, so the query gains an
     * observer exactly when an admin opens the history, and an edit made in another section has
     * usually just written a row. Honouring `staleTime` there left that edit invisible until a
     * reload. Writes that land while the section is already open must invalidate
     * {@link auditQueries.all} from their own mutation.
     */
    history: (entityType: AuditEntityType, entityId: string) =>
        infiniteQueryOptions({
            queryKey: [...auditQueries.all, entityType, entityId] as const,
            enabled: entityId !== "",
            queryFn: ({ pageParam, signal }) =>
                getAuditHistory(
                    {
                        entityType,
                        entityId,
                        page: pageParam,
                        size: AUDIT_HISTORY_PAGE_SIZE,
                    },
                    { signal },
                ),
            initialPageParam: 0,
            getNextPageParam: (lastPage: Page<AuditHistoryItem>) => {
                const next = lastPage.page + 1;
                return next < lastPage.totalPages ? next : undefined;
            },
            // Refetch whenever the section opens; see the doc above. That reloads every page
            // already loaded, sequentially from page zero, and all of them are needed: a row
            // written while the section was closed shifts every later offset down, so refetching
            // page zero alone would leave the stale later pages missing the row that moved across
            // the boundary. Refetching the whole sequence is what keeps the joined list free of
            // duplicates and gaps. `staleTime` no longer gates this mount refetch; it still
            // throttles the focus and reconnect ones.
            refetchOnMount: "always",
            staleTime: 30_000,
        }),
};
