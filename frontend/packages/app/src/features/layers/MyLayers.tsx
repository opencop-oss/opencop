// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useMutation, useQueries, useQuery, useQueryClient } from "@tanstack/react-query";
import { Box, Flex, HStack, Text } from "@chakra-ui/react";
import {
    ArchivedBadge,
    ColumnFilterBuilder,
    ResourceTable,
    TypeColorLabel,
    createColumnHelper,
} from "@opencop/ui";
import type { FilterableColumn, ResourceTableToolbar } from "@opencop/ui";
import { useState, useMemo, useCallback, useRef } from "react";
import { useTranslation } from "react-i18next";
import { FaArchive, FaCog, FaDownload, FaFilter, FaPlus, FaTrash } from "react-icons/fa";
import { FaEye, FaEyeSlash } from "react-icons/fa6";
import { ViewDrawerProvider, useViewDrawerContext } from "~/features/drawers/ViewDrawerContext";
import { LayerRoles } from "@opencop/api";
import type { MyLayerInfo, LayerRole, AccessScope } from "@opencop/api";
import { layerMutations, layerQueries } from "~/features/layers/layers.queries";
import {
    layerGroupMutations,
    layerGroupQueries,
} from "~/features/layer-groups/layer-groups.queries";
import type { LayerGroupRole, MyLayerGroup } from "~/features/layer-groups/layer-groups.queries";
import { ScopeIndicator } from "~/features/layers/ScopeIndicator";
import {
    MY_LAYERS_ROLE_DESCRIPTIONS,
    ACCESS_SCOPE_DESCRIPTIONS,
} from "~/features/layer-groups/layer-groups.util";
import { RoleInfoTooltip } from "~/features/layer-groups/RoleInfoTooltip";
import { aggregateQueries } from "~/features/admin/aggregate-queries.util";
import { useResourceTableState } from "~/features/admin/use-resource-table-state.hook";
import { useTableColumnVisibility } from "~/features/admin/use-table-column-visibility.hook";
import { auditColumns } from "~/features/admin/audit-columns.util";
import { useSelectedRows } from "~/features/admin/use-selected-rows.hook";
import { AddLayerDialog } from "~/features/layers/dialogs/AddLayerDialog";
import { AddLayerGroupDialog } from "~/features/layer-groups/dialogs/AddLayerGroupDialog";
import { DuplicateLayerDialog } from "~/features/layers/dialogs/DuplicateLayerDialog";
import { ConfirmDeleteDialog } from "~/features/membership-management/ConfirmDeleteDialog";
import { ConfirmArchiveDialog } from "~/features/membership-management/ConfirmArchiveDialog";
import { ExportLayersDialog } from "~/features/layers/dialogs/ExportLayersDialog";
import { ImportLayerFileDialog } from "~/features/layers/dialogs/ImportLayerFileDialog";
import { LayerDrawer } from "~/features/layers/LayerDrawer";
import type { LayerDrawerState, LayerModalState } from "~/features/layers/LayerDrawer";
import { MyLayerGroupDrawer } from "~/features/layer-groups/MyLayerGroupDrawer";
import type {
    MyLayerGroupDrawerState,
    MyLayerGroupModalState,
} from "~/features/layer-groups/MyLayerGroupDrawer";
import { useUserTimezone } from "~/features/preferences/use-user-timezone.hook";
import { useIsAdmin } from "~/features/auth/use-is-admin.hook";
import { useToast } from "~/features/notifications/use-toast.hook";
import { useFilterPanelStore } from "~/features/filters/filter-panel.store";
import { useFilterPanelActions } from "~/features/filters/filter-panel.actions";
import { usePanelExclusionActions } from "~/features/filters/panel-exclusion.actions";
import { usePanelExcludedIds } from "~/features/filters/use-panel-excluded-ids.hook";
import { PanelExclusionIcon } from "~/features/filters/PanelExclusionIcon";
import { PanelExclusionIndicator } from "~/features/filters/PanelExclusionIndicator";
import { canExportLayer, canMergeLayer } from "~/features/exports/export-access.util";
import type { NamedExportSubject } from "~/features/exports/exports.util";
import { MergeLayersDialog } from "~/features/layers/dialogs/MergeLayersDialog";
import type { MergeLayerItem } from "~/features/layers/dialogs/MergeLayersDialog";
import { FaCodeBranch } from "react-icons/fa6";
import { useMergeSelection } from "~/features/layers/use-merge-selection.hook";

