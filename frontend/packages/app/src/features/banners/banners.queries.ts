// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { type QueryClient, mutationOptions, queryOptions } from "@tanstack/react-query";
import {
    createBanner,
    createBannerTranslation,
    deleteBanner,
    deleteBannerTranslation,
    getBanner,
    getBanners,
    getBannerTranslations,
    patchBanner,
    patchBannerTranslation,
} from "@opencop/api";
import type {
    BannerCreate,
    BannerPatch,
    BannerTranslationCreate,
    BannerTranslationPatch,
} from "@opencop/api";

export const bannerQueries = {
    all: ["banners"] as const,
    lists: () => [...bannerQueries.all, "list"] as const,
    list: () =>
        queryOptions({
            queryKey: [...bannerQueries.lists(), "all"] as const,
            queryFn: ({ signal }) => getBanners({ signal }),
        }),
    details: () => [...bannerQueries.all, "detail"] as const,
    detail: (id: string) =>
        queryOptions({
            queryKey: [...bannerQueries.details(), id] as const,
            queryFn: ({ signal }) => getBanner(id, { signal }),
            enabled: !!id,
        }),
    translations: (bannerId: string) =>
        queryOptions({
            queryKey: [...bannerQueries.all, "translations", bannerId] as const,
            queryFn: ({ signal }) => getBannerTranslations(bannerId, { signal }),
            enabled: !!bannerId,
        }),
};

export const bannerMutations = {
    create: (queryClient: QueryClient) =>
        mutationOptions({
            mutationKey: [...bannerQueries.all, "create"],
            mutationFn: (data: BannerCreate) => createBanner(data),
            onSuccess: () => {
                queryClient.invalidateQueries({ queryKey: bannerQueries.lists() });
            },
        }),
    update: (queryClient: QueryClient) =>
        mutationOptions({
            mutationKey: [...bannerQueries.all, "update"],
            mutationFn: ({ id, data }: { id: string; data: BannerPatch }) => patchBanner(id, data),
            onSuccess: (_, { id }) => {
                queryClient.invalidateQueries({ queryKey: bannerQueries.lists() });
                queryClient.invalidateQueries({ queryKey: bannerQueries.detail(id).queryKey });
            },
        }),
    delete: (queryClient: QueryClient) =>
        mutationOptions({
            mutationKey: [...bannerQueries.all, "delete"],
            mutationFn: (id: string) => deleteBanner(id),
            onSuccess: (_, id) => {
                queryClient.invalidateQueries({ queryKey: bannerQueries.lists() });
                queryClient.removeQueries({ queryKey: bannerQueries.detail(id).queryKey });
            },
        }),
    createTranslation: (queryClient: QueryClient) =>
        mutationOptions({
            mutationKey: [...bannerQueries.all, "createTranslation"],
            mutationFn: ({ bannerId, data }: { bannerId: string; data: BannerTranslationCreate }) =>
                createBannerTranslation(bannerId, data),
            onSuccess: (_, { bannerId }) => {
                queryClient.invalidateQueries({ queryKey: bannerQueries.lists() });
                queryClient.invalidateQueries({
                    queryKey: bannerQueries.detail(bannerId).queryKey,
                });
            },
        }),
    patchTranslation: (queryClient: QueryClient) =>
        mutationOptions({
            mutationKey: [...bannerQueries.all, "patchTranslation"],
            mutationFn: ({
                bannerId,
                translationId,
                data,
            }: {
                bannerId: string;
                translationId: string;
                data: BannerTranslationPatch;
            }) => patchBannerTranslation(bannerId, translationId, data),
            onSuccess: (_, { bannerId }) => {
                queryClient.invalidateQueries({ queryKey: bannerQueries.lists() });
                queryClient.invalidateQueries({
                    queryKey: bannerQueries.detail(bannerId).queryKey,
                });
            },
        }),
    deleteTranslation: (queryClient: QueryClient) =>
        mutationOptions({
            mutationKey: [...bannerQueries.all, "deleteTranslation"],
            mutationFn: ({
                bannerId,
                translationId,
            }: {
                bannerId: string;
                translationId: string;
            }) => deleteBannerTranslation(bannerId, translationId),
            onSuccess: (_, { bannerId }) => {
                queryClient.invalidateQueries({ queryKey: bannerQueries.lists() });
                queryClient.invalidateQueries({
                    queryKey: bannerQueries.detail(bannerId).queryKey,
                });
            },
        }),
};
