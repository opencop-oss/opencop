// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import {
    type QueryClient,
    infiniteQueryOptions,
    keepPreviousData,
    mutationOptions,
    queryOptions,
} from "@tanstack/react-query";
import { apiFetch, getUserGroupsPage } from "@opencop/api";
import type { GroupSource, Page, UserGroupSearchResult } from "@opencop/api";
import { userDirectoryQueries } from "~/features/user-directory/user-directory.queries";

export interface Group {
    id: string;
    name: string;
    description: string | null;
    createdAt: string;
    source: GroupSource;
}

export interface CreateGroup {
    name: string;
    description?: string;
}

export interface GroupMember {
    userId: string;
    username: string;
    firstName: string;
    lastName: string;
    email: string;
    joinedAt: string;
}

export type GroupLayerRole = "OWNER" | "ADMIN" | "EDITOR" | "MEMBER";

export interface GroupLayerAssignment {
    groupId: string;
    groupName: string;
    groupSource: GroupSource;
    layerId: string;
    layerName: string;
    role: GroupLayerRole;
    assignedAt: string;
}

// ── Groups ────────────────────────────────────────────────────────────────────

async function getAdminGroups(): Promise<Group[]> {
    return apiFetch<Group[]>("/admin/user-groups");
}

async function createGroup(data: CreateGroup): Promise<Group> {
    return apiFetch<Group>("/admin/user-groups", {
        method: "POST",
        body: JSON.stringify(data),
    });
}

async function patchGroup(
    groupId: string,
    data: { name?: string; description?: string },
): Promise<Group> {
    return apiFetch<Group>(`/admin/user-groups/${groupId}`, {
        method: "PATCH",
        body: JSON.stringify(data),
    });
}

async function deleteGroup(groupId: string): Promise<void> {
    return apiFetch<void>(`/admin/user-groups/${groupId}`, { method: "DELETE" });
}

// ── Group Members ─────────────────────────────────────────────────────────────

async function getGroupMembers(
    groupId: string,
    options?: { signal?: AbortSignal },
): Promise<GroupMember[]> {
    return apiFetch<GroupMember[]>(`/admin/user-groups/${groupId}/members`, {
        signal: options?.signal,
    });
}

async function patchGroupMembers(
    groupId: string,
    payload: { add?: string[]; remove?: string[] },
): Promise<void> {
    return apiFetch<void>(`/admin/user-groups/${groupId}/members`, {
        method: "PATCH",
        body: JSON.stringify(payload),
    });
}

// ── Group ↔ Layer Assignment ──────────────────────────────────────────────────

async function getGroupLayers(
    groupId: string,
    options?: { signal?: AbortSignal },
): Promise<GroupLayerAssignment[]> {
    return apiFetch<GroupLayerAssignment[]>(`/admin/user-groups/${groupId}/layers`, {
        signal: options?.signal,
    });
}

export async function assignGroupToLayer(
    groupId: string,
    layerId: string,
    role: GroupLayerRole,
): Promise<GroupLayerAssignment> {
    return apiFetch<GroupLayerAssignment>(`/admin/user-groups/${groupId}/layers`, {
        method: "POST",
        body: JSON.stringify({ layerId, role }),
    });
}

async function removeGroupFromLayer(groupId: string, layerId: string): Promise<void> {
    return apiFetch<void>(`/admin/user-groups/${groupId}/layers/${layerId}`, { method: "DELETE" });
}

const USER_GROUP_PICKER_PAGE_SIZE = 25;

// ── Query / Mutation factories ────────────────────────────────────────────────

export const groupQueries = {
    all: ["groups"] as const,
    lists: () => [...groupQueries.all, "list"] as const,
    adminList: () =>
        queryOptions({
            queryKey: [...groupQueries.lists(), "admin"] as const,
            queryFn: () => getAdminGroups(),
        }),
    searches: () => [...groupQueries.all, "search"] as const,
    /**
     * Server-paged, searchable user-group pool for the drawer assign pickers. Unlike
     * {@link groupQueries.adminList}, which is the unpaged admin directory and answers 403 without
     * `ROLE_admin`, this is reachable by any authenticated caller under a minimal projection.
     */
    infiniteSearch: (search: string) =>
        infiniteQueryOptions({
            queryKey: [...groupQueries.searches(), search] as const,
            queryFn: ({ pageParam, signal }) =>
                getUserGroupsPage(
                    {
                        search: search || undefined,
                        page: pageParam,
                        size: USER_GROUP_PICKER_PAGE_SIZE,
                        sort: ["name,asc"],
                    },
                    { signal },
                ),
            initialPageParam: 0,
            getNextPageParam: (lastPage: Page<UserGroupSearchResult>) => {
                const next = lastPage.page + 1;
                return next < lastPage.totalPages ? next : undefined;
            },
            // Keep the current results visible while a new search page loads, so the
            // picker list doesn't flash its skeleton on every debounced keystroke.
            placeholderData: keepPreviousData,
        }),
    details: () => [...groupQueries.all, "detail"] as const,
    members: (id: string) =>
        queryOptions({
            queryKey: [...groupQueries.details(), id, "members"] as const,
            queryFn: ({ signal }) => getGroupMembers(id, { signal }),
            enabled: !!id,
        }),
    groupLayers: (groupId: string) =>
        queryOptions({
            queryKey: [...groupQueries.details(), groupId, "layers"] as const,
            queryFn: ({ signal }) => getGroupLayers(groupId, { signal }),
            enabled: !!groupId,
        }),
};

