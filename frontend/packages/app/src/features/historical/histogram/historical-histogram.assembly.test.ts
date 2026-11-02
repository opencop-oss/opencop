// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import type { HistogramResponse } from "@opencop/api";

import { assembleMultiSourceBuckets, maxStackedActive } from "./historical-histogram.assembly";

/** Build a HistogramResponse with `active`/`updated` counts per bucket. */
function resp(buckets: { active?: number; updated?: number }[]): HistogramResponse {
    return {
        buckets: buckets.map((b, i) => ({
            start: `2026-01-01T0${i}:00:00.000Z`,
            end: `2026-01-01T0${i + 1}:00:00.000Z`,
            values: [
                ...(b.active !== undefined
                    ? [{ key: "k", metric: "ACTIVE" as const, count: b.active }]
                    : []),
                ...(b.updated !== undefined
                    ? [{ key: "k", metric: "UPDATED" as const, count: b.updated }]
                    : []),
            ],
        })),
    };
}

describe("assembleMultiSourceBuckets", () => {
    it("returns [] when no source has buckets", () => {
        expect(assembleMultiSourceBuckets([], new Map())).toEqual([]);
        expect(assembleMultiSourceBuckets(["a"], new Map([["a", undefined]]))).toEqual([]);
        expect(assembleMultiSourceBuckets(["a"], new Map([["a", resp([])]]))).toEqual([]);
    });

    it("stacks two index-aligned sources per bucket with correct totals", () => {
        const responses = new Map<string, HistogramResponse>([
            [
                "a",
                resp([
                    { active: 5, updated: 8 },
                    { active: 3, updated: 2 },
                ]),
            ],
            [
                "b",
                resp([
                    { active: 2, updated: 1 },
                    { active: 7, updated: 9 },
                ]),
            ],
        ]);
        const out = assembleMultiSourceBuckets(["a", "b"], responses);

        expect(out).toHaveLength(2);
        // Source order preserved (stable stack position).
        expect(out[0].sources.map((s) => s.sourceId)).toEqual(["a", "b"]);
        expect(out[0].sources[0]).toEqual({ sourceId: "a", active: 5, updated: 8 });
        expect(out[0].sources[1]).toEqual({ sourceId: "b", active: 2, updated: 1 });
        expect(out[0].totalActive).toBe(7);
        expect(out[1].totalActive).toBe(10);
        expect(out[0].start).toBe("2026-01-01T00:00:00.000Z");
    });

    it("contributes zeros for a missing/short source rather than truncating the axis", () => {
        const responses = new Map<string, HistogramResponse | undefined>([
            ["a", resp([{ active: 5 }, { active: 6 }, { active: 7 }])],
            ["b", resp([{ active: 1 }])], // short — still loading
            ["c", undefined], // errored / not yet fetched
        ]);
        const out = assembleMultiSourceBuckets(["a", "b", "c"], responses);

        expect(out).toHaveLength(3); // axis from the longest (a)
        expect(out[0].sources).toEqual([
            { sourceId: "a", active: 5, updated: 0 },
            { sourceId: "b", active: 1, updated: 0 },
            { sourceId: "c", active: 0, updated: 0 },
        ]);
        // Bucket 1: b has no bucket here → 0.
        expect(out[1].sources[1]).toEqual({ sourceId: "b", active: 0, updated: 0 });
        expect(out[2].totalActive).toBe(7);
    });

    it("separates ACTIVE from UPDATED per source", () => {
        const responses = new Map<string, HistogramResponse>([
            ["a", resp([{ active: 4, updated: 9 }])],
        ]);
        const out = assembleMultiSourceBuckets(["a"], responses);
        expect(out[0].sources[0].active).toBe(4);
        expect(out[0].sources[0].updated).toBe(9);
    });

    it("maxStackedActive returns the tallest stacked column", () => {
        const responses = new Map<string, HistogramResponse>([
            ["a", resp([{ active: 5 }, { active: 3 }])],
            ["b", resp([{ active: 2 }, { active: 7 }])],
        ]);
        const out = assembleMultiSourceBuckets(["a", "b"], responses);
        expect(maxStackedActive(out)).toBe(10);
        expect(maxStackedActive([])).toBe(0);
    });
});
