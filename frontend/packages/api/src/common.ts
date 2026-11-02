// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { FetchOptions } from "./client";

/** Backend supports "ALL"; the web client always passes "ACTIVE" or "ARCHIVED" explicitly. */
export type RecordVisibility = "ACTIVE" | "ARCHIVED" | "ALL";

export type RecordQueryOptions = FetchOptions & { recordVisibility?: RecordVisibility };

/**
 * Paged response returned by the backend `PageResponse<T>` record. `page` is the zero-indexed
 * page number; there is no `last`/`first` flag — derive "has next" from `page + 1 < totalPages`.
 */
export interface Page<T> {
    content: T[];
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
}

/** A single Spring `Pageable` sort clause in wire format, e.g. `"username,asc"`. */
export type SortDirection = "asc" | "desc";
export type SortParam = `${string},${SortDirection}`;

/** Offset-pagination request params shared by the server-paged list endpoints. */
export interface PageRequestParams {
    page?: number;
    size?: number;
    sort?: SortParam[];
}

/**
 * Server-enforced cap on search-term length. `SearchInput`'s `maxLength` default (`@opencop/ui`)
 * mirrors this value by hand — the UI is a leaf package and can't import the data plane, so the
 * two must be kept in sync.
 */
export const MAX_SEARCH_LENGTH = 100;

/**
 * Server-enforced cap on a single filter value, kept in sync by hand with
 * `FilterCriterion.MAX_VALUE_LENGTH` in opencop-java. Exceeding it is a 400, so the serializer
 * clips instead.
 */
export const MAX_FILTER_VALUE_LENGTH = 100;

/**
 * Server-enforced cap on how many filter criteria one request may carry, kept in sync by hand with
 * `FilterCriterion.MAX_CRITERIA` in opencop-java. Exceeding it is a 400 that fails the whole page,
 * so the builder stops accepting chips at the cap rather than letting the grid error.
 */
export const MAX_FILTER_CRITERIA = 10;

/**
 * One per-column filter clause in wire format: `field,operator,value` (e.g. `"email,contains,jo"`),
 * mirroring the `sort=name,asc` convention. The backend splits on the first two commas only, so a
 * value keeps any commas of its own; `between` joins its endpoints with `..`. Repeat the param to
 * stack clauses — they AND.
 *
 * Shared by every server-filtered list endpoint; which fields and operators a given endpoint
 * accepts is its own contract.
 */
export type FilterParam = `${string},${string},${string}`;

export function normalizeSearchTerm(term: string | null | undefined): string | undefined {
    if (term == null) return undefined;
    const cleaned = term.trim().slice(0, MAX_SEARCH_LENGTH);
    return cleaned === "" ? undefined : cleaned;
}

/**
 * Builds a query string for a paged endpoint. Skips null/undefined/empty values,
 * appends array values as repeated params (so `sort` becomes `sort=a&sort=b`), and
 * stringifies scalars. Keeps the paged api fns free of ad-hoc URLSearchParams glue.
 */
export function buildPageParams(
    params: Record<string, string | number | boolean | string[] | null | undefined>,
): URLSearchParams {
    const searchParams = new URLSearchParams();
    for (const [key, value] of Object.entries(params)) {
        if (value == null || value === "") continue;
        if (Array.isArray(value)) {
            for (const entry of value) {
                if (entry != null && entry !== "") searchParams.append(key, String(entry));
            }
        } else {
            searchParams.append(key, String(value));
        }
    }
    return searchParams;
}

/**
 * Retry handler for an infinite list's error state: if rows are already loaded, retry just the
 * failed next page; otherwise the initial load failed, so refetch from the start. Wire to a
 * `ResourceTable` `onRetry` (pass `hasRows`, the query's `fetchNextPage`, and its `refetch`).
 */
export function resolveInfiniteRetry(
    hasRows: boolean,
    fetchNextPage: () => unknown,
    refetch: () => unknown,
): void {
    if (hasRows) fetchNextPage();
    else refetch();
}
