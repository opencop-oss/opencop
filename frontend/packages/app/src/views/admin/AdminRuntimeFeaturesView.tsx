// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Alert, Button, Text } from "@chakra-ui/react";
import { useQuery } from "@tanstack/react-query";
import { getErrorMessage } from "@opencop/api";
import {
    createColumnHelper,
    ColumnFilterBuilder,
    ResourceTable,
    toFilterableColumns,
} from "@opencop/ui";
import type { ColumnFiltersState, ResourceTableToolbar } from "@opencop/ui";
import { DateTime } from "luxon";
import { type ReactElement, useCallback, useMemo } from "react";
import { useTranslation } from "react-i18next";
import { appMetadataQueries } from "~/features/admin/app-metadata.queries";
import { useResourceTableState } from "~/features/admin/use-resource-table-state.hook";
import { RuntimeFeatureDrawer } from "~/features/admin/runtime-features/RuntimeFeatureDrawer";
import type { RuntimeFeatureDrawerState } from "~/features/admin/runtime-features/RuntimeFeatureDrawer";
import {
    audienceUserGroupIds,
    consumerClassLabelKey,
} from "~/features/admin/runtime-features/audience.util";
import { StatusBadge } from "~/features/admin/runtime-features/StatusBadge";
import {
    EMPTY_CELL,
    STATUS_LABEL_KEY,
    STATUS_ORDER,
} from "~/features/admin/runtime-features/status.const";
import {
    buildRuntimeFeatureRows,
    computeFlagDrift,
    type RuntimeFeatureRow,
} from "~/features/admin/runtime-features/runtime-features.rows";
import { useRuntimeFeatureSync } from "~/features/admin/runtime-features/use-runtime-feature-sync.hook";
import { ViewDrawerProvider, useViewDrawerContext } from "~/features/drawers/ViewDrawerContext";
import { useToast } from "~/features/notifications/use-toast.hook";
import { useUserTimezone } from "~/features/preferences/use-user-timezone.hook";

const columnHelper = createColumnHelper<RuntimeFeatureRow>();

/**
 * Archived flags are hidden on load: the useful default view is "what this build actually uses".
 *
 * Seeded as a real filter rather than a hard-coded slice of the data, so it renders as a removable
 * chip in the filter bar. An admin can see that rows are being filtered and clear it in one click —
 * an archived row keeps its stored audience and a build that still declares the name keeps gating on
 * it, so it must never be possible to conclude from this page that the row does not exist.
 */
const DEFAULT_COLUMN_FILTERS: ColumnFiltersState = [
    // ColumnFilterBuilder stores one column's filters as an array, even when there is only one.
    { id: "status", value: [{ operator: "notEquals", value: "archived" }] },
];

export function AdminRuntimeFeaturesView() {
    return (
        <ViewDrawerProvider initialModal={{ type: "none" }}>
            <AdminRuntimeFeaturesViewContent />
        </ViewDrawerProvider>
    );
}

