// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { FilterFn } from "@tanstack/react-table";
import { DateTime } from "luxon";
import type { ActiveColumnFilterValue } from "./column-filter.model";

type FilterValue = ActiveColumnFilterValue | ActiveColumnFilterValue[];

function toArray(raw: FilterValue): ActiveColumnFilterValue[] {
    return Array.isArray(raw) ? raw : [raw];
}

function applyStringSingle(cell: string, fv: ActiveColumnFilterValue): boolean {
    const v = String(fv.value ?? "").toLowerCase();
    switch (fv.operator) {
        case "equals":
            return cell === v;
        case "notEquals":
            return cell !== v;
        case "contains":
            return cell.includes(v);
        case "notContains":
            return !cell.includes(v);
        case "startsWith":
            return cell.startsWith(v);
        case "isEmpty":
            return cell === "";
        case "isNotEmpty":
            return cell !== "";
        default:
            return true;
    }
}

export const ocStringFilter: FilterFn<unknown> = (row, columnId, raw: FilterValue) => {
    const cell = String(row.getValue(columnId) ?? "").toLowerCase();
    return toArray(raw).every((fv) => applyStringSingle(cell, fv));
};
ocStringFilter.autoRemove = (val: FilterValue) => {
    const filters = toArray(val);
    return (
        filters.length === 0 ||
        filters.every(
            (fv) => !fv || (fv.operator !== "isEmpty" && fv.operator !== "isNotEmpty" && !fv.value),
        )
    );
};

function applyEnumSingle(cell: string, fv: ActiveColumnFilterValue): boolean {
    switch (fv.operator) {
        case "equals":
            return cell === String(fv.value ?? "");
        case "notEquals":
            return cell !== String(fv.value ?? "");
        case "inList":
            return (fv.values ?? []).includes(cell);
        default:
            return true;
    }
}

export const ocEnumFilter: FilterFn<unknown> = (row, columnId, raw: FilterValue) => {
    const cell = String(row.getValue(columnId) ?? "");
    return toArray(raw).every((fv) => applyEnumSingle(cell, fv));
};
ocEnumFilter.autoRemove = (val: FilterValue) => {
    const filters = toArray(val);
    return filters.length === 0 || filters.every((fv) => !fv || (!fv.value && !fv.values?.length));
};

function applyNumericSingle(cell: number, fv: ActiveColumnFilterValue): boolean {
    const v = Number(fv.value);
    switch (fv.operator) {
        case "eq":
            return cell === v;
        case "neq":
            return cell !== v;
        case "gt":
            return cell > v;
        case "gte":
            return cell >= v;
        case "lt":
            return cell < v;
        case "lte":
            return cell <= v;
        default:
            return true;
    }
}

export const ocNumericFilter: FilterFn<unknown> = (row, columnId, raw: FilterValue) => {
    const cell = Number(row.getValue(columnId));
    return toArray(raw).every((fv) => applyNumericSingle(cell, fv));
};
ocNumericFilter.autoRemove = (val: FilterValue) => {
    const filters = toArray(val);
    return filters.length === 0 || filters.every((fv) => !fv || fv.value === undefined);
};

export const ocBooleanFilter: FilterFn<unknown> = (row, columnId, raw: FilterValue) => {
    const cell = row.getValue(columnId);
    if (cell == null) return false;
    return toArray(raw).every((fv) => (fv.operator === "isTrue" ? !!cell : !cell));
};
ocBooleanFilter.autoRemove = (val: FilterValue) => {
    const filters = toArray(val);
    return filters.length === 0 || filters.every((fv) => !fv?.operator);
};

/**
 * The calendar day a value falls on, in `zone`. The bounds operators compare whole days, so a cell
 * carrying a time-of-day must not be measured against a bound's midnight — otherwise `after
 * 2026-06-01` matches a row stamped `2026-06-01T14:00Z` and `between …..2026-06-03` drops all but
 * the first instant of the 3rd.
 *
 * Resolved in the viewer's zone rather than UTC, matching both the server (`FilterPredicates`
 * resolves the same operators in the zone the grid sends as `tz`) and the cell beside it, which
 * renders the same timestamp in that zone. In UTC a filtered day would exclude rows whose displayed
 * date matches it — at UTC+8 every row stamped after 16:00 UTC shows as the next day.
 */
