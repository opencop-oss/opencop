// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Flex } from "@chakra-ui/react";
import type { ConnectionState } from "@opencop/api";
import {
    ArchivedBadge,
    ColumnFilterBuilder,
    ResourceTable,
    TypeColorLabel,
    createColumnHelper,
    toFilterableColumns,
} from "@opencop/ui";
import type { ResourceTableToolbar, ToolbarAction } from "@opencop/ui";
import { useMutation, useQueries, useQueryClient } from "@tanstack/react-query";
import { useCallback, useMemo, useState, useRef } from "react";
import { useTranslation } from "react-i18next";
import { Navigate, useLocation, useSearchParams } from "react-router";
import { auditColumns } from "~/features/admin/audit-columns.util";
import { ViewDrawerProvider, useViewDrawerContext } from "~/features/drawers/ViewDrawerContext";
import { FaArchive, FaCog, FaDownload, FaFilter, FaPlay, FaPlus, FaStop } from "react-icons/fa";
import { aggregateQueries } from "~/features/admin/aggregate-queries.util";
import { useResourceTableState } from "~/features/admin/use-resource-table-state.hook";
import { useSelectedRows } from "~/features/admin/use-selected-rows.hook";
import { useTableColumnVisibility } from "~/features/admin/use-table-column-visibility.hook";
import { DataSourceDrawer } from "~/features/data-sources/DataSourceDrawer";
import type { DataSourceDrawerState, ModalState } from "~/features/data-sources/DataSourceDrawer";
import { AddDataSourceDialog } from "~/features/data-sources/add-data-source/AddDataSourceDialog";
import {
    ENTITY_ADMIN_PATH,
    ENTITY_BADGE_CONFIG,
    buildMapperNameMap,
    normalizeSource,
} from "~/features/data-sources/data-resources.util";
import type { ResourceRow } from "~/features/data-sources/data-resources.util";
import { profileQueries } from "~/features/data-sources/data-source-profiles.queries";
import {
    dataSourceMutations,
    dataSourceQueries,
} from "~/features/data-sources/data-sources.queries";
import { ConfirmArchiveDialog } from "~/features/membership-management/ConfirmArchiveDialog";
import { ConnectionStatusIndicator } from "~/features/websocket/ConnectionStatusIndicator";
import { formatRelativeTime } from "~/date.util";
import { useUserTimezone } from "~/features/preferences/use-user-timezone.hook";
import { useFilterPanelStore } from "~/features/filters/filter-panel.store";
import { RelatedEntityLink } from "~/features/data-sources/detail/RelatedEntityLink";
import { ExportDataSourcesDialog } from "~/features/data-sources/dialogs/ExportDataSourcesDialog";
import type { NamedExportSubject } from "~/features/exports/exports.util";

const EMPTY_ARRAY: never[] = [];
const columnHelper = createColumnHelper<ResourceRow>();

/** Module-level so the selection hook keeps a stable identity across renders. */
const getResourceRowId = (row: ResourceRow) => row.id;

type AdminDataSourcesViewModal =
    | ModalState
    | { type: "exportDataSources"; subjects: NamedExportSubject[] };

function DataSourceDialogs({
    modalState,
    onClose,
    onDelete,
    finalFocusEl,
}: {
    modalState: AdminDataSourcesViewModal;
    onClose: () => void;
    onDelete: () => void;
    finalFocusEl: () => HTMLElement | null;
}) {
    switch (modalState.type) {
        case "none":
            return null;
        case "addDataSource":
            return <AddDataSourceDialog open onClose={onClose} />;
        case "deleteSource":
            return (
                <ConfirmArchiveDialog
                    isOpen
                    onClose={onClose}
                    name={modalState.dataSource.name}
                    onConfirm={onDelete}
                    finalFocusEl={finalFocusEl}
                />
            );
    }
}

