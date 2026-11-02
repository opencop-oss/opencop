// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Badge, Button, Flex, Text } from "@chakra-ui/react";
import {
    startTransition,
    useActionState,
    useCallback,
    useMemo,
    useState,
    type ReactElement,
} from "react";
import { useTranslation } from "react-i18next";
import { assertNever } from "assert-never";
import {
    ColumnFilterBuilder,
    ResourceTable,
    createColumnHelper,
    toFilterableColumns,
    type ColumnFiltersState,
} from "@opencop/ui";
import type { PluginRecord, PluginStatus } from "@opencop/contributions/host";
import { formatProblem } from "@opencop/contributions/validation";
import { usePluginEnablementsState, usePluginRecords } from "./plugin-records";
import type { EnablementsFailureReason } from "./plugin-loader";
import { retryPluginLoad } from "./plugin-runtime";

const STATUS_PALETTE: Record<PluginStatus, string> = {
    active: "green",
    failed: "red",
    "version-mismatch": "orange",
};

const STATUS_LABEL_KEY: Record<PluginStatus, string> = {
    active: "adminPlugins.statusActive",
    failed: "adminPlugins.statusFailed",
    "version-mismatch": "adminPlugins.statusVersionMismatch",
};

interface PluginRow {
    id: string;
    name: string;
    version: string;
    status: PluginStatus;
    statusLabel: string;
    details: string;
}

const columnHelper = createColumnHelper<PluginRow>();

const FAILURE_MESSAGE_KEY: Record<EnablementsFailureReason, string> = {
    network: "adminPlugins.loadFailedNetwork",
    http: "adminPlugins.loadFailedHttp",
    schema: "adminPlugins.loadFailedSchema",
};

/**
 * Why the plugin list is empty, when the reason is that we could not find out
 * what to load.
 *
 * An empty table cannot distinguish "this install has no plugins" from "the
 * enablements document was unreachable" — and now that a whole feature ships as a
 * plugin, the second means military point symbols are simply gone for
 * the session. Retrying reconciles rather than resets, so it is safe to offer:
 * plugins that did load stay loaded.
 */
function EnablementsBanner(): ReactElement | null {
    const { t } = useTranslation();
    const state = usePluginEnablementsState();
    // React 19's action state rather than a hand-rolled pending pair: the button
    // dispatches one async action and needs only its in-flight flag.
    const [, dispatchRetry, retrying] = useActionState(async () => {
        await retryPluginLoad();
        return null;
    }, null);

    const onRetry = useCallback(() => {
        startTransition(() => dispatchRetry());
    }, [dispatchRetry]);

    // Exhaustive rather than an `if` chain: a sixth `EnablementsState` variant
    // should be a compile error here, not a banner that silently renders nothing.
    switch (state.status) {
        case "retrying":
            return (
                <Text color="fg.muted" paddingBottom="2">
                    {t("adminPlugins.loadRetrying")}
                </Text>
            );
        case "failed":
            break; // The banner below.
        case "pending":
        case "absent":
        case "loaded":
            return null;
        default:
            return assertNever(state);
    }

    return (
        <Flex
            direction="column"
            gap="2"
            padding="3"
            marginBottom="3"
            borderWidth="1px"
            borderColor="border.error"
            borderRadius="md"
            role="alert"
        >
            <Text fontWeight="medium">{t("adminPlugins.loadFailedTitle")}</Text>
            <Text color="fg.muted">
                {t(FAILURE_MESSAGE_KEY[state.reason] as never, { detail: state.detail })}
            </Text>
            <Button
                alignSelf="flex-start"
                size="sm"
                colorPalette="brand"
                onClick={onRetry}
                loading={retrying}
            >
                {t("adminPlugins.retry")}
            </Button>
        </Flex>
    );
}

/**
 * Admin list of loaded plugins with manifest metadata + status. Reactively reads
 * the manager's record registry, so it is empty until {@link PluginLoader} has
 * reconciled the enablements document. Built on the shared `ResourceTable` so it
 * matches the other admin tables.
 *
 * The status shown is the **lifecycle** status the manager records at load time.
 * A contribution that throws later is contained by the host and logged, but does
 * not move a record off `active`; runtime health is a separate contract that does
 * not exist yet.
 */
/** Every reason a plugin was refused, one per line; `undefined` when it was refused for none. */
function refusalLines(problems: PluginRecord["problems"]): string | undefined {
    const lines = problems
        .filter(({ severity }) => severity === "error")
        .map((problem) => (problem.kind === "manifest" ? problem.message : formatProblem(problem)));
    return lines.length > 0 ? lines.join("\n") : undefined;
}

export function PluginsPanel(): ReactElement {
    const { t } = useTranslation();
    const records = usePluginRecords();

    const [globalFilter, setGlobalFilter] = useState("");
    const [columnFilters, setColumnFilters] = useState<ColumnFiltersState>([]);

    const rows = useMemo(
        (): PluginRow[] =>
            records.map(({ manifest, status, error, problems }) => ({
                id: manifest.id,
                name: manifest.name,
                version: manifest.version,
                status,
                statusLabel: t(STATUS_LABEL_KEY[status] as never),
                details: refusalLines(problems) ?? error ?? manifest.description ?? "",
            })),
        [records, t],
    );

    const columns = useMemo(
        () => [
            columnHelper.accessor("name", {
                header: () => t("adminPlugins.name"),
                cell: (info) => <Text fontWeight="medium">{info.getValue()}</Text>,
                enableSorting: true,
            }),
            columnHelper.accessor("version", {
                header: () => t("adminPlugins.version"),
                cell: (info) => info.getValue(),
                enableSorting: true,
            }),
            columnHelper.accessor("statusLabel", {
                header: () => t("adminPlugins.status"),
                cell: (info) => (
                    <Badge
                        colorPalette={STATUS_PALETTE[info.row.original.status]}
                        variant={info.row.original.status === "active" ? "solid" : "subtle"}
                    >
                        {info.getValue()}
                    </Badge>
                ),
                enableSorting: false,
            }),
            columnHelper.accessor("details", {
                header: () => t("adminPlugins.details"),
                cell: (info) => (
                    <Text color="fg.muted" whiteSpace="pre-line">
                        {info.getValue()}
                    </Text>
                ),
                enableSorting: false,
            }),
        ],
        [t],
    );

    return (
        <Flex height="100%" width="100%" direction="column">
            <EnablementsBanner />
            <ResourceTable
                aria-label={t("adminPlugins.title")}
                data={rows}
                columns={columns}
                getRowId={(row) => row.id}
                globalFilter={globalFilter}
                onGlobalFilterChange={setGlobalFilter}
                columnFilters={columnFilters}
                onColumnFiltersChange={setColumnFilters}
                // Empty toolbar (no bulk actions on a read-only list) so the
                // shared search + filter bar renders, matching the other admin tables.
                toolbar={{}}
                filterBar={
                    <ColumnFilterBuilder
                        filterableColumns={toFilterableColumns(columns)}
                        value={columnFilters}
                        onChange={setColumnFilters}
                        searchValue={globalFilter}
                        onSearchChange={setGlobalFilter}
                        searchPlaceholder={t("resourceTable.filterForAnyField")}
                    />
                }
            />
        </Flex>
    );
}
