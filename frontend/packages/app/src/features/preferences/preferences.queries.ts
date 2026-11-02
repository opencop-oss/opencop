// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { type QueryClient, mutationOptions, queryOptions } from "@tanstack/react-query";
import {
    getPreferences,
    patchPreferences,
    uploadAvatar,
    deleteAvatar,
    type UserPreferencesPatch,
    type UserPreferences,
    type MapCaptureResponse,
    type MapTile,
} from "@opencop/api";
import { tileQueries } from "~/features/imagery/imagery.queries";
import { mapCaptureQueries } from "~/features/mapcapture/mapcapture.queries";
import { createThumbnail } from "@opencop/ui";

function resolveOptimisticInitialView(
    previousPreferences: UserPreferences,
    selectedId: string | null | undefined,
    availableCaptures: MapCaptureResponse[] | undefined,
) {
    if (selectedId === undefined) return previousPreferences.initialView;
    if (selectedId === null) return null;

    if (previousPreferences.initialView?.id === selectedId) {
        return previousPreferences.initialView;
    }

    return (
        availableCaptures?.find((capture) => capture.id === selectedId) ??
        previousPreferences.initialView
    );
}

function resolveOptimisticDefaultTile(
    previousPreferences: UserPreferences,
    selectedId: string | null | undefined,
    availableTiles: MapTile[] | undefined,
) {
    if (selectedId === undefined) return previousPreferences.defaultTile;
    if (selectedId === null) return null;

    if (previousPreferences.defaultTile?.id === selectedId) {
        return previousPreferences.defaultTile;
    }

    return (
        availableTiles?.find((tile) => tile.id === selectedId) ?? previousPreferences.defaultTile
    );
}

export const preferencesQueries = {
    all: ["preferences"] as const,
    current: () =>
        queryOptions({
            queryKey: [...preferencesQueries.all, "current"] as const,
            queryFn: ({ signal }) => getPreferences({ signal }),
            staleTime: 5 * 60 * 1000, // 5 minutes
        }),
};

export const preferencesMutations = {
    update: (queryClient: QueryClient) =>
        mutationOptions({
            mutationKey: [...preferencesQueries.all, "update"],
            mutationFn: (data: UserPreferencesPatch) => patchPreferences(data),
            onMutate: async (data: UserPreferencesPatch) => {
                // Cancel any outgoing refetches
                await queryClient.cancelQueries({
                    queryKey: preferencesQueries.current().queryKey,
                });

                // Snapshot the previous value
                const previousPreferences = queryClient.getQueryData<UserPreferences>(
                    preferencesQueries.current().queryKey,
                );

                // Optimistically update to the new value
                if (previousPreferences) {
                    const simplePreferenceUpdates: Partial<UserPreferences> = {
                        ...(data.username !== undefined && { username: data.username }),
                        ...(data.locale !== undefined && { locale: data.locale }),
                        ...(data.measurementSystem !== undefined && {
                            measurementSystem: data.measurementSystem,
                        }),
                        ...(data.temperatureUnit !== undefined && {
                            temperatureUnit: data.temperatureUnit,
                        }),
                        ...(data.timezone !== undefined && { timezone: data.timezone }),
                        ...(data.zoomBehavior !== undefined && {
                            zoomBehavior: data.zoomBehavior,
                        }),
                    };
                    const availableCaptures = queryClient.getQueryData<MapCaptureResponse[]>(
                        mapCaptureQueries.list().queryKey,
                    );
                    const availableTiles = queryClient.getQueryData<MapTile[]>(
                        tileQueries.list().queryKey,
                    );

                    const optimisticInitialView = resolveOptimisticInitialView(
                        previousPreferences,
                        data.initialView,
                        availableCaptures,
                    );
                    const optimisticDefaultTile = resolveOptimisticDefaultTile(
                        previousPreferences,
                        data.defaultTileId,
                        availableTiles,
                    );

                    const optimisticData = {
                        ...previousPreferences,
                        ...simplePreferenceUpdates,
                        initialView: optimisticInitialView,
                        defaultTile: optimisticDefaultTile,
                    };

                    queryClient.setQueryData(preferencesQueries.current().queryKey, optimisticData);
                }

                return { previousPreferences };
            },
            onError: (_err, _data, context) => {
                // Rollback to previous value on error
                if (context?.previousPreferences) {
                    queryClient.setQueryData(
                        preferencesQueries.current().queryKey,
                        context.previousPreferences,
                    );
                }
            },
            onSuccess: (updatedPreferences) => {
                // Update the cache with server response
                queryClient.setQueryData(preferencesQueries.current().queryKey, updatedPreferences);
            },
        }),
    avatar: {
        upload: (queryClient: QueryClient) =>
            mutationOptions({
                mutationKey: [...preferencesQueries.all, "avatar", "upload"],
                mutationFn: async (file: File) => {
                    const thumbnail = await createThumbnail(file, 64);
                    return uploadAvatar(file, thumbnail ? [thumbnail] : []);
                },
                onSuccess: (updatedPreferences) => {
                    // Update the entire preferences cache with server response
                    queryClient.setQueryData(
                        preferencesQueries.current().queryKey,
                        updatedPreferences,
                    );
                },
            }),
        delete: (queryClient: QueryClient) =>
            mutationOptions({
                mutationKey: [...preferencesQueries.all, "avatar", "delete"],
                mutationFn: () => deleteAvatar(),
                onMutate: async () => {
                    // Cancel any outgoing refetches
                    await queryClient.cancelQueries({
                        queryKey: preferencesQueries.current().queryKey,
                    });

                    // Snapshot the previous value
                    const previousPreferences = queryClient.getQueryData<UserPreferences>(
                        preferencesQueries.current().queryKey,
                    );

                    // Optimistically remove avatar
                    if (previousPreferences) {
                        queryClient.setQueryData(preferencesQueries.current().queryKey, {
                            ...previousPreferences,
                            avatarMediaId: null,
                            avatar: null,
                        });
                    }

                    return { previousPreferences };
                },
                onError: (_err, _data, context) => {
                    // Rollback on error
                    if (context?.previousPreferences) {
                        queryClient.setQueryData(
                            preferencesQueries.current().queryKey,
                            context.previousPreferences,
                        );
                    }
                },
                onSuccess: (updatedPreferences) => {
                    // Update with server response
                    queryClient.setQueryData(
                        preferencesQueries.current().queryKey,
                        updatedPreferences,
                    );
                },
            }),
    },
};
