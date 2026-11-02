// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useCallback } from "react";

interface UseBulkActionReturn<T> {
    handleBulkAction: () => Promise<{
        results: PromiseSettledResult<Awaited<T>>[];
        failures: number;
    }>;
}

/**
 * Fans a per-id action out over an explicit id list and clears the selection afterwards.
 *
 * The ids come from the caller's resolved selection rather than a raw `rowSelection` record, so
 * what this acts on is exactly what the toolbar counted and what any sibling action would target.
 */
export function useBulkAction<T>(
    action: (id: string) => Promise<T>,
    ids: readonly string[],
    resetSelection: () => void,
): UseBulkActionReturn<T> {
    const handleBulkAction = useCallback(async () => {
        const results = await Promise.allSettled(ids.map((id) => action(id)));
        resetSelection();
        return { results, failures: results.filter((r) => r.status === "rejected").length };
    }, [ids, action, resetSelection]);

    return { handleBulkAction };
}
