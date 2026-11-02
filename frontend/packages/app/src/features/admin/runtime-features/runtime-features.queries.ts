// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { infiniteQueryOptions, mutationOptions, type QueryClient } from "@tanstack/react-query";
import {
    getRuntimeFeatureHistory,
    patchRuntimeFeatureAudience,
    reconcileRuntimeFeatures,
    type Page,
    type RuntimeFeatureAudiencePatch,
    type RuntimeFeatureHistoryItem,
} from "@opencop/api";
import { appMetadataQueries } from "~/features/admin/app-metadata.queries";

const RUNTIME_FEATURE_HISTORY_PAGE_SIZE = 50;

export const runtimeFeatureQueries = {
    all: ["runtime-features"] as const,
    histories: () => [...runtimeFeatureQueries.all, "history"] as const,
    history: (featureName: string) =>
        infiniteQueryOptions({
            queryKey: [...runtimeFeatureQueries.histories(), featureName] as const,
            queryFn: ({ pageParam, signal }) =>
                getRuntimeFeatureHistory(
                    { featureName, page: pageParam, size: RUNTIME_FEATURE_HISTORY_PAGE_SIZE },
                    { signal },
                ),
            initialPageParam: 0,
            getNextPageParam: (lastPage: Page<RuntimeFeatureHistoryItem>) => {
                const next = lastPage.page + 1;
                return next < lastPage.totalPages ? next : undefined;
            },
            staleTime: 30_000,
        }),
};

/**
 * Invalidates both views of Runtime Feature state after an attempted write.
 *
 * Core can commit the feature row and its audit row before the HTTP response reaches this browser.
 * A lost or unreadable response therefore makes the outcome unknown even though TanStack reports
 * an error. Active observers refetch immediately; an inactive history refetches when next opened.
 * We deliberately pay for up to two low-frequency admin reads on every settled outcome, including
 * a definitive 4xx, instead of maintaining an error-classification policy that could mistake a
 * proxy or transport failure for proof that nothing committed.
 */
const invalidateRuntimeFeatureState = async (
    queryClient: QueryClient,
    featureName?: string,
): Promise<void> => {
    await Promise.all([
        queryClient.invalidateQueries({
            queryKey: appMetadataQueries.detail().queryKey,
        }),
        queryClient.invalidateQueries({
            queryKey:
                featureName == null
                    ? runtimeFeatureQueries.histories()
                    : runtimeFeatureQueries.history(featureName).queryKey,
        }),
    ]);
};

/**
 * Every mutation invalidates `app-metadata`, because that query is the single source of both the
 * admin catalog (`admin.runtimeFeatures`) and the resolved `features` list the gates read. Skipping
 * it would leave a just-enabled flag reading disabled until the 60s stale time lapsed.
 */
export const runtimeFeatureMutations = {
    patchAudience: (queryClient: QueryClient) =>
        mutationOptions({
            mutationKey: [...runtimeFeatureQueries.all, "audience", "replace"],
            mutationFn: ({ name, patch }: { name: string; patch: RuntimeFeatureAudiencePatch }) =>
                patchRuntimeFeatureAudience(name, patch),
            onSettled: (_data, _error, { name }) =>
                invalidateRuntimeFeatureState(queryClient, name),
        }),

    /**
     * Names come only from the manifest.
     *
     * Invalidates on every settled outcome, not just one that created something. A run that only
     * confirms existing definitions still teaches this browser about rows another caller created,
     * so skipping the refetch would leave them reading Unsynced against a catalog that already has
     * them. A failed run is the same case for a different reason: the server may have committed an
     * earlier prefix before it stopped, so the cached catalog is of unknown accuracy either way.
     */
    reconcile: (queryClient: QueryClient) =>
        mutationOptions({
            mutationKey: [...runtimeFeatureQueries.all, "reconcile"],
            mutationFn: (names: readonly string[]) => reconcileRuntimeFeatures(names),
            onSettled: () => invalidateRuntimeFeatureState(queryClient),
        }),
};
