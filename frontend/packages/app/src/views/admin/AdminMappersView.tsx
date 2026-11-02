// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Flex, VisuallyHidden } from "@chakra-ui/react";
import {
    ArchivedBadge,
    ColumnFilterBuilder,
    ResourceTable,
    TypeColorLabel,
    createColumnHelper,
    toFilterableColumns,
} from "@opencop/ui";
import type { ResourceTableToolbar } from "@opencop/ui";
import type { DataSourceInfo } from "@opencop/api";
import { useQueries } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { FaArchive, FaCog, FaFilter, FaPlus } from "react-icons/fa";
import { useNavigate } from "react-router";
import { aggregateQueries } from "~/features/admin/aggregate-queries.util";
import { auditColumns } from "~/features/admin/audit-columns.util";
import { useResourceTableState } from "~/features/admin/use-resource-table-state.hook";
import { useTableColumnVisibility } from "~/features/admin/use-table-column-visibility.hook";
import { MapperDrawer } from "~/features/data-sources/MapperDrawer";
import type { MapperDrawerState } from "~/features/data-sources/MapperDrawer";
import {
    ENTITY_BADGE_CONFIG,
    MAPPER_BUILDER_PATH,
    connectionEgressMapperId,
    normalizeMapper,
} from "~/features/data-sources/data-resources.util";
import type { RelatedEntity, ResourceRow } from "~/features/data-sources/data-resources.util";
import { profileQueries } from "~/features/data-sources/data-source-profiles.queries";
import { dataSourceQueries } from "~/features/data-sources/data-sources.queries";
import { UsedBySources } from "~/features/data-sources/UsedBySources";
import { ViewDrawerProvider, useViewDrawerContext } from "~/features/drawers/ViewDrawerContext";
import { useUserTimezone } from "~/features/preferences/use-user-timezone.hook";
import { UsedByLoadError } from "~/features/data-sources/detail/UsedByLoadError";

const EMPTY_ARRAY: never[] = [];
const columnHelper = createColumnHelper<ResourceRow>();

const getResourceRowId = (row: ResourceRow) => row.id;

/** The sources using each mapper, keyed by its id. */
function sourcesByMapper(sources: readonly DataSourceInfo[]): Map<string, RelatedEntity[]> {
    const byId = new Map<string, RelatedEntity[]>();
    const add = (id: string | undefined, entity: RelatedEntity) => {
        if (!id) return;
        const list = byId.get(id);
        if (list) list.push(entity);
        else byId.set(id, [entity]);
    };
    for (const ds of sources) {
        const entity = { id: ds.id, name: ds.name, type: "source" as const };
        add(ds.setup?.mapperId ?? undefined, entity);
        add(connectionEgressMapperId(ds.setup), entity);
    }
    return byId;
}

/** Admin table of mappers. */
export function AdminMappersView() {
    return (
        <ViewDrawerProvider initialModal={{ type: "none" }}>
            <AdminMappersViewContent />
        </ViewDrawerProvider>
    );
}

