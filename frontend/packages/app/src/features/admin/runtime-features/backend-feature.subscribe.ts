// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { QueryObserver, type QueryClient } from "@tanstack/react-query";
import type { AppMetadata } from "@opencop/api";
import { queryClient as appQueryClient } from "~/queryClient";
import { appMetadataQueries } from "~/features/admin/app-metadata.queries";
import { isRuntimeFeatureEnabled } from "~/features/admin/app-metadata-state";
import type { BackendFeatureId } from "./runtime-features.manifest";

/**
 * The non-hook twin of {@link useBackendFeatureEnabled}: whether a
 * backend-owned feature is available to this caller, for code with no React
 * lifecycle to hang a hook on (a feature installer deciding whether its tool
 * exists).
 *
 * Same fail-closed reading as the hook. A query with nothing to read from —
 * pending, or errored before it ever resolved — reads as disabled; a background
 * refetch failure keeps the last-known-good answer. `onChange` is called with
 * the current answer immediately and then on every change of it, never with
 * the same value twice in a row. The observer keeps the metadata query alive
 * for as long as the subscription lasts, with the query's own `staleTime`.
 */
export function subscribeBackendFeature(
    id: BackendFeatureId,
    onChange: (enabled: boolean) => void,
    client: QueryClient = appQueryClient,
): () => void {
    const observer = new QueryObserver(client, appMetadataQueries.detail());
    let last: boolean | undefined;

    const emit = (data: AppMetadata | null | undefined) => {
        const next = data == null ? false : isRuntimeFeatureEnabled(data.features, id);
        if (next === last) return;
        last = next;
        onChange(next);
    };

    emit(observer.getCurrentResult().data);
    return observer.subscribe((result) => emit(result.data));
}
