// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useQueries } from "@tanstack/react-query";
import { useCallback, useMemo } from "react";
import { tileQueries } from "./imagery.queries";

export function useTileTokens(tileIds: string[], enabled: boolean): Map<string, string | null> {
    const stableKey = useMemo(() => tileIds.join(","), [tileIds]);
    const combineTokens = useCallback(
        (results: Array<{ data?: { token?: string | null } }>) =>
            new Map(tileIds.map((id, i) => [id, results[i]?.data?.token ?? null])),
        // `tileIds` is intentionally omitted — it's a new array reference on every render.
        // `stableKey` (its join) only changes when the contents change, preventing all
        // useQueries subscriptions from re-mounting unnecessarily on each render.
        // eslint-disable-next-line react-hooks/exhaustive-deps
        [stableKey],
    );

    return useQueries({
        queries: tileIds.map((id) => ({
            ...tileQueries.detail(id),
            enabled,
        })),
        combine: combineTokens,
    });
}
