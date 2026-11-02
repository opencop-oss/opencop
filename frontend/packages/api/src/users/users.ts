// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { apiFetch, type FetchOptions } from "../client";
import {
    buildPageParams,
    normalizeSearchTerm,
    type Page,
    type PageRequestParams,
    type RecordQueryOptions,
} from "../common";
import type { AccessMode, DataSourceRole, TransportType } from "../data-sources/data-sources";

export type AssignedLayer = {
    layerId: string;
    layerName?: string;
    role: string;
};

export type AssignedDataSource = {
    serverId: string;
    serverName?: string;
    role: string;
    directRole: DataSourceRole | null;
    serverState?: string;
    access: AccessMode;
    transportType: TransportType;
};

export interface User {
    id: string;
    idpUserId: string;
    idpUsername: string;
    username: string;
    firstName: string;
    lastName: string;
    displayName?: string;
    email: string;
    active?: boolean;
    admin?: boolean;
    issuer?: string;
    createdAt?: string;
    updatedAt?: string;
    deletedAt?: string | null;
    layers?: AssignedLayer[];
}

export interface UsersPageParams extends PageRequestParams {
    search?: string;
}

export async function getUsersPage(
    params: UsersPageParams = {},
    options?: FetchOptions,
): Promise<Page<User>> {
    const query = buildPageParams({
        ...params,
        search: normalizeSearchTerm(params.search),
    }).toString();
    return apiFetch<Page<User>>(`/users/search${query ? `?${query}` : ""}`, {
        signal: options?.signal,
    });
}

export async function getUserById(userId: string, options?: FetchOptions): Promise<User> {
    return apiFetch<User>(`/users/${userId}`, { signal: options?.signal });
}

export async function getAssignedLayers(
    userId: string,
    options?: RecordQueryOptions,
): Promise<AssignedLayer[]> {
    if (!userId) return [];
    const params = options?.recordVisibility ? `?recordVisibility=${options.recordVisibility}` : "";
    const data = await apiFetch<AssignedLayer[]>(`/users/${userId}/layers${params}`, {
        signal: options?.signal,
    });
    return Array.isArray(data) ? data : [];
}

export async function updateUserAdmin(userId: string, isAdmin: boolean): Promise<User> {
    return apiFetch<User>(`/admin/users/${userId}`, {
        method: "PATCH",
        body: JSON.stringify({ isAdmin }),
    });
}

export async function getAssignedDataSources(
    userId: string,
    options?: RecordQueryOptions,
): Promise<AssignedDataSource[]> {
    if (!userId) return [];
    const params = options?.recordVisibility ? `?recordVisibility=${options.recordVisibility}` : "";
    const data = await apiFetch<AssignedDataSource[]>(`/users/${userId}/sources${params}`, {
        signal: options?.signal,
    });
    return Array.isArray(data) ? data : [];
}
