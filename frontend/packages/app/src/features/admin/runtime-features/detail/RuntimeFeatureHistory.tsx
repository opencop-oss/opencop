// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Stack, Text } from "@chakra-ui/react";
import { type RuntimeFeatureAudience, type RuntimeFeatureHistoryItem } from "@opencop/api";
import { createColumnHelper } from "@opencop/ui";
import { type ReactElement, useMemo } from "react";
import { useTranslation } from "react-i18next";
import { sortByIsoTimestamp } from "~/features/admin/sort-by-iso-timestamp.util";
import { actorLabel, occurredAtLabel } from "~/features/drawers/audit-change.util";
import { AuditHistoryTable } from "~/features/drawers/AuditHistoryTable";
import { useAuditHistoryPaging } from "~/features/drawers/use-audit-history-paging.hook";
import { useUserTimezone } from "~/features/preferences/use-user-timezone.hook";
import { runtimeFeatureQueries } from "../runtime-features.queries";
import { EMPTY_CELL } from "../status.const";

const columnHelper = createColumnHelper<RuntimeFeatureHistoryItem>();

function audiencesEqual(
    before: RuntimeFeatureAudience | null,
    after: RuntimeFeatureAudience,
): boolean {
    if (before == null || before.mode !== after.mode) return false;
    if (before.mode !== "USER_GROUPS" || after.mode !== "USER_GROUPS") return true;

    const beforeIds = new Set(before.userGroupIds);
    return (
        beforeIds.size === after.userGroupIds.length &&
        after.userGroupIds.every((id) => beforeIds.has(id))
    );
}

function groupDelta(
    before: RuntimeFeatureAudience | null,
    after: RuntimeFeatureAudience,
): { added: number; removed: number } | null {
    if (before?.mode !== "USER_GROUPS" || after.mode !== "USER_GROUPS") return null;

    const beforeIds = new Set(before.userGroupIds);
    const afterIds = new Set(after.userGroupIds);
    return {
        added: after.userGroupIds.filter((id) => !beforeIds.has(id)).length,
        removed: before.userGroupIds.filter((id) => !afterIds.has(id)).length,
    };
}

/**
 * The drawer's successful-action history for one feature.
 *
 * Its own file rather than an inner component of the drawer content, matching how every other
 * entity drawer keeps a side-nav section (see `LayerMembersSection`): it owns its own query and
 * empty/error states and needs nothing from its parent but the name.
 *
 * The paging, drift recovery and focus handling live in `useAuditHistoryPaging` and
 * `AuditHistoryTable`, shared with the resource audit ledger — this file is only the columns that
 * make a feature's audience history readable.
 */
export function RuntimeFeatureHistory({ name }: { name: string }): ReactElement {
    const { t } = useTranslation();
    const timezone = useUserTimezone();
    const paging = useAuditHistoryPaging(runtimeFeatureQueries.history(name));

    const columns = useMemo(() => {
        const audienceLabel = (audience: RuntimeFeatureAudience): string =>
            audience.mode === "USER_GROUPS"
                ? t("runtimeFeatures.audienceGroupCount", {
                      groupCount: audience.userGroupIds.length,
                  })
                : t(`runtimeFeatures.audienceModes.${audience.mode}` as never);
        const operationOf = (item: RuntimeFeatureHistoryItem): string =>
            t(
                `runtimeFeatures.historyOperations.${
                    item.operation === "PATCH" &&
                    audiencesEqual(item.beforeAudience, item.afterAudience)
                        ? "PATCH_NO_CHANGE"
                        : item.operation
                }` as never,
            );
        const transitionOf = (item: RuntimeFeatureHistoryItem): string =>
            `${item.beforeAudience ? audienceLabel(item.beforeAudience) : EMPTY_CELL} → ${audienceLabel(item.afterAudience)}`;
        const deltaOf = (item: RuntimeFeatureHistoryItem): string | null => {
            const delta = groupDelta(item.beforeAudience, item.afterAudience);
            return delta != null && (delta.added > 0 || delta.removed > 0)
                ? t("runtimeFeatures.audienceGroupDelta", delta)
                : null;
        };

        return [
            columnHelper.accessor("occurredAt", {
                id: "occurredAt",
                header: t("runtimeFeatures.changedAt"),
                size: 184,
                cell: (info) => occurredAtLabel(info.getValue(), timezone),
                enableSorting: true,
                sortingFn: sortByIsoTimestamp,
                meta: {
                    filterDef: { label: t("runtimeFeatures.changedAt"), type: "date" as const },
                },
                filterFn: "ocDateFilter",
            }),
            // Accessed as every line the cell shows so the search box reaches the audiences too.
            columnHelper.accessor(
                (item) => [operationOf(item), transitionOf(item), deltaOf(item)].join(" "),
                {
                    id: "change",
                    header: t("runtimeFeatures.change"),
                    size: 300,
                    cell: (info) => {
                        const item = info.row.original;
                        const delta = deltaOf(item);
                        return (
                            <Stack gap={0}>
                                <Text>{operationOf(item)}</Text>
                                <Text fontSize="xs" color="fg.muted">
                                    {transitionOf(item)}
                                </Text>
                                {delta != null && (
                                    <Text fontSize="xs" color="fg.muted">
                                        {delta}
                                    </Text>
                                )}
                            </Stack>
                        );
                    },
                    enableSorting: true,
                    meta: {
                        filterDef: {
                            label: t("runtimeFeatures.change"),
                            type: "string" as const,
                            operators: ["contains", "notContains"] as const,
                        },
                    },
                    filterFn: "ocStringFilter",
                },
            ),
            columnHelper.accessor((item) => item.revisionAfter, {
                id: "revision",
                header: t("runtimeFeatures.revision"),
                size: 96,
                cell: (info) =>
                    `${info.row.original.revisionBefore ?? EMPTY_CELL} → ${info.getValue()}`,
                enableSorting: true,
            }),
            columnHelper.accessor((item) => actorLabel(item.actor), {
                id: "actor",
                header: t("runtimeFeatures.actor"),
                size: 144,
                enableSorting: true,
                meta: {
                    filterDef: { label: t("runtimeFeatures.actor"), type: "string" as const },
                },
                filterFn: "ocStringFilter",
            }),
        ];
    }, [t, timezone]);

    return (
        <AuditHistoryTable
            paging={paging}
            columns={columns}
            labels={{
                table: t("runtimeFeatures.history"),
                empty: t("runtimeFeatures.noHistory"),
                unavailable: t("runtimeFeatures.historyUnavailable"),
                refreshFailed: t("runtimeFeatures.historyRefreshFailed"),
                changedDuringPaging: t("runtimeFeatures.historyChangedDuringPaging"),
                loadMore: t("runtimeFeatures.historyLoadMore"),
            }}
        />
    );
}
