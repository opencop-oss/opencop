// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useElementActions, useSourceActions, useSources } from "@opencop/ingest";
import { useFilterPanelStore } from "./filter-panel.store";
import type { SortOrder, VisibilityFilter } from "./filter-panel.store";
import { useCallback, useMemo } from "react";

export interface FilterPanelActions {
    hideSource(sourceId: string): void;
    showSource(sourceId: string): void;
    allVisible(ids: string[]): boolean;
    anySyncing(ids: string[]): boolean;
    anyVisible(ids: string[]): boolean;
    showSources(ids: string[]): void;
    hideSources(ids: string[]): void;
    hideLayerGroup(groupId: string, layerIds: string[]): void;
    showLayerGroup(groupId: string, layerIds: string[]): void;
    setLayerSortOrder(order: SortOrder): void;
    setDataSourceSortOrder(order: SortOrder): void;
    setVisibilityFilter(filter: VisibilityFilter): void;
}

export function useFilterPanelActions(): FilterPanelActions {
    const { hideSource: hideElements, showSource: showElements } = useElementActions();
    const { enable, disable } = useSourceActions();
    const sourceStates = useSources((states) => states);
    const hiddenSources = useFilterPanelStore((state) => state.hiddenSources);
    const hiddenSet = useMemo(() => new Set(hiddenSources), [hiddenSources]);

    const hideSource = useCallback(
        (sourceId: string) => {
            useFilterPanelStore.getState().setHiddenSources((prev) => [...prev, sourceId]);
            hideElements(sourceId);
            disable(sourceId);
        },
        [disable, hideElements],
    );

    const showSource = useCallback(
        (sourceId: string) => {
            useFilterPanelStore
                .getState()
                .setHiddenSources((prev) => prev.filter((id) => id !== sourceId));
            showElements(sourceId);
            enable(sourceId);
        },
        [enable, showElements],
    );

    const allVisible = useCallback(
        (ids: string[]) => {
            return ids.every((id) => !hiddenSet.has(id));
        },
        [hiddenSet],
    );

    const anySyncing = useCallback(
        (ids: string[]) => {
            return ids.some((id) => sourceStates[id] === "syncing");
        },
        [sourceStates],
    );

    const anyVisible = useCallback(
        (ids: string[]) => {
            return ids.some((id) => !hiddenSet.has(id));
        },
        [hiddenSet],
    );

    const showSources = useCallback(
        (ids: string[]) => {
            ids.forEach((id) => {
                if (new Set(useFilterPanelStore.getState().hiddenSources).has(id)) showSource(id);
            });
        },
        [showSource],
    );

    const hideSources = useCallback(
        (ids: string[]) => {
            ids.forEach((id) => {
                if (!new Set(useFilterPanelStore.getState().hiddenSources).has(id)) hideSource(id);
            });
        },
        [hideSource],
    );

    const hideLayerGroup = useCallback(
        (groupId: string, layerIds: string[]) => {
            useFilterPanelStore
                .getState()
                .setHiddenLayerGroups((prev) =>
                    prev.includes(groupId) ? prev : [...prev, groupId],
                );
            layerIds.forEach((id) => {
                if (!new Set(useFilterPanelStore.getState().hiddenSources).has(id)) hideSource(id);
            });
        },
        [hideSource],
    );

    const showLayerGroup = useCallback(
        (groupId: string, layerIds: string[]) => {
            useFilterPanelStore
                .getState()
                .setHiddenLayerGroups((prev) => prev.filter((id) => id !== groupId));
            layerIds.forEach((id) => {
                if (new Set(useFilterPanelStore.getState().hiddenSources).has(id)) showSource(id);
            });
        },
        [showSource],
    );

    const setLayerSortOrder = useFilterPanelStore((s) => s.setLayerSortOrder);
    const setDataSourceSortOrder = useFilterPanelStore((s) => s.setDataSourceSortOrder);
    const setVisibilityFilter = useFilterPanelStore((s) => s.setVisibilityFilter);

    return useMemo(
        () => ({
            hideSource,
            showSource,
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
        }),
        [
            hideSource,
            showSource,
            allVisible,
            anyVisible,
            anySyncing,
            showSources,
            hideSources,
            hideLayerGroup,
            showLayerGroup,
            setLayerSortOrder,
            setDataSourceSortOrder,
            setVisibilityFilter,
        ],
    );
}
