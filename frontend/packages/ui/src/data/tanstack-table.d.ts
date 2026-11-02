// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { FilterFn, RowData } from "@tanstack/react-table";
import type { ColumnFilterDef } from "./column-filter.model";

declare module "@tanstack/react-table" {
    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    interface ColumnMeta<TData extends RowData, TValue> {
        filterDef?: ColumnFilterDef;
        isActionsColumn?: boolean;
        /** Plain-text name for the column picker. Required for any column whose header is not a literal string, including `() => t(...)`; a literal string header is used as-is. */
        columnLabel?: string;
    }

    interface FilterFns {
        ocStringFilter: FilterFn<unknown>;
        ocEnumFilter: FilterFn<unknown>;
        ocNumericFilter: FilterFn<unknown>;
        ocBooleanFilter: FilterFn<unknown>;
        ocDateFilter: FilterFn<unknown>;
    }
}
