// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { apiFetch, type FetchOptions } from "../client";
import { buildPageParams, normalizeSearchTerm, type Page, type PageRequestParams } from "../common";

/**
 * How a user group's lifecycle is managed. A local group and a SCIM group may legally share a name
 * since V23, so this is what tells two same-name candidates apart in an assign picker.
 */
export type GroupSource = "OPENCOP" | "SCIM";

/**
 * Minimal projection of a user group, as returned by `GET /user-groups/search`. Carries only an id
 * to select by and a name to display. The fuller shape comes from the admin listing instead.
 */
export interface UserGroupSearchResult {
    id: string;
    name: string;
    /** Whether the group is managed locally or provisioned over SCIM. */
    source: GroupSource;
}

export interface UserGroupsPageParams extends PageRequestParams {
    search?: string;
}

export async function getUserGroupsPage(
    params: UserGroupsPageParams = {},
    options?: FetchOptions,
): Promise<Page<UserGroupSearchResult>> {
    const query = buildPageParams({
        ...params,
        search: normalizeSearchTerm(params.search),
    }).toString();
    return apiFetch<Page<UserGroupSearchResult>>(`/user-groups/search${query ? `?${query}` : ""}`, {
        signal: options?.signal,
    });
}
