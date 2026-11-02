// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { type QueryClient, mutationOptions, queryOptions } from "@tanstack/react-query";
import { apiFetch } from "@opencop/api";
import type { AccessScope, GroupSource, LayerRole } from "@opencop/api";
import { dataSourceQueries } from "~/features/data-sources/data-sources.queries";
import { useFilterPanelStore } from "~/features/filters/filter-panel.store";
import { layerQueries } from "~/features/layers/layers.queries";
import { layerDirectoryQueries } from "~/features/layers/layer-directory.queries";

// ── Types ─────────────────────────────────────────────────────────────────────

export type LayerGroupAccessScope = "PRIVATE" | "GLOBAL";

export type LayerGroupRole = "OWNER" | "ADMIN" | "MAINTAINER" | "MEMBER";

export const LayerGroupRoles = {
    OWNER: "OWNER",
    ADMIN: "ADMIN",
    MAINTAINER: "MAINTAINER",
    MEMBER: "MEMBER",
} as const satisfies Record<string, LayerGroupRole>;

export interface LayerGroupLayerSummary {
    id: string;
    name: string;
    accessScope: AccessScope;
    role: string;
}

export interface LayerGroup {
    id: string;
    name: string;
    description: string | null;
    accessScope: LayerGroupAccessScope;
    createdAt: string;
}

export interface MyLayerGroup {
    id: string;
    name: string;
    description: string | null;
    containerRole: LayerGroupRole;
    accessScope: LayerGroupAccessScope;
    createdAt: string;
    layers: LayerGroupLayerSummary[];
}

export interface LayerGroupMember {
    userId: string;
    username: string;
    firstName: string;
    lastName: string;
    email: string;
    role: LayerGroupRole;
    joinedAt: string;
}

export interface LayerGroupLayer {
    layerGroupId: string;
    layerGroupName: string;
    layerId: string;
    layerName: string;
    role: LayerRole;
    assignedAt: string;
}

export interface LayerGroupUserGroup {
    userGroupId: string;
    name: string;
    role: LayerGroupRole;
    assignedAt: string;
    source: GroupSource;
}

export interface CreateLayerGroup {
    name: string;
    description?: string;
    accessScope?: LayerGroupAccessScope;
}

// ── API functions ─────────────────────────────────────────────────────────────

async function getMyLayerGroups(): Promise<MyLayerGroup[]> {
    return apiFetch<MyLayerGroup[]>("/me/layer-groups");
}

async function getAdminLayerGroups(options?: { signal?: AbortSignal }): Promise<LayerGroup[]> {
    return apiFetch<LayerGroup[]>("/admin/layer-groups", { signal: options?.signal });
}

/**
 * Creates a layer group owned by the caller. Open to any authenticated user, since the server
 * defaults the scope to PRIVATE and rejects GLOBAL for non-admins; one route serves both.
 */
async function createLayerGroup(data: CreateLayerGroup): Promise<LayerGroup> {
    return apiFetch<LayerGroup>("/layer-groups", {
        method: "POST",
        body: JSON.stringify(data),
    });
}

async function getLayerGroup(id: string): Promise<LayerGroup> {
    return apiFetch<LayerGroup>(`/layer-groups/${id}`);
}

async function patchLayerGroup(
    id: string,
    data: { name?: string; description?: string; accessScope?: LayerGroupAccessScope },
): Promise<LayerGroup> {
    return apiFetch<LayerGroup>(`/layer-groups/${id}`, {
        method: "PATCH",
        body: JSON.stringify(data),
    });
}

async function deleteLayerGroup(id: string): Promise<void> {
    return apiFetch<void>(`/layer-groups/${id}`, { method: "DELETE" });
}

async function getLayerGroupMembers(
    id: string,
    options?: { signal?: AbortSignal },
): Promise<LayerGroupMember[]> {
    return apiFetch<LayerGroupMember[]>(`/layer-groups/${id}/members`, {
        signal: options?.signal,
    });
}

