// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Flex, HStack, Text } from "@chakra-ui/react";
import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useDebouncedValue } from "@tanstack/react-pacer";
import {
    ArchivedBadge,
    ColumnFilterBuilder,
    ResourceTable,
    TypeColorLabel,
    createColumnHelper,
    toFilterableColumns,
} from "@opencop/ui";
import type { ResourceTableToolbar } from "@opencop/ui";
import type { SortingState } from "@tanstack/react-table";
import { useState, useMemo, useCallback, useRef } from "react";
import { useTranslation } from "react-i18next";
import { ScopeIndicator } from "~/features/layers/ScopeIndicator";
import { auditColumns } from "~/features/admin/audit-columns.util";
import { ViewDrawerProvider, useViewDrawerContext } from "~/features/drawers/ViewDrawerContext";
import { FaArchive, FaCog, FaDownload, FaFilter, FaPlus } from "react-icons/fa";
import { useBulkAction } from "~/features/admin/use-bulk-action.hook";
import { useResourceTableState } from "~/features/admin/use-resource-table-state.hook";
import { useSelectedRows } from "~/features/admin/use-selected-rows.hook";
import { useTableColumnVisibility } from "~/features/admin/use-table-column-visibility.hook";
import { MAX_FILTER_CRITERIA, resolveInfiniteRetry } from "@opencop/api";
import type { AccessScope, LayerInfo, SortParam } from "@opencop/api";
import { layerMutations, layerQueries } from "~/features/layers/layers.queries";
import { layerDirectoryQueries } from "~/features/layers/layer-directory.queries";
import {
    LAYER_DIRECTORY_FILTER_FIELDS,
    toLayerDirectoryFilterParams,
    toLayerDirectorySortField,
} from "~/features/layers/layer-directory-filters.util";
import {
    layerGroupMutations,
    layerGroupQueries,
} from "~/features/layer-groups/layer-groups.queries";
import type { LayerGroup } from "~/features/layer-groups/layer-groups.queries";
import { ACCESS_SCOPE_DESCRIPTIONS } from "~/features/layer-groups/layer-groups.util";
import { RoleInfoTooltip } from "~/features/layer-groups/RoleInfoTooltip";
import { AddLayerDialog } from "~/features/layers/dialogs/AddLayerDialog";
import { AssignLayersToGroupDialog } from "~/features/layers/dialogs/AssignLayersToGroupDialog";
import type { AssignLayerSubject } from "~/features/layers/dialogs/AssignLayersToGroupDialog";
import { ConfirmDeleteDialog } from "~/features/membership-management/ConfirmDeleteDialog";
import { ConfirmArchiveDialog } from "~/features/membership-management/ConfirmArchiveDialog";
import { ImportLayerFileDialog } from "~/features/layers/dialogs/ImportLayerFileDialog";
import { ExportLayersDialog } from "~/features/layers/dialogs/ExportLayersDialog";
import { DuplicateLayerDialog } from "~/features/layers/dialogs/DuplicateLayerDialog";
import { AdminLayerDrawer } from "~/features/layers/AdminLayerDrawer";
import type {
    AdminLayerDrawerState,
    AdminLayerModalState,
} from "~/features/layers/AdminLayerDrawer";
import { AdminLayerGroupDrawer } from "~/features/layer-groups/AdminLayerGroupDrawer";
import type {
    AdminLayerGroupDrawerState,
    AdminLayerGroupModalState,
} from "~/features/layer-groups/AdminLayerGroupDrawer";
import { AddLayerGroupDialog } from "~/features/layer-groups/dialogs/AddLayerGroupDialog";
import { useUserTimezone } from "~/features/preferences/use-user-timezone.hook";
import { useToast } from "~/features/notifications/use-toast.hook";
import { useFilterPanelStore } from "~/features/filters/filter-panel.store";
import { MergeLayersDialog } from "~/features/layers/dialogs/MergeLayersDialog";
import type { MergeLayerItem } from "~/features/layers/dialogs/MergeLayersDialog";
import { FaCodeBranch } from "react-icons/fa6";
import { TbFolder } from "react-icons/tb";
import { useMergeSelection } from "~/features/layers/use-merge-selection.hook";

// ── Row types ─────────────────────────────────────────────────────────────────

interface AdminCombinedRow {
    id: string;
    rowType: "layer" | "layer-group";
    name: string;
    accessScope?: AccessScope;
    /** Raw ISO timestamp — formatted in the cell, so the date filter has a real value to send. */
    createdAt?: string;
    updatedAt?: string | null;
    /** Last time a user opened an object on this layer; null until one has been. */
    lastEventAt?: string | null;
    archived?: boolean;
}

