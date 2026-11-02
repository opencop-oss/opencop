// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { mutationOptions, queryOptions } from "@tanstack/react-query";
import type { QueryClient } from "@tanstack/react-query";
import { getLayer, mergeLayers, validateMerge } from "@opencop/api";
import type { LayerInfo, MergeRequest, MergeValidateRequest } from "@opencop/api";
import { layerQueries } from "./layers.queries";
import { layerDirectoryQueries } from "./layer-directory.queries";

export const MERGE_POLL_INTERVAL_MS = 3000;

function getMergeLifecycleState(layer?: Pick<LayerInfo, "lifecycleState" | "status">) {
    return layer?.lifecycleState ?? layer?.status;
}

function isTerminalMergeState(state?: string) {
    return state === "ACTIVE" || state === "MERGE_FAILED";
}

function delay(ms: number) {
    return new Promise<void>((resolve) => setTimeout(resolve, ms));
}

async function invalidateListsWhenMergeSettles(queryClient: QueryClient, layerId: string) {
    while (true) {
        try {
            const layer = await queryClient.fetchQuery(mergeQueries.pollTarget(layerId));
            if (isTerminalMergeState(getMergeLifecycleState(layer))) {
                await queryClient.invalidateQueries({ queryKey: layerQueries.lists() });
                await queryClient.invalidateQueries({ queryKey: layerDirectoryQueries.lists() });
                return;
            }
        } catch {
            // Keep polling so a transient read failure does not strand the layer lists.
        }

        await delay(MERGE_POLL_INTERVAL_MS);
    }
}

export const mergeQueries = {
    pollTarget: (id: string) =>
        queryOptions({
            queryKey: [...layerQueries.details(), id, "merge-poll"] as const,
            queryFn: ({ signal }) => getLayer(id, { signal }),
            staleTime: 0,
            refetchInterval: (query) => {
                const state = getMergeLifecycleState(query.state.data);
                return state === "PENDING_MERGE" ? MERGE_POLL_INTERVAL_MS : false;
            },
            enabled: !!id,
        }),
};

export const mergeMutations = {
    validate: () =>
        mutationOptions({
            mutationKey: [...layerQueries.all, "merge", "validate"] as const,
            mutationFn: (request: MergeValidateRequest) => validateMerge(request),
        }),
    merge: (queryClient: QueryClient) =>
        mutationOptions({
            mutationKey: [...layerQueries.all, "merge", "commit"] as const,
            mutationFn: (request: MergeRequest) => mergeLayers(request),
            onSuccess: (result) => {
                // Reflect the in-progress merge in the directory grid immediately (the source
                // layers enter a merging state); the settle poll refreshes again on completion.
                queryClient.invalidateQueries({ queryKey: layerDirectoryQueries.lists() });
                void invalidateListsWhenMergeSettles(queryClient, result.targetLayer.id);
            },
        }),
};
