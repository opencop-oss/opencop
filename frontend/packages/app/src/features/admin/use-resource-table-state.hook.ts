// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { type Dispatch, type SetStateAction, useCallback, useState } from "react";
import type { ColumnFiltersState, OnChangeFn, RowSelectionState } from "@opencop/ui";

interface UseResourceTableStateReturn {
    globalFilter: string;
    onGlobalFilterChange: (value: string) => void;
    columnFilters: ColumnFiltersState;
    onColumnFiltersChange: OnChangeFn<ColumnFiltersState>;
    rowSelection: RowSelectionState;
    onRowSelectionChange: Dispatch<SetStateAction<RowSelectionState>>;
    resetSelection: () => void;
    /**
     * Clears the column filters, the search text and the selection together — the "back to the
     * default view" action. A view's own visible-set state (`showArchived`, an entity-type mirror)
     * is not this hook's to know about and still has to be reset alongside it.
     */
    resetView: () => void;
}

/**
 * Controlled `ResourceTable` state: the two filters, the selected ids, and the resets.
 *
 * **Narrowing does not clear the selection.** Filtering changes what is on screen, not what the
 * user picked, so a selection can be built up across several searches. Pair this with
 * {@link useSelectedRows} to turn the ids into rows — that is what keeps a selection resolvable
 * once its rows scroll out of the filter, and what stops the toolbar count and the bulk actions
 * from describing different sets. Only an explicit reset empties the selection.
 *
 * @param initialColumnFilters seeds the filter bar on first render, for a table whose useful
 *   default view is narrower than "everything". The filters stay visible as removable chips, so a
 *   seeded default reads as a filter the user can clear — not as rows silently missing.
 */
export function useResourceTableState(
    initialColumnFilters: ColumnFiltersState = [],
): UseResourceTableStateReturn {
    const [globalFilter, setGlobalFilter] = useState("");
    const [columnFilters, setColumnFilters] = useState<ColumnFiltersState>(initialColumnFilters);
    const [rowSelection, setRowSelection] = useState<RowSelectionState>({});

    const resetSelection = useCallback(() => setRowSelection({}), []);
    const resetView = useCallback(() => {
        setColumnFilters([]);
        setGlobalFilter("");
        setRowSelection({});
    }, []);

    return {
        globalFilter,
        onGlobalFilterChange: setGlobalFilter,
        columnFilters,
        onColumnFiltersChange: setColumnFilters,
        rowSelection,
        onRowSelectionChange: setRowSelection,
        resetSelection,
        resetView,
    };
}