function AdminRuntimeFeaturesViewContent(): ReactElement {
    const { t } = useTranslation();
    const timezone = useUserTimezone();
    const { showToast } = useToast();
    const { selectedId, openDrawer } = useViewDrawerContext();

    const {
        data: metadata,
        isLoading,
        isFetching,
        refetch,
    } = useQuery(appMetadataQueries.detail());

    const catalog = metadata?.admin?.runtimeFeatures;
    const rows = useMemo(() => buildRuntimeFeatureRows(catalog), [catalog]);
    const drift = useMemo(() => computeFlagDrift(rows), [rows]);

    const { sync, isSyncing } = useRuntimeFeatureSync();
    const tableState = useResourceTableState(DEFAULT_COLUMN_FILTERS);

    const handleSync = useCallback(async () => {
        let result;
        try {
            result = await sync(drift.missing);
        } catch (error) {
            // The server may have committed an earlier prefix before it stopped, so this says what
            // is uncertain rather than claiming nothing happened. The catalog is refetched either
            // way, so the table already shows whatever did land.
            showToast({
                type: "error",
                title: t("runtimeFeatures.syncFailed"),
                description: getErrorMessage(error),
            });
            return;
        }
        if (result.complete) {
            showToast({
                type: "success",
                title: t("runtimeFeatures.syncComplete", { count: result.created.length }),
            });
            return;
        }
        // Partial progress is real progress and is already durable; report what still needs
        // attention rather than implying the whole run failed.
        showToast({
            type: "error",
            title: t("runtimeFeatures.syncIncomplete", {
                converged: result.created.length + result.alreadyRegistered.length,
                failed: result.rejected.length,
            }),
            description: result.rejected.map((rejection) => rejection.message).join(" · "),
        });
    }, [sync, drift.missing, showToast, t]);

    const drawerState: RuntimeFeatureDrawerState = useMemo(() => {
        if (!selectedId) return { type: "closed" };
        const row = rows.find((candidate) => candidate.name === selectedId);
        return row ? { type: "runtimeFeature", entity: row } : { type: "closed" };
    }, [selectedId, rows]);

    const columns = useMemo(
        () => [
            columnHelper.accessor("name", {
                header: () => t("runtimeFeatures.name"),
                cell: (info) => <Text fontWeight="medium">{info.getValue()}</Text>,
                enableSorting: true,
                meta: {
                    filterDef: { label: t("runtimeFeatures.name"), type: "string" as const },
                },
                filterFn: "ocStringFilter",
            }),
            columnHelper.accessor((row) => row.status, {
                id: "status",
                header: () => t("runtimeFeatures.status"),
                cell: (info) => <StatusBadge status={info.row.original.status} />,
                // Sortable, unlike the badge columns on other admin tables: status is this page's
                // primary triage axis, and sorting groups every unregistered flag together.
                enableSorting: true,
                meta: {
                    filterDef: {
                        label: t("runtimeFeatures.status"),
                        type: "enum" as const,
                        options: STATUS_ORDER.map((status) => ({
                            label: t(STATUS_LABEL_KEY[status] as never) as string,
                            value: status,
                        })),
                    },
                },
                filterFn: "ocEnumFilter",
            }),
            columnHelper.accessor((row) => row.record?.consumerClass ?? EMPTY_CELL, {
                id: "consumer",
                header: () => t("runtimeFeatures.consumer"),
                cell: (info) => {
                    const consumerClass = info.row.original.record?.consumerClass;
                    return (
                        <Text color="fg.muted">
                            {consumerClass
                                ? t(consumerClassLabelKey(consumerClass) as never)
                                : EMPTY_CELL}
                        </Text>
                    );
                },
                enableSorting: true,
                meta: {
                    filterDef: {
                        label: t("runtimeFeatures.consumer"),
                        type: "enum" as const,
                        options: (["FRONTEND_ONLY", "BACKEND_ENFORCED"] as const).map(
                            (consumerClass) => {
                                const label = t(
                                    consumerClassLabelKey(consumerClass) as never,
                                ) as string;
                                return { label, value: consumerClass };
                            },
                        ),
                    },
                },
                filterFn: "ocEnumFilter",
            }),
            columnHelper.accessor((row) => row.record?.audience.mode ?? EMPTY_CELL, {
                id: "audience",
                header: () => t("runtimeFeatures.audience"),
                cell: (info) => {
                    const record = info.row.original.record;
                    if (!record) return <Text color="fg.muted">{EMPTY_CELL}</Text>;
                    const count = audienceUserGroupIds(record.audience).length;
                    return (
                        <Text>
                            {t(`runtimeFeatures.audienceModes.${record.audience.mode}` as never)}
                            {record.audience.mode === "USER_GROUPS" && ` (${count})`}
                        </Text>
                    );
                },
                enableSorting: true,
                meta: {
                    filterDef: {
                        label: t("runtimeFeatures.audience"),
                        type: "enum" as const,
                        options: (["NONE", "ALL", "USER_GROUPS"] as const).map((mode) => ({
                            label: t(`runtimeFeatures.audienceModes.${mode}` as never) as string,
                            value: mode,
                        })),
                    },
                },
                filterFn: "ocEnumFilter",
            }),
            columnHelper.accessor((row) => row.record?.revision ?? "", {
                id: "revision",
                header: () => t("runtimeFeatures.revision"),
                cell: (info) => (
                    <Text color="fg.muted">{info.row.original.record?.revision ?? EMPTY_CELL}</Text>
                ),
                enableSorting: true,
            }),
            columnHelper.accessor((row) => row.record?.updatedAt ?? "", {
                id: "updatedAt",
                header: () => t("runtimeFeatures.updatedAt"),
                cell: (info) => {
                    const updatedAt = info.row.original.record?.updatedAt;
                    return (
                        <Text color="fg.muted">
                            {updatedAt
                                ? DateTime.fromISO(updatedAt)
                                      .setZone(timezone)
                                      .toLocaleString(DateTime.DATETIME_MED)
                                : EMPTY_CELL}
                        </Text>
                    );
                },
                enableSorting: true,
            }),
        ],
        [t, timezone],
    );

    // Refetch only — it never registers anything, so an admin can always reread the catalog without
    // wondering whether the button also wrote to it. Sync is a primary action that is meaningful
    // solely when there is drift, so it lives in the alert rather than sitting permanently disabled
    // in the toolbar.
    const toolbar: ResourceTableToolbar = { onRefresh: refetch, isRefreshing: isFetching };

    return (
        <>
            {drift.missing.length > 0 && (
                <UnsyncedFlagsAlert
                    names={drift.missing}
                    onSync={handleSync}
                    isSyncing={isSyncing}
                />
            )}
            {drift.classConflicts.length > 0 && <ClassConflictAlert names={drift.classConflicts} />}
            <ResourceTable
                data={rows}
                columns={columns}
                isLoading={isLoading}
                isFetching={isFetching}
                getRowId={(row) => row.name}
                onRowClick={(row) => openDrawer(row.name)}
                toolbar={toolbar}
                aria-label={t("adminNav.runtimeFeatures")}
                detailDrawer={<RuntimeFeatureDrawer drawerState={drawerState} />}
                {...tableState}
                filterBar={
                    <ColumnFilterBuilder
                        filterableColumns={toFilterableColumns(columns)}
                        value={tableState.columnFilters}
                        onChange={tableState.onColumnFiltersChange}
                        searchValue={tableState.globalFilter}
                        onSearchChange={tableState.onGlobalFilterChange}
                        searchPlaceholder={t("resourceTable.filterForAnyField")}
                    />
                }
            />
        </>
    );
}

