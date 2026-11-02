// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useEffect, useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import type { MapTile } from "@opencop/api";
import { tileQueries } from "./imagery.queries";

/**
 * Filters the tile list by `predicate` and calls `reconcile` with the matching IDs
 * whenever the list loads or changes. Pass stable function references for both arguments.
 */
export function useTileReconcile(
    predicate: (tile: MapTile) => boolean,
    reconcile: (ids: string[]) => void,
) {
    const { data: allTiles = [], isLoading, isError } = useQuery(tileQueries.list());
    const tileIds = useMemo(
        () => allTiles.filter(predicate).map((t) => t.id),
        [allTiles, predicate],
    );
    useEffect(() => {
        if (isLoading || isError) return;
        reconcile(tileIds);
    }, [tileIds, isLoading, isError, reconcile]);
}
