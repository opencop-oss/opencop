// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it, vi } from "vitest";
import {
    buildPageParams,
    MAX_SEARCH_LENGTH,
    normalizeSearchTerm,
    resolveInfiniteRetry,
} from "./common";

describe("normalizeSearchTerm", () => {
    it("trims surrounding whitespace", () => {
        expect(normalizeSearchTerm("  jo  ")).toBe("jo");
    });

    it("returns undefined for empty, blank, or nullish input", () => {
        expect(normalizeSearchTerm("")).toBeUndefined();
        expect(normalizeSearchTerm("   ")).toBeUndefined();
        expect(normalizeSearchTerm(undefined)).toBeUndefined();
        expect(normalizeSearchTerm(null)).toBeUndefined();
    });

    it("clips to the max length", () => {
        const long = "a".repeat(MAX_SEARCH_LENGTH + 50);
        expect(normalizeSearchTerm(long)).toHaveLength(MAX_SEARCH_LENGTH);
    });

    it("keeps a normal term unchanged", () => {
        expect(normalizeSearchTerm("john.doe")).toBe("john.doe");
    });
});

describe("buildPageParams", () => {
    it("emits falsy-but-valid scalars — page 0 and active false are not skipped", () => {
        const params = buildPageParams({ page: 0, active: false });
        expect(params.get("page")).toBe("0");
        expect(params.get("active")).toBe("false");
    });

    it("repeats array values as multiple params (Spring Pageable sort)", () => {
        const params = buildPageParams({ sort: ["username,asc", "email,desc"] });
        expect(params.getAll("sort")).toEqual(["username,asc", "email,desc"]);
    });

    it("skips null, undefined, and empty-string values", () => {
        const params = buildPageParams({ size: 25, search: undefined, type: null, filter: "" });
        expect(params.get("size")).toBe("25");
        expect(params.has("search")).toBe(false);
        expect(params.has("type")).toBe(false);
        expect(params.has("filter")).toBe(false);
    });

    it("drops empty entries within an array", () => {
        const params = buildPageParams({ sort: ["name,asc", ""] });
        expect(params.getAll("sort")).toEqual(["name,asc"]);
    });

    it("produces an empty query string when every value is skipped", () => {
        expect(buildPageParams({ search: undefined, sort: undefined }).toString()).toBe("");
    });
});

describe("resolveInfiniteRetry", () => {
    it("retries the next page when rows are already loaded", () => {
        const fetchNextPage = vi.fn();
        const refetch = vi.fn();
        resolveInfiniteRetry(true, fetchNextPage, refetch);
        expect(fetchNextPage).toHaveBeenCalledTimes(1);
        expect(refetch).not.toHaveBeenCalled();
    });

    it("refetches from the start when no rows are loaded yet", () => {
        const fetchNextPage = vi.fn();
        const refetch = vi.fn();
        resolveInfiniteRetry(false, fetchNextPage, refetch);
        expect(refetch).toHaveBeenCalledTimes(1);
        expect(fetchNextPage).not.toHaveBeenCalled();
    });
});
