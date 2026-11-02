// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useCallback } from "react";
import { runtimeFeatureMutations } from "./runtime-features.queries";

/**
 * The single entry point into flag reconciliation.
 *
 * Registration is a durable catalog write, so it is never triggered as a side effect of loading a
 * page — an admin opening the app should not silently mutate the environment's catalog. The drift
 * alert reports what is missing and this runs only when they choose to.
 *
 * The server owns the per-name loop and reports per-name outcomes, so there is nothing to sequence
 * here: one request, one result. Because there is exactly one trigger, "did we already reconcile?"
 * needs no persisted marker — drift is recomputed from the live catalog on every render and
 * disappears when the catalog catches up.
 */
export function useRuntimeFeatureSync() {
    const queryClient = useQueryClient();
    const mutation = useMutation(runtimeFeatureMutations.reconcile(queryClient));

    const { mutateAsync } = mutation;
    const sync = useCallback((names: readonly string[]) => mutateAsync(names), [mutateAsync]);

    return { sync, isSyncing: mutation.isPending };
}
