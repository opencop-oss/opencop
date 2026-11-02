// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { type Dispatch, type SetStateAction, useCallback, useMemo, useState } from "react";
import type { RowSelectionState } from "@opencop/ui";

interface UseSelectedRowsReturn<T> {
    /** Every selected row, whether or not it is still on screen. */
    selectedRows: T[];
    /** Hand this to `ResourceTable` in place of the raw setter — it is what captures the rows. */
    onRowSelectionChange: Dispatch<SetStateAction<RowSelectionState>>;
}

/**
 * Resolves selected ids to rows, retaining a row once it has been ticked.
 *
 * The capture happens in the selection handler, the moment the row is picked and therefore
 * on screen. That is what lets a selection be built up across several searches, and what keeps
 * the set honest afterwards: without it a selection outliving its rows leaves ids nothing can
 * resolve, so actions reading raw ids act on rows the user can no longer see, actions resolving
 * against the loaded page silently drop them, and the toolbar count agrees with neither.
 *
 * A live row wins over its captured copy, so a refetch's fresher data is what gets acted on.
 *
 * Call this *after* the rows exist. The returned handler only has to reach `ResourceTable`, which
 * renders below, so a view whose query depends on the filter state can still use it.
 *
 * @param rows the rows currently available — the loaded page(s) for a server-paged table
 * @param getRowId must match the `getRowId` given to `ResourceTable`; keep it stable
 * @param rowSelection selected ids, from `useResourceTableState`
 * @param setRowSelection that hook's `onRowSelectionChange`
 */
export function useSelectedRows<T>(
    rows: readonly T[],
    getRowId: (row: T) => string,
    rowSelection: RowSelectionState,
    setRowSelection: Dispatch<SetStateAction<RowSelectionState>>,
): UseSelectedRowsReturn<T> {
    const [captured, setCaptured] = useState<ReadonlyMap<string, T>>(() => new Map());

    const onRowSelectionChange: Dispatch<SetStateAction<RowSelectionState>> = useCallback(
        (updater) => {
            const next = typeof updater === "function" ? updater(rowSelection) : updater;
            setCaptured((prev) => {
                const kept = new Map<string, T>();
                for (const [id, isSelected] of Object.entries(next)) {
                    if (!isSelected) continue;
                    const row =
                        rows.find((candidate) => getRowId(candidate) === id) ?? prev.get(id);
                    if (row !== undefined) kept.set(id, row);
                }
                return kept;
            });
            setRowSelection(next);
        },
        [rows, getRowId, rowSelection, setRowSelection],
    );

    const selectedRows = useMemo(() => {
        const live = new Map(rows.map((row) => [getRowId(row), row] as const));
        return Object.keys(rowSelection)
            .filter((id) => rowSelection[id])
            .map((id) => live.get(id) ?? captured.get(id))
            .filter((row): row is T => row !== undefined);
    }, [rows, getRowId, rowSelection, captured]);

    return { selectedRows, onRowSelectionChange };
}
