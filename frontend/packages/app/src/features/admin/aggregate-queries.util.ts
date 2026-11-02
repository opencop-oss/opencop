// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

interface QuerySnapshot {
    isLoading: boolean;
    isFetching: boolean;
    refetch: () => Promise<unknown>;
}

interface AggregatedQueries {
    isLoading: boolean;
    isFetching: boolean;
    refetchAll: () => Promise<PromiseSettledResult<unknown>[]>;
}

export function aggregateQueries(queries: readonly QuerySnapshot[]): AggregatedQueries {
    const isLoading = queries.some((q) => q.isLoading);
    const isFetching = queries.some((q) => q.isFetching) && !isLoading;
    const refetchAll = () => Promise.allSettled(queries.map((q) => q.refetch()));
    return { isLoading, isFetching, refetchAll };
}
