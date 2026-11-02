// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { apiFetch, type FetchOptions } from "../client";
import type { DataSourceLayer } from "../data-sources/data-sources";
import type { EmptyObject } from "type-fest";

export type AccessScope = "PRIVATE" | "GLOBAL";

export type { RecordVisibility } from "../common";
import type { RecordQueryOptions } from "../common";

/**
 * Response from /v1/layers (the admin endpoint) is LayerInfo[] and represents all layers.
 */
export interface LayerInfo {
    id: string;
    name: string;
    createdAt: string;
    createdBy?: string | null;
    updatedAt?: string | null;
    updatedBy?: string | null;
    lastEventAt?: string | null;
    status: string;
    lifecycleState?: string;
    editing: boolean;
    description?: string;
    accessScope: AccessScope;
    defaultVisible: boolean;
    archivedAt?: string | null;
    archivedBy?: string | null;
}

// Body type for creating a layer
export type CreateLayer = { name: string; accessScope: AccessScope };

/**
 * Response item from GET /v1/layers/{id}/members
 */
export interface LayerUser {
    layerId: string;
    userId: string;
    role: LayerRole;
    joinedAt: string;
    username: string;
    firstName: string;
    lastName: string;
    email: string;
}

/** Valid layer roles per api. */
export type LayerRole = "OWNER" | "ADMIN" | "MEMBER" | "EDITOR";

export const LayerRoles = {
    OWNER: "OWNER",
    ADMIN: "ADMIN",
    MEMBER: "MEMBER",
    EDITOR: "EDITOR",
} as const satisfies Record<string, LayerRole>;

/**
 * Response from /v1/me/layers is MyLayerInfo[] and represents the user's layer memberships.
 * Note the shape/field-names are not consistent with the admin endpoint for listing layers
 * which is `LayerInfo`.
 */
export interface MyLayerInfo {
    layerId: string;
    layerName: string;
    role: LayerRole;
    createdAt?: string;
    createdBy?: string | null;
    updatedAt?: string | null;
    updatedBy?: string | null;
    /** Latest recorded layer activity; null when none has been recorded. */
    lastEventAt?: string | null;
    accessScope: AccessScope;
    defaultVisible: boolean;
    archivedAt?: string | null;
    archivedBy?: string | null;
}

export async function getMyLayers(options?: RecordQueryOptions): Promise<MyLayerInfo[]> {
    const params = options?.recordVisibility ? `?recordVisibility=${options.recordVisibility}` : "";
    return apiFetch<MyLayerInfo[]>(`/me/layers${params}`, { signal: options?.signal });
}

export async function createLayer(create: CreateLayer): Promise<LayerInfo> {
    return apiFetch<LayerInfo>("/layers", {
        method: "POST",
        body: JSON.stringify(create),
    });
}

export async function getLayer(id: string, options?: FetchOptions): Promise<LayerInfo> {
    return apiFetch<LayerInfo>(`/layers/${id}`, { signal: options?.signal });
}

export async function getLayers(options?: RecordQueryOptions): Promise<LayerInfo[]> {
    const params = options?.recordVisibility ? `?recordVisibility=${options.recordVisibility}` : "";
    return apiFetch<LayerInfo[]>(`/admin/layers${params}`, { signal: options?.signal });
}

export async function assignUserToLayer(
    layerId: string,
    userId: string,
    role: LayerRole,
): Promise<void> {
    const body: Record<string, LayerRole> = {
        [userId]: role,
    };

    return apiFetch<void>(`/layers/${layerId}/members`, {
        method: "POST",
        body: JSON.stringify(body),
    });
}

export async function updateLayerMembership(
    id: string,
    members: Record<string, LayerRole>,
): Promise<void> {
    const body: { members: string[]; editors: string[]; admins: string[]; owners: string[] } = {
        members: [],
        editors: [],
        admins: [],
        owners: [],
    };

    for (const [principal, role] of Object.entries(members)) {
        if (role === "MEMBER") body.members.push(principal);
        else if (role === "EDITOR") body.editors.push(principal);
        else if (role === "ADMIN") body.admins.push(principal);
        else if (role === "OWNER") body.owners.push(principal);
    }

    return apiFetch<void>(`/layers/${id}/members`, {
        method: "PATCH",
        body: JSON.stringify(body),
    });
}

export async function removeUserFromLayer(id: string, userId: string): Promise<void> {
    return apiFetch<void>(`/layers/${id}/members`, {
        method: "DELETE",
        body: JSON.stringify([userId]),
    });
}

export async function patchLayer(
    id: string,
    data: { name?: string; accessScope?: AccessScope; defaultVisible?: boolean },
): Promise<LayerInfo> {
    return apiFetch<LayerInfo>(`/layers/${id}`, {
        method: "PATCH",
        body: JSON.stringify(data),
    });
}

export async function deleteLayer(id: string): Promise<void> {
    return apiFetch<void>(`/layers/${id}`, { method: "DELETE" });
}

export async function getMembers(id: string, options?: FetchOptions): Promise<LayerUser[]> {
    return apiFetch<LayerUser[]>(`/layers/${id}/members`, { signal: options?.signal });
}

/**
 * A **user group**'s role on a layer. Not to be confused with a layer group — see
 * `LayerGroupLayer` in the layer-groups feature for that relation.
 */
export interface LayerUserGroupAssignment {
    groupId: string;
    groupName: string;
    layerId: string;
    role: LayerRole;
    assignedAt: string;
}

/** The **user groups** granted a role on this layer. */
export async function getUserGroupsForLayer(
    id: string,
    options?: FetchOptions,
): Promise<LayerUserGroupAssignment[]> {
    return apiFetch<LayerUserGroupAssignment[]>(`/layers/${id}/user-groups`, {
        signal: options?.signal,
    });
}

/** Revokes a **user group**'s role on a layer. */
export async function removeUserGroupFromLayer(groupId: string, layerId: string): Promise<void> {
    return apiFetch<void>(`/admin/user-groups/${groupId}/layers/${layerId}`, { method: "DELETE" });
}

export async function importLayerFile(
    layerId: string,
    mapperId: string,
    file: File,
): Promise<void> {
    const formData = new FormData();
    formData.append("data", new Blob([JSON.stringify({ mapperId })], { type: "application/json" }));
    formData.append("file", file);

    return apiFetch<void>(`/layers/${layerId}/import`, {
        method: "POST",
        body: formData,
    });
}

export async function getDataSources(
    id: string,
    options?: FetchOptions,
): Promise<DataSourceLayer[]> {
    return apiFetch<DataSourceLayer[]>(`/layers/${id}/sources`, { signal: options?.signal });
}

export interface CloneLayerRequest {
    name: string;
    users: EmptyObject;
    copyRemarks: boolean;
    copyMedia: boolean;
}

export async function cloneLayer(layerId: string, request: CloneLayerRequest): Promise<LayerInfo> {
    return apiFetch<LayerInfo>(`/layers/${layerId}/clone`, {
        method: "POST",
        body: JSON.stringify(request),
    });
}
