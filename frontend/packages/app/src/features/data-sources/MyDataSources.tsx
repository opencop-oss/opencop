// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Flex, HStack, Text } from "@chakra-ui/react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
    ArchivedBadge,
    ColumnFilterBuilder,
    OC_FILTER_FNS,
    ResourceTable,
    createColumnHelper,
} from "@opencop/ui";
import type { FilterableColumn, ResourceTableToolbar } from "@opencop/ui";
import { useState, useMemo, useCallback, useRef } from "react";
import { ViewDrawerProvider, useViewDrawerContext } from "~/features/drawers/ViewDrawerContext";
import { useTranslation } from "react-i18next";
import {
    FaArchive,
    FaCog,
    FaDownload,
    FaFilter,
    FaPlay,
    FaPlus,
    FaStop,
    FaTrash,
} from "react-icons/fa";
import { DataSourceRoles } from "@opencop/api";
import type { DataSourceRole, MyDataSource } from "@opencop/api";
import {
    dataSourceMutations,
    dataSourceQueries,
} from "~/features/data-sources/data-sources.queries";
import { ConnectionStatusIndicator } from "~/features/websocket/ConnectionStatusIndicator";
import { AddDataSourceDialog } from "~/features/data-sources/add-data-source/AddDataSourceDialog";
import { ConfirmArchiveDialog } from "~/features/membership-management/ConfirmArchiveDialog";
import { ExportDataSourcesDialog } from "~/features/data-sources/dialogs/ExportDataSourcesDialog";
import { canExportDataSource } from "~/features/exports/export-access.util";
import type { NamedExportSubject } from "~/features/exports/exports.util";
import { MyDataSourceDrawer } from "~/features/data-sources/MyDataSourceDrawer";
import type {
    MyDataSourceDrawerState,
    MyDataSourceModalState,
} from "~/features/data-sources/MyDataSourceDrawer";
import { RoleInfoTooltip } from "~/features/layer-groups/RoleInfoTooltip";
import { useToast } from "~/features/notifications/use-toast.hook";
import { usePanelExclusionActions } from "~/features/filters/panel-exclusion.actions";
import { usePanelExcludedIds } from "~/features/filters/use-panel-excluded-ids.hook";
import { PanelExclusionIcon } from "~/features/filters/PanelExclusionIcon";
import { PanelExclusionIndicator } from "~/features/filters/PanelExclusionIndicator";
import { useFilterPanelStore } from "~/features/filters/filter-panel.store";
import {
    DATA_SOURCE_ROLE_DESCRIPTIONS,
    isConnected,
} from "~/features/data-sources/data-sources.util";
import { useResourceTableState } from "~/features/admin/use-resource-table-state.hook";
import { useSelectedRows } from "~/features/admin/use-selected-rows.hook";
import { useTableColumnVisibility } from "~/features/admin/use-table-column-visibility.hook";
import { auditColumns } from "~/features/admin/audit-columns.util";
import { useUserTimezone } from "~/features/preferences/use-user-timezone.hook";
import { useIsAdmin } from "~/features/auth/use-is-admin.hook";
import { SourceSummaryAccessMode } from "~/features/data-sources/SourceSummaryAccessMode";

interface DataSourceRow {
    id: string;
    name: string;
    transportType: MyDataSource["transportType"];
    access: MyDataSource["access"];
    state: string;
    role: DataSourceRole;
    /** Raw ISO timestamps — formatted in the cell so the column sorts by instant. */
    createdAt?: string | null;
    updatedAt?: string | null;
    lastEventAt?: string | null;
    archived: boolean;
    panelExcluded: boolean;
}

type MyDataSourcesViewModal =
    | MyDataSourceModalState
    | { type: "exportDataSources"; subjects: NamedExportSubject[] };

type SelectionAction = NonNullable<ResourceTableToolbar["selectionActions"]>[number];

const columnHelper = createColumnHelper<DataSourceRow>();

/** Module-level so the selection hook keeps a stable identity across renders. */
const getMyDataSourceRowId = (row: DataSourceRow) => row.id;

/**
 * User-facing data sources page — shows data sources the user has access to.
 * Uses ResourceTable with a side drawer for details and actions.
 * Bulk connection and delete actions are limited to active OWNER rows.
 *
 * The bulk action shape mirrors `AdminDataSourcesView.useSelectionActions` by
 * design — same icons, labels, and state derivation — so future divergence
 * is easy to spot. If a third surface appears we should hoist the shared
 * derivation into a reusable hook in `features/data-sources/`.
 */
