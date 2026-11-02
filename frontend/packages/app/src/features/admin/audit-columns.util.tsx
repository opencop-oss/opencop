// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { HStack, Text } from "@chakra-ui/react";
import type { ColumnDef, ColumnMeta } from "@tanstack/react-table";
import type { TFunction } from "i18next";
import { formatDateMilitary } from "~/date.util";
import { LastEventInfoTooltip } from "~/features/drawers/LastEventInfoTooltip";
import type { LastEventSubject } from "~/features/drawers/LastEventInfoTooltip";
import { sortByIsoTimestamp } from "./sort-by-iso-timestamp.util";

/** Raw ISO timestamps; formatted in the cell so each column sorts by instant. */
export interface AuditRow {
    createdAt?: string | null;
    updatedAt?: string | null;
    lastEventAt?: string | null;
}

type AuditColumn<T> = ColumnDef<T, string | null | undefined>;

type AuditLabelKey = "common.createdAt" | "common.updatedAt" | "common.lastEventAt";

/**
 * The Created At / Last Updated / Last Event columns shared by the resource tables, in display
 * order. `createdAtFilterDef` makes Created At filterable.
 *
 * Sorts client-side by instant, with empty values last in either direction. In a server-paged
 * table the column ids (`createdAt`, `updatedAt`, `lastEventAt`) must be in the server's sort
 * allowlist, since `sortingFn` is ignored under `manualSorting`.
 */
export function auditColumns<T extends AuditRow>(
    t: TFunction,
    timezone: string,
    lastEventSubject: LastEventSubject,
    createdAtFilterDef?: ColumnMeta<T, unknown>["filterDef"],
): AuditColumn<T>[] {
    const timestampColumn = (
        key: keyof AuditRow,
        labelKey: AuditLabelKey,
        header?: AuditColumn<T>["header"],
        filterDef?: ColumnMeta<T, unknown>["filterDef"],
    ) =>
        ({
            id: key,
            // null -> undefined so `sortUndefined` sees an empty value
            accessorFn: (row) => row[key] ?? undefined,
            header: header ?? (() => t(labelKey)),
            cell: (info) => {
                const val = info.getValue();
                return val ? formatDateMilitary(val, timezone) : "-";
            },
            enableSorting: true,
            // Honoured for asc and desc, unlike the comparator, which TanStack inverts for desc
            sortUndefined: "last",
            // Pinned so the first click doesn't depend on whether row 1's value is empty
            sortDescFirst: false,
            sortingFn: sortByIsoTimestamp,
            size: 200,
            meta: { columnLabel: t(labelKey), ...(filterDef && { filterDef }) },
        }) satisfies AuditColumn<T>;

    return [
        timestampColumn("createdAt", "common.createdAt", undefined, createdAtFilterDef),
        timestampColumn("updatedAt", "common.updatedAt"),
        timestampColumn("lastEventAt", "common.lastEventAt", () => (
            <HStack gap={1}>
                <Text color="fg.muted">{t("common.lastEventAt")}</Text>
                <LastEventInfoTooltip subject={lastEventSubject} />
            </HStack>
        )),
    ];
}
