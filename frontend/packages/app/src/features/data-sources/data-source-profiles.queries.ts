// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { mutationOptions, QueryClient, queryOptions } from "@tanstack/react-query";
import {
    getMapperProfileFullMeta,
    getMapperProfileVersions,
    getEgressMapperProfiles,
    getMapperProfiles,
    getMapperProfilesAdmin,
    deleteMapperProfile,
} from "@opencop/api";

export const profileQueries = {
    all: ["profiles"] as const,

    mapperProfileList: () =>
        queryOptions({
            queryKey: [...profileQueries.all, "mapper", "list"] as const,
            queryFn: ({ signal }) => getMapperProfiles({ signal }),
        }),
    egressMapperProfileList: () =>
        queryOptions({
            queryKey: [...profileQueries.all, "mapper", "list", "egress"] as const,
            queryFn: ({ signal }) => getEgressMapperProfiles({ signal }),
        }),
    mapperProfileAdminDetail: (id: string) =>
        queryOptions({
            queryKey: [...profileQueries.all, "mapper", "admin-detail", id] as const,
            queryFn: ({ signal }) => getMapperProfileFullMeta(id, { signal }),
            enabled: !!id,
        }),
    mapperProfileAdminList: () =>
        queryOptions({
            queryKey: [...profileQueries.all, "mapper", "admin-list"] as const,
            queryFn: ({ signal }) => getMapperProfilesAdmin({ signal }),
        }),
    mapperProfileVersions: (id: string) =>
        queryOptions({
            queryKey: [...profileQueries.all, "mapper", "versions", id] as const,
            queryFn: ({ signal }) => getMapperProfileVersions(id, { signal }),
            enabled: !!id,
        }),
};

export const profileMutations = {
    deleteMapperProfile: (queryClient: QueryClient) =>
        mutationOptions({
            mutationKey: [...profileQueries.all, "mapper", "delete"],
            mutationFn: ({ id }: { id: string }) => deleteMapperProfile(id),
            onSuccess: () =>
                queryClient.invalidateQueries({ queryKey: [...profileQueries.all, "mapper"] }),
        }),
};
