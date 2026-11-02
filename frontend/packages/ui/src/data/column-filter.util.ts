// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ColumnDef } from "@tanstack/react-table";
import type { ColumnFilterDef } from "./column-filter.model.ts";

export interface FilterableColumn {
    id: string;
    filterDef: ColumnFilterDef;
}

/** Derives FilterableColumn entries from column defs that carry a meta.filterDef. */
// eslint-disable-next-line @typescript-eslint/no-explicit-any
export function toFilterableColumns<TData>(columns: ColumnDef<TData, any>[]): FilterableColumn[] {
    return columns.flatMap((col) => {
        const filterDef = (col.meta as { filterDef?: ColumnFilterDef } | undefined)?.filterDef;
        if (!filterDef) return [];
        const id = ("accessorKey" in col ? String(col.accessorKey) : undefined) ?? col.id;
        if (!id) return [];
        return [{ id, filterDef }];
    });
}
