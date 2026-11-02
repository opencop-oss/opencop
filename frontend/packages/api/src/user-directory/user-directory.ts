// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { apiFetch, type FetchOptions } from "../client";
import {
    buildPageParams,
    normalizeSearchTerm,
    type FilterParam,
    type Page,
    type PageRequestParams,
} from "../common";

/** Fields present on every user directory row, regardless of kind. */
interface UserDirectoryEntryBase {
    id: string;
    name: string;
    active: boolean;
    createdAt?: string;
    updatedAt?: string;
}

/** A user row — carries the user-only profile fields. */
export interface UserDirectoryUserEntry extends UserDirectoryEntryBase {
    type: "USER";
    firstName?: string | null;
    lastName?: string | null;
    email?: string | null;
    displayName?: string | null;
    admin?: boolean | null;
}

/** A group row — the user-only profile fields do not apply. */
export interface UserDirectoryGroupEntry extends UserDirectoryEntryBase {
    type: "GROUP";
}

/**
 * A row in the combined user/group directory grid. Discriminated on `type` so the user-only
 * fields (`firstName`/`lastName`/`email`/`displayName`/`admin`) are reachable only after
 * narrowing to a USER entry — the user/group invariant is enforced by the type, not a comment.
 */
export type UserDirectoryEntry = UserDirectoryUserEntry | UserDirectoryGroupEntry;

export interface UserDirectoryPageParams extends PageRequestParams {
    search?: string;
    filter?: FilterParam[];
    /**
     * IANA zone the grid renders timestamps in. The server resolves date-only filter operands to
     * day boundaries in this zone, so a filtered day means the day shown on screen. Omitted, the
     * server resolves in UTC.
     */
    timezone?: string;
}

/**
 * Server-paged, filterable, sortable combined users+groups directory. Backs the admin
 * Users grid so it loads a bounded page rather than the whole user directory.
 */
export async function getUserDirectoryPage(
    params: UserDirectoryPageParams = {},
    options?: FetchOptions,
): Promise<Page<UserDirectoryEntry>> {
    const query = buildPageParams({
        ...params,
        search: normalizeSearchTerm(params.search),
    }).toString();
    return apiFetch<Page<UserDirectoryEntry>>(`/admin/user-directory${query ? `?${query}` : ""}`, {
        signal: options?.signal,
    });
}
