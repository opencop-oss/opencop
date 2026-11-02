// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { MutationCache, QueryCache, QueryClient } from "@tanstack/react-query";
import Logger from "js-logger";
import {
    ApiError,
    AUTH_QUERY_KEY,
    cacheUser,
    isNonRetryableAuthError,
    updateLastActivity,
} from "@opencop/api";
import type { AuthUser } from "@opencop/api";

const logger = Logger.get("query");

/** Default retry count matching React Query's client-side default */
const DEFAULT_QUERY_RETRY_COUNT = 3;

/** Check if a query is the auth session query */
function isAuthQuery(query: { queryKey: readonly unknown[] }): boolean {
    return (
        query.queryKey.length === AUTH_QUERY_KEY.length &&
        query.queryKey.every((k, i) => k === AUTH_QUERY_KEY[i])
    );
}

export const queryClient = new QueryClient({
    defaultOptions: {
        queries: {
            staleTime: 60_000, // 1 minute — prevents unnecessary refetches on mount/tab-focus
            retry: (failureCount, error) =>
                !isNonRetryableAuthError(error) && failureCount < DEFAULT_QUERY_RETRY_COUNT,
        },
    },
    queryCache: new QueryCache({
        onSuccess: (data, query) => {
            // Track activity on any successful API call
            updateLastActivity();

            // Cache user data on successful auth fetch (keeps TTL fresh)
            if (isAuthQuery(query)) {
                cacheUser(data as AuthUser);
            }
        },
        onError: (error, query) => {
            if (error instanceof ApiError && error.status === 401) {
                // Skip invalidation if this IS the auth query (prevents infinite loop)
                if (isAuthQuery(query)) return;

                // Invalidate auth query - triggers AuthProvider to refetch and handle 401
                queryClient.invalidateQueries({ queryKey: AUTH_QUERY_KEY });
                return;
            }

            // Expected, benign 404: a query tagged `expectedReadModelLag404` (the event
            // detail query) can 404 right after a broadcast *create* because the queryable
            // read-model lags the write via an async Kafka projection. The panel renders
            // from the optimistic element until the row lands, so this is not a real
            // failure — recognise it here (and note it in DEV to separate it from a genuine
            // 404) instead of letting it read as an error. The browser still logs the
            // network 404; this is the code path that documents and detects
            // that it is expected.
            if (
                error instanceof ApiError &&
                error.status === 404 &&
                query.meta?.expectedReadModelLag404 === true
            ) {
                if (import.meta.env.DEV) {
                    logger.debug(
                        "[events] details/latest 404 is expected right after a broadcast create — " +
                            "read-model lags the write (async projection); rendering from the " +
                            "optimistic element until it lands.",
                    );
                }
            }
        },
    }),
    mutationCache: new MutationCache({
        onError: (error) => {
            if (error instanceof ApiError && error.status === 401) {
                queryClient.invalidateQueries({ queryKey: AUTH_QUERY_KEY });
            }
        },
    }),
});
// This code is only for TypeScript
declare global {
    interface Window {
        __TANSTACK_QUERY_CLIENT__: import("@tanstack/query-core").QueryClient;
    }
}

// This code is for all users
window.__TANSTACK_QUERY_CLIENT__ = queryClient;
