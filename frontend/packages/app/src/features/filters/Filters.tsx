// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { VStack, Flex, Box, HStack, Text } from "@chakra-ui/react";
import { useTranslation } from "react-i18next";
import { useQueries, useQuery } from "@tanstack/react-query";
import { Fragment, useCallback, useMemo, useRef, useState } from "react";
import {
    AccentHeading,
    SearchInput,
    ResourceTypeFilterTab,
    ResizeHandle,
    Divider,
    computeFlexSizes,
} from "@opencop/ui";
import type { ResourceTypeFilterOption } from "@opencop/ui";
import { useHistoricalMode } from "~/features/historical/data-plane/historical.store";
import { layerQueries } from "~/features/layers/layers.queries";
import { layerGroupQueries } from "~/features/layer-groups/layer-groups.queries";
import { dataSourceQueries } from "~/features/data-sources/data-sources.queries";
import { useFilterPanelStore } from "./filter-panel.store";
import { dataSourceMatchesSearch, isOffered } from "./panel-exclusion.util";
import { usePanelExcludedIds } from "./use-panel-excluded-ids.hook";
import type { SortOrder, VisibilityFilter } from "./filter-panel.store";
import { useShallow } from "zustand/react/shallow";
import { FilterSection } from "./FilterSection";
import { SourceFilter } from "./SourceFilter";
import { LayerGroupFilter } from "./LayerGroupFilter";
import { useFilterPanelActions } from "./filter-panel.actions";
import { difference, partition, union } from "lodash";

const SORT_CYCLE: SortOrder[] = ["none", "name-asc", "name-desc"];

function nextSort(current: SortOrder): SortOrder {
    const idx = SORT_CYCLE.indexOf(current);
    return SORT_CYCLE[(idx + 1) % SORT_CYCLE.length];
}

/**
 * Generic search → visibility filter → sort pipeline. `matchesSearchExtra` lets an item match on
 * more than its own name (e.g. a layer matching via its data sources).
 */
function filterAndSort<T>(
    items: T[],
    getName: (item: T) => string,
    getId: (item: T) => string,
    searchTerm: string,
    visibilityFilter: VisibilityFilter,
    sortOrder: SortOrder,
    isVisible: (id: string) => boolean,
    matchesSearchExtra?: (item: T) => boolean,
): T[] {
    const lowerSearch = searchTerm.toLowerCase();
    let filtered = items;

    if (searchTerm) {
        filtered = filtered.filter(
            (item) =>
                getName(item).toLowerCase().includes(lowerSearch) ||
                (matchesSearchExtra?.(item) ?? false),
        );
    }

    switch (visibilityFilter) {
        case "all":
        case "pinned": // pin membership is applied by the caller, after sorting
            break;
        case "visible":
            filtered = filtered.filter((item) => isVisible(getId(item)));
            break;
        case "hidden":
            filtered = filtered.filter((item) => !isVisible(getId(item)));
            break;
        default:
            visibilityFilter satisfies never;
    }

    if (sortOrder === "none") return filtered;

    const sorted = [...filtered];
    switch (sortOrder) {
        case "name-asc":
            sorted.sort((a, b) => getName(a).localeCompare(getName(b)));
            break;
        case "name-desc":
            sorted.sort((a, b) => getName(b).localeCompare(getName(a)));
            break;
    }

    return sorted;
}

type SectionId = "layers" | "data-sources";

const HANDLE_HEIGHT = 8; // matches sizeMap.sm.handleH in ResizeHandle
const LAYERS = "layers" as const;
const DATA_SOURCES = "data-sources" as const;
/** Open state of the Data Sources section until the user has toggled it. */
const DATA_SOURCES_DEFAULT_OPEN = false;

/**
 * Filters panel. In historical mode the live-layer visibility this panel
 * controls doesn't apply (historical elements render from their own pipeline),
 * so the panel is replaced with an unavailable notice — gated in this thin
 * wrapper so `FiltersContent`'s data hooks never run conditionally.
 */
