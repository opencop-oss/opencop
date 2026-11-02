// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { Element } from "@opencop/elements";
import { describe, expect, it, vi } from "vitest";

import { installHistoricalDataManagerDiagnostics } from "./HistoricalDataManagerDiagnostics";
import type { HistoricalDataManager } from "./HistoricalDataManager";

// The diagnostics only read `manager.source`, `manager.activeBucketIndex` and
// `manager.store.getAll()`. A fake manager whose store hands back an iterator of
// N element-likes reproduces the real `buildPerObjectSummary` path exactly —
// `getAll` on the real ElementStore is itself a `Map.values()` iterator.
function makeElements(n: number): Set<Element> {
    const set = new Set<Element>();
    for (let i = 0; i < n; i++) {
        // Only objectId + filterValue are read by the summary builder.
        set.add({ objectId: `obj-${i % 900}`, filterValue: i % 160 } as unknown as Element);
    }
    return set;
}

function makeManager(elements: Set<Element>): HistoricalDataManager {
    return {
        source: "perf-src",
        activeBucketIndex: 0,
        store: { getAll: () => elements.values() },
    } as unknown as HistoricalDataManager;
}

describe("HistoricalDataManagerDiagnostics — large-store regression", () => {
    // 17M events over 30 days produced a single type whose element count
    // crossed V8's ~110k argument-count cap partway through chunk streaming.
    // The old summary builder did `array.push(...store.getByType(type))`, so
    // the spread overflowed and threw a *misleading* "Maximum call stack size
    // exceeded" RangeError on every playhead-bucket change once the store
    // passed that size.
    it("does not overflow the call stack with >110k elements of one type", () => {
        const diag = installHistoricalDataManagerDiagnostics(makeManager(makeElements(150_000)));
        // Silence the per-change logger.info line for the test run.
        vi.spyOn(console, "info").mockImplementation(() => undefined);

        expect(() => diag.onPlayheadBucketChange(0, 1, null)).not.toThrow();

        diag.dispose();
        vi.restoreAllMocks();
    });

    it("confirms the naive spread overflowed at this scale (documents the mechanism)", () => {
        // Demonstration of the root cause, not a behavior guard: asserts V8's
        // argument-count cap (~110k) rejects a 150k-element spread. The cap is a
        // V8 implementation detail — if a future engine raises or removes it, this
        // test would pass for the wrong reason and should be updated, not trusted.
        const iter = makeElements(150_000).values();
        expect(() => {
            const acc: Element[] = [];
            // This is the shape the diagnostics + perf bridge used before the fix.
            acc.push(...iter);
        }).toThrow(RangeError);
    });
});
