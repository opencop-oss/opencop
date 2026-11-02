// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, Button, Checkbox, Flex, Input, Popover, Portal, Stack } from "@chakra-ui/react";
import type { ColumnFiltersState } from "@tanstack/react-table";
import { useState, type ReactElement } from "react";
import { useTranslation } from "react-i18next";
import { FaPlus } from "react-icons/fa";
import { v4 as uuidv4 } from "uuid";
import { OcSelect } from "../forms/OcSelect.tsx";
import { OcTag } from "../forms/OcTag.tsx";
import { SearchInput } from "./SearchInput.tsx";
import type {
    ActiveColumnFilterValue,
    ColumnFilterDef,
    ColumnFilterType,
    ColumnFilterOperator,
} from "./column-filter.model.ts";
import type { FilterableColumn } from "./column-filter.util.ts";

export type { FilterableColumn } from "./column-filter.util.ts";

export interface ColumnFilterBuilderProps {
    filterableColumns: FilterableColumn[];
    value: ColumnFiltersState;
    onChange: (filters: ColumnFiltersState) => void;
    searchValue?: string;
    onSearchChange?: (value: string) => void;
    searchPlaceholder?: string;
    /**
     * Caps how many chips can be applied. Set it on grids whose backend enforces a criteria limit,
     * so reaching the cap disables "+ Add Filter" instead of letting the next chip 400 the page.
     */
    maxFilters?: number;
}

// ─── Operator config ──────────────────────────────────────────────────────────

const OPERATORS: Record<ColumnFilterType, ColumnFilterOperator[]> = {
    string: [
        "contains",
        "notContains",
        "equals",
        "notEquals",
        "startsWith",
        "isEmpty",
        "isNotEmpty",
    ],
    enum: ["equals", "notEquals", "inList"],
    numeric: ["eq", "neq", "gt", "gte", "lt", "lte"],
    boolean: ["isTrue", "isFalse"],
    date: ["before", "after", "between", "last7Days", "last30Days"],
};

const NO_VALUE_OPERATORS = new Set<ColumnFilterOperator>([
    "isEmpty",
    "isNotEmpty",
    "isTrue",
    "isFalse",
    "last7Days",
    "last30Days",
]);

const DEFAULT_OPERATOR: Record<ColumnFilterType, ColumnFilterOperator> = {
    string: "equals",
    enum: "equals",
    numeric: "eq",
    boolean: "isTrue",
    date: "before",
};

/**
 * A column's own `operators` list wins over the full set implied by its type. Keyed on `undefined`
 * rather than length, so an explicitly empty list offers nothing instead of falling open to every
 * operator — the failure this field exists to prevent.
 */
function operatorsFor(def: ColumnFilterDef): readonly ColumnFilterOperator[] {
    return def.operators !== undefined ? def.operators : OPERATORS[def.type];
}

/** Undefined when the column declares an empty list, so nothing is preselected. */
function defaultOperatorFor(def: ColumnFilterDef): ColumnFilterOperator | null {
    return def.operators !== undefined ? (def.operators[0] ?? null) : DEFAULT_OPERATOR[def.type];
}

// ─── Chip label helper ────────────────────────────────────────────────────────

function formatChipLabel(
    col: FilterableColumn,
    fv: ActiveColumnFilterValue,
    t: (k: string) => string,
): string {
    const opLabel = t(`resourceTable.filterOperators.${fv.operator}`);
    if (NO_VALUE_OPERATORS.has(fv.operator)) return `${col.filterDef.label}: ${opLabel}`;
    if (fv.operator === "inList") {
        const labels = (fv.values ?? []).map(
            (v) => col.filterDef.options?.find((o) => o.value === v)?.label ?? v,
        );
        return `${col.filterDef.label}: ${opLabel} ${labels.join(", ")}`;
    }
    if (fv.operator === "between") return `${col.filterDef.label}: ${fv.value} – ${fv.valueTo}`;
    const displayValue =
        col.filterDef.type === "enum"
            ? (col.filterDef.options?.find((o) => o.value === fv.value)?.label ?? fv.value)
            : fv.value;
    return `${col.filterDef.label}: ${opLabel} ${displayValue}`;
}

