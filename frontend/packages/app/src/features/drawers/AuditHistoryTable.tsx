// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Button, Flex, HStack, Text } from "@chakra-ui/react";
import {
    ColumnFilterBuilder,
    type ColumnFiltersState,
    ResourceTable,
    toFilterableColumns,
} from "@opencop/ui";
import type { SortingState } from "@tanstack/react-table";
import { type ReactElement, useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useUserTimezone } from "~/features/preferences/use-user-timezone.hook";
import type { AnyColumns } from "./drawer-relation.model";
import type { AuditHistoryPaging } from "./use-audit-history-paging.hook";

/** The strings a history reader owns, because its empty and failure states name its resource. */
export interface AuditHistoryTableLabels {
    /** Accessible name for the table. */
    table: string;
    /** The query succeeded and this resource has never been administered. */
    empty: string;
    /** The query failed with nothing in hand — we do not know whether there is history. */
    unavailable: string;
    /** A background refresh failed; the rows on screen are still the last good ones. */
    refreshFailed: string;
    /** Rows shifted under the offset sequence while it was being paged. */
    changedDuringPaging: string;
    loadMore: string;
}

interface AuditHistoryTableProps<TItem extends { id: number }> {
    paging: AuditHistoryPaging<TItem>;
    columns: AnyColumns<TItem>;
    labels: AuditHistoryTableLabels;
}

/**
 * A history as a {@link ResourceTable}, with its drift banner and paging footer.
 *
 * Further pages load from the footer button. Sorting, searching and filtering run over the loaded
 * rows, so the rest of the ledger is fetched as soon as any of them is active — otherwise they
 * would rank and match only whichever pages happened to be in hand.
 *
 * Assumes one resource's history stays small (low hundreds): every row is rendered at once, since
 * the table only virtualizes in infinite mode, and a reopen refetches every cached page. A ledger
 * that could grow past that needs server-side search and sort instead.
 *
 * Takes the paging state rather than a query so the hazard handling in
 * {@link useAuditHistoryPaging} stays in one place while each reader supplies its own columns.
 */
