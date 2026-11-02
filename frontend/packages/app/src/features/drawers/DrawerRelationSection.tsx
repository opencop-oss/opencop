// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box } from "@chakra-ui/react";
import type { ReactElement, ReactNode } from "react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { FaPlus, FaTrash, FaUserShield } from "react-icons/fa";
import { assertNever } from "assert-never";
import {
    type ColumnFiltersState,
    DrawerSectionHeader,
    ColumnFilterBuilder,
    ResourceTable,
    type ResourceTableToolbar,
    type RowSelectionState,
    toFilterableColumns,
} from "@opencop/ui";
import { useBulkAction } from "~/features/admin/use-bulk-action.hook";
import { useSelectedRows } from "~/features/admin/use-selected-rows.hook";
import { useToast } from "~/features/notifications/use-toast.hook";
import { useUserTimezone } from "~/features/preferences/use-user-timezone.hook";
import { AssignPanel } from "./AssignPanel";
import { ChangeRolePanel } from "./ChangeRolePanel";
import type { ChangeRoleAlerts, ChangeRoleRow } from "./change-role.model";
import type {
    AnyColumns,
    DrawerRelationRoleOptions,
    DrawerRelationSectionLabels,
    DrawerRelationServerPaging,
} from "./drawer-relation.model";

export interface DrawerRelationSectionProps<
    T extends object,
    U extends object,
    R extends string = string,
> {
    labels: DrawerRelationSectionLabels;

    // ── Assigned-items list (default view) ──
    assigned: T[];
    assignedColumns: AnyColumns<T>;
    getAssignedRowId: (row: T) => string;
    isLoading?: boolean;
    isFetching?: boolean;
    /**
     * The assigned-list query failed. Without this an errored query renders as an empty table,
     * which reads as "this relation has no members" — indistinguishable from a real empty state.
     */
    assignedError?: boolean;
    onRefresh?: () => Promise<unknown>;
    /** Revoke a single assigned item by id. Called once per selected row. */
    onRevoke: (id: string) => Promise<unknown>;

    // ── Assign picker (toolbar "Assign" → picker panel) ──
    unassigned: U[];
    unassignedColumns: AnyColumns<U>;
    getUnassignedRowId: (row: U) => string;
    unassignedLoading?: boolean;
    /** Assign the chosen items (optionally with a role) in one action. */
    onAssign: (ids: string[], role?: R) => Promise<unknown>;
    /** When provided, a role must be picked before items can be added. */
    roleOptions?: DrawerRelationRoleOptions<R>;
    /** Optional info affordance (e.g. a role-description tooltip) shown next to the role label. */
    roleInfo?: ReactNode;
    /**
     * Notified when the section enters/leaves the full-panel change-role sub-flow,
     * so the host drawer can lock its chrome (nav/overview/actions) like edit mode.
     */
    onLockedChange?: (locked: boolean) => void;
    /**
     * Change the role of the selected assigned rows to `role`. When provided
     * alongside `roleOptions`, a bulk "Change role" action appears once rows are
     * selected. Called once with all selected ids.
     */
    onChangeRole?: (ids: string[], role: R) => Promise<unknown>;
    /**
     * Builds the rows shown in the change-role dialog's table for the given
     * selected ids (called once when the dialog opens). Required for the dialog
     * to list who is affected.
     */
    getChangeRoleRows?: (ids: string[]) => ChangeRoleRow[];
    /**
     * Computes the alerts for the currently-checked rows + chosen role — a
     * `warning` (soft) and/or `blocked` rows (hard; disables Apply). Re-run as the
     * user checks/unchecks rows or changes the role.
     */
    validateChangeRole?: (checkedIds: string[], role: R) => ChangeRoleAlerts;
    /**
     * When exactly one row is selected, the "Change role" action calls this with
     * that row's id instead of opening the bulk table dialog (e.g. to show a
     * simpler single-member dialog).
     */
    onChangeRoleSingle?: (id: string) => void;
    /**
     * Optional gate for the bulk "Change role" action. Return `false` for a
     * selection that has nothing changeable (e.g. only the layer's sole owner)
     * to disable the toolbar button.
     */
    canChangeRoleFor?: (ids: string[]) => boolean;
    /**
     * Optional gate for the bulk "Revoke access" action. Return `false` for a
     * selection that can't be revoked (e.g. it includes every owner — the layer
     * must keep one) to disable the toolbar button.
     */
    canRevokeFor?: (ids: string[]) => boolean;

    /**
     * Present → the assign picker is server-paged; absent → client mode (pool fully loaded and
     * filtered in-memory). See {@link DrawerRelationServerPaging}.
     */
    serverPaging?: DrawerRelationServerPaging;

    /** When true, the section is view-only: no Assign button, no row selection/Revoke. */
    readOnly?: boolean;
}

