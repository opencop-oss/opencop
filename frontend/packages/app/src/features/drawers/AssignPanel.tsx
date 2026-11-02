// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, Button, Stack } from "@chakra-ui/react";
import type { ReactElement } from "react";
import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { FaPlus } from "react-icons/fa";
import { omit } from "lodash";
import { getErrorMessage } from "@opencop/api";
import {
    DrawerSectionHeader,
    OcSelect,
    ResourceTable,
    Tooltip,
    type RowSelectionState,
} from "@opencop/ui";
import { useToast } from "~/features/notifications/use-toast.hook";
import { succeededAssignIds } from "./relation-section.util";
import type {
    AnyColumns,
    DrawerRelationRoleOptions,
    DrawerRelationSectionLabels,
    DrawerRelationServerPaging,
} from "./drawer-relation.model";

/**
 * Assign sub-panel: a heading naming the flow and its target with a back control
 * beside it, then a search + multi-select picker of unassigned items with an
 * optional role and an Add action. Owns its own transient state
 * (fresh each time it mounts), so the parent just toggles it via `mode`.
 */
export function AssignPanel<U extends object, R extends string = string>({
    unassigned,
    unassignedColumns,
    getUnassignedRowId,
    unassignedLoading,
    onAssign,
    roleOptions,
    serverPaging,
    labels,
    onBack,
    onAdded,
}: {
    unassigned: U[];
    unassignedColumns: AnyColumns<U>;
    getUnassignedRowId: (row: U) => string;
    unassignedLoading?: boolean;
    onAssign: (ids: string[], role?: R) => Promise<unknown>;
    roleOptions?: DrawerRelationRoleOptions<R>;
    serverPaging?: DrawerRelationServerPaging;
    labels: DrawerRelationSectionLabels;
    /** Return to list mode without assigning. */
    onBack: () => void;
    /** Called after a successful assign (parent returns to list mode). */
    onAdded: () => void;
}): ReactElement {
    const { t } = useTranslation();
    const { showToast } = useToast();
    const serverSearch = serverPaging != null;

    const [assignFilter, setAssignFilter] = useState("");
    const [assignSelection, setAssignSelection] = useState<RowSelectionState>({});
    const [role, setRole] = useState("");
    const [isAssigning, setIsAssigning] = useState(false);
    const assignIds = useMemo(() => Object.keys(assignSelection), [assignSelection]);
    const needsRole = roleOptions != null;

    // Options in resolver form depend on the current selection.
    const resolvedRoleOptions = useMemo(
        () => (typeof roleOptions === "function" ? roleOptions(assignIds) : roleOptions),
        [roleOptions, assignIds],
    );

    /**
     * Narrowing the selection can withdraw an already-picked role, so the role is derived rather
     * than synced by an effect: no render or submit sees a role the caller may no longer assign.
     */
    const effectiveRole = resolvedRoleOptions?.some((o) => o.value === role) ? role : "";
    const canAdd = assignIds.length > 0 && (!needsRole || effectiveRole !== "") && !isAssigning;

    /**
     * The button spells out what pressing it will do. Bare verb until something is actually
     * selected, then the count — and the chosen role when the relation carries one, so the
     * user can see they're adding *as* that role without re-reading the select.
     */
    const roleLabel = resolvedRoleOptions?.find((o) => o.value === effectiveRole)?.label;
    const addLabel =
        assignIds.length === 0
            ? labels.add
            : roleLabel != null
              ? t("adminDialogs.addAsRole", { role: roleLabel, selected: assignIds.length })
              : t("adminDialogs.addWithCount", { selected: assignIds.length });

    /** A disabled button explains nothing on its own — say which prerequisite is missing. */
    const addDisabledReason =
        needsRole && effectiveRole === "" ? t("adminDialogs.selectRoleFirst") : undefined;

    async function handleAdd() {
        if (!canAdd) return;
        setIsAssigning(true);
        try {
            await onAssign(assignIds, needsRole ? (effectiveRole as R) : undefined);
            onAdded();
        } catch (e) {
            // A fan-out assign can partially succeed. Drop whatever already committed before the
            // user retries — otherwise the retry re-sends those ids, and a role changed in the
            // meantime would silently overwrite the assignments that landed.
            const committed = succeededAssignIds(e);
            if (committed.length > 0) {
                setAssignSelection((prev) => omit(prev, committed));
            }
            // Keep the generic title, but append the server's reason for context.
            showToast({
                type: "error",
                title: labels.assignError,
                description: getErrorMessage(e),
            });
        } finally {
            setIsAssigning(false);
        }
    }

    return (
        <Stack gap={1} minH={0} flex="1">
            <DrawerSectionHeader
                title={labels.assignTitle}
                description={labels.assignDescription}
                back={{ label: labels.back, onBack }}
            />
            {/* Flex column, not a plain block: ResourceTable's scroll container is `flex="1"
                minH={0}`, which is inert unless its parent is a flex container. As a block the
                container grows to content height, so it never scrolls and the row virtualizer
                sees the whole list as its viewport — every row renders and the "near the end →
                load more" effect refires until the entire pool is fetched. */}
            <Box flex="1" minH={0} display="flex" flexDirection="column">
                <ResourceTable
                    transparent
                    aria-label={labels.assignAriaLabel}
                    data={unassigned}
                    columns={unassignedColumns}
                    isLoading={unassignedLoading}
                    getRowId={getUnassignedRowId}
                    enableRowSelection
                    rowSelection={assignSelection}
                    onRowSelectionChange={setAssignSelection}
                    globalFilter={assignFilter}
                    onGlobalFilterChange={(value) => {
                        setAssignFilter(value);
                        serverPaging?.onSearchChange(value);
                    }}
                    manualFiltering={serverSearch}
                    enableSorting={!serverSearch}
                    onLoadMore={serverPaging?.onLoadMore}
                    hasNextPage={serverPaging?.hasMore}
                    isFetchingNextPage={serverPaging?.isFetchingMore}
                    isError={serverPaging?.isError}
                    onRetry={serverPaging?.onRetry}
                    toolbar={{
                        leading: (
                            <>
                                {needsRole && (
                                    <Box w="160px">
                                        <OcSelect
                                            size="xs"
                                            items={resolvedRoleOptions}
                                            value={effectiveRole}
                                            onValueChange={setRole}
                                            placeholder={
                                                labels.rolePlaceholder ??
                                                t("adminDialogs.rolePlaceholder")
                                            }
                                        />
                                    </Box>
                                )}
                                {/* Ark disabled controls emit no pointer events, so the reason
                                    needs a focusable wrapper to hang the tooltip on — same
                                    pattern as DrawerToolbarButton. */}
                                <Tooltip
                                    content={addDisabledReason}
                                    disabled={addDisabledReason == null}
                                >
                                    <Box
                                        as="span"
                                        tabIndex={addDisabledReason != null ? 0 : -1}
                                        display="inline-flex"
                                    >
                                        <Button
                                            size="xs"
                                            colorPalette="brand"
                                            onClick={handleAdd}
                                            disabled={!canAdd}
                                            loading={isAssigning}
                                            loadingText={addLabel}
                                        >
                                            <FaPlus /> {addLabel}
                                        </Button>
                                    </Box>
                                </Tooltip>
                            </>
                        ),
                    }}
                />
            </Box>
        </Stack>
    );
}