interface UnsyncedFlagsAlertProps {
    names: readonly string[];
    onSync: () => void;
    isSyncing: boolean;
}

/**
 * Reports declared flags the catalog is missing, and carries the only control that creates them.
 *
 * Informational, not a warning: a missing definition resolves to disabled, so an unsynced flag is a
 * pending setup step rather than something broken. Registration is a durable per-environment catalog
 * write, so it stays an explicit act — nothing here fires on load. It is also self-correcting: drift
 * is recomputed from the live catalog, so the alert disappears once the environment catches up and
 * needs no "already reconciled" marker to suppress it.
 */
function UnsyncedFlagsAlert({ names, onSync, isSyncing }: UnsyncedFlagsAlertProps): ReactElement {
    const { t } = useTranslation();
    return (
        <Alert.Root status="info" mb={3}>
            <Alert.Indicator />
            <Alert.Content>
                <Alert.Title>{t("runtimeFeatures.driftTitle")}</Alert.Title>
                <Alert.Description>
                    {/* No `count`: the message names the flags rather than counting them, so
                        passing one only puts it on i18next's plural mechanism, which generates
                        per-locale variants (French has a `many` category) that nobody fills in. */}
                    {t("runtimeFeatures.driftMissing", { names: names.join(", ") })}
                </Alert.Description>
            </Alert.Content>
            {/* Trailing action rather than stacked under the text: `ms="auto"` pins it to the far
                edge regardless of how long the name list runs, and `flexShrink` stops a long list
                from squeezing the label onto two lines. */}
            <Button
                size="sm"
                colorPalette="brand"
                onClick={onSync}
                loading={isSyncing}
                alignSelf="center"
                ms="auto"
                flexShrink={0}
            >
                {t("runtimeFeatures.sync")}
            </Button>
        </Alert.Root>
    );
}

/**
 * A declared name already owned by a `BACKEND_ENFORCED` row.
 *
 * Kept separate from the unsynced alert and left at error severity: Sync cannot resolve it —
 * registration never reclassifies a backend-owned row — so it needs a manifest edit or a rename, and
 * offering no action alongside an informational notice would understate it.
 */
function ClassConflictAlert({ names }: { names: readonly string[] }): ReactElement {
    const { t } = useTranslation();
    return (
        <Alert.Root status="error" mb={3}>
            <Alert.Indicator />
            <Alert.Content>
                <Alert.Title>{t("runtimeFeatures.driftConflictTitle")}</Alert.Title>
                <Alert.Description>
                    {t("runtimeFeatures.driftClassConflict", { names: names.join(", ") })}
                </Alert.Description>
            </Alert.Content>
        </Alert.Root>
    );
}