// ── Row types ─────────────────────────────────────────────────────────────────

interface MyLayerRowBase {
    id: string;
    name: string;
    accessScope?: AccessScope;
    /** Raw ISO timestamps — formatted in the cell so the column sorts by instant. */
    createdAt?: string;
    updatedAt?: string | null;
    /** Latest recorded layer activity; layer groups carry none. */
    lastEventAt?: string | null;
    archived?: boolean;
    hidden: boolean;
    /** Removed from the map's Layers and Sources panel (layers only). */
    panelExcluded: boolean;
}

interface MyLayerRow extends MyLayerRowBase {
    rowType: "layer";
    role: LayerRole;
}

interface MyLayerGroupRow extends MyLayerRowBase {
    rowType: "layer-group";
    role: LayerGroupRole;
}

type MyLayerCombinedRow = MyLayerRow | MyLayerGroupRow;

type MyLayersViewModal =
    | LayerModalState
    | Exclude<MyLayerGroupModalState, { type: "none" }>
    | { type: "addLayerGroup" }
    | { type: "exportLayers"; subjects: NamedExportSubject[] }
    | {
          type: "mergeLayers";
          layers: MergeLayerItem[];
          selectionLimitExceeded: boolean;
      };

function isLayerRow(row: MyLayerCombinedRow): row is MyLayerRow {
    return row.rowType === "layer";
}

/** Archived layers are absent from the map panel, so the bulk actions never apply to them. */
function isActiveLayerRow(row: MyLayerCombinedRow): boolean {
    return isLayerRow(row) && !row.archived;
}

// ── Column helper ─────────────────────────────────────────────────────────────

const columnHelper = createColumnHelper<MyLayerCombinedRow>();

/** Module-level so the selection hook keeps a stable identity across renders. */
const getMyLayerRowId = (row: MyLayerCombinedRow) => row.id;

// Renders a slashed-eye marker next to the name when the row is hidden; nothing
// when visible. The toggle itself lives in the toolbar selection action.
function HiddenIndicator({ hidden }: { hidden: boolean }) {
    const { t } = useTranslation();
    if (!hidden) return null;
    return (
        <Box as="span" display="inline-flex" color="fg.muted" title={t("filters.filterHidden")}>
            <FaEyeSlash size={13} />
        </Box>
    );
}

// ── Component ─────────────────────────────────────────────────────────────────

/**
 * User-facing layers page - shows layers and layer groups where user is a member.
 * Checkboxes are available for every active layer; each bulk action gates itself by role
 * (merge, export and delete are role-limited, disable is available to everyone).
 */
export function MyLayers() {
    return (
        <ViewDrawerProvider<MyLayersViewModal> initialModal={{ type: "none" }}>
            <MyLayersContent />
        </ViewDrawerProvider>
    );
}