function useSelectionActions(
    selectedRows: ResourceRow[],
    actions: {
        onReconnect: () => void;
        onDisconnect: () => void;
        onExport: () => void;
        onDelete: () => void;
    },
) {
    const { t } = useTranslation();

    const hasSelection = selectedRows.length > 0;
    const allEditable = hasSelection && selectedRows.every((r) => r.editable);
    const hasConnected = selectedRows.some((r) => r.connectionState?.toUpperCase() === "CONNECTED");
    const hasDisconnected = selectedRows.some(
        (r) => r.connectionState?.toUpperCase() !== "CONNECTED",
    );

    return [
        {
            label: t("resourceTable.reconnect"),
            icon: <FaPlay />,
            disabled: !hasDisconnected,
            onClick: actions.onReconnect,
        },
        {
            label: t("resourceTable.disconnect"),
            icon: <FaStop />,
            disabled: !hasConnected,
            onClick: actions.onDisconnect,
        },
        {
            label: t("common.export"),
            icon: <FaDownload />,
            showLabel: true,
            disabled: !hasSelection,
            onClick: actions.onExport,
        },
        {
            label: t("common.archive"),
            icon: <FaArchive />,
            disabled: !allEditable,
            onClick: actions.onDelete,
            confirm: {
                title: t("resourceTable.confirmArchiveTitle"),
                message: t("resourceTable.confirmArchiveMessage", { count: selectedRows.length }),
                isDestructive: false,
            },
        },
    ] satisfies ToolbarAction[];
}

export function AdminDataSourcesView() {
    const [searchParams] = useSearchParams();
    const { search } = useLocation();
    // Send old mapper links to their new page.
    if (searchParams.get("type") === "mapper") {
        return <Navigate replace to={{ pathname: ENTITY_ADMIN_PATH.mapper, search }} />;
    }
    return (
        <ViewDrawerProvider<AdminDataSourcesViewModal> initialModal={{ type: "none" }}>
            <AdminDataSourcesViewContent />
        </ViewDrawerProvider>
    );
}