export function Filters() {
    const historicalMode = useHistoricalMode();
    if (historicalMode === "historical") return <FiltersUnavailable />;
    return <FiltersContent />;
}

/** Shown in place of the panel while historical mode is active. */
function FiltersUnavailable() {
    const { t } = useTranslation();
    return (
        <VStack w="full" h="full" align="start">
            <Box mb="2">
                <AccentHeading>{t("mapbar.layersAndSources")}</AccentHeading>
            </Box>
            <Flex flex="1" w="full" align="center" justify="center" px="6">
                <Text fontSize="sm" color="fg.muted" textAlign="center">
                    {t(
                        "filters.unavailableHistorical",
                        "Layer filters are unavailable in historical mode",
                    )}
                </Text>
            </Flex>
        </VStack>
    );
}

/**
 * Filters panel - Shows layer groups (with indented layers) and ungrouped layers,
 * plus data sources, with visibility toggles, search, sort, and hide/show all controls.
 */
function FiltersContent() {
    const { t } = useTranslation();

    // ── Data fetching ──────────────────────────────────────────────────────────

    const { data: allLayers, isLoading: isLoadingLayers } = useQuery(layerQueries.myList());
    const { data: layerGroups = [], isLoading: isLoadingGroups } = useQuery(
        layerGroupQueries.myList(),
    );
    const { data: allDataSources, isLoading: isLoadingDataSources } = useQuery(
        dataSourceQueries.myList(),
    );

    // Layers and sources the user removed from this panel never reach the derived lists below,
    // so the bulk show/hide controls can't act on them either.
    const excludedSet = usePanelExcludedIds();
    const layers = useMemo(
        () => (allLayers ?? []).filter((l) => isOffered(excludedSet, l.layerId)),
        [allLayers, excludedSet],
    );
    const dataSources = useMemo(
        () => (allDataSources ?? []).filter((ds) => isOffered(excludedSet, ds.serverId)),
        [allDataSources, excludedSet],
    );

    // ── Store state ────────────────────────────────────────────────────────────

    const {
        hiddenSources,
        textVisibleSources,
        pinnedSources,
        layerSortOrder,
        dsSortOrder,
        visibilityFilter,
        expandedSections,
    } = useFilterPanelStore(
        useShallow((s) => ({
            hiddenSources: s.hiddenSources,
            textVisibleSources: s.textVisibleSources,
            pinnedSources: s.pinnedSources,
            layerSortOrder: s.layerSortOrder,
            dsSortOrder: s.dataSourceSortOrder,
            visibilityFilter: s.visibilityFilter,
            expandedSections: s.expandedSections,
        })),
    );
    const setTextVisibleSources = useFilterPanelStore((s) => s.setTextVisibleSources);

    const hiddenSet = useMemo(() => new Set(hiddenSources), [hiddenSources]);
    const {
        allVisible,
        anySyncing,
        anyVisible,
        showSources,
        hideSources,
        hideLayerGroup,
        showLayerGroup,
        setLayerSortOrder,
        setDataSourceSortOrder,
        setVisibilityFilter,
    } = useFilterPanelActions();

    const [searchTerm, setSearchTerm] = useState("");

    const visibilityFilterOptions: ResourceTypeFilterOption<VisibilityFilter>[] = useMemo(
        () => [
            { label: t("filters.filterAll"), value: "all" },
            { label: t("filters.filterVisible"), value: "visible" },
            { label: t("filters.filterHidden"), value: "hidden" },
            { label: t("filters.filterPinned"), value: "pinned" },
        ],
        [t],
    );

    const layerIds = useMemo(() => layers.map((info) => info.layerId), [layers]);
    const dataSourceIds = useMemo(() => dataSources.map((info) => info.serverId), [dataSources]);

    // ── Data sources per layer (only fetched while searching) ──────────────────

    const searching = searchTerm !== "";
    const combineLayerDataSources = useCallback(
        (results: { data?: { serverId: string; serverName: string | null }[] }[]) =>
            new Map(
                layerIds.map((id, i) => [
                    id,
                    (results[i]?.data ?? []).filter((ds) => isOffered(excludedSet, ds.serverId)),
                ]),
            ),
        [layerIds, excludedSet],
    );
    const layerDataSourcesById = useQueries({
        queries: layerIds.map((id) => ({ ...layerQueries.dataSources(id), enabled: searching })),
        combine: combineLayerDataSources,
    });
    const lowerSearch = searchTerm.toLowerCase();
    const layerMatchesViaDataSource = useCallback(
        (layerId: string) =>
            searching &&
            (layerDataSourcesById.get(layerId) ?? []).some((ds) =>
                dataSourceMatchesSearch(ds, lowerSearch),
            ),
        [searching, layerDataSourcesById, lowerSearch],
    );

    // ── Group layer maps (from embedded layers in myList response) ────────────

    const groupLayerMap = useMemo(
        () =>
            new Map(
                layerGroups.map((g) => [
                    g.id,
                    g.layers
                        .filter((l) => isOffered(excludedSet, l.id))
                        .map((l) => ({
                            layerId: l.id,
                            layerName: l.name,
                            accessScope: l.accessScope,
                        })),
                ]),
            ),
        [layerGroups, excludedSet],
    );

    const groupedLayerIdSet = useMemo(
        () => new Set(Array.from(groupLayerMap.values()).flatMap((ls) => ls.map((l) => l.layerId))),
        [groupLayerMap],
    );

    // Only ungrouped IDs go to showSources/hideSources; grouped layers are handled
    // exclusively by showLayerGroup/hideLayerGroup to avoid double-processing.
    const ungroupedLayerIds = useMemo(
        () => layerIds.filter((id) => !groupedLayerIdSet.has(id)),
        [layerIds, groupedLayerIdSet],
    );

    // ── Loading ────────────────────────────────────────────────────────────────

    const isLoadingLayersAll = isLoadingLayers || isLoadingGroups;

    // ── Derived lists ──────────────────────────────────────────────────────────

    const displayLayers = useMemo(
        () =>
            filterAndSort(
                layers,
                (l) => l.layerName,
                (l) => l.layerId,
                searchTerm,
                visibilityFilter,
                layerSortOrder,
                (id) => !hiddenSet.has(id),
                (l) => layerMatchesViaDataSource(l.layerId),
            ),
        [
            layers,
            searchTerm,
            visibilityFilter,
            layerSortOrder,
            hiddenSet,
            layerMatchesViaDataSource,
        ],
    );

    // Groups matching search (by group name OR any constituent layer name)
    const displayGroups = useMemo(() => {
        // Pinned mode lists pinned items only, and those render outside their groups.
        if (visibilityFilter === "pinned") return [];
        const lower = searchTerm.toLowerCase();
        return layerGroups.flatMap((g) => {
            const gLayers = groupLayerMap.get(g.id) ?? [];
            const toggleLayerIds = gLayers.map((l) => l.layerId);
            // A group whose layers were all removed from the panel has nothing left to show.
            if (gLayers.length === 0 && g.layers.length > 0) return [];
            if (searchTerm === "") {
                return [{ group: g, layers: gLayers, toggleLayerIds, autoExpand: false }];
            }
            const nameMatches = g.name.toLowerCase().includes(lower);
            const layerMatches = gLayers.filter(
                (l) =>
                    l.layerName.toLowerCase().includes(lower) ||
                    layerMatchesViaDataSource(l.layerId),
            );
            if (!nameMatches && layerMatches.length === 0) return [];
            // If matched by group name, show all layers; if matched by layer, show only matches
            return [
                {
                    group: g,
                    layers: nameMatches ? gLayers : layerMatches,
                    toggleLayerIds,
                    autoExpand: layerMatches.length > 0,
                },
            ];
        });
    }, [layerGroups, groupLayerMap, searchTerm, visibilityFilter, layerMatchesViaDataSource]);

    const pinnedSet = useMemo(() => new Set(pinnedSources), [pinnedSources]);

    /**
     * Pinned layers float to the top, alphabetical regardless of the sort toggle. A grouped pinned
     * layer is deliberately listed here and in its group: the group's eye toggles every member, so
     * moving it out would leave it hidden or shown by a control it no longer sits under. Pin,
     * visibility and text state sync through the store; row-local state (object list, nested
     * chevron) is per copy.
     */
    const pinnedLayers = useMemo(
        () =>
            displayLayers
                .filter((l) => pinnedSet.has(l.layerId))
                .sort((a, b) => a.layerName.localeCompare(b.layerName)),
        [displayLayers, pinnedSet],
    );

    // Ungrouped: matched layers that don't belong to any group
    const ungroupedLayers = useMemo(
        () =>
            visibilityFilter === "pinned"
                ? []
                : displayLayers.filter(
                      (l) => !groupedLayerIdSet.has(l.layerId) && !pinnedSet.has(l.layerId),
                  ),
        [displayLayers, groupedLayerIdSet, pinnedSet, visibilityFilter],
    );

    const filteredDataSources = useMemo(
        () =>
            filterAndSort(
                dataSources,
                (ds) => ds.serverName ?? ds.serverId,
                (ds) => ds.serverId,
                searchTerm,
                visibilityFilter,
                dsSortOrder,
                (id) => !hiddenSet.has(id),
            ),
        [dataSources, searchTerm, visibilityFilter, dsSortOrder, hiddenSet],
    );

    const [pinnedDataSources, otherDataSources] = useMemo(() => {
        const [pinned, rest] = partition(filteredDataSources, (ds) => pinnedSet.has(ds.serverId));
        pinned.sort((a, b) =>
            (a.serverName ?? a.serverId).localeCompare(b.serverName ?? b.serverId),
        );
        return [pinned, visibilityFilter === "pinned" ? [] : rest] as const;
    }, [filteredDataSources, pinnedSet, visibilityFilter]);

    // One ordered list per section so pinning reorders a row instead of remounting it, which
    // would reset its expansion and drop keyboard focus.
    const layerRows = useMemo(
        () => [
            ...pinnedLayers.map((layer) => ({ type: "layer" as const, layer, pinnedBlock: true })),
            ...displayGroups.map((entry) => ({ type: "group" as const, entry })),
            ...ungroupedLayers.map((layer) => ({
                type: "layer" as const,
                layer,
                pinnedBlock: false,
            })),
        ],
        [pinnedLayers, displayGroups, ungroupedLayers],
    );
    const dataSourceRows = useMemo(
        () => [
            ...pinnedDataSources.map((ds) => ({ ds, pinnedBlock: true })),
            ...otherDataSources.map((ds) => ({ ds, pinnedBlock: false })),
        ],
        [pinnedDataSources, otherDataSources],
    );

    // In Pinned mode the panel lists only pinned rows, so the bulk controls act on just those.
    const pinnedMode = visibilityFilter === "pinned";
    const listedLayerIds = useMemo(
        () => (pinnedMode ? pinnedLayers.map((l) => l.layerId) : layerIds),
        [pinnedMode, pinnedLayers, layerIds],
    );
    const listedDataSourceIds = useMemo(
        () => (pinnedMode ? pinnedDataSources.map((ds) => ds.serverId) : dataSourceIds),
        [pinnedMode, pinnedDataSources, dataSourceIds],
    );

    // ── Bulk visibility ────────────────────────────────────────────────────────

    const textVisibleSet = useMemo(() => new Set(textVisibleSources), [textVisibleSources]);
    const anyLayerTextVisible = listedLayerIds.some((id) => textVisibleSet.has(id));
    const anyLayerTextHidden = listedLayerIds.some((id) => !textVisibleSet.has(id));
    const anyDSTextVisible = listedDataSourceIds.some((id) => textVisibleSet.has(id));
    const anyDSTextHidden = listedDataSourceIds.some((id) => !textVisibleSet.has(id));

    // Union/filter rather than replace: preserves visible state of the other
    // section and of sources not currently listed (e.g. persisted from a
    // prior session).
    const showAllText = (ids: string[]) => () => setTextVisibleSources((prev) => union(prev, ids));
    const hideAllText = (ids: string[]) => () => {
        setTextVisibleSources((prev) => difference(prev, ids));
    };

    const allLayersVisible = allVisible(listedLayerIds);
    const anyLayerSyncing = anySyncing(listedLayerIds);
    const allDSVisible = allVisible(listedDataSourceIds);
    const anyDSSyncing = anySyncing(listedDataSourceIds);
    const anyLayersVisible = anyVisible(listedLayerIds);
    const anyDSVisible = anyVisible(listedDataSourceIds);

    // ── Resize state ───────────────────────────────────────────────────────────

    const [sectionSizes, setSectionSizes] = useState<Record<SectionId, number>>({
        layers: 1,
        "data-sources": 1,
    });
    const containerRef = useRef<HTMLDivElement | null>(null);

    const layersOpen = expandedSections[LAYERS] ?? true;
    const dataSourcesOpen = expandedSections[DATA_SOURCES] ?? DATA_SOURCES_DEFAULT_OPEN;

    const layersDsResizeHandler = (delta: number) => {
        const el = containerRef.current;
        if (!el) return;
        const totalH = el.clientHeight - HANDLE_HEIGHT;
        setSectionSizes(
            (prev) => computeFlexSizes(prev, delta, LAYERS, DATA_SOURCES, totalH) ?? prev,
        );
    };

    const showAllLayers = () => {
        if (pinnedMode) return showSources(listedLayerIds);
        showSources(ungroupedLayerIds);
        layerGroups.forEach((g) => {
            const ids = (groupLayerMap.get(g.id) ?? []).map((l) => l.layerId);
            showLayerGroup(g.id, ids);
        });
    };

    const hideAllLayers = () => {
        if (pinnedMode) return hideSources(listedLayerIds);
        hideSources(ungroupedLayerIds);
        layerGroups.forEach((g) => {
            const ids = (groupLayerMap.get(g.id) ?? []).map((l) => l.layerId);
            hideLayerGroup(g.id, ids);
        });
    };

    return (
        <VStack w="full" h="full" align="start">
            {/* Flex with space-between intentional: aligns header row with other panels.
                A second element (e.g. action button) will slot in here once the shared
                PanelHeading primitive lands. */}
            <Flex
                direction="row"
                justifyContent="space-between"
                alignItems="center"
                mb="2"
                w="full"
                flexWrap="nowrap"
            >
                <AccentHeading>{t("mapbar.layersAndSources")}</AccentHeading>
            </Flex>

            <HStack gap={2} px="1" mb="1" w="full">
                <Box flex={1}>
                    <SearchInput
                        value={searchTerm}
                        onChange={setSearchTerm}
                        size="sm"
                        placeholder={t("filters.searchPlaceholder")}
                    />
                </Box>
                <ResourceTypeFilterTab
                    options={visibilityFilterOptions}
                    value={visibilityFilter}
                    onChange={setVisibilityFilter}
                    aria-label={t("filters.title")}
                />
            </HStack>

            <Flex
                ref={containerRef}
                direction="column"
                flex="1 1 0"
                minH="0px"
                overflow="hidden"
                px="1"
                pb="1"
                userSelect="none"
                w="full"
            >
                <FilterSection
                    id={LAYERS}
                    label={t("sidebar.layers")}
                    emptyMessage={pinnedMode ? t("filters.noPinned") : t("filters.noLayers")}
                    isLoading={isLoadingLayersAll}
                    itemCount={layerRows.length}
                    allVisible={allLayersVisible}
                    anyVisible={anyLayersVisible}
                    anySyncing={anyLayerSyncing}
                    anyTextVisible={anyLayerTextVisible}
                    anyTextHidden={anyLayerTextHidden}
                    sortOrder={layerSortOrder}
                    sortDisabled={pinnedMode}
                    flexSize={sectionSizes[LAYERS]}
                    showAllItems={showAllLayers}
                    hideAllItems={hideAllLayers}
                    showAllText={showAllText(listedLayerIds)}
                    hideAllText={hideAllText(listedLayerIds)}
                    onToggleSort={() => setLayerSortOrder(nextSort(layerSortOrder))}
                >
                    {layerRows.map((row, i) => {
                        const divider = i === pinnedLayers.length - 1 && i < layerRows.length - 1;
                        return (
                            <Fragment
                                key={
                                    row.type === "group"
                                        ? `group:${row.entry.group.id}`
                                        : `layer:${row.layer.layerId}`
                                }
                            >
                                {row.type === "group" ? (
                                    <LayerGroupFilter
                                        group={row.entry.group}
                                        layers={row.entry.layers}
                                        toggleLayerIds={row.entry.toggleLayerIds}
                                        defaultExpanded={row.entry.autoExpand}
                                        searchTerm={searchTerm}
                                    />
                                ) : (
                                    <SourceFilter
                                        sourceId={row.layer.layerId}
                                        sourceName={row.layer.layerName}
                                        accessScope={row.layer.accessScope}
                                        kind="layer"
                                        searchTerm={searchTerm}
                                        inPinnedBlock={row.pinnedBlock}
                                    />
                                )}
                                {divider && <Divider my={1} />}
                            </Fragment>
                        );
                    })}
                </FilterSection>

                {layersOpen && dataSourcesOpen && (
                    <ResizeHandle
                        onResize={layersDsResizeHandler}
                        label={t("filters.resizeHandle")}
                        valueMin={0}
                        valueMax={100}
                        valueNow={Math.round(
                            (sectionSizes[LAYERS] /
                                (sectionSizes[LAYERS] + sectionSizes[DATA_SOURCES])) *
                                100,
                        )}
                    />
                )}

                <FilterSection
                    id={DATA_SOURCES}
                    defaultExpanded={DATA_SOURCES_DEFAULT_OPEN}
                    label={t("sidebar.data-sources")}
                    emptyMessage={pinnedMode ? t("filters.noPinned") : t("filters.noDataSources")}
                    isLoading={isLoadingDataSources}
                    itemCount={dataSourceRows.length}
                    allVisible={allDSVisible}
                    anyVisible={anyDSVisible}
                    anySyncing={anyDSSyncing}
                    anyTextVisible={anyDSTextVisible}
                    anyTextHidden={anyDSTextHidden}
                    sortOrder={dsSortOrder}
                    sortDisabled={pinnedMode}
                    flexSize={sectionSizes[DATA_SOURCES]}
                    showAllItems={() => showSources(listedDataSourceIds)}
                    hideAllItems={() => hideSources(listedDataSourceIds)}
                    showAllText={showAllText(listedDataSourceIds)}
                    hideAllText={hideAllText(listedDataSourceIds)}
                    onToggleSort={() => setDataSourceSortOrder(nextSort(dsSortOrder))}
                >
                    {dataSourceRows.map(({ ds, pinnedBlock }, i) => (
                        <Fragment key={ds.serverId}>
                            <SourceFilter
                                sourceId={ds.serverId}
                                sourceName={ds.serverName ?? ds.serverId}
                                kind="dataSource"
                                inPinnedBlock={pinnedBlock}
                            />
                            {i === pinnedDataSources.length - 1 &&
                                i < dataSourceRows.length - 1 && <Divider my={1} />}
                        </Fragment>
                    ))}
                </FilterSection>
            </Flex>
        </VStack>
    );
}
