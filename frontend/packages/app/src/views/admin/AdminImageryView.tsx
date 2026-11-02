// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Badge, Flex, Text } from "@chakra-ui/react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useCallback, useState, useMemo } from "react";
import { useTranslation } from "react-i18next";
import { FaBan, FaCheck, FaCog, FaFilter, FaPlus, FaStar, FaTrash } from "react-icons/fa";
import {
    ColumnFilterBuilder,
    ResourceTable,
    OcTag,
    TypeColorLabel,
    createColumnHelper,
    toFilterableColumns,
    type ResourceTableToolbar,
    type ToolbarAction,
    Tooltip,
} from "@opencop/ui";
import { ViewDrawerProvider, useViewDrawerContext } from "~/features/drawers/ViewDrawerContext";
import { useResourceTableState } from "~/features/admin/use-resource-table-state.hook";
import { useSelectedRows } from "~/features/admin/use-selected-rows.hook";
import { tileQueries, tileMutations } from "~/features/imagery/imagery.queries";
import { useToast } from "~/features/notifications/use-toast.hook";
import { canBePrimary } from "~/features/imagery/imagery.constants";
import { getTileConfig, getTileKind } from "~/features/imagery/tile-config.util";
import { isWeatherTile } from "~/features/weather/weather.util";
import { ScopeIndicator } from "~/features/layers/ScopeIndicator";
import { AddTileDialog } from "~/features/imagery/dialogs/AddTileDialog";
import { ImageryTileDrawer } from "~/features/imagery/ImageryTileDrawer";
import type { ImageryTileDrawerState } from "~/features/imagery/ImageryTileDrawer";
import type { MapTileAccessScope } from "@opencop/api";

interface TileRow {
    id: string;
    name: string;
    kind: string;
    displayKind: string;
    description: string;
    url: string;
    primary: boolean;
    enabled: boolean;
    accessScope: MapTileAccessScope;
    tileType: "BASEMAP" | "WEATHER";
}

const columnHelper = createColumnHelper<TileRow>();

/** Module-level so the selection hook keeps a stable identity across renders. */
const getTileRowId = (row: TileRow) => row.id;

export function AdminImageryView() {
    return (
        <ViewDrawerProvider<{ type: "none" }> initialModal={{ type: "none" }}>
            <AdminImageryViewContent />
        </ViewDrawerProvider>
    );
}