function MyLayersContent() {
    const { t } = useTranslation();
    const { showToast } = useToast();
    const queryClient = useQueryClient();
    const { selectedId, selectedType, openDrawer, closeDrawer, modal, setModal } =
        useViewDrawerContext<MyLayersViewModal>();
    // Focus target for dialogs whose trigger unmounts with the drawer (see closeDrawer).
    const tableRef = useRef<HTMLDivElement>(null);

    // ── Table + filter state ────────────────────────────────────────────────────

    const tableState = useResourceTableState();
    const [showArchived, setShowArchived] = useState(false);

    // ── Data fetching ─────────────────────────────────────────────────────────

    const {
        data: layers = [],
        isLoading: layersLoading,
        refetch: refetchLayers,
        isFetching: layersFetching,
    } = useQuery(layerQueries.myList(showArchived));

    const {
        data: layerGroups = [],
        isLoading: layerGroupsLoading,
        refetch: refetchLayerGroups,
        isFetching: layerGroupsFetching,
    } = useQuery(layerGroupQueries.myList());

    const { isLoading, isFetching, refetchAll } = aggregateQueries([
        { isLoading: layersLoading, isFetching: layersFetching, refetch: refetchLayers },
        {
            isLoading: layerGroupsLoading,
            isFetching: layerGroupsFetching,
            refetch: refetchLayerGroups,
        },
    ]);

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

    // ── Other state ───────────────────────────────────────────────────────────

    const isAdmin = useIsAdmin();
    const timezone = useUserTimezone();
    const [isBulkBusy, setIsBulkBusy] = useState(false);
    const closeModal = () => setModal({ type: "none" });

    // ── Rows ──────────────────────────────────────────────────────────────────

    const hiddenSources = useFilterPanelStore((s) => s.hiddenSources);
    const hiddenLayerGroups = useFilterPanelStore((s) => s.hiddenLayerGroups);
    const excludedSet = usePanelExcludedIds();

    const rows: MyLayerCombinedRow[] = useMemo(() => {
        const layerRows: MyLayerRow[] = layers.map((layer) => ({
            id: layer.layerId,
            rowType: "layer",
            name: layer.layerName || t("layers.unnamedLayer"),
            role: layer.role,
            accessScope: layer.accessScope,
            createdAt: layer.createdAt,
            updatedAt: layer.updatedAt,
            lastEventAt: layer.lastEventAt,
            archived: showArchived || !!layer.archivedAt,
            hidden: hiddenSources.includes(layer.layerId),
            panelExcluded: excludedSet.has(layer.layerId),
        }));

        const groupRows: MyLayerGroupRow[] = layerGroups.map((group) => ({
            id: group.id,
            rowType: "layer-group",
            name: group.name,
            role: group.containerRole,
            accessScope: group.accessScope,
            createdAt: group.createdAt,
            hidden: hiddenLayerGroups.includes(group.id),
            panelExcluded: false,
        }));

        // Layer groups have no archived state (they are deleted, not archived),
        // so the archived view shows only archived layers.
        return showArchived ? layerRows : [...layerRows, ...groupRows];
    }, [layers, layerGroups, t, showArchived, hiddenSources, hiddenLayerGroups, excludedSet]);

    // ── Drawer state (id + type from ViewDrawerContext) ───────────────────────

    const layerDrawerState: LayerDrawerState = useMemo(() => {
        if (!selectedId || selectedType !== "layer") return { type: "closed" };
        const layer = layers.find((l) => l.layerId === selectedId);
        return layer ? { type: "layer", entity: layer } : { type: "closed" };
    }, [selectedId, selectedType, layers]);

    const layerGroupDrawerState: MyLayerGroupDrawerState = useMemo(() => {
        if (!selectedId || selectedType !== "layer-group") return { type: "closed" };
        const group = layerGroups.find((g) => g.id === selectedId);
        return group ? { type: "layer-group", entity: group } : { type: "closed" };
    }, [selectedId, selectedType, layerGroups]);

    const handleRowClick = (row: MyLayerCombinedRow) => openDrawer(row.id, row.rowType);

    // ── Delete handlers ───────────────────────────────────────────────────────

    const handleDeleteLayer = async (layer: MyLayerInfo) => {
        try {
            await deleteLayer(layer.layerId);
            closeModal();
            closeDrawer();
        } catch {
            showToast({ type: "error", title: t("layers.deleteError") });
        }
    };

    const handleDeleteLayerGroup = async (group: MyLayerGroup) => {
        try {
            await deleteLayerGroup(group.id);
            closeModal();
            closeDrawer();
        } catch {
            showToast({ type: "error", title: t("adminLayerGroups.failedToDelete") });
        }
    };

    // ── Visibility (map show/hide) ──────────────────────────────────────────────

    const { hideSource, showSource, hideLayerGroup, showLayerGroup, anySyncing } =
        useFilterPanelActions();

    // Retains rows once ticked, so narrowing never shrinks what the bulk actions cover.
    const { selectedRows, onRowSelectionChange } = useSelectedRows(
        rows,
        getMyLayerRowId,
        tableState.rowSelection,
        tableState.onRowSelectionChange,
    );
    const selectedExportableLayers = useMemo(
        () => selectedRows.map((row) => ({ id: row.id, name: row.name })),
        [selectedRows],
    );
    const selectedGroupIds = useMemo(
        () => selectedRows.filter((r) => r.rowType === "layer-group").map((r) => r.id),
        [selectedRows],
    );
    const selectedLayerIds = useMemo(
        () => selectedRows.filter((r) => r.rowType === "layer").map((r) => r.id),
        [selectedRows],
    );

    const handleBulkDelete = useCallback(async () => {
        const ids = selectedLayerIds;
        if (ids.length === 0) return;
        tableState.resetSelection();
        setIsBulkBusy(true);
        try {
            const results = await Promise.allSettled(ids.map((id) => deleteLayer(id)));
            const failures = results.filter((r) => r.status === "rejected");
            if (failures.length > 0) {
                showToast({
                    type: "error",
                    title: t("layers.bulkDeleteError", { count: failures.length }),
                });
            }
        } finally {
            setIsBulkBusy(false);
        }
    }, [selectedLayerIds, tableState, deleteLayer, showToast, t]);

    // Fetch child layer ids for any selected groups so the toggle can fan out to
    // their layers (mirrors LayerGroupVisibilityCell).
    const groupLayerQueries = useQueries({
        queries: selectedGroupIds.map((id) => layerGroupQueries.layers(id)),
    });
    const groupLayerIds = new Map<string, string[]>();
    selectedGroupIds.forEach((id, i) => {
        groupLayerIds.set(
            id,
            (groupLayerQueries[i]?.data ?? []).map((l) => l.layerId),
        );
    });

    const allSelectedHidden = selectedRows.length > 0 && selectedRows.every((r) => r.hidden);

    const { setExcluded } = usePanelExclusionActions();
    const allSelectedPanelExcluded =
        selectedRows.length > 0 && selectedRows.every((r) => r.panelExcluded);

    const anySelectedPanelExcluded = selectedRows.some((r) => r.panelExcluded);

    const handleTogglePanelExclusion = () =>
        setExcluded(selectedLayerIds, !allSelectedPanelExcluded);

    const handleToggleVisibility = () => {
        if (allSelectedHidden) {
            selectedLayerIds.forEach((id) => showSource(id));
            selectedGroupIds.forEach((id) => showLayerGroup(id, groupLayerIds.get(id) ?? []));
        } else {
            selectedLayerIds.forEach((id) => hideSource(id));
            selectedGroupIds.forEach((id) => hideLayerGroup(id, groupLayerIds.get(id) ?? []));
        }
    };

    const allSelectedExportableLayers = useMemo(
        () =>
            selectedRows.length > 0 &&
            selectedRows.every(
                (row) => isLayerRow(row) && !row.archived && canExportLayer(row.role, isAdmin),
            ),
        [selectedRows, isAdmin],
    );

    const allSelectedDeletableLayers = useMemo(
        () =>
            selectedRows.length > 0 &&
            selectedRows.every(
                (row) => row.rowType === "layer" && row.role === LayerRoles.OWNER && !row.archived,
            ),
        [selectedRows],
    );

    const canSelectForMerge = useCallback(
        (row: MyLayerCombinedRow) =>
            isLayerRow(row) && !row.archived && canMergeLayer(row.role, isAdmin),
        [isAdmin],
    );

    const { mergeDialogSelection, canMergeSelection } = useMergeSelection(
        selectedRows,
        canSelectForMerge,
    );

    // ── Columns ───────────────────────────────────────────────────────────────

    const columnVisibility = useTableColumnVisibility("my-layers");

    const columns = useMemo(
        () => [
            columnHelper.accessor("name", {
                header: () => t("layers.name"),
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
                            {info.getValue() || "-"}
                            <HiddenIndicator hidden={info.row.original.hidden} />
                            <PanelExclusionIndicator excluded={info.row.original.panelExcluded} />
                            {archived && <ArchivedBadge />}
                        </Flex>
                    );
                },
                enableSorting: true,
                enableHiding: false,
                meta: {
                    filterDef: {
                        label: t("layers.name"),
                        type: "string" as const,
                    },
                },
                filterFn: "ocStringFilter",
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
                enableSorting: false,
                size: 130,
                meta: {
                    columnLabel: t("resourceTable.columnType"),
                    filterDef: {
                        label: t("resourceTable.columnType"),
                        type: "enum" as const,
                        options: [
                            { label: t("adminLayers.layer"), value: "layer" },
                            { label: t("adminLayerGroups.layerGroup"), value: "layer-group" },
                        ],
                    },
                },
                filterFn: "ocEnumFilter",
            }),
            columnHelper.accessor("role", {
                header: () => (
                    <HStack gap={1}>
                        <Text color="fg.muted">{t("layers.role")}</Text>
                        <RoleInfoTooltip
                            roles={MY_LAYERS_ROLE_DESCRIPTIONS}
                            ariaLabel={t("layers.role")}
                        />
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
                    columnLabel: t("layers.role"),
                    filterDef: {
                        label: t("layers.role"),
                        type: "enum" as const,
                        options: [
                            { label: t("roles.owner"), value: "OWNER" },
                            { label: t("roles.admin"), value: "ADMIN" },
                            { label: t("roles.editor"), value: "EDITOR" },
                            { label: t("roles.maintainer"), value: "MAINTAINER" },
                            { label: t("roles.member"), value: "MEMBER" },
                        ],
                    },
                },
                filterFn: "ocEnumFilter",
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
                meta: { columnLabel: t("adminLayers.accessScope") },
            }),
            ...auditColumns<MyLayerCombinedRow>(t, timezone, "layer"),
        ],
        [t, timezone],
    );

    // ── Filter bar ────────────────────────────────────────────────────────────

    const filterableColumns: FilterableColumn[] = useMemo(
        () => [
            {
                id: "name",
                filterDef: {
                    label: t("layers.name"),
                    type: "string" as const,
                },
            },
            {
                id: "rowType",
                filterDef: {
                    label: t("resourceTable.columnType"),
                    type: "enum" as const,
                    options: [
                        { label: t("adminLayers.layer"), value: "layer" },
                        { label: t("adminLayerGroups.layerGroup"), value: "layer-group" },
                    ],
                },
            },
            {
                id: "role",
                filterDef: {
                    label: t("layers.role"),
                    type: "enum" as const,
                    options: [
                        { label: t("roles.owner"), value: "OWNER" },
                        { label: t("roles.admin"), value: "ADMIN" },
                        { label: t("roles.editor"), value: "EDITOR" },
                        { label: t("roles.maintainer"), value: "MAINTAINER" },
                        { label: t("roles.member"), value: "MEMBER" },
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

    // ── Toolbar ───────────────────────────────────────────────────────────────

    const selectedCount = selectedRows.length;

    const toolbar: ResourceTableToolbar = {
        onRefresh: refetchAll,
        isRefreshing: isFetching,
        actions: [
            ...(showArchived
                ? []
                : [
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
        selectionActions:
            selectedCount > 0
                ? [
                      {
                          label: t("mergeLayers.merge"),
                          icon: <FaCodeBranch />,
                          showLabel: true,
                          onClick: () =>
                              setModal({
                                  type: "mergeLayers",
                                  layers: mergeDialogSelection.layers,
                                  selectionLimitExceeded:
                                      mergeDialogSelection.selectionLimitExceeded,
                              }),
                          disabled: !canMergeSelection,
                      },
                      {
                          id: "toggle-visibility",
                          label: allSelectedHidden ? t("common.show") : t("common.hide"),
                          icon: allSelectedHidden ? <FaEye /> : <FaEyeSlash />,
                          onClick: handleToggleVisibility,
                          disabled: anySelectedPanelExcluded || anySyncing(selectedLayerIds),
                          disabledReason: anySelectedPanelExcluded
                              ? t("filters.panelExclusion.visibilityLocked")
                              : undefined,
                      },
                      {
                          id: "toggle-panel-exclusion",
                          label: allSelectedPanelExcluded
                              ? t("filters.panelExclusion.enable")
                              : t("filters.panelExclusion.disable"),
                          tooltip: allSelectedPanelExcluded
                              ? t("filters.panelExclusion.showSelected")
                              : t("filters.panelExclusion.hideSelected"),
                          icon: <PanelExclusionIcon excluded={allSelectedPanelExcluded} />,
                          onClick: handleTogglePanelExclusion,
                      },
                      {
                          label: t("common.export"),
                          icon: <FaDownload />,
                          showLabel: true,
                          onClick: () =>
                              setModal({
                                  type: "exportLayers",
                                  subjects: [...selectedExportableLayers],
                              }),
                          disabled: !allSelectedExportableLayers,
                      },
                      {
                          label: t("common.delete"),
                          icon: <FaTrash />,
                          onClick: handleBulkDelete,
                          colorPalette: "red",
                          disabled: isBulkBusy || !allSelectedDeletableLayers,
                          confirm: {
                              title: t("resourceTable.confirmDeleteTitle"),
                              message: t("resourceTable.confirmDeleteMessage", {
                                  count: selectedCount,
                              }),
                          },
                      },
                  ]
                : [],
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
                aria-label={t("layers.title")}
                data={rows}
                columns={columns}
                isLoading={isLoading}
                isFetching={isFetching}
                getRowId={getMyLayerRowId}
                onRowClick={handleRowClick}
                globalFilter={tableState.globalFilter}
                onGlobalFilterChange={tableState.onGlobalFilterChange}
                columnFilters={tableState.columnFilters}
                onColumnFiltersChange={tableState.onColumnFiltersChange}
                enableRowSelection={isActiveLayerRow}
                rowSelection={tableState.rowSelection}
                onRowSelectionChange={onRowSelectionChange}
                selectedCount={selectedCount}
                toolbar={toolbar}
                filterBar={filterBarEl}
                {...columnVisibility}
                detailDrawer={
                    <>
                        <LayerDrawer drawerState={layerDrawerState} />
                        <MyLayerGroupDrawer drawerState={layerGroupDrawerState} />
                    </>
                }
            />

            {modal.type === "addLayer" && (
                <AddLayerDialog isOpen onClose={closeModal} onCreate={closeModal} />
            )}

            {modal.type === "addLayerGroup" && <AddLayerGroupDialog isOpen onClose={closeModal} />}

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

            {modal.type === "importFile" && (
                <ImportLayerFileDialog isOpen onClose={closeModal} layerId={modal.layerId} />
            )}

            {modal.type === "exportLayers" && (
                <ExportLayersDialog
                    isOpen
                    onClose={closeModal}
                    subjects={modal.subjects}
                    title={t("layers.title")}
                />
            )}

            {modal.type === "deleteLayer" && (
                <ConfirmArchiveDialog
                    isOpen
                    onClose={closeModal}
                    name={modal.layer.layerName}
                    onConfirm={() => handleDeleteLayer(modal.layer)}
                    finalFocusEl={() => tableRef.current}
                />
            )}

            {modal.type === "duplicateLayer" && (
                <DuplicateLayerDialog
                    key={modal.layer.layerId}
                    isOpen
                    onClose={closeModal}
                    layerId={modal.layer.layerId}
                    layerName={modal.layer.layerName}
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
