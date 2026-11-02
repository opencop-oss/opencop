// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Flex, Text } from "@chakra-ui/react";
import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useDebouncedValue } from "@tanstack/react-pacer";
import {
    ColumnFilterBuilder,
    ResourceTable,
    TypeColorLabel,
    createColumnHelper,
    toFilterableColumns,
} from "@opencop/ui";
import type { ResourceTableToolbar } from "@opencop/ui";
import type { SortingState } from "@tanstack/react-table";
import { MAX_FILTER_CRITERIA, resolveInfiniteRetry, type SortParam } from "@opencop/api";
import { useCallback, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { formatDateOnly } from "~/date.util";
import { useUserTimezone } from "~/features/preferences/use-user-timezone.hook";
import { useResourceTableState } from "~/features/admin/use-resource-table-state.hook";
import { useSelectedRows } from "~/features/admin/use-selected-rows.hook";
import { aggregateQueries } from "~/features/admin/aggregate-queries.util";
import { userDirectoryQueries } from "~/features/user-directory/user-directory.queries";
import {
    USER_DIRECTORY_FILTER_FIELDS,
    toUserDirectoryFilterParams,
    toUserDirectorySortField,
} from "~/features/user-directory/user-directory-filters.util";
import { ViewDrawerProvider, useViewDrawerContext } from "~/features/drawers/ViewDrawerContext";
import { FaPlus, FaTrash, FaUsers } from "react-icons/fa";
import { userQueries } from "~/features/users/users.queries";
import { groupMutations, groupQueries } from "~/features/groups/groups.queries";
import type { Group } from "~/features/groups/groups.queries";
import { UserDrawer } from "~/features/users/UserDrawer";
import type { UserDrawerState } from "~/features/users/UserDrawer";
import { AdminGroupDrawer } from "~/features/groups/AdminGroupDrawer";
import type {
    AdminGroupDrawerState,
    AdminGroupModalState,
} from "~/features/groups/AdminGroupDrawer";
import { AddGroupDialog } from "~/features/groups/dialogs/AddGroupDialog";
import { AddUsersToGroupDialog } from "~/features/groups/dialogs/AddUsersToGroupDialog";
import { ConfirmDeleteDialog } from "~/features/membership-management/ConfirmDeleteDialog";
import { useToast } from "~/features/notifications/use-toast.hook";

interface AdminCombinedRow {
    id: string;
    rowType: "user" | "group";
    name: string;
    isAdmin?: boolean;
    firstName?: string;
    lastName?: string;
    email?: string;
    active: boolean;
    createdAt?: string;
}

type AdminCombinedModalState =
    | { type: "none" }
    | AdminGroupModalState
    | { type: "addToGroup"; users: { id: string; name: string }[] };

const columnHelper = createColumnHelper<AdminCombinedRow>();

/** Module-level so the selection hook keeps a stable identity across renders. */
const getAdminUserRowId = (row: AdminCombinedRow) => row.id;

export function AdminUsersView() {
    return (
        <ViewDrawerProvider<AdminCombinedModalState> initialModal={{ type: "none" }}>
            <AdminUsersViewContent />
        </ViewDrawerProvider>
    );
}

function AdminUsersViewContent() {
    const { t } = useTranslation();
    const { showToast } = useToast();
    const queryClient = useQueryClient();
    const timezone = useUserTimezone();
    const { selectedId, selectedType, openDrawer, closeDrawer, modal, setModal } =
        useViewDrawerContext<AdminCombinedModalState>();

    const tableState = useResourceTableState();

    // Only the search text is debounced; the chips commit on an explicit Apply or close, so each
    // change is already one discrete event.
    const [debouncedSearch] = useDebouncedValue(tableState.globalFilter, { wait: 300 });
    const [sorting, setSorting] = useState<SortingState>([]);
    const sort = useMemo(
        () =>
            sorting.map(
                (s): SortParam => `${toUserDirectorySortField(s.id)},${s.desc ? "desc" : "asc"}`,
            ),
        [sorting],
    );
    const filter = useMemo(
        () => toUserDirectoryFilterParams(tableState.columnFilters),
        [tableState.columnFilters],
    );

    // Serialized rather than delimiter-joined: a filter value may contain the separator, and two
    // different chip sets collapsing to one key would silently skip the scroll reset.
    const resetScrollKey = useMemo(
        () => JSON.stringify([debouncedSearch, sort, filter]),
        [debouncedSearch, sort, filter],
    );

    const {
        data,
        isLoading: userDirectoryLoading,
        isFetching: userDirectoryFetching,
        fetchNextPage,
        hasNextPage,
        isFetchingNextPage,
        isError,
        refetch: refetchUserDirectory,
    } = useInfiniteQuery(
        userDirectoryQueries.infiniteList({
            search: debouncedSearch || undefined,
            sort: sort.length ? sort : undefined,
            filter: filter.length ? filter : undefined,
            // Sent whenever a filter is applied so Created ranges mean the viewer's day, matching
            // the timezone the Created column renders in.
            timezone: filter.length ? timezone : undefined,
        }),
    );

    const {
        data: groups = [],
        isLoading: groupsLoading,
        isFetching: groupsFetching,
        refetch: refetchGroups,
    } = useQuery(groupQueries.adminList());

    const { isLoading, isFetching, refetchAll } = aggregateQueries([
        {
            isLoading: userDirectoryLoading,
            isFetching: userDirectoryFetching,
            refetch: refetchUserDirectory,
        },
        { isLoading: groupsLoading, isFetching: groupsFetching, refetch: refetchGroups },
    ]);

    const { mutateAsync: deleteGroup } = useMutation(groupMutations.delete(queryClient));

    const rows: AdminCombinedRow[] = useMemo(
        () =>
            (data?.pages ?? []).flatMap((page) =>
                page.content.map((entry) =>
                    entry.type === "USER"
                        ? {
                              id: entry.id,
                              rowType: "user" as const,
                              name: entry.name,
                              isAdmin: entry.admin ?? undefined,
                              firstName: entry.firstName ?? undefined,
                              lastName: entry.lastName ?? undefined,
                              email: entry.email ?? undefined,
                              active: entry.active,
                              createdAt: entry.createdAt,
                          }
                        : {
                              id: entry.id,
                              rowType: "group" as const,
                              name: entry.name,
                              active: entry.active,
                              createdAt: entry.createdAt,
                          },
                ),
            ),
        [data],
    );
    const totalCount = data?.pages[0]?.totalElements;

    const closeModal = () => setModal({ type: "none" });

    const { data: selectedUser } = useQuery({
        ...userQueries.byId(selectedId ?? ""),
        enabled: !!selectedId && selectedType === "user",
    });

    const userDrawerState: UserDrawerState =
        selectedType === "user" && selectedUser
            ? { type: "user", entity: selectedUser }
            : { type: "closed" };

    const groupDrawerState: AdminGroupDrawerState = useMemo(() => {
        if (!selectedId || selectedType !== "group") return { type: "closed" };
        const group = groups.find((g) => g.id === selectedId);
        return group ? { type: "group", entity: group } : { type: "closed" };
    }, [selectedId, selectedType, groups]);

    const handleRowClick = (row: AdminCombinedRow) => openDrawer(row.id, row.rowType);

    const handleDeleteGroup = async (group: Group) => {
        try {
            await deleteGroup(group.id);
            closeModal();
            closeDrawer();
        } catch {
            showToast({ type: "error", title: t("adminGroups.failedToDelete") });
        }
    };

    // Retains rows once seen: the directory query restarts at page 0 on every search or sort
    // change, so without this a selection made before the change would resolve to nothing and the
    // toolbar would report rows the actions could no longer reach.
    const { selectedRows, onRowSelectionChange } = useSelectedRows(
        rows,
        getAdminUserRowId,
        tableState.rowSelection,
        tableState.onRowSelectionChange,
    );

    const allSelectedAreUsers =
        selectedRows.length > 0 && selectedRows.every((r) => r.rowType === "user");
    const allSelectedAreGroups =
        selectedRows.length > 0 && selectedRows.every((r) => r.rowType === "group");

    const handleBulkDeleteGroups = useCallback(async () => {
        await Promise.allSettled(selectedRows.map((r) => deleteGroup(r.id)));
        tableState.resetSelection();
    }, [selectedRows, deleteGroup, tableState]);

    const columns = useMemo(
        () => [
            columnHelper.accessor("name", {
                header: () => t("adminUsers.username"),
                cell: (info) => {
                    const isUser = info.row.original.rowType === "user";
                    return (
                        <Flex align="center" gap={2}>
                            <TypeColorLabel
                                abbr={isUser ? "U" : "G"}
                                color={isUser ? "amber" : "cyan"}
                                label={isUser ? t("adminUsers.user") : t("adminUsers.group")}
                            />
                            <Text>{info.getValue() || "-"}</Text>
                        </Flex>
                    );
                },
                enableSorting: true,
                meta: {
                    filterDef: {
                        label: t("adminUsers.username"),
                        type: "string" as const,
                        operators: USER_DIRECTORY_FILTER_FIELDS.name.operators,
                    },
                },
            }),
            columnHelper.accessor("rowType", {
                header: () => t("resourceTable.columnType"),
                cell: (info) => (
                    <Text fontSize="sm" color="fg.muted">
                        {info.getValue() === "user" ? t("adminUsers.user") : t("adminUsers.group")}
                    </Text>
                ),
                // Sorts by the backend's `type`, not this column's id — see toUserDirectorySortField.
                enableSorting: true,
                size: 110,
                meta: {
                    filterDef: {
                        label: t("resourceTable.columnType"),
                        type: "enum" as const,
                        options: [
                            { label: t("adminUsers.user"), value: "user" },
                            { label: t("adminUsers.group"), value: "group" },
                        ],
                        operators: USER_DIRECTORY_FILTER_FIELDS.rowType.operators,
                    },
                },
            }),
            columnHelper.accessor("isAdmin", {
                header: () => t("adminUsers.role"),
                cell: (info) => {
                    if (info.row.original.rowType !== "user") return "-";
                    return (
                        <Text fontSize="sm" color="fg.muted">
                            {info.getValue() ? t("adminUsers.admin") : t("adminUsers.general")}
                        </Text>
                    );
                },
                enableSorting: false,
                size: 110,
                // Enum rather than boolean so the chip reads "Role: Equals Admin", not "Role: Is
                // True"; the backend takes `admin,equals,true` as an alias for `isTrue`.
                meta: {
                    filterDef: {
                        label: t("adminUsers.role"),
                        type: "enum" as const,
                        options: [
                            { label: t("adminUsers.admin"), value: "true" },
                            { label: t("adminUsers.general"), value: "false" },
                        ],
                        operators: USER_DIRECTORY_FILTER_FIELDS.isAdmin.operators,
                    },
                },
            }),
            columnHelper.accessor("firstName", {
                header: () => t("adminUsers.first"),
                cell: (info) => info.getValue() || "-",
                enableSorting: true,
                meta: {
                    filterDef: {
                        label: t("adminUsers.first"),
                        type: "string" as const,
                        operators: USER_DIRECTORY_FILTER_FIELDS.firstName.operators,
                    },
                },
            }),
            columnHelper.accessor("lastName", {
                header: () => t("adminUsers.last"),
                cell: (info) => info.getValue() || "-",
                enableSorting: true,
                meta: {
                    filterDef: {
                        label: t("adminUsers.last"),
                        type: "string" as const,
                        operators: USER_DIRECTORY_FILTER_FIELDS.lastName.operators,
                    },
                },
            }),
            columnHelper.accessor("email", {
                header: () => t("adminUsers.email"),
                cell: (info) => info.getValue() || "-",
                enableSorting: true,
                meta: {
                    filterDef: {
                        label: t("adminUsers.email"),
                        type: "string" as const,
                        operators: USER_DIRECTORY_FILTER_FIELDS.email.operators,
                    },
                },
            }),
            // Groups are stored active and have no activate/deactivate control, so `Status =
            // Active` returns them while their cell reads "-", and `Inactive` excludes them.
            // Decided to keep that rather than exclude groups from both directions: deactivation is
            // a user-account concept, and a group hidden from an Active filter would look deleted.
            // The "-" reflects that the control does not exist, not that the value is unknown.
            columnHelper.accessor("active", {
                header: () => t("adminUsers.status"),
                cell: (info) => {
                    if (info.row.original.rowType !== "user") return "-";
                    return (
                        <Text fontSize="sm" color="fg.muted">
                            {info.getValue() ? t("adminUsers.active") : t("adminUsers.inactive")}
                        </Text>
                    );
                },
                enableSorting: false,
                size: 110,
                meta: {
                    filterDef: {
                        label: t("adminUsers.status"),
                        type: "enum" as const,
                        options: [
                            { label: t("adminUsers.active"), value: "true" },
                            { label: t("adminUsers.inactive"), value: "false" },
                        ],
                        operators: USER_DIRECTORY_FILTER_FIELDS.active.operators,
                    },
                },
            }),
            columnHelper.accessor("createdAt", {
                header: () => t("adminUsers.createdAt"),
                cell: (info) => {
                    const value = info.getValue();
                    return value ? formatDateOnly(value, timezone) : "-";
                },
                enableSorting: true,
                size: 130,
                meta: {
                    filterDef: {
                        label: t("adminUsers.createdAt"),
                        type: "date" as const,
                        operators: USER_DIRECTORY_FILTER_FIELDS.createdAt.operators,
                    },
                },
            }),
        ],
        [t, timezone],
    );

    // No column declares a `filterFn`: manualFiltering means TanStack never runs one, and omitting
    // them rules out client-side double-filtering over the loaded pages.
    const filterableColumns = useMemo(() => toFilterableColumns(columns), [columns]);

    const filterBarEl = (
        <ColumnFilterBuilder
            filterableColumns={filterableColumns}
            value={tableState.columnFilters}
            onChange={tableState.onColumnFiltersChange}
            maxFilters={MAX_FILTER_CRITERIA}
            searchValue={tableState.globalFilter}
            onSearchChange={tableState.onGlobalFilterChange}
            searchPlaceholder={t("adminUsers.searchHint")}
        />
    );

    const toolbar: ResourceTableToolbar = {
        onRefresh: refetchAll,
        isRefreshing: isFetching,
        actions: [
            {
                label: t("common.create"),
                icon: <FaPlus />,
                showLabel: true,
                menuItems: [
                    {
                        label: t("adminGroups.newGroup"),
                        onClick: () => setModal({ type: "addGroup" }),
                    },
                ],
            },
        ],
        selectionActions: [
            {
                label: t("adminUsers.addToGroup"),
                icon: <FaUsers />,
                disabled: !allSelectedAreUsers,
                onClick: () =>
                    setModal({
                        type: "addToGroup",
                        users: selectedRows.map((r) => ({ id: r.id, name: r.name })),
                    }),
            },
            {
                label: t("common.delete"),
                icon: <FaTrash />,
                disabled: !allSelectedAreGroups,
                disabledReason: t("adminUsers.usersNotDeletable", {
                    appTitle: import.meta.env.VITE_APP_TITLE || "OpenCOP",
                }),
                confirm: {
                    title: t("resourceTable.confirmDeleteTitle"),
                    message: t("resourceTable.confirmDeleteMessage", {
                        count: selectedRows.length,
                    }),
                },
                onClick: handleBulkDeleteGroups,
            },
        ],
    };

    return (
        <Flex height="100%" width="100%" direction="column">
            <ResourceTable
                aria-label={t("adminNav.users")}
                data={rows}
                columns={columns}
                isLoading={isLoading}
                isFetching={isFetching && !isFetchingNextPage}
                getRowId={getAdminUserRowId}
                onRowClick={handleRowClick}
                enableRowSelection
                globalFilter={tableState.globalFilter}
                onGlobalFilterChange={tableState.onGlobalFilterChange}
                columnFilters={tableState.columnFilters}
                onColumnFiltersChange={tableState.onColumnFiltersChange}
                filterBar={filterBarEl}
                rowSelection={tableState.rowSelection}
                onRowSelectionChange={onRowSelectionChange}
                selectedCount={selectedRows.length}
                manualSorting
                manualFiltering
                sorting={sorting}
                onSortingChange={setSorting}
                onLoadMore={fetchNextPage}
                hasNextPage={hasNextPage}
                isFetchingNextPage={isFetchingNextPage}
                isError={isError}
                onRetry={() =>
                    resolveInfiniteRetry(rows.length > 0, fetchNextPage, refetchUserDirectory)
                }
                totalCount={totalCount}
                resetScrollKey={resetScrollKey}
                emptyMessage={
                    debouncedSearch || filter.length ? t("resourceTable.noMatches") : undefined
                }
                toolbar={toolbar}
                detailDrawer={
                    <>
                        <UserDrawer drawerState={userDrawerState} />
                        <AdminGroupDrawer drawerState={groupDrawerState} />
                    </>
                }
            />

            {modal.type === "addGroup" && <AddGroupDialog isOpen onClose={closeModal} />}

            {modal.type === "addToGroup" && (
                <AddUsersToGroupDialog isOpen onClose={closeModal} users={modal.users} />
            )}

            {modal.type === "deleteGroup" && (
                <ConfirmDeleteDialog
                    isOpen
                    onClose={closeModal}
                    userName={modal.group.name}
                    onDelete={() => handleDeleteGroup(modal.group)}
                />
            )}
        </Flex>
    );
}