function AdminImageryViewContent() {
    const { t } = useTranslation();
    const { showToast } = useToast();
    const queryClient = useQueryClient();
    const { data: tiles = [], isLoading, isFetching, refetch } = useQuery(tileQueries.adminList());
    const { mutateAsync: makePrimary } = useMutation(tileMutations.makePrimary(queryClient));
    const { mutateAsync: deleteTile } = useMutation(tileMutations.delete(queryClient));
    const { mutateAsync: updateTile } = useMutation(tileMutations.update(queryClient));

    const { selectedId: drawerTileId, openDrawer } = useViewDrawerContext<{ type: "none" }>();
    const imageryDrawerState: ImageryTileDrawerState = useMemo(
        () => (drawerTileId ? { type: "tile", entity: drawerTileId } : { type: "closed" }),
        [drawerTileId],
    );
    const tableState = useResourceTableState();
    const { resetSelection } = tableState;
    const [isAddOpen, setIsAddOpen] = useState(false);

    const formattedTiles = useMemo(
        () =>
            tiles
                .map((tile) => {
                    const tileKind = getTileKind(tile);
                    const tileConfig = getTileConfig(tile);

                    return {
                        id: tile.id,
                        name: tile.name,
                        kind: tileKind,
                        displayKind:
                            tileKind === "TERRAIN_MESH"
                                ? (tileConfig as { encoding: string }).encoding
                                : tileKind,
                        description: tile.description || "-",
                        url: tile.url,
                        primary: tile.primary,
                        enabled: tile.enabled,
                        accessScope: tile.accessScope,
                        tileType: (isWeatherTile(tile) ? "WEATHER" : "BASEMAP") as
                            | "WEATHER"
                            | "BASEMAP",
                    };
                })
                .sort((a, b) => a.name.localeCompare(b.name)),
        [tiles],
    );

    // Retains rows once ticked, so narrowing never shrinks what the bulk actions cover.
    const { selectedRows, onRowSelectionChange } = useSelectedRows(
        formattedTiles,
        getTileRowId,
        tableState.rowSelection,
        tableState.onRowSelectionChange,
    );

    const columns = useMemo(
        () => [
            columnHelper.accessor("name", {
                header: t("adminImagery.name"),
                cell: (info) => {
                    const row = info.row.original;
                    const isWeather = row.tileType === "WEATHER";
                    return (
                        <Flex align="center" gap={2}>
                            <TypeColorLabel
                                abbr={isWeather ? "W" : "B"}
                                color={isWeather ? "teal" : "violet"}
                                label={t(
                                    `adminImagery.types.${row.tileType.toLowerCase()}` as never,
                                )}
                            />
                            {info.getValue()}
                            {row.primary && (
                                <OcTag size="sm" colorPalette="cyan" startElement={<FaStar />}>
                                    {t("adminImagery.primary")}
                                </OcTag>
                            )}
                        </Flex>
                    );
                },
                meta: {
                    filterDef: {
                        label: t("adminImagery.name"),
                        type: "string" as const,
                    },
                },
                filterFn: "ocStringFilter",
            }),
            columnHelper.accessor("tileType", {
                header: t("adminImagery.type"),
                cell: (info) => (
                    <Text fontSize="sm" color="fg.muted">
                        {t(`adminImagery.types.${info.getValue().toLowerCase()}` as never)}
                    </Text>
                ),
                enableSorting: false,
                size: 120,
                meta: {
                    filterDef: {
                        label: t("adminImagery.type"),
                        type: "enum" as const,
                        options: [
                            { label: t("adminImagery.types.basemap"), value: "BASEMAP" },
                            { label: t("adminImagery.types.weather"), value: "WEATHER" },
                        ],
                    },
                },
                filterFn: "ocEnumFilter",
            }),
            columnHelper.accessor("enabled", {
                header: t("adminImagery.status"),
                cell: (info) =>
                    info.getValue() ? (
                        <Badge colorPalette="green" variant="subtle">
                            {t("adminImagery.enabled")}
                        </Badge>
                    ) : (
                        <Badge colorPalette="gray" variant="subtle">
                            {t("adminImagery.disabled")}
                        </Badge>
                    ),
                enableSorting: false,
                size: 120,
                meta: {
                    filterDef: {
                        label: t("adminImagery.status"),
                        type: "enum" as const,
                        options: [
                            { label: t("adminImagery.enabled"), value: "true" },
                            { label: t("adminImagery.disabled"), value: "false" },
                        ],
                    },
                },
                filterFn: "ocEnumFilter",
            }),
            columnHelper.accessor("accessScope", {
                header: t("adminImagery.accessScope"),
                cell: (info) => <ScopeIndicator scope={info.getValue()} />,
                enableSorting: false,
                size: 100,
                meta: {
                    filterDef: {
                        label: t("adminImagery.accessScope"),
                        type: "enum" as const,
                        options: [
                            { label: t("adminLayers.scopePrivate"), value: "PRIVATE" },
                            { label: t("adminLayers.scopeGlobal"), value: "GLOBAL" },
                        ],
                    },
                },
                filterFn: "ocEnumFilter",
            }),
            columnHelper.accessor("displayKind", {
                header: t("adminImagery.kind"),
                cell: (info) => {
                    const displayKind = info.getValue();
                    return (
                        <Text fontSize="sm" color="fg.muted">
                            {t(`adminImagery.kinds.${displayKind.toLowerCase()}` as never)}
                        </Text>
                    );
                },
                enableSorting: false,
                size: 120,
                meta: {
                    filterDef: {
                        label: t("adminImagery.kind"),
                        type: "enum" as const,
                        options: [
                            { label: t("adminImagery.kinds.vector"), value: "VECTOR" },
                            { label: t("adminImagery.kinds.raster"), value: "RASTER" },
                            { label: t("adminImagery.kinds.wms"), value: "WMS" },
                            { label: t("adminImagery.kinds.wmts"), value: "WMTS" },
                            { label: t("adminImagery.kinds.raster_dem"), value: "RASTER_DEM" },
                            { label: t("adminImagery.kinds.tile_3d"), value: "TILE_3D" },
                            { label: t("adminImagery.kinds.terrain_rgb"), value: "TERRAIN_RGB" },
                            { label: t("adminImagery.kinds.terrarium"), value: "TERRARIUM" },
                            { label: t("adminImagery.kinds.weather"), value: "WEATHER" },
                        ],
                    },
                },
                filterFn: "ocEnumFilter",
            }),
            columnHelper.accessor("description", {
                header: t("adminImagery.description"),
                cell: (info) => {
                    const description = info.getValue();
                    const maxLength = 50;
                    if (description.length > maxLength) {
                        return (
                            <Tooltip
                                content={description}
                                contentProps={{
                                    maxW: "300px",
                                    whiteSpace: "normal",
                                    textTransform: "none",
                                }}
                            >
                                <Text cursor="default" color="fg.muted">
                                    {description.slice(0, maxLength)}...
                                </Text>
                            </Tooltip>
                        );
                    }
                    return <Text color="fg.muted">{description}</Text>;
                },
                meta: {
                    filterDef: {
                        label: t("adminImagery.description"),
                        type: "string" as const,
                    },
                },
                filterFn: "ocStringFilter",
            }),
        ],
        [t],
    );

    const setEnabled = useCallback(
        async (rows: TileRow[], enabled: boolean) => {
            const results = await Promise.allSettled(
                rows.map((row) => updateTile({ id: row.id, data: { enabled } })),
            );
            const failed = results.filter((r) => r.status === "rejected").length;
            if (failed > 0) {
                showToast({
                    type: "error",
                    title: t(
                        enabled
                            ? "adminImagery.enablePartialFailure"
                            : "adminImagery.disablePartialFailure",
                        { count: failed },
                    ),
                });
            }
        },
        [updateTile, showToast, t],
    );

    const selectionActions = useMemo((): ToolbarAction[] => {
        const actions: ToolbarAction[] = [];

        // Make Primary: contextual — only appears when exactly one qualifying tile is selected
        const selectedTile =
            selectedRows.length === 1 ? tiles.find((t) => t.id === selectedRows[0].id) : undefined;
        if (selectedTile && canBePrimary(selectedTile) && !selectedRows[0].primary) {
            actions.push({
                label: t("adminImagery.makePrimary"),
                icon: <FaStar />,
                onClick: async () => {
                    await makePrimary(selectedRows[0].id);
                    resetSelection();
                },
            });
        }

        // Disable / Enable: contextual, so a mixed selection offers both directions
        const toDisable = selectedRows.filter((row) => row.enabled);
        const toEnable = selectedRows.filter((row) => !row.enabled);
        const blockedByPrimary = toDisable.some((row) => row.primary);

        if (toDisable.length > 0) {
            actions.push({
                label: t("adminImagery.disable"),
                icon: <FaBan />,
                disabled: blockedByPrimary,
                disabledReason: blockedByPrimary
                    ? t("adminImagery.cannotDisablePrimary")
                    : undefined,
                confirm: {
                    title: t("adminImagery.confirmDisableTitle"),
                    message: t("adminImagery.confirmDisableMessage", { count: toDisable.length }),
                    confirmLabel: t("adminImagery.disable"),
                    isDestructive: false,
                },
                onClick: async () => {
                    await setEnabled(toDisable, false);
                    resetSelection();
                },
            });
        }

        if (toEnable.length > 0) {
            actions.push({
                label: t("adminImagery.enable"),
                icon: <FaCheck />,
                onClick: async () => {
                    await setEnabled(toEnable, true);
                    resetSelection();
                },
            });
        }

        // Delete: always visible, grayed until at least one row is selected. The primary is
        // excluded for the same reason it can't be disabled — the catalog would be left with no
        // basemap to fall back to, and the server rejects it.
        const deletingPrimary = selectedRows.some((row) => row.primary);

        actions.push({
            label: t("common.delete"),
            icon: <FaTrash />,
            disabled: selectedRows.length === 0 || deletingPrimary,
            disabledReason: deletingPrimary ? t("adminImagery.cannotDeletePrimary") : undefined,
            colorPalette: "red",
            confirm: {
                title: t("adminImagery.confirmDeleteTitle"),
                message: t("adminImagery.confirmDeleteMessage"),
                confirmLabel: t("common.delete"),
            },
            onClick: async () => {
                const results = await Promise.allSettled(selectedRows.map((r) => deleteTile(r.id)));
                const failed = results.filter((r) => r.status === "rejected").length;
                if (failed > 0) {
                    showToast({
                        type: "error",
                        title: t("adminImagery.deletePartialFailure", { count: failed }),
                    });
                }
                resetSelection();
            },
        });

        return actions;
    }, [selectedRows, tiles, makePrimary, deleteTile, setEnabled, t, showToast, resetSelection]);

    const toolbar: ResourceTableToolbar = useMemo(
        () => ({
            onRefresh: refetch,
            isRefreshing: isFetching,
            actions: [
                {
                    label: t("common.create"),
                    icon: <FaPlus />,
                    showLabel: true,
                    onClick: () => setIsAddOpen(true),
                },
                {
                    label: t("resourceTable.manageView"),
                    icon: <FaCog />,
                    showLabel: true,
                    menuItems: [
                        {
                            label: t("resourceTable.default"),
                            icon: <FaFilter />,
                            onClick: () => {
                                tableState.resetView();
                            },
                        },
                    ],
                },
            ],
            selectionActions,
        }),
        [refetch, isFetching, t, selectionActions, tableState],
    );

    const filterableColumns = useMemo(() => toFilterableColumns(columns), [columns]);

    return (
        <Flex height="100%" width="100%" direction="column">
            <ResourceTable<TileRow>
                aria-label={t("adminImagery.title")}
                data={formattedTiles}
                columns={columns}
                isLoading={isLoading}
                isFetching={isFetching}
                enableRowSelection
                getRowId={getTileRowId}
                {...tableState}
                onRowSelectionChange={onRowSelectionChange}
                selectedCount={selectedRows.length}
                onRowClick={(row) => openDrawer(row.id)}
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
                detailDrawer={<ImageryTileDrawer drawerState={imageryDrawerState} />}
            />
            <AddTileDialog isOpen={isAddOpen} onClose={() => setIsAddOpen(false)} />
        </Flex>
    );
}
