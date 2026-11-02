// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * Integration coverage for the historical view's loading-narration models —
 * `useHistoricalStatusModel` and `useHistogramModel` — driven through the real
 * React Query fan-out and real HTTP responses (MSW). This exercises the status
 * bar's and histogram's *derived state*, not their rendered output; neither
 * component is mounted here.
 *
 * These behaviours are timing- and fault-injection-shaped — a slow source
 * alongside a fast one, a query on its third retry, one source of three failing
 * — which the repo's decision tree routes here rather than to e2e.
 */
import { QueryClient } from "@tanstack/react-query";
import { act } from "react";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { HISTOGRAM_METRIC, type HistogramResponse } from "@opencop/api";

import { API_BASE, HttpResponse, http, renderHook, server, waitFor } from "~/test";
import type { StatusTier } from "./use-historical-status-model.hook";

import { historicalQueries } from "../data-plane/historical.queries";
import { useHistoricalStore } from "../data-plane/historical.store";
import { useHistogramModel } from "../histogram/use-histogram-model.hook";
import { useMultiSourceHistograms } from "../histogram/use-multi-source-histograms.hook";
import { useHistoricalStatusModel } from "./use-historical-status-model.hook";

const FAST_SOURCE = "11111111-1111-1111-1111-111111111111";
const SLOW_SOURCE = "22222222-2222-2222-2222-222222222222";

const FROM = "2026-01-01T00:00:00.000Z";
const TO = "2026-01-02T00:00:00.000Z";

/** A response with one non-empty bucket, so "no events" never fires spuriously. */
function histogramWithEvents(): HistogramResponse {
    return {
        buckets: [
            {
                start: FROM,
                end: TO,
                values: [{ key: "active", metric: HISTOGRAM_METRIC.Active, count: 7 }],
            },
        ],
        effectiveFrom: FROM,
        effectiveTo: TO,
    };
}

/**
 * Query client with the harness's isolation but real retries: the histogram
 * query declares its own retry count, and a zero delay keeps the four attempts
 * inside a test's budget without changing how many there are.
 */
function retryingQueryClient(): QueryClient {
    return new QueryClient({
        defaultOptions: {
            queries: { staleTime: Infinity, retryDelay: 0 },
            mutations: { retry: false },
        },
    });
}

/** Put the store in the state an operator reaches by pressing Activate. */
function activateWith(sourceIds: string[]): void {
    const { actions } = useHistoricalStore.getState();
    act(() => {
        actions.enterHistorical();
        actions.setSourceIds(
            sourceIds,
            Object.fromEntries(sourceIds.map((id) => [id, "SOURCE" as const])),
        );
        actions.setTimeRange(FROM, TO);
        actions.activate();
    });
}

/** Narrow a tier, failing the test with the actual kind when it doesn't match. */
function expectTier<K extends StatusTier["kind"]>(
    tier: StatusTier,
    kind: K,
): Extract<StatusTier, { kind: K }> {
    expect(tier.kind).toBe(kind);
    return tier as Extract<StatusTier, { kind: K }>;
}

/**
 * Serve the per-source histogram endpoint. Every source a test activates must be
 * named in exactly one of `ok` / `failing` / `pending`; an unnamed source throws
 * rather than silently taking a default outcome the call site never asked for.
 */
function serveHistograms(options: {
    /** Sources whose histogram resolves immediately with one non-empty bucket. */
    ok?: string[];
    /** Sources whose histogram returns 500 on every attempt. */
    failing?: string[];
    /** Sources whose histogram request never answers. */
    pending?: string[];
    /**
     * Fail this many attempts and then hold the next request open forever, so
     * "a retry is in flight" is a state the test can observe rather than race.
     */
    stallAfterFailures?: number;
}): void {
    const { ok = [], failing = [], pending = [], stallAfterFailures } = options;
    const attemptsBySource = new Map<string, number>();
    server.use(
        // The legend resolves subject names from these; empty lists are enough
        // (the tests assert on load state, not on names) and keep the harness's
        // no-unmocked-network guarantee satisfied.
        http.get(`${API_BASE}/me/sources`, () => HttpResponse.json([])),
        http.get(`${API_BASE}/me/layers`, () => HttpResponse.json([])),
        http.get(`${API_BASE}/events/:sourceId/histogram`, async ({ params }) => {
            const id = params.sourceId as string;
            if (ok.includes(id)) return HttpResponse.json(histogramWithEvents());
            if (pending.includes(id)) await new Promise(() => {});
            if (!failing.includes(id)) {
                throw new Error(`serveHistograms: no outcome declared for source ${id}`);
            }
            const attempt = (attemptsBySource.get(id) ?? 0) + 1;
            attemptsBySource.set(id, attempt);
            if (stallAfterFailures !== undefined && attempt > stallAfterFailures) {
                await new Promise(() => {});
            }
            return HttpResponse.json(
                { status: 500, message: "Something went wrong." },
                { status: 500 },
            );
        }),
    );
}