function AdminMappersViewContent() {
    const { t } = useTranslation();
    const navigate = useNavigate();
    const timezone = useUserTimezone();
    const {
        selectedId: drawerEntityId,
        selectedType: rawType,
        openDrawer,
    } = useViewDrawerContext();

    const [showArchived, setShowArchived] = useState(false);

    // Sources, active and archived, only feed Used By.
    const [sourcesQuery, archivedSourcesQuery, mappersQuery] = useQueries({
        queries: [
            dataSourceQueries.adminList(),
            dataSourceQueries.adminList(true),
            profileQueries.mapperProfileAdminList(),
        ],
    });

    const sources = sourcesQuery.data ?? EMPTY_ARRAY;
    const archivedSources = archivedSourcesQuery.data ?? EMPTY_ARRAY;
    const mappers = mappersQuery.data ?? EMPTY_ARRAY;

    const { isLoading, isFetching, refetchAll } = aggregateQueries([
        sourcesQuery,
        archivedSourcesQuery,
        mappersQuery,
    ]);
    // Only a list that never loaded fails: a failed background refresh keeps the last good data.
    const isListError = mappersQuery.isLoadingError;
    // Rows stay valid without sources; only their Used By cells are unknown.
    const isUsageError = sourcesQuery.isLoadingError || archivedSourcesQuery.isLoadingError;

    const tableState = useResourceTableState();

    const usedBy = useMemo(() => sourcesByMapper(sources), [sources]);
    const archivedUsedBy = useMemo(() => sourcesByMapper(archivedSources), [archivedSources]);

    const rows = useMemo(
        () => mappers.map((m) => normalizeMapper(m, usedBy.get(m.id) ?? [], t)),
        [mappers, usedBy, t],
    );

    const visibleRows = useMemo(
        () => (isListError ? EMPTY_ARRAY : rows.filter((r) => r.archived === showArchived)),
        [rows, showArchived, isListError],
    );

    const drawerState: MapperDrawerState = useMemo(() => {
        const match =
            rawType === "mapper" && drawerEntityId
                ? mappers.find((m) => m.id === drawerEntityId)
                : undefined;
        return match ? { type: "mapper", entity: match } : { type: "closed" };
    }, [drawerEntityId, rawType, mappers]);

    const resetView = (archived: boolean) => {
        setShowArchived(archived);
        tableState.resetView();
    };

    const toolbar: ResourceTableToolbar = {
        onRefresh: refetchAll,
        isRefreshing: isFetching,
        actions: [
            {
                label: t("common.create"),
                icon: <FaPlus />,
                showLabel: true,
                disabled: showArchived,
                onClick: () => navigate(`${MAPPER_BUILDER_PATH}/new`),
            },
            {
                label: t("resourceTable.manageView"),
                icon: <FaCog />,
                showLabel: true,
                menuItems: [
                    {
                        label: showArchived
                            ? t("resourceTable.hideArchived")
                            : t("resourceTable.showArchived"),
                        icon: <FaArchive />,
                        onClick: () => resetView(!showArchived),
                    },
                    {
                        label: t("resourceTable.default"),
                        icon: <FaFilter />,
                        onClick: () => resetView(false),
                    },
                ],
            },
        ],
    };

    const columnVisibility = useTableColumnVisibility("admin-mappers");

    const columns = useMemo(
        () => [
            columnHelper.accessor("name", {
                header: () => t("resourceTable.columnName"),
                cell: (info) => {
                    const config = ENTITY_BADGE_CONFIG[info.row.original.entityType];
                    return (
                        <Flex align="center" gap={2}>
                            <TypeColorLabel
                                abbr={config.abbr}
                                color={config.color}
                                label={t(config.labelKey)}
                            />
                            {info.getValue() || "-"}
                            {info.row.original.archived && <ArchivedBadge />}
                        </Flex>
                    );
                },
                enableSorting: true,
                enableHiding: false,
                meta: {
                    filterDef: {
                        label: t("resourceTable.columnName"),
                        type: "string" as const,
                    },
                },
                filterFn: "ocStringFilter",
            }),
            columnHelper.accessor((row) => row.usedBy.map((rel) => rel.name).join(", "), {
                id: "usedBy",
                header: () => t("admin.usedBy"),
                cell: (info) => {
                    if (isUsageError) {
                        return <UsedByLoadError />;
                    }
                    const { id, usedBy } = info.row.original;
                    const archived = archivedUsedBy.get(id) ?? EMPTY_ARRAY;
                    if (usedBy.length === 0 && archived.length === 0) return "-";
                    return (
                        <UsedBySources
                            active={usedBy}
                            archived={archived}
                            compact
                            // Keep the click from also opening this row's drawer.
                            onLinkClick={(e) => e.stopPropagation()}
                        />
                    );
                },
                enableSorting: true,
                meta: { columnLabel: t("admin.usedBy") },
            }),
            ...auditColumns<ResourceRow>(t, timezone, "mapper"),
        ],
        [t, timezone, isUsageError, archivedUsedBy],
    );

    const filterableColumns = useMemo(() => toFilterableColumns(columns), [columns]);

    return (
        <Flex height="100%" width="100%" direction="column" borderRadius="3xl" bg="bg">
            {/* Mounted before it has anything to say, so screen readers observe the text change;
                one announcement for the page rather than one per row. */}
            <VisuallyHidden role="status">
                {isUsageError ? t("admin.usedByLoadErrorStatus") : ""}
            </VisuallyHidden>
            <ResourceTable<ResourceRow>
                aria-label={t("adminNav.mappers")}
                data={visibleRows}
                columns={columns}
                isLoading={isLoading}
                isFetching={isFetching}
                isError={isListError}
                onRetry={refetchAll}
                getRowId={getResourceRowId}
                {...tableState}
                {...columnVisibility}
                onRowClick={(row) => openDrawer(row.id, row.entityType)}
                toolbar={toolbar}
                filterBar={
                    <ColumnFilterBuilder
                        filterableColumns={filterableColumns}
                        value={tableState.columnFilters}
                        onChange={tableState.onColumnFiltersChange}
                        searchValue={tableState.globalFilter}
                        onSearchChange={tableState.onGlobalFilterChange}
                        searchPlaceholder={t("resourceTable.filterForAnyField")}
                    />
                }
                detailDrawer={
                    <MapperDrawer
                        drawerState={drawerState}
                        usedBy={usedBy}
                        archivedUsedBy={archivedUsedBy}
                        isUsageError={isUsageError}
                    />
                }
            />
        </Flex>
    );
}
