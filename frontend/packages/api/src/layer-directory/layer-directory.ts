// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { apiFetch, type FetchOptions } from "../client";
import {
    buildPageParams,
    normalizeSearchTerm,
    type FilterParam,
    type Page,
    type PageRequestParams,
    type RecordVisibility,
} from "../common";
import type { AccessScope } from "../layers/layers";

/** Fields shared by every directory row, regardless of type. */
export interface LayerDirectoryEntryBase {
    id: string;
    name: string;
    accessScope: AccessScope;
    createdAt?: string;
    updatedAt?: string | null;
    // The admin Layers grid can expose this as Last Event once the trigger flow populates it.
    lastEventAt?: string | null;
}

/** A layer row — carries the lifecycle-derived `archived` flag. */
export interface LayerDirectoryLayerEntry extends LayerDirectoryEntryBase {
    type: "LAYER";
    archived: boolean;
}

/** A layer-group row — groups have no lifecycle, so no `archived`. */
export interface LayerDirectoryGroupEntry extends LayerDirectoryEntryBase {
    type: "LAYER_GROUP";
}

/**
 * A row in the combined layer/layer-group directory grid. Discriminated on `type` so the
 * layer-only `archived` flag is reachable only after narrowing — the layer/group invariant is
 * enforced by the type, not a comment.
 */
export type LayerDirectoryEntry = LayerDirectoryLayerEntry | LayerDirectoryGroupEntry;

export interface LayerDirectoryPageParams extends PageRequestParams {
    /** Free-text substring search across the entry name. */
    search?: string;
    /** ACTIVE → active layers + all groups; ARCHIVED → only archived layers; ALL → both. */
    recordVisibility?: RecordVisibility;
    /**
     * Per-column clauses; repeat to stack. Narrowing to one kind is a clause too
     * (`type,equals,LAYER`) rather than a dedicated param, so there is one way to say it.
     */
    filter?: FilterParam[];
    /**
     * Viewer's IANA zone (e.g. `America/Chicago`). Date-only criteria resolve their day boundaries
     * in this zone, so a filtered day means the day shown on screen. Omitted, the server resolves
     * in UTC.
     */
    timezone?: string;
}

/**
 * Server-paged, filterable, sortable combined layers+layer-groups directory. Backs the admin
 * Layers grid so it loads a bounded page rather than the whole list.
 */
export async function getLayerDirectoryPage(
    params: LayerDirectoryPageParams = {},
    options?: FetchOptions,
): Promise<Page<LayerDirectoryEntry>> {
    const query = buildPageParams({
        ...params,
        search: normalizeSearchTerm(params.search),
    }).toString();
    return apiFetch<Page<LayerDirectoryEntry>>(
        `/admin/layer-directory${query ? `?${query}` : ""}`,
        { signal: options?.signal },
    );
}