async function patchLayerGroupMembers(
    id: string,
    payload: { add?: { userId: string; role: LayerGroupRole }[]; remove?: string[] },
): Promise<void> {
    return apiFetch<void>(`/layer-groups/${id}/members`, {
        method: "PATCH",
        body: JSON.stringify(payload),
    });
}

async function getLayerGroupLayers(
    id: string,
    options?: { signal?: AbortSignal },
): Promise<LayerGroupLayer[]> {
    return apiFetch<LayerGroupLayer[]>(`/layer-groups/${id}/layers`, {
        signal: options?.signal,
    });
}

/**
 * The reverse of {@link getLayerGroupLayers}: every layer group the given layer belongs to.
 * Returns the same row shape, so `layerGroupId` / `layerGroupName` are the meaningful fields here.
 */
async function getLayerGroupsForLayer(
    layerId: string,
    options?: { signal?: AbortSignal },
): Promise<LayerGroupLayer[]> {
    return apiFetch<LayerGroupLayer[]>(`/layers/${layerId}/layer-groups`, {
        signal: options?.signal,
    });
}

async function assignLayersToGroup(
    groupId: string,
    layerIds: string[],
    role: LayerRole,
): Promise<LayerGroupLayer[]> {
    return apiFetch<LayerGroupLayer[]>(`/layer-groups/${groupId}/layers`, {
        method: "POST",
        body: JSON.stringify({ layerIds, role }),
    });
}

async function removeLayerFromGroup(groupId: string, layerId: string): Promise<void> {
    return apiFetch<void>(`/layer-groups/${groupId}/layers/${layerId}`, { method: "DELETE" });
}

async function getLayerGroupUserGroups(
    id: string,
    options?: { signal?: AbortSignal },
): Promise<LayerGroupUserGroup[]> {
    return apiFetch<LayerGroupUserGroup[]>(`/layer-groups/${id}/user-groups`, {
        signal: options?.signal,
    });
}

async function patchLayerGroupUserGroups(
    id: string,
    payload: { add?: { userGroupId: string; role: LayerGroupRole }[]; remove?: string[] },
): Promise<void> {
    return apiFetch<void>(`/layer-groups/${id}/user-groups`, {
        method: "PATCH",
        body: JSON.stringify(payload),
    });
}

// ── Query / Mutation factories ────────────────────────────────────────────────

export const layerGroupQueries = {
    all: ["layer-groups"] as const,
    lists: () => [...layerGroupQueries.all, "list"] as const,
    /**
     * Every layer group, unpaged — for the assign **pickers**, which need the full candidate set to
     * filter by scope and drop already-assigned rows.
     *
     * Not what backs the admin Layers grid: that is `layerDirectoryQueries.infiniteList`, which is
     * server-paged and returns layers and groups combined. This one briefly had no consumers after
     * the grid moved to the directory endpoint and was removed as dead code; the pickers added here
     * are its live consumers, and `GET /admin/layer-groups` still serves it.
     */
    adminList: () =>
        queryOptions({
            queryKey: [...layerGroupQueries.lists(), "admin"] as const,
            queryFn: ({ signal }) => getAdminLayerGroups({ signal }),
        }),
    myList: () =>
        queryOptions({
            queryKey: [...layerGroupQueries.lists(), "mine"] as const,
            queryFn: () => getMyLayerGroups(),
        }),
    details: () => [...layerGroupQueries.all, "detail"] as const,
    detail: (id: string) =>
        queryOptions({
            queryKey: [...layerGroupQueries.details(), id] as const,
            queryFn: () => getLayerGroup(id),
            enabled: !!id,
        }),
    members: (id: string) =>
        queryOptions({
            queryKey: [...layerGroupQueries.details(), id, "members"] as const,
            queryFn: ({ signal }) => getLayerGroupMembers(id, { signal }),
            enabled: !!id,
        }),
    layers: (id: string) =>
        queryOptions({
            queryKey: [...layerGroupQueries.details(), id, "layers"] as const,
            queryFn: ({ signal }) => getLayerGroupLayers(id, { signal }),
            enabled: !!id,
        }),
    userGroups: (id: string) =>
        queryOptions({
            queryKey: [...layerGroupQueries.details(), id, "userGroups"] as const,
            queryFn: ({ signal }) => getLayerGroupUserGroups(id, { signal }),
            enabled: !!id,
        }),
    /**
     * Keyed by *layer*, not by group — hence its own namespace rather than a `details()` child.
     * Assign/remove invalidate this per affected layer; {@link layerGroupQueries.forLayers} is the
     * prefix to invalidate them all.
     */
    forLayers: () => [...layerGroupQueries.all, "forLayer"] as const,
    forLayer: (layerId: string) =>
        queryOptions({
            queryKey: [...layerGroupQueries.forLayers(), layerId] as const,
            queryFn: ({ signal }) => getLayerGroupsForLayer(layerId, { signal }),
            enabled: !!layerId,
        }),
};

