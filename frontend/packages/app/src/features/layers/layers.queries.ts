// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { type QueryClient, mutationOptions, queryOptions } from "@tanstack/react-query";
import {
    getLayers,
    getMyLayers,
    getLayer,
    getMembers,
    getUserGroupsForLayer,
    getLayerDataSources,
    cloneLayer,
    createLayer,
    deleteLayer,
    importLayerFile,
    patchLayer,
    removeUserFromLayer,
    removeUserGroupFromLayer,
    assignUserToLayer,
    updateLayerMembership,
} from "@opencop/api";
import { ApiError, getErrorMessage } from "@opencop/api";
import type {
    AccessScope,
    CloneLayerRequest,
    CreateLayer,
    LayerRole,
    LayerUser,
} from "@opencop/api";
import type { TFunction } from "i18next";
import { assignGroupToLayer, groupQueries } from "../groups/groups.queries";
import { userQueries } from "../users/users.queries";
import { auditQueries } from "../admin/audit/audit.queries";
import { layerDirectoryQueries } from "./layer-directory.queries";

/**
 * Resolves the user-facing message for a layer create/save failure: a 409
 * surfaces the "layer already exists" copy; everything else defers to the shared
 * `getErrorMessage` (which unwraps `ApiError.body.message`). Shared by the admin
 * and "my" layer edit forms so the 409 handling lives in one place.
 */
export function getLayerSaveErrorMessage(err: unknown, t: TFunction): string {
    if (err instanceof ApiError && err.status === 409) return t("adminLayers.layerExists");
    return getErrorMessage(err, t("adminLayers.failedToCreate"));
}

export const layerQueries = {
    all: ["layers"] as const,
    lists: () => [...layerQueries.all, "list"] as const,
    adminList: (archived = false) =>
        queryOptions({
            queryKey: [...layerQueries.lists(), "admin", archived] as const,
            queryFn: ({ signal }) =>
                getLayers({ signal, recordVisibility: archived ? "ARCHIVED" : "ACTIVE" }),
        }),
    myLists: () => [...layerQueries.lists(), "mine"] as const,
    myList: (archived = false) =>
        queryOptions({
            queryKey: [...layerQueries.myLists(), archived] as const,
            queryFn: ({ signal }) =>
                getMyLayers({ signal, recordVisibility: archived ? "ARCHIVED" : "ACTIVE" }),
        }),
    details: () => [...layerQueries.all, "detail"] as const,
    detail: (id: string) =>
        queryOptions({
            queryKey: [...layerQueries.details(), id] as const,
            queryFn: ({ signal }) => getLayer(id, { signal }),
            enabled: !!id,
        }),
    members: (id: string) =>
        queryOptions({
            queryKey: [...layerQueries.details(), id, "members"] as const,
            queryFn: ({ signal }) => getMembers(id, { signal }),
            enabled: !!id,
        }),
    dataSources: (id: string) =>
        queryOptions({
            queryKey: [...layerQueries.details(), id, "dataSources"] as const,
            queryFn: ({ signal }) => getLayerDataSources(id, { signal }),
            enabled: !!id,
        }),
    /**
     * **User** groups holding a role on this layer. For the layer *groups* a layer belongs to,
     * see `layerGroupQueries.forLayer`.
     */
    userGroups: (id: string) =>
        queryOptions({
            queryKey: [...layerQueries.details(), id, "userGroups"] as const,
            queryFn: ({ signal }) => getUserGroupsForLayer(id, { signal }),
            enabled: !!id,
        }),
};

