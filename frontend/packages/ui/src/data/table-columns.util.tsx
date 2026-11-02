// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, Button, HStack } from "@chakra-ui/react";
import type { ReactNode } from "react";
import { FaPencilAlt } from "react-icons/fa";
import { type ColumnDef, createColumnHelper } from "@tanstack/react-table";
import type { ColumnFilterDef } from "./column-filter.model.ts";
import { ResourceTypeBadge } from "./ResourceTypeBadge";

// eslint-disable-next-line @typescript-eslint/no-explicit-any -- mirrors ResourceTable's ColumnDef value-type workaround
type AnyColumn<T> = ColumnDef<T, any>;

/** A selectable role for {@link clickableRoleColumn}, mapping a stored value to its display label. */
export interface RoleColumnOption<R extends string = string> {
    label: string;
    value: R;
}

/**
 * Reusable {@link ResourceTable} column helpers (user / name / role, plus the
 * clickable role cell) shared across the entity-drawer relation sections. Each
 * returns a single {@link ColumnDef} so callers compose the exact set they need,
 * e.g. `[usernameColumn(t("adminUsers.user")), roleColumn(t("adminUsers.role"))]`.
 *
 * Callers pass the display `header` (and any other labels) — these helpers hold no
 * i18n coupling, so they stay leaf-safe in `@opencop/ui`.
 */

// Accessor functions (not string keys) so the helpers stay generic — TanStack's
// `DeepKeys` string-key typing doesn't resolve against a generic constraint.

/** Username column — assigned/unassigned user tables. */
export function usernameColumn<T extends { username: string }>(header: string): AnyColumn<T> {
    return createColumnHelper<T>().accessor((row) => row.username, {
        id: "username",
        header,
        enableSorting: true,
        meta: { filterDef: { label: header, type: "string" } },
        filterFn: "ocStringFilter",
    });
}

/** "Name" column — layer / group / data-source pickers. */
export function nameColumn<T extends { name: string }>(header: string): AnyColumn<T> {
    return createColumnHelper<T>().accessor((row) => row.name, {
        id: "name",
        header,
        enableSorting: true,
        meta: { filterDef: { label: header, type: "string" } },
        filterFn: "ocStringFilter",
    });
}

/** How a user group's lifecycle is managed, mirroring the server's `GroupManagementSource`. */
export type GroupSource = "OPENCOP" | "SCIM";

/**
 * "Name" column for user groups, marking the ones the identity provider manages.
 *
 * A local group and a SCIM group may legally share a name, so a name alone cannot identify which
 * population a grant would reach. Only the SCIM side is badged: the pair is always local-vs-provisioned,
 * so one badge separates them, and badging every row would be noise on the common case.
 *
 * Sorting and filtering still run on the name alone, so the badge cannot skew either.
 */
export function groupNameColumn<T extends { name: string; source?: GroupSource }>(
    header: string,
    scimLabel: string,
): AnyColumn<T> {
    return createColumnHelper<T>().accessor((row) => row.name, {
        id: "name",
        header,
        enableSorting: true,
        meta: { filterDef: { label: header, type: "string" } },
        filterFn: "ocStringFilter",
        cell: (info) => (
            <HStack gap="2">
                <Box>{info.getValue()}</Box>
                {info.row.original.source === "SCIM" && (
                    <ResourceTypeBadge label={scimLabel} colorPalette="purple" />
                )}
            </HStack>
        ),
    });
}

/**
 * "Role" column — assigned tables that carry a (non-editable) role.
 *
 * Pass `options` to make the column filterable: an enum filter needs the vocabulary to render
 * readable chips, so without it the column is sortable but not filterable.
 */
export function roleColumn<T extends { role: string }, R extends string = string>(
    header: string,
    options?: RoleColumnOption<R>[],
): AnyColumn<T> {
    return createColumnHelper<T>().accessor((row) => row.role, {
        id: "role",
        header,
        enableSorting: true,
        ...roleFilterProps(header, options),
    });
}

