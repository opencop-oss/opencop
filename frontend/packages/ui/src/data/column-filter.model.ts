// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

export type ColumnFilterType = "string" | "enum" | "numeric" | "date" | "boolean";

export interface ColumnFilterEnumOption {
    label: string;
    /** Raw cell value matched case-sensitively by `ocEnumFilter`. Must be the exact string the column returns (e.g. `"ADMIN"`, not `"Admin"`). */
    value: string;
}

export interface ColumnFilterDef {
    label: string;
    type: ColumnFilterType;
    options?: ColumnFilterEnumOption[];
    /**
     * Restricts the operators offered for this column, in display order; the first is preselected.
     * Omit for the full set implied by `type`, correct for grids filtering client-side over a
     * fully-loaded dataset. Server-filtered grids must set it so the builder never offers an
     * operator the backend rejects.
     */
    operators?: readonly ColumnFilterOperator[];
}

export type ColumnStringOperator =
    | "equals"
    | "notEquals"
    | "contains"
    | "notContains"
    | "startsWith"
    | "isEmpty"
    | "isNotEmpty";

export type ColumnEnumOperator = "equals" | "notEquals" | "inList";

export type ColumnNumericOperator = "eq" | "neq" | "gt" | "gte" | "lt" | "lte";

export type ColumnBooleanOperator = "isTrue" | "isFalse";

export type ColumnDateOperator = "before" | "after" | "between" | "last7Days" | "last30Days";

export type ColumnFilterOperator =
    | ColumnStringOperator
    | ColumnEnumOperator
    | ColumnNumericOperator
    | ColumnBooleanOperator
    | ColumnDateOperator;

export interface ActiveColumnFilterValue {
    operator: ColumnFilterOperator;
    value?: unknown;
    valueTo?: unknown;
    values?: string[];
    /** Stable key for React reconciliation — set by ColumnFilterBuilder, ignored by filter fns */
    _key?: string;
}