/**
 * Reusable relation manager for entity drawers. Shows the assigned items in a
 * searchable {@link ResourceTable} with multi-select bulk **Revoke**, and an
 * **Assign** toolbar action that swaps the panel to a picker of unassigned
 * items (multi-select, optional role) — replacing the old membership dialogs.
 * All data and mutations are injected; this component owns only the UI state.
 */
export function DrawerRelationSection<
    T extends object,
    U extends object,
    R extends string = string,
>({
    labels,
    assigned,
    assignedColumns,
    getAssignedRowId,
    isLoading,
    isFetching,
    assignedError,
    onRefresh,
    onRevoke,
    unassigned,
    unassignedColumns,
    getUnassignedRowId,
    unassignedLoading,
    onAssign,
    roleOptions,
    serverPaging,
    roleInfo,
    onLockedChange,
    onChangeRole,
    getChangeRoleRows,
    validateChangeRole,
    onChangeRoleSingle,
    canRevokeFor,
    canChangeRoleFor,
    readOnly = false,
}: DrawerRelationSectionProps<T, U, R>): ReactElement {
    const { t } = useTranslation();
    const { showToast } = useToast();

    const [mode, setMode] = useState<"list" | "assign" | "changeRole">("list");

    // Lock the host drawer's chrome while any full-panel sub-flow is open. Both non-list
    // modes take the panel over and unmount the toolbar holding the button that opened them,
    // so reporting only one of them left the picker dropping focus to the document body.
    // The cleanup releases the lock if this unmounts mid-flow (e.g. the drawer closes),
    // so the parent is never left stranded in the locked state.
    useEffect(() => {
        onLockedChange?.(mode !== "list");
        return () => onLockedChange?.(false);
    }, [mode, onLockedChange]);

    // ── List mode: search + revoke selection ──
    // Read here rather than threaded through each section, so every relation table resolves date
    // filters in the same zone its cells render in.
    const timezone = useUserTimezone();
    const [listFilter, setListFilter] = useState("");
    const [listColumnFilters, setListColumnFilters] = useState<ColumnFiltersState>([]);
    // A section opts into per-column filtering purely by attaching `meta.filterDef` to its assigned
    // columns; with none declared there is no bar and the toolbar keeps its plain search box.
    const filterableColumns = useMemo(
        () => toFilterableColumns(assignedColumns),
        [assignedColumns],
    );
    const [rowSelection, setRowSelection] = useState<RowSelectionState>({});
    // Retains rows once seen, so narrowing the list never silently shrinks what Revoke will act
    // on — the toolbar count and the fan-out below describe the same set by construction.
    const { selectedRows, onRowSelectionChange } = useSelectedRows(
        assigned,
        getAssignedRowId,
        rowSelection,
        setRowSelection,
    );
    const selectedCount = selectedRows.length;
    const selectedIds = useMemo(
        () => selectedRows.map(getAssignedRowId),
        [selectedRows, getAssignedRowId],
    );
    const resetSelection = useCallback(() => setRowSelection({}), []);
    const { handleBulkAction: revokeSelected } = useBulkAction(
        onRevoke,
        selectedIds,
        resetSelection,
    );

    async function handleRevoke() {
        const { failures } = await revokeSelected();
        if (failures > 0) showToast({ type: "error", title: labels.revokeError });
    }

    // ── Bulk change-role mode ──
    const canChangeRole = onChangeRole != null && roleOptions != null;
    // The selected members shown in the change-role panel, snapshotted when it opens.
    const [changeRoleRows, setChangeRoleRows] = useState<ChangeRoleRow[]>([]);

    function enterChangeRole() {
        // A single selection uses the simpler single-member flow when available.
        if (selectedIds.length === 1 && onChangeRoleSingle) {
            onChangeRoleSingle(selectedIds[0]);
            return;
        }
        setChangeRoleRows(getChangeRoleRows?.(selectedIds) ?? []);
        setMode("changeRole");
    }

    function enterAssign() {
        // Reset the pool's own server search (it lives above AssignPanel, so it outlives the
        // picker), then clear the list's filter bar — both halves of it. The user is about to add
        // a row and has to be able to see it on return; an armed chip or a leftover typed term can
        // hide a just-assigned row, which reads as the assign having silently failed. Search and
        // chips are one control (ColumnFilterBuilder renders them in one row), so they clear together.
        serverPaging?.onSearchChange("");
        setListFilter("");
        setListColumnFilters([]);
        setMode("assign");
    }

    // The default list view: toolbar + assigned-items table. Extracted so the
    // exhaustive switch below can fall back to it for the (canChangeRole-guarded, so
    // impossible) "changeRole with no handler" case without duplicating it.
    function renderList(): ReactElement {
        const toolbar: ResourceTableToolbar = {
            onRefresh,
            isRefreshing: isFetching,
            actions: readOnly
                ? []
                : [
                      {
                          label: labels.assign,
                          icon: <FaPlus />,
                          showLabel: true,
                          onClick: enterAssign,
                          // Assign leaves the list for the picker, which carries its own
                          // selection — a live list selection would silently survive the round
                          // trip and re-arm the bulk actions on return. Clear it first.
                          disabled: selectedCount > 0,
                          disabledReason: t("adminDialogs.clearSelectionToAssign"),
                      },
                  ],
            selectionActions:
                !readOnly && selectedCount > 0
                    ? [
                          ...(canChangeRole
                              ? [
                                    {
                                        label: labels.changeRole,
                                        icon: <FaUserShield />,
                                        onClick: enterChangeRole,
                                        // Nothing to change (e.g. only the sole owner is selected).
                                        disabled:
                                            canChangeRoleFor != null &&
                                            !canChangeRoleFor(selectedIds),
                                    },
                                ]
                              : []),
                          {
                              label: labels.revoke,
                              icon: <FaTrash />,
                              colorPalette: "red",
                              onClick: handleRevoke,
                              // e.g. can't revoke the layer's last owner — it must keep one.
                              disabled: canRevokeFor != null && !canRevokeFor(selectedIds),
                              disabledReason: labels.revokeDisabledReason,
                              confirm: {
                                  title: labels.revokeConfirm.title,
                                  message: labels.revokeConfirm.message(selectedCount),
                                  confirmLabel: labels.revokeConfirm.confirmLabel,
                              },
                          },
                      ]
                    : [],
        };

        return (
            <Box flex="1" minH={0} display="flex" flexDirection="column">
                <DrawerSectionHeader title={labels.title} description={labels.description} />
                <ResourceTable
                    transparent
                    aria-label={labels.listAriaLabel}
                    data={assigned}
                    columns={assignedColumns}
                    isLoading={isLoading}
                    isFetching={isFetching && !isLoading}
                    isError={assignedError}
                    onRetry={onRefresh}
                    getRowId={getAssignedRowId}
                    enableRowSelection={!readOnly}
                    rowSelection={rowSelection}
                    onRowSelectionChange={onRowSelectionChange}
                    selectedCount={selectedCount}
                    globalFilter={listFilter}
                    onGlobalFilterChange={setListFilter}
                    timezone={timezone}
                    columnFilters={listColumnFilters}
                    onColumnFiltersChange={setListColumnFilters}
                    filterBar={
                        filterableColumns.length > 0 ? (
                            <ColumnFilterBuilder
                                filterableColumns={filterableColumns}
                                value={listColumnFilters}
                                onChange={setListColumnFilters}
                                searchValue={listFilter}
                                onSearchChange={setListFilter}
                                searchPlaceholder={t("resourceTable.filterForAnyField")}
                            />
                        ) : undefined
                    }
                    toolbar={toolbar}
                />
            </Box>
        );
    }

    switch (mode) {
        case "assign":
            return (
                <AssignPanel
                    unassigned={unassigned}
                    unassignedColumns={unassignedColumns}
                    getUnassignedRowId={getUnassignedRowId}
                    unassignedLoading={unassignedLoading}
                    onAssign={onAssign}
                    roleOptions={roleOptions}
                    serverPaging={serverPaging}
                    labels={labels}
                    onBack={() => setMode("list")}
                    onAdded={() => setMode("list")}
                />
            );
        case "changeRole":
            // onChangeRole is guaranteed by the toolbar's canChangeRole gate that
            // enters this mode; if it's ever absent, fall back to the list rather
            // than render a blank panel.
            return onChangeRole ? (
                <ChangeRolePanel
                    initialRows={changeRoleRows}
                    roleOptions={roleOptions}
                    roleInfo={roleInfo}
                    validateChangeRole={validateChangeRole}
                    onChangeRole={onChangeRole}
                    labels={labels}
                    onCancel={() => setMode("list")}
                    onApplied={() => {
                        setRowSelection({});
                        setMode("list");
                    }}
                />
            ) : (
                renderList()
            );
        case "list":
            return renderList();
        default:
            return assertNever(mode);
    }
}
