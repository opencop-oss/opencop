// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { MapUrlEncoder, MapUrlDecoder } from "../../map-url-codec.util";

export const SELECTION_URL_KEYS = ["selected"] as const;
export type SelectionUrlKey = (typeof SELECTION_URL_KEYS)[number];

export interface PersistedSelectionUrlState {
    selected: string | null;
}

/**
 * Selection encoder.
 *
 * Selected handling:
 *   - omit `selected` from the input → key not in result (URL unchanged)
 *   - `selected: null` or `""`        → key in result with value `null` (delete URL param)
 *   - `selected: "ns:id"`              → key in result with the string
 *
 * The omit-vs-null distinction matters because the storage adapter applies
 * this output as a diff against the existing URL params: undefined keys are
 * skipped, null deletes, string sets.
 */
export const encodeSelectionUrl: MapUrlEncoder<PersistedSelectionUrlState> = (state) => {
    const out: Partial<Record<SelectionUrlKey, string | null>> = {};
    if ("selected" in state) {
        out.selected = state.selected && state.selected.length > 0 ? state.selected : null;
    }
    return out;
};

export const decodeSelectionUrl: MapUrlDecoder<PersistedSelectionUrlState> = (params) => {
    const out: Partial<PersistedSelectionUrlState> = {};
    const sel = params.get("selected");
    if (sel !== null && sel !== "") out.selected = sel;
    return out;
};
