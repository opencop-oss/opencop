// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { type QueryClient, mutationOptions, queryOptions } from "@tanstack/react-query";
import {
    getTiles,
    getAdminTiles,
    getTileMembers,
    getTileUserGroups,
    assignTileMembers,
    assignTileUserGroups,
    revokeTileMembers,
    revokeTileUserGroups,
    getTile,
    createTile,
    updateTile,
    makeTilePrimary,
    deleteTile,
} from "@opencop/api";
import type { CreateMapTile, UpdateMapTile } from "@opencop/api";
import { fetchWmsTimeDimension, fetchKvpWmtsTimeDimension } from "./wms-capabilities.util";
export type { WmsTimeDimension } from "./wms-capabilities.util";

export const tileQueries = {
    all: ["tiles"] as const,
    lists: () => [...tileQueries.all, "list"] as const,
    /** Operator-facing catalog. The server withholds disabled tiles. */
    list: () =>
        queryOptions({
            queryKey: [...tileQueries.lists(), "all"] as const,
            queryFn: ({ signal }) => getTiles({ signal }),
        }),
    /** Full catalog including disabled tiles. Admin surfaces only, 403s for everyone else. */
    adminList: () =>
        queryOptions({
            queryKey: [...tileQueries.lists(), "admin"] as const,
            queryFn: ({ signal }) => getAdminTiles({ signal }),
        }),
    details: () => [...tileQueries.all, "detail"] as const,
    detail: (id: string) =>
        queryOptions({
            queryKey: [...tileQueries.details(), id] as const,
            queryFn: ({ signal }) => getTile(id, { signal }),
            enabled: !!id,
        }),
    /** Users who can see a tile. */
    members: (id: string) =>
        queryOptions({
            queryKey: [...tileQueries.details(), id, "members"] as const,
            queryFn: ({ signal }) => getTileMembers(id, { signal }),
            enabled: !!id,
        }),
    /** User groups whose members can see a tile. */
    userGroups: (id: string) =>
        queryOptions({
            queryKey: [...tileQueries.details(), id, "userGroups"] as const,
            queryFn: ({ signal }) => getTileUserGroups(id, { signal }),
            enabled: !!id,
        }),
};

export const wmsCapabilitiesQueries = {
    all: ["wms-capabilities"] as const,
    timeDimension: (wmsUrl: string, layerName: string) =>
        queryOptions({
            queryKey: [...wmsCapabilitiesQueries.all, "wms", wmsUrl, layerName] as const,
            queryFn: ({ signal }) => fetchWmsTimeDimension(wmsUrl, layerName, signal),
            staleTime: Infinity,
            gcTime: 30 * 60 * 1000,
            enabled: !!wmsUrl,
            retry: false,
        }),
    kvpWmtsTimeDimension: (tileUrl: string, layerName: string) =>
        queryOptions({
            queryKey: [...wmsCapabilitiesQueries.all, "kvp-wmts", tileUrl, layerName] as const,
            queryFn: ({ signal }) => fetchKvpWmtsTimeDimension(tileUrl, layerName, signal),
            staleTime: Infinity,
            gcTime: 30 * 60 * 1000,
            enabled: !!tileUrl && !!layerName,
            retry: false,
        }),
};

export const tileMutations = {
    create: (queryClient: QueryClient) =>
        mutationOptions({
            mutationKey: [...tileQueries.all, "create"],
            mutationFn: (data: CreateMapTile) => createTile(data),
            onSuccess: () => {
                queryClient.invalidateQueries({ queryKey: tileQueries.lists() });
            },
        }),
    update: (queryClient: QueryClient) =>
        mutationOptions({
            mutationKey: [...tileQueries.all, "update"],
            mutationFn: ({ id, data }: { id: string; data: UpdateMapTile }) => updateTile(id, data),
            onSuccess: (_, { id }) => {
                queryClient.invalidateQueries({ queryKey: tileQueries.lists() });
                queryClient.invalidateQueries({ queryKey: tileQueries.detail(id).queryKey });
            },
        }),
    makePrimary: (queryClient: QueryClient) =>
        mutationOptions({
            mutationKey: [...tileQueries.all, "makePrimary"],
            mutationFn: (id: string) => makeTilePrimary(id),
            onSuccess: () => {
                queryClient.invalidateQueries({ queryKey: tileQueries.lists() });
                queryClient.invalidateQueries({ queryKey: tileQueries.details() });
            },
        }),
    delete: (queryClient: QueryClient) =>
        mutationOptions({
            mutationKey: [...tileQueries.all, "delete"],
            mutationFn: (id: string) => deleteTile(id),
            onSuccess: (_, id) => {
                queryClient.invalidateQueries({ queryKey: tileQueries.lists() });
                queryClient.removeQueries({ queryKey: tileQueries.detail(id).queryKey });
            },
        }),
};

/**
 * These invalidate the tile lists as well as the grant list, because changing who holds a grant
 * changes which tiles the server returns to that user.
 */
export const tileGrantMutations = {
    assignMembers: (queryClient: QueryClient) =>
        mutationOptions({
            mutationKey: [...tileQueries.all, "assignMembers"],
            mutationFn: ({ id, userIds }: { id: string; userIds: string[] }) =>
                assignTileMembers(id, userIds),
            onSuccess: (_, { id }) => {
                queryClient.invalidateQueries({ queryKey: tileQueries.members(id).queryKey });
                queryClient.invalidateQueries({ queryKey: tileQueries.lists() });
            },
        }),
    revokeMembers: (queryClient: QueryClient) =>
        mutationOptions({
            mutationKey: [...tileQueries.all, "revokeMembers"],
            mutationFn: ({ id, userIds }: { id: string; userIds: string[] }) =>
                revokeTileMembers(id, userIds),
            onSuccess: (_, { id }) => {
                queryClient.invalidateQueries({ queryKey: tileQueries.members(id).queryKey });
                queryClient.invalidateQueries({ queryKey: tileQueries.lists() });
            },
        }),
    assignUserGroups: (queryClient: QueryClient) =>
        mutationOptions({
            mutationKey: [...tileQueries.all, "assignUserGroups"],
            mutationFn: ({ id, userGroupIds }: { id: string; userGroupIds: string[] }) =>
                assignTileUserGroups(id, userGroupIds),
            onSuccess: (_, { id }) => {
                queryClient.invalidateQueries({ queryKey: tileQueries.userGroups(id).queryKey });
                queryClient.invalidateQueries({ queryKey: tileQueries.lists() });
            },
        }),
    revokeUserGroups: (queryClient: QueryClient) =>
        mutationOptions({
            mutationKey: [...tileQueries.all, "revokeUserGroups"],
            mutationFn: ({ id, userGroupIds }: { id: string; userGroupIds: string[] }) =>
                revokeTileUserGroups(id, userGroupIds),
            onSuccess: (_, { id }) => {
                queryClient.invalidateQueries({ queryKey: tileQueries.userGroups(id).queryKey });
                queryClient.invalidateQueries({ queryKey: tileQueries.lists() });
            },
        }),
};