/**
 * A multi-group assign where some groups committed and others failed.
 *
 * `message` is the failed group names, so the generic `getErrorMessage` toast reads correctly.
 * `succeededIds` is what callers must prune from any retained selection before a retry: the assign
 * endpoint upserts, so re-sending a group that already committed would silently rewrite its role
 * if the user changed the role between attempts.
 */
export class PartialAssignError extends Error {
    // Declared and assigned rather than constructor parameter properties — the project builds with
    // `erasableSyntaxOnly`, which forbids that shorthand.
    readonly succeededIds: string[];
    readonly failedNames: string[];

    constructor(succeededIds: string[], failedNames: string[]) {
        super(failedNames.join(", "));
        this.name = "PartialAssignError";
        this.succeededIds = succeededIds;
        this.failedNames = failedNames;
    }
}

export const layerGroupMutations = {
    create: (queryClient: QueryClient) =>
        mutationOptions({
            mutationKey: [...layerGroupQueries.all, "create"],
            mutationFn: (data: CreateLayerGroup) => createLayerGroup(data),
            onSuccess: () => {
                queryClient.invalidateQueries({ queryKey: layerGroupQueries.lists() });
                queryClient.invalidateQueries({ queryKey: layerDirectoryQueries.lists() });
            },
        }),
    patch: (queryClient: QueryClient) =>
        mutationOptions({
            mutationKey: [...layerGroupQueries.all, "patch"],
            mutationFn: ({
                id,
                ...data
            }: {
                id: string;
                name?: string;
                description?: string;
                accessScope?: LayerGroupAccessScope;
            }) => patchLayerGroup(id, data),
            onSuccess: (updatedGroup) => {
                queryClient.invalidateQueries({ queryKey: layerGroupQueries.lists() });
                queryClient.invalidateQueries({ queryKey: layerDirectoryQueries.lists() });
                queryClient.setQueryData(
                    layerGroupQueries.detail(updatedGroup.id).queryKey,
                    updatedGroup,
                );
            },
        }),
    delete: (queryClient: QueryClient) =>
        mutationOptions({
            mutationKey: [...layerGroupQueries.all, "delete"],
            mutationFn: (id: string) => deleteLayerGroup(id),
            onSuccess: (_, id) => {
                queryClient.invalidateQueries({ queryKey: layerGroupQueries.lists() });
                queryClient.invalidateQueries({ queryKey: layerDirectoryQueries.lists() });
                queryClient.removeQueries({
                    queryKey: [...layerGroupQueries.details(), id],
                });
                // The deleted group drops out of every layer's reverse view. Which layers belonged
                // to it isn't known here without another fetch, so invalidate the whole namespace.
                queryClient.invalidateQueries({ queryKey: layerGroupQueries.forLayers() });
                useFilterPanelStore
                    .getState()
                    .setHiddenLayerGroups((prev) => prev.filter((gId) => gId !== id));
            },
        }),
    patchMembers: (queryClient: QueryClient) =>
        mutationOptions({
            mutationKey: [...layerGroupQueries.all, "patchMembers"],
            mutationFn: ({
                groupId,
                add,
                remove,
            }: {
                groupId: string;
                add?: { userId: string; role: LayerGroupRole }[];
                remove?: string[];
            }) => patchLayerGroupMembers(groupId, { add, remove }),
            onSuccess: (_, { groupId }) => {
                queryClient.invalidateQueries({
                    queryKey: layerGroupQueries.members(groupId).queryKey,
                });
                queryClient.invalidateQueries({ queryKey: layerGroupQueries.lists() });
            },
        }),
    /**
     * Assigns the same layers to one or more groups.
     *
     * The endpoint is keyed by group and batches only layers, so multi-group assignment is
     * necessarily a client-side fan-out. It lives here rather than at each call site so every
     * caller — including the single-group case — gets the same partial-failure semantics and a
     * single invalidation pass: a group that fails does not roll back the groups that succeeded,
     * and the rejection reports both sides so the caller can name the failures and prune the
     * successes from a retry.
     */
    assignLayersToGroups: (queryClient: QueryClient) =>
        mutationOptions({
            mutationKey: [...layerGroupQueries.all, "assignLayersToGroups"],
            mutationFn: async ({
                groups,
                layerIds,
                role,
            }: {
                /** Names are carried so the rejection can identify the failed groups. */
                groups: { id: string; name: string }[];
                layerIds: string[];
                role: LayerRole;
            }) => {
                const results = await Promise.allSettled(
                    groups.map((group) => assignLayersToGroup(group.id, layerIds, role)),
                );
                const failed = groups.filter((_, index) => results[index].status === "rejected");
                if (failed.length > 0) {
                    throw new PartialAssignError(
                        groups
                            .filter((_, index) => results[index].status === "fulfilled")
                            .map((group) => group.id),
                        failed.map((group) => group.name),
                    );
                }
            },
            // onSettled, not onSuccess: a partial failure still committed some assignments, so the
            // cache must be refreshed on the rejection path too.
            onSettled: (_data, _error, { groups, layerIds }) => {
                for (const group of groups) {
                    queryClient.invalidateQueries({
                        queryKey: layerGroupQueries.layers(group.id).queryKey,
                    });
                }
                for (const layerId of layerIds) {
                    queryClient.invalidateQueries({
                        queryKey: layerGroupQueries.forLayer(layerId).queryKey,
                    });
                }
                queryClient.invalidateQueries({ queryKey: layerQueries.myLists() });
                queryClient.invalidateQueries({ queryKey: dataSourceQueries.myLists() });
                queryClient.invalidateQueries({ queryKey: layerGroupQueries.lists() });
            },
        }),
    removeLayer: (queryClient: QueryClient) =>
        mutationOptions({
            mutationKey: [...layerGroupQueries.all, "removeLayer"],
            mutationFn: ({ groupId, layerId }: { groupId: string; layerId: string }) =>
                removeLayerFromGroup(groupId, layerId),
            onSuccess: (_, { groupId, layerId }) => {
                queryClient.invalidateQueries({
                    queryKey: layerQueries.myLists(),
                });
                queryClient.invalidateQueries({
                    queryKey: dataSourceQueries.myLists(),
                });
                queryClient.invalidateQueries({
                    queryKey: layerGroupQueries.layers(groupId).queryKey,
                });
                queryClient.invalidateQueries({
                    queryKey: layerGroupQueries.forLayer(layerId).queryKey,
                });
                queryClient.invalidateQueries({ queryKey: layerGroupQueries.lists() });
            },
        }),
    patchUserGroups: (queryClient: QueryClient) =>
        mutationOptions({
            mutationKey: [...layerGroupQueries.all, "patchUserGroups"],
            mutationFn: ({
                groupId,
                add,
                remove,
            }: {
                groupId: string;
                add?: { userGroupId: string; role: LayerGroupRole }[];
                remove?: string[];
            }) => patchLayerGroupUserGroups(groupId, { add, remove }),
            onSuccess: (_, { groupId }) => {
                queryClient.invalidateQueries({
                    queryKey: layerGroupQueries.userGroups(groupId).queryKey,
                });
                queryClient.invalidateQueries({ queryKey: layerGroupQueries.lists() });
            },
        }),
};
