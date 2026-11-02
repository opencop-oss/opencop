// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { Page } from "@opencop/api";
import {
    type InfiniteData,
    type QueryKey,
    type UseInfiniteQueryOptions,
    useInfiniteQuery,
    useQueryClient,
} from "@tanstack/react-query";
import { uniqBy } from "lodash";
import { type RefObject, useCallback, useEffect, useMemo, useRef, useState } from "react";

/** How the one recovery action reads: a drift refresh, or a retry of one that failed. */
type PagingRecoveryState = "idle" | "pending" | "failed";

/**
 * The options object an `infiniteQueryOptions(...)` call produces for one offset-paged history.
 *
 * Named here so a history reader can pass its query in as data rather than re-deriving the page
 * sequence; the page param is the zero-based offset page number every such endpoint takes.
 */
type HistoryQueryOptions<TItem, TQueryKey extends QueryKey = QueryKey> = UseInfiniteQueryOptions<
    Page<TItem>,
    Error,
    InfiniteData<Page<TItem>>,
    TQueryKey,
    number
>;

export interface AuditHistoryPaging<TItem> {
    /** Rows in backend order, de-duplicated by id. */
    items: TItem[];
    /** The largest total any loaded page reported, floored at the number of rows in hand. */
    displayedTotal: number;
    isPending: boolean;
    /** The initial load failed and there is nothing to show — not "no history". */
    isUnavailable: boolean;
    isRefetching: boolean;
    isRefetchError: boolean;
    isFetchingNextPage: boolean;
    isFetchNextPageError: boolean;
    hasNextPage: boolean;
    /** Whether to offer the drift-recovery action in place of the paging action. */
    showPagingRecovery: boolean;
    pagingRecoveryState: PagingRecoveryState;
    refetch: () => void;
    /** The button path: loads the next page, then moves focus to the surviving control. */
    loadNextPage: () => void;
    /** Loads the next page without moving focus, for loads the reader did not click for. */
    autoLoadNextPage: () => void;
    restartFromFirstPage: () => void;
    /** Attach to the recovery button, the paging button and the loaded-count text. */
    refreshButtonRef: RefObject<HTMLButtonElement | null>;
    pagingButtonRef: RefObject<HTMLButtonElement | null>;
    loadedCountRef: RefObject<HTMLParagraphElement | null>;
}

/**
 * Offset paging over a durable history, with drift detection, recovery and focus management.
 *
 * Rows shift under an offset sequence when new ones are written mid-paging, and a disabled loading
 * button drops focus to the body; both are handled here once rather than per reader.
 *
 * @param query the history's `infiniteQueryOptions`, whose page param is the offset page number
 */
