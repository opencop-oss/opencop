// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { type QueryClient, mutationOptions, queryOptions } from "@tanstack/react-query";
import {
    createMapCapture,
    deleteMapCapture,
    getMapCapture,
    getMapCaptures,
    patchMapCapture,
} from "@opencop/api";
import type { MapCaptureCreate, MapCapturePatch } from "@opencop/api";

export const mapCaptureQueries = {
    all: ["mapCaptures"] as const,
    lists: () => [...mapCaptureQueries.all, "list"] as const,
    list: () =>
        queryOptions({
            queryKey: [...mapCaptureQueries.lists(), "all"] as const,
            queryFn: ({ signal }) => getMapCaptures({ signal }),
        }),
    details: () => [...mapCaptureQueries.all, "detail"] as const,
    detail: (id: string) =>
        queryOptions({
            queryKey: [...mapCaptureQueries.details(), id] as const,
            queryFn: ({ signal }) => getMapCapture(id, { signal }),
            enabled: !!id,
        }),
};

export const mapCaptureMutations = {
    create: (queryClient: QueryClient) =>
        mutationOptions({
            mutationKey: [...mapCaptureQueries.all, "create"],
            mutationFn: (data: MapCaptureCreate) => createMapCapture(data),
            onSuccess: () => {
                queryClient.invalidateQueries({ queryKey: mapCaptureQueries.lists() });
            },
        }),
    update: (queryClient: QueryClient) =>
        mutationOptions({
            mutationKey: [...mapCaptureQueries.all, "update"],
            mutationFn: ({ id, data }: { id: string; data: MapCapturePatch }) =>
                patchMapCapture(id, data),
            onSuccess: (_, { id }) => {
                queryClient.invalidateQueries({ queryKey: mapCaptureQueries.lists() });
                queryClient.invalidateQueries({ queryKey: mapCaptureQueries.detail(id).queryKey });
            },
        }),
    delete: (queryClient: QueryClient) =>
        mutationOptions({
            mutationKey: [...mapCaptureQueries.all, "delete"],
            mutationFn: (id: string) => deleteMapCapture(id),
            onSuccess: (_, id) => {
                queryClient.invalidateQueries({ queryKey: mapCaptureQueries.lists() });
                queryClient.removeQueries({ queryKey: mapCaptureQueries.detail(id).queryKey });
            },
        }),
};
