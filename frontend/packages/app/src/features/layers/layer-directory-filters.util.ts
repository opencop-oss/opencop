// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import {
    createFilterParamSerializer,
    type FilterFieldDef,
} from "~/features/admin/filter-params.util";

/**
 * Single source of truth for which Admin > Layers columns are server-filterable. `AdminLayersView`
 * reads its column `operators` from here, so the builder cannot offer what the serializer would
 * drop. `contains` leads the name column, matching the search box's semantics.
 *
 * Diverges from the user directory's map in three places, all enforced by the backend allowlist
 * (`LayerDirectoryFilterField`):
 *
 * - `name` has no `isEmpty`/`isNotEmpty` — both underlying name columns are NOT NULL.
 * - there is no `archived` entry — the grid narrows on it through the Manage-view
 *   `recordVisibility` toggle, not a chip.
 * - `rowType` has no `toWire`. The row model spells the value "layer-group", which upper-cases to
 *   the "LAYER-GROUP" the backend rejects, so the column's `filterDef.options` carry the wire
 *   tokens instead. Safe because the column is server-filtered and declares no `filterFn`, so an
 *   option value never has to equal the rendered cell.
 */
export const LAYER_DIRECTORY_FILTER_FIELDS = {
    name: {
        field: "name",
        operators: ["contains", "notContains", "startsWith", "equals", "notEquals"],
    },
    rowType: { field: "type", operators: ["equals", "notEquals"] },
    // The row value is already the wire token (`AccessScope` = "PRIVATE" | "GLOBAL").
    accessScope: { field: "accessScope", operators: ["equals", "notEquals"] },
    createdAt: { field: "createdAt", operators: ["before", "after", "between"] },
} satisfies Record<string, FilterFieldDef>;

const serializer = createFilterParamSerializer(
    LAYER_DIRECTORY_FILTER_FIELDS,
    "layer-directory-filters",
);

/**
 * The backend attribute a grid column sorts by. The grid calls the entry kind `rowType` while the
 * API calls it `type`, so translating here keeps a legitimate sort from reaching the server as an
 * unknown field, which it answers with `400 INVALID_SORT`. Falls back to the id, correct wherever
 * the names already match.
 */
export const toLayerDirectorySortField = serializer.toSortField;

/**
 * Turns the grid's filter chips into repeated `filter` query params for
 * `GET /admin/layer-directory`. Order follows the chips so the React Query key stays stable across
 * renders.
 */
export const toLayerDirectoryFilterParams = serializer.toFilterParams;
