// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import {
    createFilterParamSerializer,
    type FilterFieldDef,
} from "~/features/admin/filter-params.util";

const toUpperCase = (value: unknown) => String(value).toUpperCase();

/**
 * Single source of truth for which Admin > Users columns are server-filterable. `AdminUsersView`
 * reads its column `operators` from here, so the builder cannot offer what the serializer would
 * drop. `contains` leads each string column, matching the search box's semantics.
 */
export const USER_DIRECTORY_FILTER_FIELDS = {
    name: {
        field: "name",
        operators: ["contains", "notContains", "startsWith", "equals", "notEquals"],
    },
    rowType: { field: "type", operators: ["equals"], toWire: toUpperCase },
    isAdmin: { field: "admin", operators: ["equals"] },
    active: { field: "active", operators: ["equals"] },
    firstName: {
        field: "firstName",
        operators: ["contains", "startsWith", "equals", "isEmpty", "isNotEmpty"],
    },
    lastName: {
        field: "lastName",
        operators: ["contains", "startsWith", "equals", "isEmpty", "isNotEmpty"],
    },
    email: {
        field: "email",
        operators: ["contains", "startsWith", "equals", "isEmpty", "isNotEmpty"],
    },
    createdAt: { field: "createdAt", operators: ["before", "after", "between"] },
} satisfies Record<string, FilterFieldDef>;

const serializer = createFilterParamSerializer(
    USER_DIRECTORY_FILTER_FIELDS,
    "user-directory-filters",
);

/**
 * The backend attribute a grid column sorts by. The grid names some columns after its own row model
 * (`rowType`, `isAdmin`) rather than the API (`type`, `admin`), so translating here keeps a
 * legitimate sort from ever reaching the server as an unknown field, which it answers with
 * `400 INVALID_SORT`. Falls back to the id, correct wherever the names already match.
 */
export const toUserDirectorySortField = serializer.toSortField;

/**
 * Turns the grid's filter chips into repeated `filter` query params for
 * `GET /admin/user-directory`. Order follows the chips so the React Query key stays stable across
 * renders.
 */
export const toUserDirectoryFilterParams = serializer.toFilterParams;