beforeEach(() => {
    useHistoricalStore.setState({
        mode: "live",
        sourceIds: [],
        subjectTypes: {},
        from: null,
        to: null,
        activated: false,
        zoomStack: [],
        playheadPosition: null,
        dismissedSignature: null,
    });
});
afterEach(() => {
    useHistoricalStore.getState().actions.exitHistorical();
});

describe("historical loading-state honesty", () => {
    it("never offers the idle interaction hint while a chart request is still in flight", async () => {
        // The reported shape: a fast source resolving beside a slow one. The
        // fast response used to satisfy the idle-hint gate, so the bar invited
        // the operator to brush and scrub while the spinner was still running.
        serveHistograms({ ok: [FAST_SOURCE], pending: [SLOW_SOURCE] });
        activateWith([FAST_SOURCE, SLOW_SOURCE]);

        const { result } = renderHook(() => useHistoricalStatusModel(), {
            queryClient: retryingQueryClient(),
        });

        await waitFor(() => expect(result.current.tier.kind).toBe("preparing"));
        // Give the fast source's response every chance to flip the cascade.
        await new Promise((resolve) => setTimeout(resolve, 50));
        expect(result.current.tier.kind).toBe("preparing");
    });

    it("names the attempt once a chart request is being retried", async () => {
        // Two failures, then the third request never answers — so the retrying
        // state is a stable thing to assert on instead of a window to race.
        serveHistograms({ failing: [FAST_SOURCE], stallAfterFailures: 2 });
        activateWith([FAST_SOURCE]);

        const { result } = renderHook(() => useHistoricalStatusModel(), {
            queryClient: retryingQueryClient(),
        });

        await waitFor(() => {
            expect(expectTier(result.current.tier, "preparing").attempt).toBe(3);
        });
        expect(expectTier(result.current.tier, "preparing").totalAttempts).toBe(4);
    });

    it("reaches a settled failure state instead of narrating forever", async () => {
        serveHistograms({ failing: [FAST_SOURCE] });
        activateWith([FAST_SOURCE]);

        const { result } = renderHook(
            () => ({
                status: useHistoricalStatusModel(),
                model: useHistogramModel({
                    sourceIds: [FAST_SOURCE],
                    from: FROM,
                    to: TO,
                    intervalMode: "auto",
                    activated: true,
                }),
            }),
            { queryClient: retryingQueryClient() },
        );

        await waitFor(() => expect(result.current.model.allFailed).toBe(true));
        expect(result.current.model.isLoading).toBe(false);
        expect(result.current.status.tier.kind).not.toBe("preparing");
    });

    it("keeps the healthy source's chart when one of two sources fails", async () => {
        serveHistograms({ ok: [FAST_SOURCE], failing: [SLOW_SOURCE] });
        activateWith([FAST_SOURCE, SLOW_SOURCE]);

        const { result } = renderHook(
            () => ({
                status: useHistoricalStatusModel(),
                model: useHistogramModel({
                    sourceIds: [FAST_SOURCE, SLOW_SOURCE],
                    from: FROM,
                    to: TO,
                    intervalMode: "auto",
                    activated: true,
                }),
            }),
            { queryClient: retryingQueryClient() },
        );

        await waitFor(() => expect(result.current.model.isLoading).toBe(false));
        // The whole chart is not discarded — the panel renders the error state
        // only when every source failed.
        expect(result.current.model.allFailed).toBe(false);
        expect(result.current.model.histogramBuckets.length).toBeGreaterThan(0);
        // ...and the bar says the picture is partial rather than inviting a brush.
        expect(expectTier(result.current.status.tier, "hint").message).toContain("1 of 2");
    });

    it("does not report a source as failed when a same-key refetch fails but retains its data", async () => {
        // TanStack Query keeps a query's last-good `data` through a same-key
        // refetch failure (`isRefetchError = isError && hasData`, per
        // `packages/query-core/src/queryObserver.ts`). A naive tally that counts
        // "has data" and "is error" independently would let a single source push
        // `allResolved` and `allFailed` true at once — and `allFailed` is what
        // the panel uses to discard the chart entirely.
        let shouldFail = false;
        server.use(
            http.get(`${API_BASE}/me/sources`, () => HttpResponse.json([])),
            http.get(`${API_BASE}/me/layers`, () => HttpResponse.json([])),
            http.get(`${API_BASE}/events/:sourceId/histogram`, () =>
                shouldFail
                    ? HttpResponse.json(
                          { status: 500, message: "Something went wrong." },
                          { status: 500 },
                      )
                    : HttpResponse.json(histogramWithEvents()),
            ),
        );
        activateWith([FAST_SOURCE]);
        const queryClient = retryingQueryClient();

        const { result } = renderHook(
            () => useMultiSourceHistograms([FAST_SOURCE], { from: FROM, to: TO, bars: 24 }, true),
            { queryClient },
        );

        await waitFor(() => expect(result.current.allResolved).toBe(true));

        shouldFail = true;
        await act(async () => {
            await queryClient.refetchQueries({ queryKey: historicalQueries.histograms() });
        });
        await waitFor(() => {
            const query = queryClient
                .getQueryCache()
                .findAll({ queryKey: historicalQueries.histograms() })[0];
            expect(query?.state.status).toBe("error");
            expect(query?.state.data).toBeDefined();
        });

        expect(result.current.allResolved).toBe(true);
        expect(result.current.allFailed).toBe(false);
        expect(result.current.errorCount).toBe(0);
    });

    it("reports a range change as unresolved rather than carrying the previous range's data", async () => {
        // A range/zoom/interval change is a new query key. `useQueries` hands
        // its `placeholderData` no `previousData` argument (documented, and the
        // reason `keepPreviousData` would be inert here), so the replacement
        // request must read as "still loading the current range" — not as the
        // old range's data resolved for the new one, which would let the idle
        // interaction hints appear over a chart that is no longer current.
        const LATER_FROM = "2026-02-01T00:00:00.000Z";
        const LATER_TO = "2026-02-02T00:00:00.000Z";
        let holdRequests = false;
        server.use(
            http.get(`${API_BASE}/me/sources`, () => HttpResponse.json([])),
            http.get(`${API_BASE}/me/layers`, () => HttpResponse.json([])),
            http.get(`${API_BASE}/events/:sourceId/histogram`, async () => {
                if (holdRequests) await new Promise(() => {});
                return HttpResponse.json(histogramWithEvents());
            }),
        );
        activateWith([FAST_SOURCE]);

        const { result, rerender } = renderHook(
            ({ params }: { params: { from: string; to: string; bars: number } }) =>
                useMultiSourceHistograms([FAST_SOURCE], params, true),
            {
                queryClient: retryingQueryClient(),
                initialProps: { params: { from: FROM, to: TO, bars: 24 } },
            },
        );

        await waitFor(() => expect(result.current.allResolved).toBe(true));

        // Switch to a range whose request never answers, so the in-flight state
        // is stable to assert on rather than a window to race.
        holdRequests = true;
        rerender({ params: { from: LATER_FROM, to: LATER_TO, bars: 24 } });

        await waitFor(() => expect(result.current.isLoading).toBe(true));
        expect(result.current.allResolved).toBe(false);
        expect(result.current.loadedCount).toBe(0);
        expect(result.current.responses.get(FAST_SOURCE)).toBeUndefined();
    });
});
