// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Stack, Text } from "@chakra-ui/react";
import type { AuditEntityType, AuditHistoryItem } from "@opencop/api";
import { createColumnHelper } from "@opencop/ui";
import { type ReactElement, useMemo } from "react";
import { useTranslation } from "react-i18next";
import { auditQueries } from "~/features/admin/audit/audit.queries";
import { sortByIsoTimestamp } from "~/features/admin/sort-by-iso-timestamp.util";
import { useUserTimezone } from "~/features/preferences/use-user-timezone.hook";
import {
    actorLabel,
    occurredAtLabel,
    deltaLabels,
    detailLabels,
    fieldChangeLabels,
} from "./audit-change.util";
import { AuditHistoryTable } from "./AuditHistoryTable";
import { useAuditHistoryPaging } from "./use-audit-history-paging.hook";

const columnHelper = createColumnHelper<AuditHistoryItem>();

interface AuditHistorySectionProps {
    entityType: AuditEntityType;
    entityId: string;
}

/**
 * One resource's audit history, as a drawer section.
 *
 * Parameterised by type and id rather than written per resource: the ledger is one table with one
 * read contract, and the Change cell is driven by what each row's payload contains, so a resource
 * or an operation added in Core shows up here with no change.
 *
 * Global administrators only — the read endpoint rejects anyone else, so mount it behind the same
 * check rather than rendering a section that can only fail.
 */
export function AuditHistorySection({
    entityType,
    entityId,
}: AuditHistorySectionProps): ReactElement {
    const { t } = useTranslation();
    const timezone = useUserTimezone();
    const paging = useAuditHistoryPaging(auditQueries.history(entityType, entityId));

    const columns = useMemo(() => {
        const operationLabel = (item: AuditHistoryItem): string =>
            t(`audit.operations.${item.operation}`, { defaultValue: item.operation });
        const detailsOf = (item: AuditHistoryItem): string[] => [
            ...fieldChangeLabels(t, item.change),
            ...deltaLabels(t, item.change),
            ...detailLabels(t, item.change),
        ];

        return [
            columnHelper.accessor("occurredAt", {
                id: "occurredAt",
                header: t("audit.changedAt"),
                size: 184,
                cell: (info) => occurredAtLabel(info.getValue(), timezone),
                enableSorting: true,
                sortingFn: sortByIsoTimestamp,
                meta: { filterDef: { label: t("audit.changedAt"), type: "date" as const } },
                filterFn: "ocDateFilter",
            }),
            // Accessed as the operation plus its details so the search box reaches the field
            // names and values a row shows, not only the operation word.
            columnHelper.accessor((item) => [operationLabel(item), ...detailsOf(item)].join(" "), {
                id: "change",
                header: t("audit.change"),
                size: 360,
                cell: (info) => {
                    const item = info.row.original;
                    return (
                        <Stack gap={0}>
                            <Text>{operationLabel(item)}</Text>
                            {detailsOf(item).map((detail) => (
                                <Text key={detail} fontSize="xs" color="fg.muted">
                                    {detail}
                                </Text>
                            ))}
                        </Stack>
                    );
                },
                enableSorting: true,
                meta: {
                    filterDef: {
                        label: t("audit.change"),
                        type: "string" as const,
                        operators: ["contains", "notContains"] as const,
                    },
                },
                filterFn: "ocStringFilter",
            }),
            columnHelper.accessor((item) => actorLabel(item.actor), {
                id: "actor",
                header: t("audit.actor"),
                size: 144,
                enableSorting: true,
                meta: { filterDef: { label: t("audit.actor"), type: "string" as const } },
                filterFn: "ocStringFilter",
            }),
        ];
    }, [t, timezone]);

    return (
        <AuditHistoryTable
            paging={paging}
            columns={columns}
            labels={{
                table: t("audit.sectionLabel"),
                empty: t("audit.noHistory"),
                unavailable: t("audit.historyUnavailable"),
                refreshFailed: t("audit.historyRefreshFailed"),
                changedDuringPaging: t("audit.historyChangedDuringPaging"),
                loadMore: t("audit.historyLoadMore"),
            }}
        />
    );
}