function dayIn(zone: string, value: Date | string | number): number {
    // Filter bounds arrive as date-only `yyyy-MM-dd` strings (ColumnFilterBuilder uses native
    // `<input type="date">`). `new Date()` reads those as UTC midnight, which lands on the previous
    // day in every UTC-negative zone — so the bound must be parsed *as a day in `zone`*, not as an
    // instant. Cell values carry an offset and keep their instant, which `fromISO` then converts.
    const parsed =
        typeof value === "string"
            ? DateTime.fromISO(value, { zone })
            : DateTime.fromJSDate(new Date(value), { zone });
    return parsed.startOf("day").toMillis();
}

function applyDateSingle(
    cell: Date,
    now: Date,
    fv: ActiveColumnFilterValue,
    zone: string,
): boolean {
    const day = (value: Date | string | number) => dayIn(zone, value);
    switch (fv.operator) {
        case "before":
            return day(cell) < day(fv.value as string | number);
        case "after":
            return day(cell) > day(fv.value as string | number);
        case "between": {
            if (!fv.value || !fv.valueTo) return true;
            const cellDay = day(cell);
            return (
                cellDay >= day(fv.value as string | number) &&
                cellDay <= day(fv.valueTo as string | number)
            );
        }
        case "last7Days": {
            // Deliberately instant-based, unlike the bounds operators above: a rolling 7x24h
            // window is elapsed time, not a span of calendar days, so it needs no zone.
            const cutoff = DateTime.fromJSDate(now).minus({ days: 7 }).toJSDate();
            return cell >= cutoff;
        }
        case "last30Days": {
            const cutoff = DateTime.fromJSDate(now).minus({ days: 30 }).toJSDate();
            return cell >= cutoff;
        }
        default:
            return true;
    }
}

/** Shared by every date filter: a bound-less range constrains nothing, so drop the chip. */
const dateAutoRemove = (val: FilterValue) => {
    const filters = toArray(val);
    return (
        filters.length === 0 ||
        filters.every(
            (fv) =>
                !fv?.operator ||
                (["before", "after"].includes(fv.operator) && !fv.value) ||
                (fv.operator === "between" && (!fv.value || !fv.valueTo)),
        )
    );
};

/**
 * Builds a date filter fixed to `zone`, bypassing `ResourceTable`'s `timezone` prop entirely — a
 * column declaring this by value pins its zone at definition time, and TanStack never consults the
 * table's `filterFns` registry for a function-valued `filterFn`.
 *
 * Only for a column whose zone must differ from its table's. For the common case set
 * `ResourceTable`'s `timezone` prop and declare `filterFn: "ocDateFilter"`, which resolves through
 * {@link ocFilterFns}.
 */
export function createOcDateFilter(zone: string): FilterFn<unknown> {
    const fn: FilterFn<unknown> = (row, columnId, raw: FilterValue) => {
        const cellRaw = row.getValue<string | number | null>(columnId);
        if (cellRaw == null) return false;
        const cell = new Date(cellRaw);
        if (isNaN(cell.getTime())) return false;
        const now = new Date();
        return toArray(raw).every((fv) => applyDateSingle(cell, now, fv, zone));
    };
    fn.autoRemove = dateAutoRemove;
    return fn;
}

/** UTC-resolved fallback, used when no zone is supplied. */
export const ocDateFilter: FilterFn<unknown> = (row, columnId, raw: FilterValue) => {
    const cellRaw = row.getValue<string | number | null>(columnId);
    if (cellRaw == null) return false;
    const cell = new Date(cellRaw);
    if (isNaN(cell.getTime())) return false;
    const now = new Date();
    return toArray(raw).every((fv) => applyDateSingle(cell, now, fv, "utc"));
};
ocDateFilter.autoRemove = dateAutoRemove;

export const OC_FILTER_FNS = {
    ocStringFilter,
    ocEnumFilter,
    ocNumericFilter,
    ocBooleanFilter,
    ocDateFilter,
} as const;

/**
 * The filter registry with its date filter bound to `zone`. `ResourceTable` builds this from its
 * `timezone` prop so a column only has to name `"ocDateFilter"` — the zone travels with the table
 * rather than through every column definition.
 */
export function ocFilterFns(zone: string | undefined): typeof OC_FILTER_FNS {
    if (!zone) return OC_FILTER_FNS;
    return { ...OC_FILTER_FNS, ocDateFilter: createOcDateFilter(zone) };
}
