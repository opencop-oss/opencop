// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { QueryClient } from "@tanstack/react-query";
import { afterEach, describe, expect, it, vi } from "vitest";
import { server, http, HttpResponse, API_BASE } from "~/test";
import type { LayerInfo, MergeResponse } from "@opencop/api";
import { layerDirectoryQueries } from "./layer-directory.queries";
import { layerQueries } from "./layers.queries";
import { MERGE_POLL_INTERVAL_MS, mergeMutations } from "./merge.queries";

const MERGE_RESPONSE = {
    targetLayer: {
        id: "target-1",
        name: "Alpha-Bravo",
        lifecycleState: "PENDING_MERGE",
        accessScope: "PRIVATE",
        createdAt: "2026-01-01T00:00:00Z",
        archivedAt: null,
        defaultVisible: true,
    },
    sourceLayerIds: ["l1", "l2"],
} satisfies MergeResponse;

const ACTIVE_LAYER = {
    id: "target-1",
    name: "Alpha-Bravo",
    lifecycleState: "ACTIVE",
    status: "ACTIVE",
    accessScope: "PRIVATE",
    createdAt: "2026-01-01T00:00:00Z",
    editing: false,
    defaultVisible: true,
} satisfies LayerInfo;

const PENDING_LAYER = {
    ...ACTIVE_LAYER,
    lifecycleState: "PENDING_MERGE",
    status: "PENDING_MERGE",
} satisfies LayerInfo;

describe("mergeMutations.merge", () => {
    afterEach(() => {
        vi.useRealTimers();
    });

    it("invalidates layer lists after the merge settles even without an active dialog", async () => {
        // Known-fragile combination: fake timers drive the poll interval while the
        // real MSW-backed apiFetch resolves the poll response. `advanceTimersByTimeAsync`
        // flushes microtasks between ticks, which covers MSW here — but if a future
        // msw/vitest/undici bump makes the fetch need real macrotask turns the fake
        // clock isn't driving, prefer advancing the timer then `await vi.waitFor(...)`
        // under real timers instead.
        vi.useFakeTimers();
        const queryClient = new QueryClient({
            defaultOptions: {
                queries: { retry: false, staleTime: Infinity },
                mutations: { retry: false },
            },
        });
        const invalidateQueriesSpy = vi.spyOn(queryClient, "invalidateQueries");

        // The poll (`getLayer`) runs through the real `apiFetch` against MSW:
        // first read is PENDING (keep polling), second read is ACTIVE (settle).
        let getLayerCalls = 0;
        server.use(
            http.get(`${API_BASE}/layers/target-1`, () => {
                getLayerCalls += 1;
                return HttpResponse.json(getLayerCalls === 1 ? PENDING_LAYER : ACTIVE_LAYER);
            }),
        );

        const onSuccess = mergeMutations.merge(queryClient).onSuccess;
        onSuccess?.(MERGE_RESPONSE, { name: "Alpha-Bravo", layerIds: ["l1", "l2"] }, undefined, {
            client: queryClient,
            meta: undefined,
        });

        await vi.advanceTimersByTimeAsync(MERGE_POLL_INTERVAL_MS);

        // Three invalidations, in this order: the directory refreshes immediately so the grid
        // shows the sources entering their merging state, then the settle poll refreshes both
        // the layer lists and the directory once the merge reaches a terminal state. The
        // directory is deliberately invalidated twice — the two fire at different moments.
        expect(invalidateQueriesSpy).toHaveBeenCalledTimes(3);
        expect(invalidateQueriesSpy).toHaveBeenNthCalledWith(1, {
            queryKey: layerDirectoryQueries.lists(),
        });
        expect(invalidateQueriesSpy).toHaveBeenNthCalledWith(2, {
            queryKey: layerQueries.lists(),
        });
        expect(invalidateQueriesSpy).toHaveBeenNthCalledWith(3, {
            queryKey: layerDirectoryQueries.lists(),
        });
    });
});
