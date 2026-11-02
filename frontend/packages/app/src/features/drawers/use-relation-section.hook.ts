// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useMemo } from "react";
import { useQuery, type UseQueryOptions } from "@tanstack/react-query";
import type { DrawerRelationSectionProps } from "./DrawerRelationSection";

/**
 * The section-specific bits a relation section supplies. The hook runs the
 * `assignedQuery` and computes the `unassigned` picker list; the pool is passed
 * already-resolved so each section keeps ownership of how it fetches/derives it
 * (single query, dual admin/my query, extra scope filter, …).
 */
type UseRelationSectionConfig<T extends object, U extends object, R extends string = string> = Pick<
    DrawerRelationSectionProps<T, U, R>,
    | "labels"
    | "assignedColumns"
    | "getAssignedRowId"
    | "onRevoke"
    | "unassignedColumns"
    | "getUnassignedRowId"
    | "onAssign"
    | "roleOptions"
    | "serverPaging"
    | "roleInfo"
    | "onChangeRole"
    | "getChangeRoleRows"
    | "validateChangeRole"
    | "onChangeRoleSingle"
    | "canChangeRoleFor"
    | "canRevokeFor"
> & {
    /**
     * Query for the currently-assigned items (the hook runs it). Typed with an
     * `any` key so a `queryOptions(...)` factory's specific key tuple assigns —
     * the key is contravariant in `staleTime`, so a `readonly unknown[]` upper
     * bound would reject it.
     */
    // eslint-disable-next-line @typescript-eslint/no-explicit-any -- see note above
    assignedQuery: UseQueryOptions<T[], Error, T[], any>;
    /** The assignable pool, already resolved by the section. */
    pool: U[];
    /** Whether the pool query is still loading. */
    poolLoading: boolean;
};

/**
 * Removes the query/filter/assembly boilerplate shared by every relation
 * section: runs the assigned query, derives the unassigned picker list, and
 * returns the {@link DrawerRelationSection} props (minus `readOnly`, which the
 * section threads through for its own gating). See any `*Section.tsx` for usage.
 */
export function useRelationSection<T extends object, U extends object, R extends string = string>(
    cfg: UseRelationSectionConfig<T, U, R>,
): Omit<DrawerRelationSectionProps<T, U, R>, "readOnly"> {
    const {
        data: assigned = [],
        isLoading,
        isFetching,
        isError: assignedError,
        refetch,
    } = useQuery(cfg.assignedQuery);
    const { pool, getAssignedRowId, getUnassignedRowId } = cfg;

    const unassigned = useMemo(
        () =>
            pool.filter(
                (p) => !assigned.some((a) => getAssignedRowId(a) === getUnassignedRowId(p)),
            ),
        [pool, assigned, getAssignedRowId, getUnassignedRowId],
    );

    return {
        labels: cfg.labels,
        assigned,
        assignedColumns: cfg.assignedColumns,
        getAssignedRowId: cfg.getAssignedRowId,
        isLoading,
        isFetching,
        assignedError,
        onRefresh: refetch,
        onRevoke: cfg.onRevoke,
        unassigned,
        unassignedColumns: cfg.unassignedColumns,
        getUnassignedRowId: cfg.getUnassignedRowId,
        unassignedLoading: cfg.poolLoading,
        onAssign: cfg.onAssign,
        roleOptions: cfg.roleOptions,
        serverPaging: cfg.serverPaging,
        roleInfo: cfg.roleInfo,
        onChangeRole: cfg.onChangeRole,
        getChangeRoleRows: cfg.getChangeRoleRows,
        validateChangeRole: cfg.validateChangeRole,
        onChangeRoleSingle: cfg.onChangeRoleSingle,
        canChangeRoleFor: cfg.canChangeRoleFor,
        canRevokeFor: cfg.canRevokeFor,
    };
}