function AdminDataSourcesViewContent() {
    const { t } = useTranslation();
    const queryClient = useQueryClient();
    const timezone = useUserTimezone();
    const {
        selectedId: drawerEntityId,
        selectedType: rawType,
        openDrawer,
        modal: modalState,
        setModal: setModalState,
    } = useViewDrawerContext<AdminDataSourcesViewModal>();
    // Focus target for dialogs whose trigger unmounts with the drawer (see closeDrawer).
    const tableRef = useRef<HTMLDivElement>(null);

    const [showArchived, setShowArchived] = useState(false);

    // Data fetching
    // Mappers only name each source's own in its links and drawer.
    const [sourcesQuery, mappersQuery] = useQueries({
        queries: [
            dataSourceQueries.adminList(showArchived),
            profileQueries.mapperProfileAdminList(),
        ],
    });

    const dataSources = sourcesQuery.data ?? EMPTY_ARRAY;
    const mappers = mappersQuery.data ?? EMPTY_ARRAY;

    const { isLoading, isFetching, refetchAll } = aggregateQueries([sourcesQuery, mappersQuery]);

    // Mutations
    const { mutateAsync: _deleteDataSource } = useMutation(dataSourceMutations.delete(queryClient));
    const deleteDataSource = useCallback(
        async (id: string) => {
            await _deleteDataSource(id);
            useFilterPanelStore.getState().removeKnownSources([id]);
        },
        [_deleteDataSource],
    );
    const { mutateAsync: disconnect } = useMutation(dataSourceMutations.disconnect(queryClient));
    const { mutateAsync: reconnect } = useMutation(dataSourceMutations.reconnect(queryClient));

    // State
    const tableState = useResourceTableState();

    const mapperNames = useMemo(() => buildMapperNameMap(mappers), [mappers]);

    const rows = useMemo(
        () => dataSources.map((ds) => normalizeSource(ds, mapperNames)),
        [dataSources, mapperNames],
    );

    const filteredRows = useMemo(() => {
        if (showArchived) return rows.filter((r) => r.archived);
        return rows.filter((r) => !r.archived);
    }, [rows, showArchived]);

    // Derive drawer state from live query data
    const drawerState: DataSourceDrawerState = useMemo(() => {
        if (!drawerEntityId || rawType !== "source") return { type: "closed" };
        const match = dataSources.find((ds) => ds.id === drawerEntityId);
        return match ? { type: "source", entity: match } : { type: "closed" };
    }, [drawerEntityId, rawType, dataSources]);

    // Selected rows helper
    // Retains rows once ticked, so narrowing never shrinks what the bulk actions cover.
    const { selectedRows, onRowSelectionChange } = useSelectedRows(
        filteredRows,
        getResourceRowId,
        tableState.rowSelection,
        tableState.onRowSelectionChange,
    );
    const handleRowClick = (row: ResourceRow) => openDrawer(row.id, row.entityType);

    const handleDeleteSelected = async () => {
        await Promise.allSettled(selectedRows.map((r) => deleteDataSource(r.id)));
        tableState.resetSelection();
    };

    const handleBulkDisconnect = async () => {
        await Promise.allSettled(selectedRows.map((r) => disconnect(r.id)));
    };

    const handleBulkReconnect = async () => {
        await Promise.allSettled(selectedRows.map((r) => reconnect(r.id)));
    };

    const handleDeleteFromModal = async () => {
        if (modalState.type !== "deleteSource") return;
        await deleteDataSource(modalState.dataSource.id);
        setModalState({ type: "none" });
    };

    const closeModal = () => setModalState({ type: "none" });

    const selectedSources = useMemo(
        () => selectedRows.map((row) => ({ id: row.id, name: row.name })),
        [selectedRows],
    );

    const selectionActions = useSelectionActions(selectedRows, {
        onReconnect: handleBulkReconnect,
        onDisconnect: handleBulkDisconnect,
        onExport: () => setModalState({ type: "exportDataSources", subjects: selectedSources }),
        onDelete: handleDeleteSelected,
    });

    const toolbar: ResourceTableToolbar = {
        onRefresh: refetchAll,
        isRefreshing: isFetching,
        actions: [
            {
                label: t("admin.addNewDataSource"),
                icon: <FaPlus />,
                showLabel: true,
                disabled: showArchived,
                onClick: () => setModalState({ type: "addDataSource" }),
            } satisfies ToolbarAction,
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
                        onClick: () => {
                            setShowArchived((v) => !v);
                            tableState.resetView();
                        },
                    },
                    {
                        label: t("resourceTable.default"),
                        icon: <FaFilter />,
                        onClick: () => {
                            setShowArchived(false);
                            tableState.resetView();
                        },
                    },
                ],
            },
        ],
        selectionActions,
    };

    const columnVisibility = useTableColumnVisibility("admin-data-sources");

    const columns = useMemo(
        () => [
            columnHelper.accessor("name", {
                header: () => t("resourceTable.columnName"),
                cell: (info) => {
                    const archived = info.row.original.archived;
                    const config = ENTITY_BADGE_CONFIG[info.row.original.entityType];
                    return (
                        <Flex align="center" gap={2}>
                            <TypeColorLabel
                                abbr={config.abbr}
                                color={config.color}
                                label={t(config.labelKey)}
                            />
                            {info.getValue() || "-"}
                            {archived && <ArchivedBadge />}
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
            columnHelper.accessor("statusLabel", {
                header: () => t("resourceTable.columnStatus"),
                cell: (info) => {
                    const row = info.row.original;
                    if (row.connectionState) {
                        return (
                            <ConnectionStatusIndicator
                                state={row.connectionState as ConnectionState}
                                reason={row.stateReason}
                            />
                        );
                    }
                    return info.getValue();
                },
                enableSorting: true,
                meta: {
                    columnLabel: t("resourceTable.columnStatus"),
                    filterDef: {
                        label: t("resourceTable.columnStatus"),
                        type: "enum" as const,
                        options: [
                            { label: t("resourceTable.connectionState.new"), value: "NEW" },
                            {
                                label: t("resourceTable.connectionState.connecting"),
                                value: "CONNECTING",
                            },
                            {
                                label: t("resourceTable.connectionState.connected"),
                                value: "CONNECTED",
                            },
                            {
                                label: t("resourceTable.connectionState.disconnected"),
                                value: "DISCONNECTED",
                            },
                            { label: t("resourceTable.connectionState.stopped"), value: "STOPPED" },
                            { label: t("resourceTable.connectionState.failed"), value: "FAILED" },
                        ],
                    },
                },
                filterFn: "ocEnumFilter",
            }),
            columnHelper.accessor("lastTelemetry", {
                header: () => t("resourceTable.columnLastTelemetry"),
                cell: (info) => {
                    const val = info.getValue();
                    return val ? formatRelativeTime(new Date(val), timezone) : "-";
                },
                enableSorting: true,
                meta: { columnLabel: t("resourceTable.columnLastTelemetry") },
            }),
            columnHelper.accessor("usesLabel", {
                header: () => t("resourceTable.uses"),
                cell: (info) => {
                    const row = info.row.original;
                    if (row.uses.length === 0) return "-";
                    return (
                        <Flex gap={2} flexWrap="wrap" align="center">
                            {row.uses.map((rel) => (
                                <RelatedEntityLink
                                    key={`${rel.type}-${rel.id}`}
                                    id={rel.id}
                                    name={rel.name}
                                    type={rel.type}
                                    typeLabel={
                                        rel.egress ? t("connectionSetup.egressMapper") : undefined
                                    }
                                    // Keep the click from also opening this row's drawer.
                                    onClick={(e) => e.stopPropagation()}
                                />
                            ))}
                        </Flex>
                    );
                },
                enableSorting: true,
                meta: { columnLabel: t("resourceTable.uses") },
            }),
            ...auditColumns<ResourceRow>(t, timezone, "dataSource"),
        ],
        [t, timezone],
    );

    const filterableColumns = useMemo(() => toFilterableColumns(columns), [columns]);

    const filterBarEl = (
        <ColumnFilterBuilder
            filterableColumns={filterableColumns}
            value={tableState.columnFilters}
            onChange={tableState.onColumnFiltersChange}
            searchValue={tableState.globalFilter}
            onSearchChange={tableState.onGlobalFilterChange}
            searchPlaceholder={t("resourceTable.filterForAnyField")}
        />
    );

    return (
        <Flex
            height="100%"
            width="100%"
            direction="column"
            borderRadius="3xl"
            bg="bg"
            ref={tableRef}
            tabIndex={-1}
            outline="none"
            // Inset ring: these containers clip at their own edge, so a ring drawn outside
            // would be cropped. Only shows after a keyboard confirm, per :focus-visible.
            _focusVisible={{
                outline: "2px solid",
                outlineColor: "brand.focusRing",
                outlineOffset: "-2px",
            }}
        >
            <ResourceTable<ResourceRow>
                aria-label={t("adminNav.dataSource")}
                data={filteredRows}
                columns={columns}
                isLoading={isLoading}
                isFetching={isFetching}
                enableRowSelection
                getRowId={getResourceRowId}
                {...tableState}
                {...columnVisibility}
                onRowSelectionChange={onRowSelectionChange}
                selectedCount={selectedRows.length}
                onRowClick={handleRowClick}
                toolbar={toolbar}
                filterBar={filterBarEl}
                detailDrawer={
                    <DataSourceDrawer drawerState={drawerState} mapperNames={mapperNames} />
                }
            />
            <DataSourceDialogs
                finalFocusEl={() => tableRef.current}
                modalState={modalState}
                onClose={closeModal}
                onDelete={handleDeleteFromModal}
            />
            {modalState.type === "exportDataSources" && (
                <ExportDataSourcesDialog
                    isOpen
                    onClose={closeModal}
                    subjects={modalState.subjects}
                    title={t("adminNav.dataSource")}
                />
            )}
        </Flex>
    );
}