// ─── Main component ───────────────────────────────────────────────────────────

export function ColumnFilterBuilder({
    filterableColumns,
    value,
    onChange,
    searchValue,
    onSearchChange,
    searchPlaceholder,
    maxFilters,
}: ColumnFilterBuilderProps): ReactElement {
    const { t } = useTranslation();
    const [open, setOpen] = useState(false);
    const [pendingCol, setPendingCol] = useState<FilterableColumn | null>(null);
    const [pendingOp, setPendingOp] = useState<ColumnFilterOperator | null>(null);
    const [pendingValue, setPendingValue] = useState<unknown>("");
    const [pendingValueTo, setPendingValueTo] = useState<unknown>("");
    const [pendingValues, setPendingValues] = useState<string[]>([]);

    const resetWizard = () => {
        setPendingCol(null);
        setPendingOp(null);
        setPendingValue("");
        setPendingValueTo("");
        setPendingValues([]);
    };

    const applyFilter = (
        col: FilterableColumn,
        op: ColumnFilterOperator,
        fv: Partial<ActiveColumnFilterValue>,
    ) => {
        const newFilter: ActiveColumnFilterValue = { operator: op, ...fv, _key: uuidv4() };
        const existing = value.find((f) => f.id === col.id);
        const existingFilters: ActiveColumnFilterValue[] = existing
            ? (existing.value as ActiveColumnFilterValue[])
            : [];
        onChange([
            ...value.filter((f) => f.id !== col.id),
            { id: col.id, value: [...existingFilters, newFilter] },
        ]);
        setOpen(false);
        resetWizard();
    };

    const handleApply = () => {
        if (!pendingCol || !pendingOp) return;
        applyFilter(pendingCol, pendingOp, {
            ...(pendingValues.length ? { values: pendingValues } : {}),
            ...(pendingValue !== "" && pendingValue != null ? { value: pendingValue } : {}),
            ...(pendingValueTo !== "" && pendingValueTo != null ? { valueTo: pendingValueTo } : {}),
        });
    };

    const removeFilterAt = (columnId: string, index: number) => {
        const updated = value
            .map((f) => {
                if (f.id !== columnId) return f;
                const arr = (f.value as ActiveColumnFilterValue[]).filter((_, i) => i !== index);
                return arr.length ? { id: f.id, value: arr } : null;
            })
            .filter((f): f is NonNullable<typeof f> => f !== null);
        onChange(updated);
    };

    /**
     * A value the serializer will actually send. Blank strings are rejected because trimming
     * happens downstream: accepting one here produces a chip that renders as applied while the
     * criterion is dropped from the request.
     */
    const hasValue = (v: unknown) =>
        v !== undefined && v !== null && (typeof v !== "string" || v.trim() !== "");

    const canApply =
        pendingCol && pendingOp
            ? NO_VALUE_OPERATORS.has(pendingOp) ||
              (pendingCol.filterDef.type === "enum" && pendingOp === "inList"
                  ? pendingValues.length > 0
                  : // `between` needs both endpoints: the serializer drops a half-built range, which
                    // would otherwise leave an applied-looking chip over unfiltered rows.
                    hasValue(pendingValue) && (pendingOp !== "between" || hasValue(pendingValueTo)))
            : false;

    const toggleValue = (v: string) => {
        setPendingValues((prev) => (prev.includes(v) ? prev.filter((x) => x !== v) : [...prev, v]));
    };

    const showValue = pendingCol && pendingOp && !NO_VALUE_OPERATORS.has(pendingOp);

    // Counts individual chips, not columns: several chips can share one column entry.
    const appliedCount = value.reduce(
        (total, f) => total + (f.value as ActiveColumnFilterValue[]).length,
        0,
    );
    const atFilterLimit = maxFilters !== undefined && appliedCount >= maxFilters;

    return (
        <Flex align="center" gap={2} wrap="wrap">
            {onSearchChange && (
                <SearchInput
                    value={searchValue ?? ""}
                    onChange={onSearchChange}
                    placeholder={searchPlaceholder}
                    size="sm"
                />
            )}

            {value.flatMap((f) => {
                const col = filterableColumns.find((c) => c.id === f.id);
                if (!col) return [];
                return (f.value as ActiveColumnFilterValue[]).map((fv, idx) => {
                    const chipLabel = formatChipLabel(col, fv, t as (k: string) => string);
                    return (
                        <OcTag
                            key={fv._key ?? `${f.id}-${idx}`}
                            closable
                            onClose={() => removeFilterAt(f.id, idx)}
                            // Named per chip so several applied at once stay distinguishable.
                            closeLabel={t("resourceTable.filterRemove", { filter: chipLabel })}
                            colorPalette="brand"
                            size="sm"
                            // The default `sm` tag padding reads tight beside the search input it
                            // sits next to; this brings the chip's breathing room in line with it.
                            padding="2"
                        >
                            {chipLabel}
                        </OcTag>
                    );
                });
            })}

            {filterableColumns.length > 0 && (
                <Popover.Root
                    open={open}
                    onOpenChange={({ open: o }) => {
                        setOpen(o);
                        if (!o) resetWizard();
                    }}
                    positioning={{ placement: "bottom-start" }}
                >
                    <Popover.Trigger asChild>
                        <Button
                            variant="toolbar"
                            size="xs"
                            gap={1}
                            disabled={atFilterLimit}
                            title={
                                atFilterLimit
                                    ? t("resourceTable.filterLimitReached", { max: maxFilters })
                                    : undefined
                            }
                        >
                            <FaPlus size={10} />
                            {t("resourceTable.addFilter")}
                        </Button>
                    </Popover.Trigger>
                    <Portal>
                        <Popover.Positioner>
                            <Popover.Content
                                bg="bg.glass"
                                backdropFilter="blur(4px)"
                                borderColor="border"
                                borderWidth="1px"
                                borderRadius="md"
                                boxShadow="md"
                                minW="240px"
                                p={0}
                            >
                                <Popover.Body p={3}>
                                    <Stack gap={2}>
                                        {/* 1 — Column */}
                                        <OcSelect
                                            items={filterableColumns.map((c) => ({
                                                label: c.filterDef.label,
                                                value: c.id,
                                            }))}
                                            value={pendingCol?.id ?? ""}
                                            onValueChange={(val) => {
                                                const col = filterableColumns.find(
                                                    (c) => c.id === String(val),
                                                );
                                                setPendingCol(col ?? null);
                                                setPendingOp(
                                                    col ? defaultOperatorFor(col.filterDef) : null,
                                                );
                                                setPendingValue("");
                                                setPendingValueTo("");
                                                setPendingValues([]);
                                            }}
                                            placeholder={t("resourceTable.filterSelectColumn")}
                                            size="sm"
                                        />

                                        {/* 2 — Operator */}
                                        <OcSelect
                                            disabled={!pendingCol}
                                            items={
                                                pendingCol
                                                    ? operatorsFor(pendingCol.filterDef).map(
                                                          (op) => ({
                                                              label: t(
                                                                  `resourceTable.filterOperators.${op}`,
                                                              ),
                                                              value: op,
                                                          }),
                                                      )
                                                    : []
                                            }
                                            value={pendingOp ?? ""}
                                            onValueChange={(val) => {
                                                const op = val as ColumnFilterOperator;
                                                setPendingOp(op);
                                                setPendingValue("");
                                                setPendingValueTo("");
                                                setPendingValues([]);
                                            }}
                                            placeholder={t("resourceTable.filterSelectOperator")}
                                            size="sm"
                                        />

                                        {/* 3 — Value */}
                                        {!showValue ? (
                                            <Input
                                                size="sm"
                                                disabled
                                                placeholder={t("resourceTable.filterEnterValue")}
                                            />
                                        ) : (
                                            <>
                                                {/* enum — single select */}
                                                {pendingCol.filterDef.type === "enum" &&
                                                    pendingOp !== "inList" && (
                                                        <OcSelect
                                                            items={(
                                                                pendingCol.filterDef.options ?? []
                                                            ).map((o) => ({
                                                                label: o.label,
                                                                value: o.value,
                                                            }))}
                                                            value={pendingValue as string}
                                                            onValueChange={setPendingValue}
                                                            placeholder={t(
                                                                "resourceTable.filterEnterValue",
                                                            )}
                                                            size="sm"
                                                        />
                                                    )}

                                                {/* enum — inList: checkboxes */}
                                                {pendingCol.filterDef.type === "enum" &&
                                                    pendingOp === "inList" && (
                                                        <Box>
                                                            <Stack gap={1}>
                                                                {(
                                                                    pendingCol.filterDef.options ??
                                                                    []
                                                                ).map((opt) => (
                                                                    <Checkbox.Root
                                                                        key={opt.value}
                                                                        size="sm"
                                                                        checked={pendingValues.includes(
                                                                            opt.value,
                                                                        )}
                                                                        onCheckedChange={() =>
                                                                            toggleValue(opt.value)
                                                                        }
                                                                    >
                                                                        <Checkbox.HiddenInput />
                                                                        <Checkbox.Control
                                                                            borderColor="slate.icon.primary"
                                                                            borderWidth="1.5px"
                                                                        />
                                                                        <Checkbox.Label
                                                                            fontSize="xs"
                                                                            color="fg"
                                                                        >
                                                                            {opt.label}
                                                                        </Checkbox.Label>
                                                                    </Checkbox.Root>
                                                                ))}
                                                            </Stack>
                                                        </Box>
                                                    )}

                                                {/* string */}
                                                {pendingCol.filterDef.type === "string" && (
                                                    <Input
                                                        size="sm"
                                                        placeholder={t(
                                                            "resourceTable.filterEnterValue",
                                                        )}
                                                        value={pendingValue as string}
                                                        onChange={(e) =>
                                                            setPendingValue(e.target.value)
                                                        }
                                                        onKeyDown={(e) =>
                                                            e.key === "Enter" &&
                                                            canApply &&
                                                            handleApply()
                                                        }
                                                    />
                                                )}

                                                {/* numeric */}
                                                {pendingCol.filterDef.type === "numeric" && (
                                                    <Input
                                                        size="sm"
                                                        type="number"
                                                        placeholder={t(
                                                            "resourceTable.filterEnterValue",
                                                        )}
                                                        value={pendingValue as string}
                                                        onChange={(e) =>
                                                            setPendingValue(e.target.value)
                                                        }
                                                        onKeyDown={(e) =>
                                                            e.key === "Enter" &&
                                                            canApply &&
                                                            handleApply()
                                                        }
                                                    />
                                                )}

                                                {/* date — single */}
                                                {pendingCol.filterDef.type === "date" &&
                                                    pendingOp !== "between" && (
                                                        <Input
                                                            size="sm"
                                                            type="date"
                                                            value={pendingValue as string}
                                                            onChange={(e) =>
                                                                setPendingValue(e.target.value)
                                                            }
                                                        />
                                                    )}

                                                {/* date — between */}
                                                {pendingCol.filterDef.type === "date" &&
                                                    pendingOp === "between" && (
                                                        <Stack gap={1.5}>
                                                            <Input
                                                                size="sm"
                                                                type="date"
                                                                placeholder={t(
                                                                    "resourceTable.filterFrom",
                                                                )}
                                                                value={pendingValue as string}
                                                                onChange={(e) =>
                                                                    setPendingValue(e.target.value)
                                                                }
                                                            />
                                                            <Input
                                                                size="sm"
                                                                type="date"
                                                                placeholder={t(
                                                                    "resourceTable.filterTo",
                                                                )}
                                                                value={pendingValueTo as string}
                                                                onChange={(e) =>
                                                                    setPendingValueTo(
                                                                        e.target.value,
                                                                    )
                                                                }
                                                            />
                                                        </Stack>
                                                    )}
                                            </>
                                        )}

                                        {/* Apply */}
                                        <Button
                                            size="xs"
                                            variant="solid"
                                            colorPalette="brand"
                                            w="100%"
                                            disabled={!canApply}
                                            onClick={handleApply}
                                        >
                                            {t("resourceTable.filterApply")}
                                        </Button>
                                    </Stack>
                                </Popover.Body>
                            </Popover.Content>
                        </Popover.Positioner>
                    </Portal>
                </Popover.Root>
            )}
        </Flex>
    );
}
