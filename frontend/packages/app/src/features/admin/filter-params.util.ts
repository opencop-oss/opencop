// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { MAX_FILTER_VALUE_LENGTH, type FilterParam } from "@opencop/api";
import type { ActiveColumnFilterValue, ColumnFilterOperator } from "@opencop/ui";
import type { ColumnFilter } from "@tanstack/react-table";

/**
 * How one grid column maps onto a server-side read's filter contract.
 *
 * `field` is the backend attribute name, which is not always the column id — a grid may name a
 * column after its own row model (`rowType`) while the API calls it something else (`type`).
 */
export interface FilterFieldDef {
    field: string;
    /**
     * Operators the backend accepts, in the order the builder offers them. Omit for a column the
     * API sorts by but cannot filter on; the entry still supplies the sort-field mapping.
     */
    operators?: readonly ColumnFilterOperator[];
    /** Normalizes a chip value to the token the backend expects. */
    toWire?: (value: unknown) => string;
}

export interface FilterParamSerializer {
    /** Turns the grid's filter chips into repeated `filter` query params. */
    toFilterParams: (columnFilters: readonly ColumnFilter[]) => FilterParam[];
    /** The backend attribute a grid column sorts by. */
    toSortField: (columnId: string) => string;
}

/** Operators the backend derives from the operator token alone, so no value travels. */
const NO_VALUE_OPERATORS = new Set<ColumnFilterOperator>([
    "isEmpty",
    "isNotEmpty",
    "isTrue",
    "isFalse",
]);

function normalizeValue(raw: unknown, def: FilterFieldDef): string {
    const wire = def.toWire ? def.toWire(raw) : String(raw ?? "");
    return wire.trim().slice(0, MAX_FILTER_VALUE_LENGTH);
}

/**
 * Serializes one filter chip, or null when it cannot form a valid clause: an operator outside the
 * column's list, an empty value, or a `between` missing an endpoint. Dropping it keeps a half-built
 * filter from 400-ing the page while the chip stays visible.
 */
function toFilterParam(
    def: FilterFieldDef,
    filterValue: ActiveColumnFilterValue,
): FilterParam | null {
    const { operator } = filterValue;
    if (!def.operators?.includes(operator)) return null;

    if (NO_VALUE_OPERATORS.has(operator)) {
        // The uniform three-token form; the backend also accepts the two-token `field,operator`.
        return `${def.field},${operator},`;
    }

    if (operator === "between") {
        const from = normalizeValue(filterValue.value, def);
        const to = normalizeValue(filterValue.valueTo, def);
        if (!from || !to) return null;
        return `${def.field},between,${from}..${to}`;
    }

    const value = normalizeValue(filterValue.value, def);
    if (!value) return null;
    return `${def.field},${operator},${value}`;
}

/**
 * Builds the chip-to-query-param serializer for one server-filtered grid.
 *
 * The grammar it emits — the `..` range join, the `MAX_FILTER_VALUE_LENGTH` clip, the
 * trim-then-drop-empty rule, the three-token no-value form — is one half of a wire contract with
 * the backend's `FilterCriterion`. It is shared so a second grid cannot drift from the first; what
 * stays per-resource is the declarative half, the `fields` map saying which column means which
 * backend field with which operators.
 *
 * @param resourceLabel names the map in the DEV diagnostic below, e.g. `"directory-filters"`.
 */
export function createFilterParamSerializer(
    fields: Readonly<Record<string, FilterFieldDef>>,
    resourceLabel: string,
): FilterParamSerializer {
    return {
        toSortField: (columnId) => fields[columnId]?.field ?? columnId,

        toFilterParams: (columnFilters) =>
            columnFilters.flatMap((columnFilter) => {
                const def = fields[columnFilter.id];
                if (!def) {
                    // Skipping is right for a stale chip but is also what a forgotten map entry
                    // looks like, and that shows up as a chip that renders while rows stay
                    // unfiltered.
                    if (import.meta.env.DEV) {
                        // eslint-disable-next-line no-console -- dev-only wiring diagnostic
                        console.error(
                            `[${resourceLabel}] No field mapping for column "${columnFilter.id}". ` +
                                `Its filter chips are being dropped — add an entry to the field map.`,
                        );
                    }
                    return [];
                }
                const values = (columnFilter.value ?? []) as ActiveColumnFilterValue[];
                return values
                    .map((filterValue) => toFilterParam(def, filterValue))
                    .filter((param): param is FilterParam => param !== null);
            }),
    };
}
