// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { difference, isEqual, union, xor } from "lodash";
import { create } from "zustand";
import { persist, subscribeWithSelector } from "zustand/middleware";

export type SortOrder = "none" | "name-asc" | "name-desc";
export type VisibilityFilter = "all" | "visible" | "hidden" | "pinned";

interface FilterPanelState {
    layerSortOrder: SortOrder;
    dataSourceSortOrder: SortOrder;
    visibilityFilter: VisibilityFilter;
    expandedSections: Record<string, boolean>;
    hiddenSources: string[];
    hiddenLayerGroups: string[];
    /** Layer and data source ids the user removed from the map panel. They stay off the map. */
    excludedIds: string[];
    knownSources: string[];
    textVisibleSources: string[];
    /** Layer and data source ids the user pinned; they float to the top of their section. */
    pinnedSources: string[];
    setHiddenSources: (updater: (prev: string[]) => string[]) => void;
    setHiddenLayerGroups: (updater: (prev: string[]) => string[]) => void;
    /** Writes the list only. Go through `usePanelExclusionActions` so excluding also toggles the source off. */
    setExcludedIds: (ids: readonly string[], excluded: boolean) => void;
    initNewSources: (infos: { id: string; defaultVisible: boolean }[]) => void;
    removeKnownSources: (ids: string[]) => void;
    setTextVisibleSources: (updater: (prev: string[]) => string[]) => void;
    togglePinnedSource: (id: string) => void;
    setLayerSortOrder: (sortOrder: SortOrder) => void;
    setDataSourceSortOrder: (sortOrder: SortOrder) => void;
    setVisibilityFilter: (filter: VisibilityFilter) => void;
    setSectionExpanded: (label: string, expanded: boolean) => void;
}

export const useFilterPanelStore = create<FilterPanelState>()(
    subscribeWithSelector(
        persist(
            (set) => ({
                hiddenSources: [],
                hiddenLayerGroups: [],
                excludedIds: [],
                knownSources: [],
                textVisibleSources: [],
                pinnedSources: [],
                layerSortOrder: "none",
                dataSourceSortOrder: "none",
                visibilityFilter: "all",
                expandedSections: {},

                setHiddenSources: (updater) =>
                    set((state) => ({ hiddenSources: updater(state.hiddenSources) })),
                setHiddenLayerGroups: (updater) =>
                    set((state) => ({ hiddenLayerGroups: updater(state.hiddenLayerGroups) })),
                setExcludedIds: (ids, excluded) =>
                    set((state) => {
                        const next = excluded
                            ? union(state.excludedIds, ids)
                            : difference(state.excludedIds, ids);
                        return isEqual(next, state.excludedIds) ? state : { excludedIds: next };
                    }),
                initNewSources: (infos) =>
                    set((state) => {
                        const toHide = infos
                            .filter((s) => s.defaultVisible === false)
                            .map((s) => s.id);
                        return {
                            knownSources: [
                                ...new Set([...state.knownSources, ...infos.map((s) => s.id)]),
                            ],
                            hiddenSources:
                                toHide.length > 0
                                    ? [...new Set([...state.hiddenSources, ...toHide])]
                                    : state.hiddenSources,
                        };
                    }),
                removeKnownSources: (ids) =>
                    set((state) => {
                        const remove = new Set(ids);
                        return {
                            knownSources: state.knownSources.filter((id) => !remove.has(id)),
                            hiddenSources: state.hiddenSources.filter((id) => !remove.has(id)),
                            excludedIds: state.excludedIds.filter((id) => !remove.has(id)),
                            textVisibleSources: state.textVisibleSources.filter(
                                (id) => !remove.has(id),
                            ),
                            pinnedSources: state.pinnedSources.filter((id) => !remove.has(id)),
                        };
                    }),
                setTextVisibleSources: (updater) =>
                    set((state) => ({ textVisibleSources: updater(state.textVisibleSources) })),
                togglePinnedSource: (id) =>
                    set((state) => ({ pinnedSources: xor(state.pinnedSources, [id]) })),
                setLayerSortOrder: (layerSortOrder) => set({ layerSortOrder }),
                setDataSourceSortOrder: (dataSourceSortOrder) => set({ dataSourceSortOrder }),
                setVisibilityFilter: (visibilityFilter) => set({ visibilityFilter }),
                setSectionExpanded: (label, expanded) =>
                    set((state) => ({
                        expandedSections: { ...state.expandedSections, [label]: expanded },
                    })),
            }),
            {
                name: "opencop:filter-panel",
                partialize: (state) => ({
                    hiddenSources: state.hiddenSources,
                    hiddenLayerGroups: state.hiddenLayerGroups,
                    excludedIds: state.excludedIds,
                    knownSources: state.knownSources,
                    textVisibleSources: state.textVisibleSources,
                    pinnedSources: state.pinnedSources,
                    layerSortOrder: state.layerSortOrder,
                    dataSourceSortOrder: state.dataSourceSortOrder,
                    visibilityFilter: state.visibilityFilter,
                    expandedSections: state.expandedSections,
                }),
            },
        ),
    ),
);
