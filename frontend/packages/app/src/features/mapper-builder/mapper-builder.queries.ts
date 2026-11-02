// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { type QueryClient, mutationOptions } from "@tanstack/react-query";
import { createMapperProfile, updateMapperProfile, previewMapper } from "@opencop/api";
import type {
    MapperProfileFullMetaRequest,
    MapperProfilePatch,
    PreviewMappingRequest,
} from "@opencop/api";
import { profileQueries } from "~/features/data-sources/data-source-profiles.queries";

export const mapperBuilderMutations = {
    create: (queryClient: QueryClient) =>
        mutationOptions({
            mutationKey: [...profileQueries.all, "create-mapper"],
            mutationFn: (meta: MapperProfileFullMetaRequest) => createMapperProfile(meta),
            onSuccess: () => {
                void queryClient.invalidateQueries({ queryKey: profileQueries.all });
            },
        }),

    update: (queryClient: QueryClient) =>
        mutationOptions({
            mutationKey: [...profileQueries.all, "update-mapper"],
            mutationFn: (meta: MapperProfilePatch) => updateMapperProfile(meta),
            onSuccess: () => {
                void queryClient.invalidateQueries({ queryKey: profileQueries.all });
            },
        }),

    preview: () =>
        mutationOptions({
            mutationKey: ["mapper-builder", "preview"],
            mutationFn: (request: PreviewMappingRequest) => previewMapper(request),
        }),
};