function MyDataSourcesContent() {
    const { t } = useTranslation();
    const { showToast } = useToast();
    const queryClient = useQueryClient();
    // Focus target for dialogs whose trigger unmounts with the drawer (see closeDrawer).
    const tableRef = useRef<HTMLDivElement>(null);
    const { selectedId, openDrawer, closeDrawer, modal, setModal } =
        useViewDrawerContext<MyDataSourcesViewModal>();

    const tableState = useResourceTableState();
    const [showArchived, setShowArchived] = useState(false);
    const isAdmin = useIsAdmin();
    const timezone = useUserTimezone();

    const {
        data: dataSources = [],
        isLoading,
        refetch,
        isFetching,
    } = useQuery(dataSourceQueries.myList(showArchived));

    const { mutateAsync: _deleteDataSource } = useMutation(dataSourceMutations.delete(queryClient));
    const deleteDataSource = useCallback(
        async (serverId: string) => {
            await _deleteDataSource(serverId);
            useFilterPanelStore.getState().removeKnownSources([serverId]);
        },
        [_deleteDataSource],
    );
    const { mutateAsync: disconnect } = useMutation(dataSourceMutations.disconnect(queryClient));
    const { mutateAsync: reconnect } = useMutation(dataSourceMutations.reconnect(queryClient));

    const [isBulkBusy, setIsBulkBusy] = useState(false);
    const excludedSet = usePanelExcludedIds();
    const { setExcluded } = usePanelExclusionActions();

    const rows: DataSourceRow[] = useMemo(
        () =>
            dataSources.map((ds) => ({
                id: ds.serverId,
                name: ds.serverName || t("dataSources.unnamed"),
                transportType: ds.transportType,
                access: ds.access,
                state: ds.serverState,
                role: ds.role as DataSourceRole,
                createdAt: ds.createdAt,
                updatedAt: ds.updatedAt,
                lastEventAt: ds.lastEventAt,
                archived: showArchived || !!ds.archivedAt,
                panelExcluded: excludedSet.has(ds.serverId),
            })),
        [dataSources, t, showArchived, excludedSet],
    );

    const drawerState: MyDataSourceDrawerState = useMemo(() => {
        if (!selectedId) return { type: "closed" };
        const ds = dataSources.find((d) => d.serverId === selectedId);
        return ds ? { type: "dataSource", entity: ds } : { type: "closed" };
    }, [selectedId, dataSources]);

    const closeModal = () => setModal({ type: "none" });

    // Runs `action` over `ids` with `Promise.allSettled`, clears row selection
    // synchronously, gates concurrent bulk calls, and returns the failure
    // count so the caller can render a localized toast with its own key.
    // Shared shape with admin's bulk handlers (`AdminDataSourcesView`).
    const { resetSelection } = tableState;
    const runBulk = useCallback(
        async (ids: string[], action: (id: string) => Promise<unknown>): Promise<number> => {
            if (ids.length === 0) return 0;
            resetSelection();
            setIsBulkBusy(true);
            try {
                const results = await Promise.allSettled(ids.map(action));
                return results.filter((r) => r.status === "rejected").length;
            } finally {
                setIsBulkBusy(false);
            }
        },
        [resetSelection],
    );

    const handleBulkStart = useCallback(
        async (ids: string[]) => {
            const failures = await runBulk(ids, reconnect);
            if (failures > 0) {
                showToast({
                    type: "error",
                    title: t("dataSources.bulkStartError", { count: failures }),
                });
            }
        },
        [runBulk, reconnect, showToast, t],
    );

    const handleBulkDisconnect = useCallback(
        async (ids: string[]) => {
            const failures = await runBulk(ids, disconnect);
            if (failures > 0) {
                showToast({
                    type: "error",
                    title: t("dataSources.bulkDisconnectError", { count: failures }),
                });
            }
        },
        [runBulk, disconnect, showToast, t],
    );

    const handleBulkDelete = useCallback(
        async (ids: string[]) => {
            const failures = await runBulk(ids, deleteDataSource);
            if (failures > 0) {
                showToast({
                    type: "error",
                    title: t("dataSources.bulkDeleteError", { count: failures }),
                });
            }
        },
        [runBulk, deleteDataSource, showToast, t],
    );

    const handleDeleteDataSource = async (ds: MyDataSource) => {
        try {
            await deleteDataSource(ds.serverId);
            closeModal();
            closeDrawer();
        } catch {
            showToast({ type: "error", title: t("adminDialogs.somethingWentWrong") });
        }
    };

    const columnVisibility = useTableColumnVisibility("my-data-sources");

    const columns = useMemo(
        () => [
            columnHelper.accessor("name", {
                header: () => t("dataSources.name"),
                cell: (info) => {
                    const archived = info.row.original.archived;
                    return (
                        <Flex align="center" gap={2}>
                            {info.getValue() || "-"}
                            <PanelExclusionIndicator excluded={info.row.original.panelExcluded} />
                            {archived && <ArchivedBadge />}
                        </Flex>
                    );
                },
                enableSorting: true,
                enableHiding: false,
                meta: {
                    filterDef: {
                        label: t("dataSources.name"),
                        type: "string" as const,
                    },
                },
                filterFn: OC_FILTER_FNS.ocStringFilter,
            }),
            columnHelper.display({
                id: "transportType",
                header: () => t("admin.protocol"),
                cell: (info) => info.row.original.transportType,
                meta: { columnLabel: t("admin.protocol") },
            }),
            columnHelper.display({
                id: "access",
                header: () => t("admin.accessMode"),
                cell: (info) => (
                    <SourceSummaryAccessMode
                        access={info.row.original.access}
                        transportType={info.row.original.transportType}
                    />
                ),
                meta: { columnLabel: t("admin.accessMode") },
            }),
            columnHelper.accessor("state", {
                header: () => t("dataSources.status"),
                cell: (info) => <ConnectionStatusIndicator state={info.getValue()} />,
                enableSorting: false,
                size: 130,
                meta: {
                    columnLabel: t("dataSources.status"),
                    filterDef: {
                        label: t("dataSources.status"),
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
                filterFn: OC_FILTER_FNS.ocEnumFilter,
            }),
            columnHelper.accessor("role", {
                header: () => (
                    <HStack gap={1}>
                        <Text color="fg.muted">{t("dataSources.role")}</Text>
                        <RoleInfoTooltip roles={DATA_SOURCE_ROLE_DESCRIPTIONS} />
                    </HStack>
                ),
                cell: (info) => (
                    <Text fontSize="sm" color="fg.muted">
                        {info.getValue()}
                    </Text>
                ),
                enableSorting: true,
                size: 130,
                meta: {
                    columnLabel: t("dataSources.role"),
                    filterDef: {
                        label: t("dataSources.role"),
                        type: "enum" as const,
                        options: [
                            { label: t("roles.owner"), value: "OWNER" },
                            { label: t("roles.admin"), value: "ADMIN" },
                            { label: t("roles.viewer"), value: "VIEWER" },
                        ],
                    },
                },
                filterFn: OC_FILTER_FNS.ocEnumFilter,
            }),
            ...auditColumns<DataSourceRow>(t, timezone, "dataSource"),
        ],
        [t, timezone],
    );

    const filterableColumns: FilterableColumn[] = useMemo(
        () => [
            {
                id: "name",
                filterDef: {
                    label: t("dataSources.name"),
                    type: "string" as const,
                },
            },
            {
                id: "state",
                filterDef: {
                    label: t("dataSources.status"),
                    type: "enum" as const,
                    options: [
                        { label: t("resourceTable.connectionState.new"), value: "NEW" },
                        {
                            label: t("resourceTable.connectionState.connecting"),
                            value: "CONNECTING",
                        },
                        { label: t("resourceTable.connectionState.connected"), value: "CONNECTED" },
                        {
                            label: t("resourceTable.connectionState.disconnected"),
                            value: "DISCONNECTED",
                        },
                        { label: t("resourceTable.connectionState.stopped"), value: "STOPPED" },
                        { label: t("resourceTable.connectionState.failed"), value: "FAILED" },
                    ],
                },
            },
            {
                id: "role",
                filterDef: {
                    label: t("dataSources.role"),
                    type: "enum" as const,
                    options: [
                        { label: t("roles.owner"), value: "OWNER" },
                        { label: t("roles.admin"), value: "ADMIN" },
                        { label: t("roles.viewer"), value: "VIEWER" },
                    ],
                },
            },
        ],
        [t],
    );

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

    // Retains rows once ticked, so narrowing never shrinks what the bulk actions cover.
    const { selectedRows, onRowSelectionChange } = useSelectedRows(
        rows,
        getMyDataSourceRowId,
        tableState.rowSelection,
        tableState.onRowSelectionChange,
    );
    const allSelectedOwnerActiveSources = useMemo(
        () =>
            selectedRows.length > 0 &&
            selectedRows.every((row) => row.role === DataSourceRoles.OWNER && !row.archived),
        [selectedRows],
    );
    const selectedExportableSources = useMemo(
        () => selectedRows.map((row) => ({ id: row.id, name: row.name })),
        [selectedRows],
    );
    const allSelectedExportable = useMemo(
        () =>
            selectedRows.length > 0 &&
            selectedRows.every((row) => !row.archived && canExportDataSource(row.role, isAdmin)),
        [selectedRows, isAdmin],
    );
    // `connectedIds` / `disconnectedIds` filter the bulk dispatch so we don't
    // fan out Start requests against already-connected rows (or vice versa).
    const connectedIds = useMemo(
        () => selectedRows.filter((row) => isConnected(row.state)).map((row) => row.id),
        [selectedRows],
    );
    const disconnectedIds = useMemo(
        () => selectedRows.filter((row) => !isConnected(row.state)).map((row) => row.id),
        [selectedRows],
    );
    const selectedCount = selectedRows.length;

    const selectionActions = useMemo<SelectionAction[]>(() => {
        if (selectedCount === 0) return [];
        const actions: SelectionAction[] = [];
        const allPanelExcluded = selectedRows.every((row) => row.panelExcluded);
        actions.push({
            id: "toggle-panel-exclusion",
            label: allPanelExcluded
                ? t("filters.panelExclusion.enable")
                : t("filters.panelExclusion.disable"),
            tooltip: allPanelExcluded
                ? t("filters.panelExclusion.showSelected")
                : t("filters.panelExclusion.hideSelected"),
            icon: <PanelExclusionIcon excluded={allPanelExcluded} />,
            onClick: () =>
                setExcluded(
                    selectedRows.map((row) => row.id),
                    !allPanelExcluded,
                ),
        });
        if (allSelectedOwnerActiveSources) {
            if (disconnectedIds.length > 0) {
                actions.push({
                    label: t("adminActions.start"),
                    icon: <FaPlay />,
                    onClick: () => handleBulkStart(disconnectedIds),
                    disabled: isBulkBusy,
                });
            }
            if (connectedIds.length > 0) {
                actions.push({
                    label: t("adminActions.disconnect"),
                    icon: <FaStop />,
                    onClick: () => handleBulkDisconnect(connectedIds),
                    disabled: isBulkBusy,
                });
            }
            const allIds = selectedRows.map((row) => row.id);
            actions.push({
                label: t("common.delete"),
                icon: <FaTrash />,
                onClick: () => handleBulkDelete(allIds),
                colorPalette: "red",
                disabled: isBulkBusy,
                confirm: {
                    title: t("resourceTable.confirmDeleteTitle"),
                    message: t("resourceTable.confirmDeleteMessage", { count: selectedCount }),
                },
            });
        }
        if (allSelectedExportable) {
            actions.push({
                label: t("common.export"),
                icon: <FaDownload />,
                showLabel: true,
                onClick: () =>
                    setModal({ type: "exportDataSources", subjects: selectedExportableSources }),
            });
        }
        return actions;
    }, [
        selectedCount,
        selectedRows,
        connectedIds,
        disconnectedIds,
        isBulkBusy,
        handleBulkStart,
        handleBulkDisconnect,
        handleBulkDelete,
        allSelectedOwnerActiveSources,
        allSelectedExportable,
        selectedExportableSources,
        setExcluded,
        setModal,
        t,
    ]);

    const toolbar: ResourceTableToolbar = {
        onRefresh: refetch,
        isRefreshing: isFetching,
        actions: [
            ...(showArchived
                ? []
                : [
                      {
                          label: t("dataSources.newDataSource"),
                          icon: <FaPlus />,
                          showLabel: true,
                          onClick: () => setModal({ type: "addDataSource" }),
                      },
                  ]),
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

    return (
        <Flex
            height="100%"
            width="100%"
            direction="column"
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
            <ResourceTable
                aria-label={t("dataSources.title")}
                data={rows}
                columns={columns}
                isLoading={isLoading}
                isFetching={isFetching}
                getRowId={getMyDataSourceRowId}
                onRowClick={(row) => openDrawer(row.id)}
                globalFilter={tableState.globalFilter}
                onGlobalFilterChange={tableState.onGlobalFilterChange}
                columnFilters={tableState.columnFilters}
                onColumnFiltersChange={tableState.onColumnFiltersChange}
                enableRowSelection={(row) => !row.archived}
                rowSelection={tableState.rowSelection}
                onRowSelectionChange={onRowSelectionChange}
                selectedCount={selectedRows.length}
                toolbar={toolbar}
                filterBar={filterBarEl}
                {...columnVisibility}
                detailDrawer={
                    <MyDataSourceDrawer drawerState={drawerState} showArchived={showArchived} />
                }
            />

            {modal.type === "addDataSource" && <AddDataSourceDialog open onClose={closeModal} />}

            {modal.type === "deleteDataSource" && (
                <ConfirmArchiveDialog
                    isOpen
                    onClose={closeModal}
                    name={modal.dataSource.serverName}
                    onConfirm={() => handleDeleteDataSource(modal.dataSource)}
                    finalFocusEl={() => tableRef.current}
                />
            )}

            {modal.type === "exportDataSources" && (
                <ExportDataSourcesDialog
                    isOpen
                    onClose={closeModal}
                    subjects={modal.subjects}
                    title={t("dataSources.title")}
                />
            )}
        </Flex>
    );
}

export function MyDataSources() {
    return (
        <ViewDrawerProvider<MyDataSourcesViewModal> initialModal={{ type: "none" }}>
            <MyDataSourcesContent />
        </ViewDrawerProvider>
    );
}