export function useAuditHistoryPaging<TItem extends { id: number }, TQueryKey extends QueryKey>(
    query: HistoryQueryOptions<TItem, TQueryKey>,
): AuditHistoryPaging<TItem> {
    const queryClient = useQueryClient();
    // Read as a plain, untagged key: `infiniteQueryOptions` tags its key with the data type, and
    // that tag is erased behind this hook's generic, so the cache helpers below must be told the
    // page shape explicitly rather than inferring it.
    const queryKey: QueryKey = query.queryKey;
    const {
        data,
        fetchNextPage,
        hasNextPage,
        isError,
        isFetchNextPageError,
        isFetchingNextPage,
        isPending,
        isRefetchError,
        isRefetching,
        refetch,
    } = useInfiniteQuery(query);
    const [focusRequest, setFocusRequest] = useState<{
        id: number;
        target: "paging-result" | "loaded-count";
    } | null>(null);
    const [pagingRecoveryState, setPagingRecoveryState] = useState<PagingRecoveryState>("idle");
    const nextFocusRequestIdRef = useRef(0);
    const handledFocusRequestIdRef = useRef(0);
    const refreshButtonRef = useRef<HTMLButtonElement>(null);
    const pagingButtonRef = useRef<HTMLButtonElement>(null);
    const loadedCountRef = useRef<HTMLParagraphElement>(null);

    const pages = data?.pages ?? [];
    // Durable audit events must not be silently duplicated or omitted when new rows shift every
    // later offset. Keep the first occurrence in backend order and treat overlap, changing totals,
    // or an unsatisfied terminal count as evidence of that drift.
    // Memoized on `data` so the table downstream sees a stable row array between renders.
    const { items, rawCount } = useMemo(() => {
        const rawItems = (data?.pages ?? []).flatMap((page) => page.content);
        return { items: uniqBy(rawItems, (item) => item.id), rawCount: rawItems.length };
    }, [data]);
    const firstTotal = pages[0]?.totalElements ?? 0;
    const displayedTotal = pages.reduce(
        (maximum, page) => Math.max(maximum, page.totalElements),
        items.length,
    );
    const hasPageOverlap = rawCount !== items.length;
    const hasChangedTotal = pages.some((page) => page.totalElements !== firstTotal);
    const hasTerminalCountMismatch =
        pages.length > 0 && !hasNextPage && items.length !== displayedTotal;
    const hasPagingDrift = hasPageOverlap || hasChangedTotal || hasTerminalCountMismatch;
    // Slicing to page zero clears the derived drift condition before its network refresh settles.
    // Keep this one recovery action mounted through that interval (and through failure), both to
    // preserve its focus and to avoid presenting a second, competing background-refresh retry.
    const showPagingRecovery = hasPagingDrift || pagingRecoveryState !== "idle";

    // Chakra disables a loading button, which makes browsers drop its focus onto the document
    // body. After each paging result, move focus to the surviving action or its meaningful
    // replacement. Request ids make repeated same-target actions distinct; the handled id
    // consumes each request once so later changes to the derived paging flags cannot refocus it.
    useEffect(() => {
        if (focusRequest == null || handledFocusRequestIdRef.current === focusRequest.id) return;
        handledFocusRequestIdRef.current = focusRequest.id;

        if (focusRequest.target === "loaded-count") {
            loadedCountRef.current?.focus();
            return;
        }

        if (showPagingRecovery) {
            refreshButtonRef.current?.focus();
        } else if (!hasNextPage) {
            loadedCountRef.current?.focus();
        } else {
            pagingButtonRef.current?.focus();
        }
    }, [focusRequest, hasNextPage, showPagingRecovery]);

    const requestFocus = useCallback((target: "paging-result" | "loaded-count"): void => {
        nextFocusRequestIdRef.current += 1;
        setFocusRequest({ id: nextFocusRequestIdRef.current, target });
    }, []);

    // Memoized because the table's load effect depends on these: a new identity every render
    // would re-run it. This repo does not build with the React Compiler.
    const loadNextPage = useCallback((): void => {
        void fetchNextPage().then(() => requestFocus("paging-result"));
    }, [fetchNextPage, requestFocus]);

    // This path moves no focus. The button path has to, because Chakra disables a loading button
    // and the browser drops its focus to the body; a load nobody clicked for never took focus in
    // the first place, so grabbing it would yank the reader out of the rows.
    const autoLoadNextPage = useCallback((): void => {
        void fetchNextPage();
    }, [fetchNextPage]);

    const restartFromFirstPage = useCallback(async (): Promise<void> => {
        setPagingRecoveryState("pending");
        await queryClient.cancelQueries({ queryKey, exact: true });
        const previousHistory = queryClient.getQueryData<InfiniteData<Page<TItem>>>(queryKey);
        // A normal infinite-query refetch reloads every cached page. Drop the later page params
        // first so this recovery action really does restart the offset sequence at page zero.
        queryClient.setQueryData<InfiniteData<Page<TItem>>>(queryKey, (current) =>
            current == null
                ? current
                : {
                      pages: current.pages.slice(0, 1),
                      pageParams: current.pageParams.slice(0, 1),
                  },
        );
        const result = await refetch();
        if (result.isSuccess) {
            setPagingRecoveryState("idle");
            requestFocus("loaded-count");
        } else {
            if (previousHistory != null) {
                queryClient.setQueryData(queryKey, previousHistory);
            }
            setPagingRecoveryState("failed");
            requestFocus("paging-result");
        }
    }, [queryClient, queryKey, refetch, requestFocus]);

    const restart = useCallback((): void => void restartFromFirstPage(), [restartFromFirstPage]);
    const retry = useCallback((): void => void refetch(), [refetch]);

    return {
        items,
        displayedTotal,
        isPending,
        isUnavailable: isError && items.length === 0,
        isRefetching,
        isRefetchError,
        isFetchingNextPage,
        isFetchNextPageError,
        hasNextPage,
        showPagingRecovery,
        pagingRecoveryState,
        refetch: retry,
        loadNextPage,
        autoLoadNextPage,
        restartFromFirstPage: restart,
        refreshButtonRef,
        pagingButtonRef,
        loadedCountRef,
    };
}
