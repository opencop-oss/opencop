// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ColumnDef, RoleColumnOption } from "@opencop/ui";

// A selectable role in the assign picker / change-role flows. Aliased to @opencop/ui's
// `RoleColumnOption` (identical `{ label; value }` shape) so the two can't drift — these
// options flow straight into `clickableRoleColumn`.
export type { RoleColumnOption as DrawerRelationRoleOption } from "@opencop/ui";

/**
 * The roles a panel may offer, either a plain array (the common case) or a resolver for relations
 * whose legal roles depend on the rows in hand: a layer group can only grant a layer role the actor
 * already holds on *every* selected layer, so the ceiling is the weakest of them and is only known
 * once a selection exists.
 */
export type DrawerRelationRoleOptions<R extends string = string> =
    | RoleColumnOption<R>[]
    | ((selectedIds: string[]) => RoleColumnOption<R>[]);

export interface DrawerRelationSectionLabels {
    /** Panel heading for the assigned-items list, e.g. "Layer Members". */
    title: string;
    /** One line stating what the list holds, in the user's terms. */
    description: string;
    /** Panel heading for the assign sub-flow, e.g. "Assign Members to Layer". */
    assignTitle: string;
    /** One line stating what the assign sub-flow does. */
    assignDescription: string;
    /** Panel heading for the change-role sub-flow, e.g. "Change Role in Layer". */
    changeRoleTitle: string;
    /** One line stating what the change-role sub-flow does. */
    changeRoleDescription: string;
    /** Toolbar button that enters assign mode, e.g. "Assign Layers". */
    assign: string;
    /** Bulk revoke action label, e.g. "Revoke access". */
    revoke: string;
    /** Confirmation shown before revoking the selected rows. */
    revokeConfirm: {
        title: string;
        message: (count: number) => string;
        confirmLabel?: string;
    };
    /** Add button in assign mode, e.g. "Add selected" (the count is appended). */
    add: string;
    /** Back control in a sub-flow, naming its destination, e.g. "Back to Members". */
    back: string;
    /** Bulk change-role action label, e.g. "Change role". */
    changeRole: string;
    /** Error toast title shown when a role change fails. */
    changeRoleError: string;
    /** Placeholder for the role select (only used when `roleOptions` is provided). */
    rolePlaceholder?: string;
    /** Error toast title shown when a revoke fails. */
    revokeError: string;
    /** Optional tooltip shown when the Revoke action is disabled (explains why). */
    revokeDisabledReason?: string;
    /** Error toast title shown when an assign fails. */
    assignError: string;
    /** Accessible label for the assigned-items table. */
    listAriaLabel: string;
    /** Accessible label for the unassigned-items picker. */
    assignAriaLabel: string;
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any -- mirrors ResourceTable's ColumnDef value-type workaround
export type AnyColumns<T> = ColumnDef<T, any>[];

/**
 * The server-paging contract for the assign picker. When supplied, the picker treats its pool as
 * a bounded, search-driven, paginated list: it pushes the search term to the server, fetches the
 * next page on scroll, and surfaces load errors with a retry. When omitted, the picker runs in
 * client mode (pool fully loaded, filtered/sorted in-memory). Bundled so the type enforces that
 * these arrive together rather than as six independently-optional props.
 */
export interface DrawerRelationServerPaging {
    /** Push the (debounced) search term to the server for server-side narrowing. */
    onSearchChange: (query: string) => void;
    /** Fetch the next page as the picker nears the bottom. */
    onLoadMore: () => void;
    hasMore: boolean;
    isFetchingMore: boolean;
    isError: boolean;
    onRetry: () => void;
}