export const layerMutations = {
    create: (queryClient: QueryClient) =>
        mutationOptions({
            mutationKey: [...layerQueries.all, "create"],
            mutationFn: (create: CreateLayer) => createLayer(create),
            onSuccess: () => {
                queryClient.invalidateQueries({ queryKey: layerQueries.lists() });
                queryClient.invalidateQueries({ queryKey: layerDirectoryQueries.lists() });
            },
        }),
    delete: (queryClient: QueryClient) =>
        mutationOptions({
            mutationKey: [...layerQueries.all, "delete"],
            mutationFn: (id: string) => deleteLayer(id),
            onSuccess: (_, id) => {
                queryClient.invalidateQueries({ queryKey: layerQueries.lists() });
                queryClient.invalidateQueries({ queryKey: layerDirectoryQueries.lists() });
                queryClient.removeQueries({ queryKey: layerQueries.detail(id).queryKey });
            },
        }),
    removeUser: (queryClient: QueryClient) =>
        mutationOptions({
            mutationKey: [...layerQueries.all, "removeUser"],
            mutationFn: ({ layerId, userId }: { layerId: string; userId: string }) =>
                removeUserFromLayer(layerId, userId),
            onSuccess: (_, { layerId, userId }) => {
                queryClient.invalidateQueries({ queryKey: layerQueries.members(layerId).queryKey });
                queryClient.invalidateQueries({
                    queryKey: userQueries.assignedLayers(userId).queryKey,
                });
                // Layer lists carry each viewer's MyLayerInfo.role (drives owner gating
                // and the drawer's role snapshot) — refresh so it reflects the change.
                queryClient.invalidateQueries({ queryKey: layerQueries.lists() });
            },
        }),
    assignUser: (queryClient: QueryClient) =>
        mutationOptions({
            mutationKey: [...layerQueries.all, "assignUser"],
            mutationFn: ({
                layerId,
                userId,
                role,
            }: {
                layerId: string;
                userId: string;
                role: LayerRole;
            }) => assignUserToLayer(layerId, userId, role),
            onSuccess: (_, { layerId, userId }) => {
                queryClient.invalidateQueries({ queryKey: layerQueries.members(layerId).queryKey });
                queryClient.invalidateQueries({
                    queryKey: userQueries.assignedLayers(userId).queryKey,
                });
                // Layer lists carry each viewer's MyLayerInfo.role (drives owner gating
                // and the drawer's role snapshot) — refresh so it reflects the change.
                queryClient.invalidateQueries({ queryKey: layerQueries.lists() });
            },
        }),
    importFile: (queryClient: QueryClient) =>
        mutationOptions({
            mutationKey: [...layerQueries.all, "importFile"],
            mutationFn: ({
                layerId,
                mapperId,
                file,
            }: {
                layerId: string;
                mapperId: string;
                file: File;
            }) => importLayerFile(layerId, mapperId, file),
            onSuccess: () => {
                queryClient.invalidateQueries({ queryKey: layerQueries.lists() });
                queryClient.invalidateQueries({ queryKey: layerDirectoryQueries.lists() });
            },
            // IMPORT_REQUESTED is recorded when the job is accepted, from a modal that leaves the
            // History section mounted.
            onSettled: () => queryClient.invalidateQueries({ queryKey: auditQueries.all }),
        }),
    patch: (queryClient: QueryClient) =>
        mutationOptions({
            mutationKey: [...layerQueries.all, "patch"],
            mutationFn: ({
                id,
                name,
                accessScope,
                defaultVisible,
            }: {
                id: string;
                name?: string;
                accessScope?: AccessScope;
                defaultVisible?: boolean;
            }) =>
                patchLayer(id, {
                    ...(name !== undefined && { name }),
                    ...(accessScope !== undefined && { accessScope }),
                    ...(defaultVisible !== undefined && { defaultVisible }),
                }),
            onSuccess: (_, { id }) => {
                queryClient.invalidateQueries({ queryKey: layerQueries.lists() });
                queryClient.invalidateQueries({ queryKey: layerDirectoryQueries.lists() });
                queryClient.invalidateQueries({ queryKey: layerQueries.detail(id).queryKey });
            },
        }),
    updateMembership: (queryClient: QueryClient) =>
        mutationOptions({
            mutationKey: [...layerQueries.all, "updateMembership"],
            mutationFn: ({
                layerId,
                members,
            }: {
                layerId: string;
                members: Record<string, LayerRole>;
            }) => updateLayerMembership(layerId, members),
            // Optimistically reflect the new roles so the drawer updates on selection — the
            // members read endpoint can lag the write, so we don't wait for the refetch.
            onMutate: async ({ layerId, members }) => {
                const queryKey = layerQueries.members(layerId).queryKey;
                await queryClient.cancelQueries({ queryKey });
                const previous = queryClient.getQueryData<LayerUser[]>(queryKey);
                queryClient.setQueryData<LayerUser[]>(queryKey, (old) =>
                    old?.map((m) => (members[m.userId] ? { ...m, role: members[m.userId] } : m)),
                );
                return { queryKey, previous };
            },
            onError: (_err, _vars, context) => {
                if (context) queryClient.setQueryData(context.queryKey, context.previous);
            },
            onSettled: (_data, _err, { layerId, members }) => {
                queryClient.invalidateQueries({ queryKey: layerQueries.members(layerId).queryKey });
                // Each affected user's "assigned layers" role may have changed too.
                for (const userId of Object.keys(members)) {
                    queryClient.invalidateQueries({
                        queryKey: userQueries.assignedLayers(userId).queryKey,
                    });
                }
                // Layer lists carry each viewer's MyLayerInfo.role (drives owner gating
                // and the drawer's role snapshot) — refresh so it reflects the change.
                queryClient.invalidateQueries({ queryKey: layerQueries.lists() });
            },
        }),
    clone: (queryClient: QueryClient) =>
        mutationOptions({
            mutationKey: [...layerQueries.all, "clone"],
            mutationFn: ({ layerId, request }: { layerId: string; request: CloneLayerRequest }) =>
                cloneLayer(layerId, request),
            onSuccess: () => {
                queryClient.invalidateQueries({ queryKey: layerQueries.lists() });
                queryClient.invalidateQueries({ queryKey: layerDirectoryQueries.lists() });
            },
            // CLONED_TO and CREATED_FROM_CLONE land on both layers; a clone started from a modal
            // leaves the History section mounted, so its mount refetch will not cover them.
            onSettled: () => queryClient.invalidateQueries({ queryKey: auditQueries.all }),
        }),
    assignGroup: (queryClient: QueryClient) =>
        mutationOptions({
            mutationKey: [...layerQueries.all, "assignGroup"],
            mutationFn: ({
                layerId,
                groupId,
                role,
            }: {
                layerId: string;
                groupId: string;
                role: LayerRole;
            }) => assignGroupToLayer(groupId, layerId, role),
            onSuccess: (_, { layerId, groupId }) => {
                queryClient.invalidateQueries({
                    queryKey: layerQueries.userGroups(layerId).queryKey,
                });
                queryClient.invalidateQueries({
                    queryKey: groupQueries.groupLayers(groupId).queryKey,
                });
            },
        }),
    removeGroup: (queryClient: QueryClient) =>
        mutationOptions({
            mutationKey: [...layerQueries.all, "removeGroup"],
            mutationFn: ({ layerId, groupId }: { layerId: string; groupId: string }) =>
                removeUserGroupFromLayer(groupId, layerId),
            onSuccess: (_, { layerId, groupId }) => {
                queryClient.invalidateQueries({
                    queryKey: layerQueries.userGroups(layerId).queryKey,
                });
                queryClient.invalidateQueries({
                    queryKey: groupQueries.groupLayers(groupId).queryKey,
                });
            },
        }),
};