/**
 * The `meta.filterDef` + `filterFn` pair a role column needs, shared by the plain and clickable
 * variants. Returns nothing without a vocabulary, which leaves the column out of
 * {@link toFilterableColumns} rather than offering a filter with no options to pick from.
 */
function roleFilterProps<R extends string>(
    label: string,
    options: RoleColumnOption<R>[] | undefined,
) {
    if (!options) return {};
    return {
        meta: {
            filterDef: {
                label,
                type: "enum" as const,
                options: options.map((o) => ({ label: o.label, value: o.value })),
            } satisfies ColumnFilterDef,
        },
        filterFn: "ocEnumFilter" as const,
    };
}

interface ClickableRoleColumnOptions<T, R extends string = string> {
    /** Column header text. */
    header: string;
    options: RoleColumnOption<R>[];
    /**
     * Full role vocabulary for the filter chip, when it differs from the assignable `options` —
     * you should be able to filter for a role you can see but not grant. Defaults to `options`.
     */
    filterOptions?: RoleColumnOption<R>[];
    getRowId: (row: T) => string;
    onClick: (id: string) => void;
    isRowLocked?: (row: T) => boolean;
    /**
     * Builds the edit button's aria-label for a row, e.g.
     * `(row) => t("adminDialogs.changeRoleFor", { name: row.username })`. Required so every
     * clickable role cell has a row-distinguishing accessible name — without it the name would
     * fall back to the visible role text, making same-role rows indistinguishable to AT.
     * Domain-neutral by design: the caller owns the wording.
     */
    getEditAriaLabel: (row: T) => string;
    headerInfo?: ReactNode;
}

/**
 * Editable "Role" column: renders the role as a per-row button that fires
 * `onClick(rowId)` (opening a role editor), unless `isRowLocked` marks the row —
 * then it renders as plain, non-interactive text.
 */
export function clickableRoleColumn<T extends { role: string }, R extends string = string>({
    header,
    options,
    filterOptions,
    getRowId,
    onClick,
    isRowLocked,
    getEditAriaLabel,
    headerInfo,
}: ClickableRoleColumnOptions<T, R>): AnyColumn<T> {
    const labelFor = (role: string) => options.find((o) => o.value === role)?.label ?? role;
    return createColumnHelper<T>().accessor((row) => row.role, {
        id: "role",
        header: headerInfo
            ? () => (
                  <HStack gap={1}>
                      {header}
                      {headerInfo}
                  </HStack>
              )
            : header,
        enableSorting: true,
        ...roleFilterProps(header, filterOptions ?? options),
        cell: (info) => {
            const row = info.row.original;
            const label = labelFor(row.role);
            if (isRowLocked?.(row))
                return (
                    <Button
                        as="span"
                        variant="plain"
                        size="sm"
                        height="auto"
                        px={0}
                        fontSize="sm"
                        fontWeight="normal"
                        cursor="default"
                    >
                        {label}
                    </Button>
                );
            return (
                <Button
                    variant="plain"
                    size="sm"
                    height="auto"
                    px={0}
                    fontSize="sm"
                    fontWeight="normal"
                    textDecoration="underline"
                    textDecorationStyle="dotted"
                    display="inline-flex"
                    alignItems="center"
                    gap={1}
                    aria-label={getEditAriaLabel(row)}
                    css={{
                        "& .role-edit-pencil": { opacity: 0, transition: "opacity 0.15s ease" },
                        "&:hover .role-edit-pencil, &:focus-visible .role-edit-pencil": {
                            opacity: 1,
                        },
                    }}
                    onClick={(e) => {
                        e.stopPropagation();
                        onClick(getRowId(row));
                    }}
                >
                    {label}
                    <Box
                        as="span"
                        className="role-edit-pencil"
                        display="inline-flex"
                        color="fg.muted"
                    >
                        <FaPencilAlt size={10} />
                    </Box>
                </Button>
            );
        },
    });
}
