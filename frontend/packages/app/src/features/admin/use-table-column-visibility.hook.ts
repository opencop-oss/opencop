// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { OnChangeFn, VisibilityState } from "@tanstack/react-table";
import { useCallback, useMemo } from "react";
import { NO_HIDDEN_COLUMNS, useTableColumnsStore } from "./table-columns.store";
import type { ColumnTableId } from "./table-columns.store";

/**
 * Column-visibility props for one `ResourceTable`, backed by the persisted per-table store.
 * Spread the result onto the table; it opts the table into the Columns picker.
 */
export function useTableColumnVisibility(tableId: ColumnTableId) {
    const hidden = useTableColumnsStore(
        (s) => s.hiddenColumnsByTable[tableId] ?? NO_HIDDEN_COLUMNS,
    );
    const setHiddenColumns = useTableColumnsStore((s) => s.actions.setHiddenColumns);
    const resetColumns = useTableColumnsStore((s) => s.actions.resetColumns);

    const columnVisibility = useMemo<VisibilityState>(
        () => Object.fromEntries(hidden.map((id) => [id, false])),
        [hidden],
    );

    const onColumnVisibilityChange = useCallback<OnChangeFn<VisibilityState>>(
        (updater) => {
            const next = typeof updater === "function" ? updater(columnVisibility) : updater;
            setHiddenColumns(
                tableId,
                Object.keys(next).filter((columnId) => next[columnId] === false),
            );
        },
        [tableId, columnVisibility, setHiddenColumns],
    );

    const onResetColumnVisibility = useCallback(
        () => resetColumns(tableId),
        [tableId, resetColumns],
    );

    return { columnVisibility, onColumnVisibilityChange, onResetColumnVisibility };
}
