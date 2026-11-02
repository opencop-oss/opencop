// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import {
    VIEW_URL_KEYS,
    encodeViewUrl,
    decodeViewUrl,
    type PersistedViewUrlState,
    type ViewUrlKey,
} from "./slices/view/view-url.util";
import {
    SELECTION_URL_KEYS,
    encodeSelectionUrl,
    decodeSelectionUrl,
    type PersistedSelectionUrlState,
    type SelectionUrlKey,
} from "./slices/selection/selection-url.util";

/**
 * Thin orchestrator: composes per-slice URL codecs into a single map URL codec.
 *
 * Each slice owns its URL representation alongside its other concerns. To add
 * a new field to the URL, add it to the relevant slice's `*-url.util.ts`, not
 * to this file. This file only needs to grow when a brand-new slice opts into
 * URL persistence.
 */

export const MAP_URL_KEYS = [...VIEW_URL_KEYS, ...SELECTION_URL_KEYS] as const;
export type MapUrlKey = ViewUrlKey | SelectionUrlKey;

export type PersistedMapUrlState = PersistedViewUrlState & PersistedSelectionUrlState;

/**
 * Slice-level encoder: takes a partial slice state, returns the URL params it
 * owns. Returning `undefined` for a key means "don't touch the URL"; `null`
 * means "delete the URL param"; a string means "set the URL param".
 */
export type MapUrlEncoder<S> = (state: Partial<S>) => Partial<Record<MapUrlKey, string | null>>;

/**
 * Slice-level decoder: reads only the keys it owns from `params` and returns
 * the slice of state derived from them. Other keys are ignored.
 */
export type MapUrlDecoder<S> = (params: URLSearchParams) => Partial<S>;

export const encodeMapUrl = (
    state: Partial<PersistedMapUrlState>,
): Partial<Record<MapUrlKey, string | null>> => ({
    ...encodeViewUrl(state),
    ...encodeSelectionUrl(state),
});

export const decodeMapUrl = (params: URLSearchParams): Partial<PersistedMapUrlState> => ({
    ...decodeViewUrl(params),
    ...decodeSelectionUrl(params),
});