// ── Modal state ───────────────────────────────────────────────────────────────

// Composed from the two drawers' own modal-state types plus the view-only
// variants (add-layer-group and the combined merge action), so a rename/extension
// on either drawer's state propagates here instead of drifting silently past the
// unchecked cast in `useViewDrawerContext`. Matches the sibling admin/"My" views.
type AdminLayersViewModal =
    | AdminLayerModalState
    | Exclude<AdminLayerGroupModalState, { type: "none" }>
    | { type: "addLayerGroup" }
    | { type: "assignToLayerGroup"; layers: AssignLayerSubject[] }
    | {
          type: "mergeLayers";
          layers: MergeLayerItem[];
          selectionLimitExceeded: boolean;
      };

// ── Column helper ─────────────────────────────────────────────────────────────

const columnHelper = createColumnHelper<AdminCombinedRow>();

/** Module-level so the selection hook keeps a stable identity across renders. */
const getAdminRowId = (row: AdminCombinedRow) => row.id;

// ── Component ─────────────────────────────────────────────────────────────────

export function AdminLayersView() {
    return (
        <ViewDrawerProvider<AdminLayersViewModal> initialModal={{ type: "none" }}>
            <AdminLayersViewContent />
        </ViewDrawerProvider>
    );
}

function AdminLayersViewContent() {
    const { t } = useTranslation();
    const { showToast } = useToast();
    const queryClient = useQueryClient();
    const { selectedId, selectedType, openDrawer, closeDrawer, modal, setModal } =
        useViewDrawerContext<AdminLayersViewModal>();
    // Focus target for dialogs whose trigger unmounts with the drawer (see closeDrawer).
    const tableRef = useRef<HTMLDivElement>(null);
    const timezone = useUserTimezone();

    // ── Filter + server-query state ───────────────────────────────────────────

    const [showArchived, setShowArchived] = useState(false);
    const tableState = useResourceTableState();

    // Debounced search + column sort + filter chips feed the query key; changing any resets to
    // page 0. Chips are not debounced — each Apply is a discrete event.
    const [debouncedSearch] = useDebouncedValue(tableState.globalFilter, { wait: 300 });
    const [sorting, setSorting] = useState<SortingState>([]);
    const sort = useMemo(
        () =>
            sorting.map(
                (s): SortParam => `${toLayerDirectorySortField(s.id)},${s.desc ? "desc" : "asc"}`,
            ),
        [sorting],
    );
    const filter = useMemo(
        () => toLayerDirectoryFilterParams(tableState.columnFilters),
        [tableState.columnFilters],
    );

    // ── Data fetching (server-paged combined layer/layer-group directory) ──────

    const {
        data,
        isLoading,
        isFetching,
        fetchNextPage,
        hasNextPage,
        isFetchingNextPage,
        isError,
        refetch: refetchDirectory,
    } = useInfiniteQuery(
        layerDirectoryQueries.infiniteList({
            search: debouncedSearch || undefined,
            sort: sort.length ? sort : undefined,
            filter: filter.length ? filter : undefined,
            // Sent whenever a filter is applied so Created ranges mean the viewer's day, matching
            // the timezone the Created column renders in.
            timezone: filter.length ? timezone : undefined,
            recordVisibility: showArchived ? "ARCHIVED" : "ACTIVE",
        }),
    );

    // ── Mutations ─────────────────────────────────────────────────────────────

    const { mutateAsync: _deleteLayer } = useMutation(layerMutations.delete(queryClient));
    const deleteLayer = useCallback(
        async (id: string) => {
            await _deleteLayer(id);
            useFilterPanelStore.getState().removeKnownSources([id]);
        },
        [_deleteLayer],
    );
    const { mutateAsync: deleteLayerGroup } = useMutation(layerGroupMutations.delete(queryClient));

    // ── Selection + bulk actions ──────────────────────────────────────────────

    const closeModal = () => setModal({ type: "none" });

    // ── Rows (flattened, accumulated pages) ───────────────────────────────────

    const rows: AdminCombinedRow[] = useMemo(
        () =>
            (data?.pages ?? []).flatMap((page) =>
                page.content.map((entry) => ({
                    id: entry.id,
                    rowType: entry.type === "LAYER" ? ("layer" as const) : ("layer-group" as const),
                    name: entry.name || t("adminLayers.unnamedLayer"),
                    accessScope: entry.accessScope,
                    createdAt: entry.createdAt,
                    updatedAt: entry.updatedAt,
                    lastEventAt: entry.lastEventAt,
                    archived: entry.type === "LAYER" ? entry.archived : false,
                })),
            ),
        [data, t],
    );
    const totalCount = data?.pages[0]?.totalElements;

    // Retains rows once seen, so a selection built across searches stays whole: the directory
    // query restarts at page 0 whenever the search or sort changes, and every action below would
    // otherwise see a different subset than the one the toolbar counted.
    const { selectedRows, onRowSelectionChange } = useSelectedRows(
        rows,
        getAdminRowId,
        tableState.rowSelection,
        tableState.onRowSelectionChange,
    );

    const selectedIds = useMemo(() => selectedRows.map(getAdminRowId), [selectedRows]);
    const { handleBulkAction } = useBulkAction(deleteLayer, selectedIds, tableState.resetSelection);

    const selectedLayers = useMemo(
        () =>
            selectedRows
                .filter((row) => row.rowType === "layer")
                .map((row) => ({ id: row.id, name: row.name })),
        [selectedRows],
    );

    // Carries accessScope too — the assign dialog filters target groups by it.
    const selectedLayerSubjects = useMemo<AssignLayerSubject[]>(
        () =>
            selectedRows
                // Type predicate rather than a bare boolean: a plain filter doesn't narrow
                // `accessScope` for the following map, which would force a cast.
                .filter(
                    (row): row is AdminCombinedRow & { accessScope: AccessScope } =>
                        row.rowType === "layer" && row.accessScope != null,
                )
                .map((row) => ({
                    id: row.id,
                    name: row.name,
                    accessScope: row.accessScope,
                })),
        [selectedRows],
    );

    // ── Drawer state — resolve the full entity by id (rows carry only grid fields) ──

    const { data: selectedLayer } = useQuery({
        ...layerQueries.detail(selectedId ?? ""),
        enabled: !!selectedId && selectedType === "layer",
    });
    const layerDrawerState: AdminLayerDrawerState =
        selectedType === "layer" && selectedLayer
            ? { type: "layer", entity: selectedLayer }
            : { type: "closed" };

    const { data: selectedGroup } = useQuery({
        ...layerGroupQueries.detail(selectedId ?? ""),
        enabled: !!selectedId && selectedType === "layer-group",
    });
    const layerGroupDrawerState: AdminLayerGroupDrawerState =
        selectedType === "layer-group" && selectedGroup
            ? { type: "layer-group", entity: selectedGroup }
            : { type: "closed" };

    const handleRowClick = (row: AdminCombinedRow) => openDrawer(row.id, row.rowType);

    // ── Delete handlers ───────────────────────────────────────────────────────

    const handleDeleteLayer = async (layer: LayerInfo) => {
        try {
            await deleteLayer(layer.id);
            closeModal();
            closeDrawer();
        } catch {
            showToast({ type: "error", title: t("layers.deleteError") });
        }
    };

    const handleDeleteLayerGroup = async (group: LayerGroup) => {
        try {
            await deleteLayerGroup(group.id);
            closeModal();
            closeDrawer();
        } catch {
            showToast({ type: "error", title: t("adminLayerGroups.failedToDelete") });
        }
    };

    const handleBulkDelete = useCallback(async () => {
        const { failures } = await handleBulkAction();
        if (failures > 0) {
            showToast({
                type: "error",
                title: t("adminLayers.bulkDeleteError", { count: failures }),
            });
        }
    }, [handleBulkAction, showToast, t]);

    // Only offer bulk delete when all selected rows are layers
    const allSelectedAreLayers = useMemo(() => {
        return selectedRows.length > 0 && selectedRows.every((row) => row.rowType === "layer");
    }, [selectedRows]);

    const canSelectForMerge = useCallback(
        (row: AdminCombinedRow) => row.rowType === "layer" && !row.archived,
        [],
    );

    const { mergeDialogSelection, canMergeSelection } = useMergeSelection(
        selectedRows,
        canSelectForMerge,
    );

    // ── Columns ───────────────────────────────────────────────────────────────

    const columnVisibility = useTableColumnVisibility("admin-layers");

    const columns = useMemo(
        () => [
            columnHelper.accessor("name", {
                header: () => t("adminLayers.name"),
                cell: (info) => {
                    const archived = info.row.original.archived;
                    const isLayer = info.row.original.rowType === "layer";
                    return (
                        <Flex align="center" gap={2}>
                            <TypeColorLabel
                                abbr={isLayer ? "L" : "LG"}
                                color={isLayer ? "yellow" : "indigo"}
                                label={
                                    isLayer
                                        ? t("adminLayers.layer")
                                        : t("adminLayerGroups.layerGroup")
                                }
                            />
                            {info.getValue()}
                            {archived && <ArchivedBadge />}
                        </Flex>
                    );
                },
                enableSorting: true,
                enableHiding: false,
                meta: {
                    filterDef: {
                        label: t("adminLayers.name"),
                        type: "string" as const,
                        operators: LAYER_DIRECTORY_FILTER_FIELDS.name.operators,
                    },
                },
            }),
            columnHelper.accessor("rowType", {
                header: () => t("resourceTable.columnType"),
                cell: (info) => (
                    <Text fontSize="sm" color="fg.muted">
                        {info.getValue() === "layer"
                            ? t("adminLayers.layer")
                            : t("adminLayerGroups.layerGroup")}
                    </Text>
                ),
                enableSorting: true,
                size: 130,
                meta: {
                    columnLabel: t("resourceTable.columnType"),
                    filterDef: {
                        label: t("resourceTable.columnType"),
                        type: "enum" as const,
                        // Wire tokens, not the row model's "layer"/"layer-group" — see
                        // LAYER_DIRECTORY_FILTER_FIELDS.
                        options: [
                            { label: t("adminLayers.layer"), value: "LAYER" },
                            { label: t("adminLayerGroups.layerGroup"), value: "LAYER_GROUP" },
                        ],
                        operators: LAYER_DIRECTORY_FILTER_FIELDS.rowType.operators,
                    },
                },
            }),
            columnHelper.accessor("accessScope", {
                header: () => (
                    <HStack gap={1}>
                        <Text color="fg.muted">{t("adminLayers.accessScope")}</Text>
                        <RoleInfoTooltip
                            roles={ACCESS_SCOPE_DESCRIPTIONS}
                            ariaLabel={t("adminLayers.accessScope")}
                        />
                    </HStack>
                ),
                cell: (info) => {
                    const scope = info.getValue();
                    if (!scope) return "-";
                    return <ScopeIndicator scope={scope} />;
                },
                enableSorting: true,
                meta: {
                    columnLabel: t("adminLayers.accessScope"),
                    filterDef: {
                        label: t("adminLayers.accessScope"),
                        type: "enum" as const,
                        options: [
                            { label: t("adminLayers.scopePrivate"), value: "PRIVATE" },
                            { label: t("adminLayers.scopeGlobal"), value: "GLOBAL" },
                        ],
                        operators: LAYER_DIRECTORY_FILTER_FIELDS.accessScope.operators,
                    },
                },
            }),
            ...auditColumns<AdminCombinedRow>(t, timezone, "layer", {
                label: t("adminLayers.createdAt"),
                type: "date" as const,
                operators: LAYER_DIRECTORY_FILTER_FIELDS.createdAt.operators,
            }),
        ],
        [t, timezone],
    );

    const filterableColumns = useMemo(() => toFilterableColumns(columns), [columns]);

    const filterBar = (
        <ColumnFilterBuilder
            filterableColumns={filterableColumns}
            value={tableState.columnFilters}
            onChange={tableState.onColumnFiltersChange}
            maxFilters={MAX_FILTER_CRITERIA}
            searchValue={tableState.globalFilter}
            onSearchChange={tableState.onGlobalFilterChange}
            searchPlaceholder={t("adminLayers.searchHint")}
        />
    );

    // ── Toolbar ───────────────────────────────────────────────────────────────

    const toolbar: ResourceTableToolbar = {
        onRefresh: refetchDirectory,
        isRefreshing: isFetching,
        actions: [
            {
                label: t("common.create"),
                icon: <FaPlus />,
                showLabel: true,
                menuItems: [
                    {
                        label: t("adminLayers.newLayer"),
                        onClick: () => setModal({ type: "addLayer" }),
                    },
                    {
                        label: t("adminLayerGroups.newLayerGroup"),
                        onClick: () => setModal({ type: "addLayerGroup" }),
                    },
                ],
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
        selectionActions: [
            {
                label: t("mergeLayers.merge"),
                icon: <FaCodeBranch />,
                showLabel: true,
                onClick: () =>
                    setModal({
                        type: "mergeLayers",
                        layers: mergeDialogSelection.layers,
                        selectionLimitExceeded: mergeDialogSelection.selectionLimitExceeded,
                    }),
                disabled: !canMergeSelection,
            },
            {
                label: t("adminLayers.assignToLayerGroup"),
                icon: <TbFolder />,
                showLabel: true,
                onClick: () =>
                    setModal({ type: "assignToLayerGroup", layers: [...selectedLayerSubjects] }),
                disabled: !allSelectedAreLayers,
            },
            {
                label: t("common.export"),
                icon: <FaDownload />,
                showLabel: true,
                onClick: () => setModal({ type: "exportLayers", subjects: [...selectedLayers] }),
                disabled: !allSelectedAreLayers,
            },
            {
                label: t("common.archive"),
                icon: <FaArchive />,
                disabled: !allSelectedAreLayers,
                onClick: handleBulkDelete,
                confirm: {
                    title: t("resourceTable.confirmArchiveTitle"),
                    message: t("resourceTable.confirmArchiveMessage", {
                        count: selectedRows.length,
                    }),
                    isDestructive: false,
                },
            },
        ],
    };

    // ── Render ────────────────────────────────────────────────────────────────

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
                aria-label={t("adminLayers.title")}
                data={rows}
                columns={columns}
                isLoading={isLoading}
                isFetching={isFetching && !isFetchingNextPage}
                getRowId={getAdminRowId}
                onRowClick={handleRowClick}
                globalFilter={tableState.globalFilter}
                onGlobalFilterChange={tableState.onGlobalFilterChange}
                columnFilters={tableState.columnFilters}
                onColumnFiltersChange={tableState.onColumnFiltersChange}
                filterBar={filterBar}
                {...columnVisibility}
                enableRowSelection={canSelectForMerge}
                rowSelection={tableState.rowSelection}
                onRowSelectionChange={onRowSelectionChange}
                selectedCount={selectedRows.length}
                manualSorting
                manualFiltering
                sorting={sorting}
                onSortingChange={setSorting}
                // Serialized rather than joined: a filter value may contain the delimiter.
                resetScrollKey={JSON.stringify([debouncedSearch, sort, filter])}
                emptyMessage={
                    debouncedSearch || filter.length ? t("resourceTable.noMatches") : undefined
                }
                onLoadMore={fetchNextPage}
                hasNextPage={hasNextPage}
                isFetchingNextPage={isFetchingNextPage}
                isError={isError}
                onRetry={() =>
                    resolveInfiniteRetry(rows.length > 0, fetchNextPage, refetchDirectory)
                }
                totalCount={totalCount}
                toolbar={toolbar}
                detailDrawer={
                    <>
                        <AdminLayerDrawer drawerState={layerDrawerState} />
                        <AdminLayerGroupDrawer drawerState={layerGroupDrawerState} />
                    </>
                }
            />

            {modal.type === "addLayer" && (
                <AddLayerDialog isOpen onClose={closeModal} onCreate={closeModal} />
            )}

            {modal.type === "mergeLayers" && (
                <MergeLayersDialog
                    isOpen
                    onClose={() => {
                        tableState.resetSelection();
                        closeModal();
                    }}
                    layers={modal.layers}
                    selectionLimitExceeded={modal.selectionLimitExceeded}
                />
            )}

            {modal.type === "addLayerGroup" && <AddLayerGroupDialog isOpen onClose={closeModal} />}

            {modal.type === "assignToLayerGroup" && (
                <AssignLayersToGroupDialog
                    isOpen
                    onClose={() => {
                        tableState.resetSelection();
                        closeModal();
                    }}
                    layers={modal.layers}
                />
            )}

            {modal.type === "importFile" && (
                <ImportLayerFileDialog isOpen onClose={closeModal} layerId={modal.layerId} />
            )}

            {modal.type === "exportLayers" && (
                <ExportLayersDialog
                    isOpen
                    onClose={closeModal}
                    subjects={modal.subjects}
                    title={t("adminLayers.title")}
                />
            )}

            {modal.type === "deleteLayer" && (
                <ConfirmArchiveDialog
                    isOpen
                    onClose={closeModal}
                    name={modal.layer.name}
                    onConfirm={() => handleDeleteLayer(modal.layer)}
                    finalFocusEl={() => tableRef.current}
                />
            )}

            {modal.type === "duplicateLayer" && (
                <DuplicateLayerDialog
                    key={modal.layer.id}
                    isOpen
                    onClose={closeModal}
                    layerId={modal.layer.id}
                    layerName={modal.layer.name}
                />
            )}

            {modal.type === "deleteGroup" && (
                <ConfirmDeleteDialog
                    isOpen
                    onClose={closeModal}
                    userName={modal.group.name}
                    onDelete={() => handleDeleteLayerGroup(modal.group)}
                />
            )}
        </Flex>
    );
}
