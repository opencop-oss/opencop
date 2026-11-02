// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { create } from "zustand";
import type { Filter } from "@opencop/ui";
import { persist, subscribeWithSelector } from "zustand/middleware";
import { compile, type CompiledFilter } from "./compiled-filter.model";
import Logger from "js-logger";

const logger = Logger.get("map-filter");

/** The mapbar panel id registered in `mapbar.config.tsx`. */
export const MAP_FILTERS_PANEL_ID = "map-filters";

export type MapFilter = Filter & { name: string };

export type MapFilterState = {
    filters: MapFilter[];
    /** Persisted activation intent. `active` is derived from this + `filters`. */
    activeNames: string[];
    /** Never persisted — closures. Rebuilt by `recompile()`. */
    active: CompiledFilter[];
};

export type MapFilterActions = {
    activateFilter(name: string): void;
    deactivateFilter(name: string): void;
    clearActive(): void;
    addFilter(filter: MapFilter): void;
    removeFilter(name: string): void;
};

export type MapFilterStore = MapFilterState & { actions: MapFilterActions };

function compileActive(filters: MapFilter[], activeNames: string[]): CompiledFilter[] {
    const wanted = new Set(activeNames);
    const compiled: CompiledFilter[] = [];
    for (const filter of filters) {
        if (!wanted.has(filter.name)) continue;
        try {
            compiled.push(compile(filter));
        } catch (err) {
            // A persisted filter can reference a path `accessorFromPredicate`
            // no longer resolves. Drop it rather than break hydration.
            logger.warn(`dropping unusable persisted filter "${filter.name}"`, err);
        }
    }
    return compiled;
}

const recompile = (filters: MapFilter[], activeNames: string[]) => ({
    filters,
    activeNames,
    active: compileActive(filters, activeNames),
});

export const useMapFilterStore = create<MapFilterStore>()(
    subscribeWithSelector(
        persist(
            (set) => ({
                filters: [],
                activeNames: [],
                active: [],
                actions: {
                    activateFilter: (name) =>
                        set((s) =>
                            s.activeNames.includes(name) || !s.filters.some((f) => f.name === name)
                                ? s
                                : recompile(s.filters, [...s.activeNames, name]),
                        ),
                    deactivateFilter: (name) =>
                        set((s) =>
                            recompile(
                                s.filters,
                                s.activeNames.filter((n) => n !== name),
                            ),
                        ),
                    clearActive: () => set((s) => recompile(s.filters, [])),
                    addFilter: (filter) =>
                        set((s) => {
                            const index = s.filters.findIndex((f) => f.name === filter.name);
                            const filters =
                                index === -1
                                    ? [...s.filters, filter]
                                    : s.filters.map((f, i) => (index === i ? filter : f));
                            return recompile(filters, s.activeNames);
                        }),
                    removeFilter: (name) =>
                        set((s) =>
                            recompile(
                                s.filters.filter((f) => f.name !== name),
                                s.activeNames.filter((n) => n !== name),
                            ),
                        ),
                },
            }),
            {
                name: "opencop:map-filter",
                partialize: ({ filters, activeNames }) => ({ filters, activeNames }),
                merge: (persisted, current) => {
                    const { filters = current.filters, activeNames = current.activeNames } =
                        (persisted as Partial<MapFilterState>) ?? {};
                    return {
                        ...current,
                        filters,
                        activeNames,
                        active: compileActive(filters, activeNames),
                    };
                },
            },
        ),
    ),
);