export function AuditHistoryTable<TItem extends { id: number }>({
    paging,
    columns,
    labels,
}: AuditHistoryTableProps<TItem>): ReactElement {
    const { t } = useTranslation();
    const timezone = useUserTimezone();
    const {
        items,
        displayedTotal,
        isPending,
        isUnavailable,
        isRefetching,
        isRefetchError,
        isFetchingNextPage,
        isFetchNextPageError,
        hasNextPage,
        showPagingRecovery,
        pagingRecoveryState,
        refetch,
        loadNextPage,
        autoLoadNextPage,
        restartFromFirstPage,
        refreshButtonRef,
        pagingButtonRef,
        loadedCountRef,
    } = paging;

    const [globalFilter, setGlobalFilter] = useState("");
    const [columnFilters, setColumnFilters] = useState<ColumnFiltersState>([]);
    const [sorting, setSorting] = useState<SortingState>([]);
    const filterableColumns = useMemo(() => toFilterableColumns(columns), [columns]);
    const needsEveryRow = globalFilter !== "" || columnFilters.length > 0 || sorting.length > 0;

    // Not while a drift or paging error is showing: auto-retrying would loop on a failing page
    // and would race the recovery action that owns the paging state.
    const canAutoLoad =
        hasNextPage &&
        !isFetchingNextPage &&
        !isFetchNextPageError &&
        !isRefetching &&
        !showPagingRecovery;

    useEffect(() => {
        if (needsEveryRow && canAutoLoad) autoLoadNextPage();
    }, [needsEveryRow, canAutoLoad, autoLoadNextPage]);

    if (isPending) return <Text fontSize="sm">{t("common.loading")}</Text>;
    // Without this branch a failed request renders as "no history", telling the admin this
    // resource has never been administered when the truth is that we do not know.
    if (isUnavailable) {
        return (
            <Text fontSize="sm" color="fg.error">
                {labels.unavailable}
            </Text>
        );
    }

    if (items.length === 0) {
        return (
            <Text fontSize="sm" color="fg.muted">
                {labels.empty}
            </Text>
        );
    }

    return (
        // Rows scroll, not the panel, so the heading and column headers stay put.
        <Flex direction="column" flex="1" minH={0} gap={3}>
            {isRefetchError && !showPagingRecovery && (
                <HStack justify="space-between" align="center" flexShrink={0}>
                    <Text fontSize="xs" color="fg.error" role="alert">
                        {labels.refreshFailed}
                    </Text>
                    <Button
                        size="xs"
                        variant="outline"
                        loading={isRefetching}
                        loadingText={t("common.retry")}
                        onClick={refetch}
                    >
                        {t("common.retry")}
                    </Button>
                </HStack>
            )}
            <ResourceTable
                transparent
                aria-label={labels.table}
                data={items}
                columns={columns}
                getRowId={(item) => String(item.id)}
                sorting={sorting}
                onSortingChange={setSorting}
                globalFilter={globalFilter}
                onGlobalFilterChange={setGlobalFilter}
                columnFilters={columnFilters}
                onColumnFiltersChange={setColumnFilters}
                isFetching={isFetchingNextPage || isRefetching}
                scrollRegionLabel={t("audit.rowsRegion")}
                // While narrowed, the table's own footer reports matched rows of the total.
                totalCount={needsEveryRow ? displayedTotal : undefined}
                timezone={timezone}
                // The search and filter bar render only inside the toolbar section.
                toolbar={{}}
                filterBar={
                    filterableColumns.length > 0 ? (
                        <ColumnFilterBuilder
                            filterableColumns={filterableColumns}
                            value={columnFilters}
                            onChange={setColumnFilters}
                            searchValue={globalFilter}
                            onSearchChange={setGlobalFilter}
                            searchPlaceholder={t("resourceTable.filterForAnyField")}
                        />
                    ) : undefined
                }
            />
            {showPagingRecovery && (
                <HStack justify="space-between" align="center" flexShrink={0}>
                    <Text
                        fontSize="xs"
                        color={pagingRecoveryState === "failed" ? "fg.error" : "fg.warning"}
                        role={pagingRecoveryState === "failed" ? "alert" : "status"}
                    >
                        {pagingRecoveryState === "failed"
                            ? labels.refreshFailed
                            : labels.changedDuringPaging}
                    </Text>
                    <Button
                        ref={refreshButtonRef}
                        size="xs"
                        variant="outline"
                        loading={pagingRecoveryState === "pending" || isRefetching}
                        loadingText={
                            pagingRecoveryState === "failed"
                                ? t("common.retry")
                                : t("common.refresh")
                        }
                        onClick={restartFromFirstPage}
                    >
                        {pagingRecoveryState === "failed" ? t("common.retry") : t("common.refresh")}
                    </Button>
                </HStack>
            )}
            <HStack justify="space-between" align="center" flexShrink={0}>
                {!needsEveryRow && (
                    <Text
                        ref={loadedCountRef}
                        tabIndex={-1}
                        fontSize="xs"
                        color="fg.muted"
                        aria-live="polite"
                        aria-atomic="true"
                    >
                        {t("resourceTable.loadedCount", {
                            loaded: items.length,
                            total: displayedTotal,
                        })}
                    </Text>
                )}
                {!showPagingRecovery && (isFetchNextPageError || hasNextPage) && (
                    <HStack gap={2} ms="auto">
                        {isFetchNextPageError && (
                            <Text key="paging-error" fontSize="xs" color="fg.error" role="alert">
                                {t("resourceTable.loadMoreError")}
                            </Text>
                        )}
                        <Button
                            key="paging-action"
                            ref={pagingButtonRef}
                            size="xs"
                            variant="outline"
                            loading={isFetchingNextPage}
                            loadingText={isFetchNextPageError ? t("common.retry") : labels.loadMore}
                            disabled={isRefetching}
                            onClick={loadNextPage}
                        >
                            {isFetchNextPageError ? t("common.retry") : labels.loadMore}
                        </Button>
                    </HStack>
                )}
            </HStack>
        </Flex>
    );
}