export const groupMutations = {
    create: (queryClient: QueryClient) =>
        mutationOptions({
            mutationKey: [...groupQueries.all, "create"],
            mutationFn: (data: CreateGroup) => createGroup(data),
            onSuccess: () => {
                queryClient.invalidateQueries({ queryKey: groupQueries.lists() });
                // The assign pickers read searches(), a sibling of lists(), so invalidating
                // lists() alone does not reach them.
                queryClient.invalidateQueries({ queryKey: groupQueries.searches() });
                queryClient.invalidateQueries({ queryKey: userDirectoryQueries.all });
            },
        }),
    patchMembers: (queryClient: QueryClient) =>
        mutationOptions({
            mutationKey: [...groupQueries.all, "patchMembers"],
            mutationFn: ({
                groupId,
                add,
                remove,
            }: {
                groupId: string;
                add?: string[];
                remove?: string[];
            }) => patchGroupMembers(groupId, { add, remove }),
            onSuccess: (_, { groupId }) => {
                queryClient.invalidateQueries({
                    queryKey: [...groupQueries.details(), groupId, "members"],
                });
            },
        }),
    assignToLayer: (queryClient: QueryClient) =>
        mutationOptions({
            mutationKey: [...groupQueries.all, "assignToLayer"],
            mutationFn: ({
                groupId,
                layerId,
                role,
            }: {
                groupId: string;
                layerId: string;
                role: GroupLayerRole;
            }) => assignGroupToLayer(groupId, layerId, role),
            onSuccess: (_, { groupId }) => {
                queryClient.invalidateQueries({
                    queryKey: groupQueries.groupLayers(groupId).queryKey,
                });
            },
        }),
    removeFromLayer: (queryClient: QueryClient) =>
        mutationOptions({
            mutationKey: [...groupQueries.all, "removeFromLayer"],
            mutationFn: ({ groupId, layerId }: { groupId: string; layerId: string }) =>
                removeGroupFromLayer(groupId, layerId),
            onSuccess: (_, { groupId }) => {
                queryClient.invalidateQueries({
                    queryKey: groupQueries.groupLayers(groupId).queryKey,
                });
            },
        }),
    patch: (queryClient: QueryClient) =>
        mutationOptions({
            mutationKey: [...groupQueries.all, "patch"],
            mutationFn: ({
                groupId,
                ...data
            }: {
                groupId: string;
                name?: string;
                description?: string;
            }) => patchGroup(groupId, data),
            onSuccess: (updatedGroup) => {
                queryClient.invalidateQueries({ queryKey: groupQueries.lists() });
                // A rename changes the only field the assign pickers render, and they read
                // searches(), which lists() does not cover.
                queryClient.invalidateQueries({ queryKey: groupQueries.searches() });
                queryClient.setQueryData(
                    [...groupQueries.details(), updatedGroup.id],
                    updatedGroup,
                );
                // A renamed group shows in the server-paged admin Users grid — refresh it too.
                queryClient.invalidateQueries({ queryKey: userDirectoryQueries.all });
            },
        }),
    delete: (queryClient: QueryClient) =>
        mutationOptions({
            mutationKey: [...groupQueries.all, "delete"],
            mutationFn: (groupId: string) => deleteGroup(groupId),
            onSuccess: (_, groupId) => {
                queryClient.invalidateQueries({ queryKey: groupQueries.lists() });
                // A deleted group must drop out of the assign pickers, which read searches().
                queryClient.invalidateQueries({ queryKey: groupQueries.searches() });
                queryClient.removeQueries({
                    queryKey: [...groupQueries.details(), groupId],
                });
                // Groups appear in the server-paged admin Users grid — refresh it too.
                queryClient.invalidateQueries({ queryKey: userDirectoryQueries.all });
            },
        }),
};
