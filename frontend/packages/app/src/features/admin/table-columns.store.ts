// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { isEqual, uniq } from "lodash";
import { create } from "zustand";
import { persist } from "zustand/middleware";

/**
 * Names one table's saved column choice. The admin and "My" view of a resource keep separate ids
 * so each remembers its own; a union so a typo cannot silently start a new saved choice.
 */
export type ColumnTableId =
    | "my-layers"
    | "my-data-sources"
    | "admin-layers"
    | "admin-data-sources"
    | "admin-mappers";

type TableColumnsState = {
    hiddenColumnsByTable: Partial<Record<ColumnTableId, string[]>>;
};

type TableColumnsActions = {
    /** Replaces the table's hidden set; an empty set clears the saved choice. */
    setHiddenColumns: (tableId: ColumnTableId, columnIds: readonly string[]) => void;
    resetColumns: (tableId: ColumnTableId) => void;
};

type TableColumnsStore = TableColumnsState & {
    actions: TableColumnsActions;
};

export const TABLE_COLUMNS_STORAGE_KEY = "opencop:table-columns";

/** Stable reference for "nothing hidden" so a selector falling back to it never re-renders. */
export const NO_HIDDEN_COLUMNS: readonly string[] = [];

/**
 * Keeps only tables mapped to non-empty arrays of unique strings, so a corrupted persisted blob
 * can never crash hydration or leak into the visibility state.
 */
function normalizeHiddenColumns(value: unknown): TableColumnsState["hiddenColumnsByTable"] {
    if (typeof value !== "object" || value === null) return {};

    const normalized: Record<string, string[]> = {};
    for (const [tableId, ids] of Object.entries(value as Record<string, unknown>)) {
        if (!Array.isArray(ids)) continue;
        const unique = uniq(ids.filter((id): id is string => typeof id === "string"));
        if (unique.length > 0) normalized[tableId] = unique;
    }
    return normalized;
}

/**
 * Which columns each table hides, persisted per browser.
 *
 * Stores the *hidden* ids rather than the visible ones: a column added in a later release then
 * shows by default instead of being invisible to everyone who has already saved a choice.
 */
export const useTableColumnsStore = create<TableColumnsStore>()(
    persist(
        (set) => ({
            hiddenColumnsByTable: {},
            actions: {
                setHiddenColumns: (tableId, columnIds) =>
                    set((state) => {
                        const next = uniq(columnIds);
                        const current = state.hiddenColumnsByTable[tableId] ?? NO_HIDDEN_COLUMNS;
                        if (isEqual(next, current)) return state;

                        const hiddenColumnsByTable = { ...state.hiddenColumnsByTable };
                        if (next.length > 0) hiddenColumnsByTable[tableId] = next;
                        else delete hiddenColumnsByTable[tableId];
                        return { hiddenColumnsByTable };
                    }),
                resetColumns: (tableId) =>
                    set((state) => {
                        if (!state.hiddenColumnsByTable[tableId]) return state;
                        const hiddenColumnsByTable = { ...state.hiddenColumnsByTable };
                        delete hiddenColumnsByTable[tableId];
                        return { hiddenColumnsByTable };
                    }),
            },
        }),
        {
            name: TABLE_COLUMNS_STORAGE_KEY,
            partialize: (state) => ({ hiddenColumnsByTable: state.hiddenColumnsByTable }),
            merge: (persisted, current) => ({
                ...current,
                hiddenColumnsByTable: normalizeHiddenColumns(
                    (persisted as { hiddenColumnsByTable?: unknown } | undefined)
                        ?.hiddenColumnsByTable,
                ),
            }),
        },
    ),
);
