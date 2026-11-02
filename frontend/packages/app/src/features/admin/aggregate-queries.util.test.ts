// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it, vi } from "vitest";
import { aggregateQueries } from "./aggregate-queries.util";

const makeIdle = () => ({ isLoading: false, isFetching: false, refetch: vi.fn() });
const makeLoading = () => ({ isLoading: true, isFetching: true, refetch: vi.fn() });
const makeFetching = () => ({ isLoading: false, isFetching: true, refetch: vi.fn() });

describe("aggregateQueries", () => {
    it("returns false/false when all queries are idle", () => {
        const result = aggregateQueries([makeIdle(), makeIdle()]);
        expect(result.isLoading).toBe(false);
        expect(result.isFetching).toBe(false);
    });

    it("returns true/false when any query is loading", () => {
        const result = aggregateQueries([makeLoading(), makeIdle()]);
        expect(result.isLoading).toBe(true);
        expect(result.isFetching).toBe(false);
    });

    it("suppresses isFetching when any query isLoading", () => {
        // Even if another query is refetching, isLoading wins
        const result = aggregateQueries([makeLoading(), makeFetching()]);
        expect(result.isLoading).toBe(true);
        expect(result.isFetching).toBe(false);
    });

    it("surfaces isFetching when refetching and not loading", () => {
        const result = aggregateQueries([makeIdle(), makeFetching()]);
        expect(result.isLoading).toBe(false);
        expect(result.isFetching).toBe(true);
    });

    it("handles a single-element array", () => {
        expect(aggregateQueries([makeFetching()])).toEqual({
            isLoading: false,
            isFetching: true,
            refetchAll: expect.any(Function),
        });
        expect(aggregateQueries([makeLoading()])).toEqual({
            isLoading: true,
            isFetching: false,
            refetchAll: expect.any(Function),
        });
    });

    it("handles an empty array", () => {
        const result = aggregateQueries([]);
        expect(result.isLoading).toBe(false);
        expect(result.isFetching).toBe(false);
    });

    it("refetchAll calls every refetch function", async () => {
        const a = { isLoading: false, isFetching: false, refetch: vi.fn().mockResolvedValue("a") };
        const b = { isLoading: false, isFetching: false, refetch: vi.fn().mockResolvedValue("b") };
        const { refetchAll } = aggregateQueries([a, b]);
        const results = await refetchAll();
        expect(a.refetch).toHaveBeenCalledOnce();
        expect(b.refetch).toHaveBeenCalledOnce();
        expect(results).toEqual([
            { status: "fulfilled", value: "a" },
            { status: "fulfilled", value: "b" },
        ]);
    });

    it("refetchAll on empty array resolves to []", async () => {
        const { refetchAll } = aggregateQueries([]);
        await expect(refetchAll()).resolves.toEqual([]);
    });
});
