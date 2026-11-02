// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { QueryClient, useQuery } from "@tanstack/react-query";
import { describe, expect, it } from "vitest";
import { API_BASE, http, HttpResponse, renderHook, server, waitFor } from "~/test";
import type { Page } from "@opencop/api";
import type { WatchboxAlertEvent, WatchboxDetection } from "./watchbox.api";
import { watchboxQueries } from "./watchbox.queries";
import { recordDetection } from "./watchbox-detections.cache";

function makeQueryClient(): QueryClient {
    return new QueryClient({ defaultOptions: { queries: { retry: false, staleTime: Infinity } } });
}

function emptyPage(): Page<WatchboxDetection> {
    return { content: [], page: 0, size: 50, totalElements: 0, totalPages: 0 };
}

function makeDetection(overrides: Partial<WatchboxDetection> & { id: string }): WatchboxDetection {
    return {
        watchboxId: "wb-1",
        configEpoch: 1,
        sourceId: "src-1",
        objectId: "obj-1",
        eventId: `evt-${overrides.id}`,
        effectiveEventTime: "2026-01-01T00:00:00Z",
        kind: "ENTER",
        objectDisplayName: null,
        detectedAt: "2026-01-01T00:00:00Z",
        ...overrides,
    };
}

function makeAlert(
    overrides: Partial<WatchboxAlertEvent> & { watchboxId: string },
): WatchboxAlertEvent {
    return {
        configEpoch: 1,
        sourceId: "src-1",
        objectId: "obj-1",
        eventId: "evt-alert",
        effectiveEventTime: "2026-01-02T00:00:00Z",
        kind: "ENTER",
        detectionId: null,
        objectDisplayName: null,
        ...overrides,
    };
}

describe("recordDetection", () => {
    it("prepends the alert as a new row when a page is already cached", () => {
        const queryClient = makeQueryClient();
        const { queryKey } = watchboxQueries.detections("wb-1");
        queryClient.setQueryData(queryKey, {
            ...emptyPage(),
            content: [makeDetection({ id: "existing" })],
            totalElements: 1,
        });

        recordDetection(
            queryClient,
            makeAlert({ watchboxId: "wb-1", detectionId: "det-1", objectDisplayName: "Truck 1" }),
        );

        const page = queryClient.getQueryData<Page<WatchboxDetection>>(queryKey);
        expect(page?.content.map((d) => d.id)).toEqual(["det-1", "existing"]);
        expect(page?.totalElements).toBe(2);
    });

    it("replaces rather than duplicates a redelivered alert for the same detection id", () => {
        const queryClient = makeQueryClient();
        const { queryKey } = watchboxQueries.detections("wb-1");
        queryClient.setQueryData(queryKey, emptyPage());

        recordDetection(
            queryClient,
            makeAlert({ watchboxId: "wb-1", detectionId: "det-1", objectDisplayName: "First" }),
        );
        recordDetection(
            queryClient,
            makeAlert({
                watchboxId: "wb-1",
                detectionId: "det-1",
                objectDisplayName: "Redelivered",
            }),
        );

        const page = queryClient.getQueryData<Page<WatchboxDetection>>(queryKey);
        expect(page?.content).toHaveLength(1);
        expect(page?.content[0]).toMatchObject({ id: "det-1", objectDisplayName: "Redelivered" });
        // Counted once, not once per delivery.
        expect(page?.totalElements).toBe(1);
    });

    it("drops the oldest row rather than growing the cached page past one page size", () => {
        const queryClient = makeQueryClient();
        const { queryKey } = watchboxQueries.detections("wb-1");
        const full = Array.from({ length: 50 }, (_, i) => makeDetection({ id: `d${i}` }));
        queryClient.setQueryData(queryKey, { ...emptyPage(), content: full, totalElements: 50 });

        recordDetection(queryClient, makeAlert({ watchboxId: "wb-1", detectionId: "det-new" }));

        const page = queryClient.getQueryData<Page<WatchboxDetection>>(queryKey);
        expect(page?.content).toHaveLength(50);
        expect(page?.content[0]?.id).toBe("det-new");
        expect(page?.content.find((d) => d.id === "d49")).toBeUndefined();
    });

    it("invalidates instead of writing when the alert has no stable detection id", () => {
        const queryClient = makeQueryClient();
        const { queryKey } = watchboxQueries.detections("wb-1");
        queryClient.setQueryData(queryKey, emptyPage());

        recordDetection(queryClient, makeAlert({ watchboxId: "wb-1", detectionId: null }));

        expect(queryClient.getQueryData<Page<WatchboxDetection>>(queryKey)?.content).toEqual([]);
        expect(queryClient.getQueryState(queryKey)?.isInvalidated).toBe(true);
    });

    it("invalidates instead of writing when nothing is cached yet", () => {
        const queryClient = makeQueryClient();
        const { queryKey } = watchboxQueries.detections("wb-1");

        recordDetection(queryClient, makeAlert({ watchboxId: "wb-1", detectionId: "det-1" }));

        // Nothing existed to mark invalidated or write into.
        expect(queryClient.getQueryData(queryKey)).toBeUndefined();
        expect(queryClient.getQueryState(queryKey)).toBeUndefined();
    });

    it("restarts a fetch already in flight so it settles with the alert's row, not the write it would overwrite", async () => {
        const queryClient = makeQueryClient();
        const { queryKey } = watchboxQueries.detections("wb-1");
        queryClient.setQueryData(queryKey, emptyPage());

        let requestCount = 0;
        let releaseFirstRequest: () => void = () => {};
        const firstRequestHeld = new Promise<void>((resolve) => {
            releaseFirstRequest = resolve;
        });

        server.use(
            http.get(`${API_BASE}/watchbox-detections`, async () => {
                requestCount++;
                if (requestCount === 1) {
                    // Simulates a fetch that started before the alert's row committed.
                    await firstRequestHeld;
                    return HttpResponse.json({ ...emptyPage(), content: [] });
                }
                // The restarted request: per the outbox guarantee, it starts after
                // commit, so it already includes the alert's row.
                return HttpResponse.json({
                    ...emptyPage(),
                    content: [makeDetection({ id: "det-1", objectDisplayName: "Truck 1" })],
                    totalElements: 1,
                });
            }),
        );

        // An active observer, mounted with data already cached: invalidateQueries
        // only auto-refetches queries with one, which is exactly the
        // "Notifications tab is open" condition this race guard exists for.
        // refetchOnMount: "always" (watchboxQueries.detections) is what starts
        // the "already in flight" first request, without a manual trigger.
        renderHook(() => useQuery(watchboxQueries.detections("wb-1")), { queryClient });
        await waitFor(() => expect(requestCount).toBe(1));

        recordDetection(
            queryClient,
            makeAlert({ watchboxId: "wb-1", detectionId: "det-1", objectDisplayName: "Truck 1" }),
        );

        // The optimistic write landed immediately, ahead of either request settling.
        expect(
            queryClient.getQueryData<Page<WatchboxDetection>>(queryKey)?.content.map((d) => d.id),
        ).toEqual(["det-1"]);

        releaseFirstRequest();
        await waitFor(() => expect(requestCount).toBe(2));
        await waitFor(() =>
            expect(
                queryClient.getQueryData<Page<WatchboxDetection>>(queryKey)?.content,
            ).toHaveLength(1),
        );

        // Settles with the restarted request's response — which already carries
        // the row — not left orphaned by the cancelled first request overwriting it.
        expect(
            queryClient.getQueryData<Page<WatchboxDetection>>(queryKey)?.content[0],
        ).toMatchObject({ id: "det-1", objectDisplayName: "Truck 1" });
    });

    it("waits for the tab's very first (already in-flight) fetch instead of invalidating it, since TanStack won't restart a fetch with no data yet", async () => {
        const queryClient = makeQueryClient();
        const { queryKey } = watchboxQueries.detections("wb-1");
        // Nothing seeded: this is the tab's first-ever open — no prior page to
        // write into, and (unlike the case above) no data for TanStack's own
        // invalidate-while-fetching machinery to key off of either.

        let requestCount = 0;
        let releaseFirstRequest: () => void = () => {};
        const firstRequestHeld = new Promise<void>((resolve) => {
            releaseFirstRequest = resolve;
        });

        server.use(
            http.get(`${API_BASE}/watchbox-detections`, async () => {
                requestCount++;
                if (requestCount === 1) {
                    // The tab's own initial fetch, already running when the alert
                    // arrives — simulates it having started before the row committed.
                    await firstRequestHeld;
                    return HttpResponse.json(emptyPage());
                }
                // The reconciliation fetch, triggered once the first one lands: per
                // the outbox guarantee it starts after commit, so it already has the row.
                return HttpResponse.json({
                    ...emptyPage(),
                    content: [makeDetection({ id: "det-1", objectDisplayName: "Truck 1" })],
                    totalElements: 1,
                });
            }),
        );

        renderHook(() => useQuery(watchboxQueries.detections("wb-1")), { queryClient });
        await waitFor(() => expect(requestCount).toBe(1));

        // Nothing to write into, so this can only fall back — and that fallback
        // must not just invalidate the in-flight first fetch: a query with no
        // data yet is exactly the case TanStack's own cancel-and-restart-on-
        // invalidate does NOT cover (that only applies once a query already has
        // data), so a naive invalidate here would silently do nothing and the
        // first fetch would settle never knowing about this alert at all.
        recordDetection(
            queryClient,
            makeAlert({ watchboxId: "wb-1", detectionId: "det-1", objectDisplayName: "Truck 1" }),
        );

        // Confirms it waited rather than uselessly invalidating: no second
        // request while the first is still the only thing this query has.
        expect(requestCount).toBe(1);

        releaseFirstRequest();
        await waitFor(() => expect(requestCount).toBe(2));
        await waitFor(() =>
            expect(
                queryClient.getQueryData<Page<WatchboxDetection>>(queryKey)?.content,
            ).toHaveLength(1),
        );
        expect(
            queryClient.getQueryData<Page<WatchboxDetection>>(queryKey)?.content[0],
        ).toMatchObject({ id: "det-1", objectDisplayName: "Truck 1" });
    });

    it("bounds a burst of alerts overlapping one in-flight fetch to a single restart, not one per alert", async () => {
        const queryClient = makeQueryClient();
        const { queryKey } = watchboxQueries.detections("wb-1");
        queryClient.setQueryData(queryKey, emptyPage());

        let requestCount = 0;
        const releases: Array<() => void> = [];
        server.use(
            http.get(`${API_BASE}/watchbox-detections`, async () => {
                requestCount++;
                // Every request (the original fetch and any restart) hangs until
                // released, so a burst alert always overlaps something in flight —
                // the exact condition that, per alert, falls back to reconciling.
                await new Promise<void>((resolve) => releases.push(resolve));
                return HttpResponse.json(emptyPage());
            }),
        );

        renderHook(() => useQuery(watchboxQueries.detections("wb-1")), { queryClient });
        await waitFor(() => expect(requestCount).toBe(1)); // the tab's own fetch, held

        for (const id of ["det-1", "det-2", "det-3", "det-4"]) {
            recordDetection(queryClient, makeAlert({ watchboxId: "wb-1", detectionId: id }));
            // Without coalescing, each of these would independently see "still
            // fetching" and trigger its own cancel-and-restart — give any such
            // (undesired) extra request a moment to be dispatched before the next.
            await new Promise((resolve) => setTimeout(resolve, 0));
        }

        // One restart for the whole four-alert burst, not one per alert (which
        // would be 5: the original fetch plus four independently-triggered ones).
        expect(requestCount).toBe(2);

        releases.splice(0).forEach((release) => release());
        await waitFor(() => expect(queryClient.isFetching({ queryKey })).toBe(0));
        // Draining the held requests didn't uncork any further restarts either.
        expect(requestCount).toBe(2);
    });

    it("throttles reconciliation across a sustained, spread-out trickle of alerts, not just a simultaneous burst", async () => {
        const queryClient = makeQueryClient();

        let requestCount = 0;
        server.use(
            http.get(`${API_BASE}/watchbox-detections`, () => {
                requestCount++;
                return HttpResponse.json(emptyPage());
            }),
        );

        // An active observer with data, so invalidateQueries actually refetches.
        renderHook(() => useQuery(watchboxQueries.detections("wb-1")), { queryClient });
        await waitFor(() => expect(requestCount).toBe(1)); // the tab's own initial fetch

        // A throttle window comfortably larger than waitFor's own polling
        // overhead below, so that overhead can't accidentally carry a later
        // call past the window — real, not fake, timers, kept short enough
        // not to slow the suite down noticeably.
        const opts = { reconcileThrottleMs: 500 };
        // No stable id, so each alert alone would fall back to reconciling —
        // isolates the throttle from the in-flight-coalescing behavior above.
        // Issued back to back, not separated by an await, so waitFor/MSW
        // overhead can't carry the second call past the window before the
        // throttler has a chance to queue it.
        recordDetection(queryClient, makeAlert({ watchboxId: "wb-1", detectionId: null }), opts);
        // Still inside the window — not run immediately, but queued for a
        // trailing catch-up at window end rather than dropped outright (see
        // the dedicated regression test below for why a dropped call isn't
        // always covered by "the next reconciliation already carries it").
        recordDetection(queryClient, makeAlert({ watchboxId: "wb-1", detectionId: null }), opts);
        await waitFor(() => expect(requestCount).toBe(2)); // the leading reconciliation

        await new Promise((resolve) => setTimeout(resolve, 50));
        expect(requestCount).toBe(2);

        // The trailing execution fires on its own once the window elapses —
        // no further alert needed to trigger it.
        await waitFor(() => expect(requestCount).toBe(3), { timeout: 1000 });

        // Once that window (and its trailing execution) has fully finished, a
        // fresh alert starts its own fresh leading reconciliation — a rolling
        // window, not a one-time cooldown.
        recordDetection(queryClient, makeAlert({ watchboxId: "wb-1", detectionId: null }), opts);
        await waitFor(() => expect(requestCount).toBe(4));
    });

    it("catches up a row lost when an alert arrives mid-restart, inside the throttle window", async () => {
        const queryClient = makeQueryClient();
        const { queryKey } = watchboxQueries.detections("wb-1");
        queryClient.setQueryData(queryKey, emptyPage());

        let requestCount = 0;
        let releaseInitialRequest: () => void = () => {};
        const initialRequestHeld = new Promise<void>((resolve) => {
            releaseInitialRequest = resolve;
        });
        let releaseRestartRequest: () => void = () => {};
        const restartRequestHeld = new Promise<void>((resolve) => {
            releaseRestartRequest = resolve;
        });

        server.use(
            http.get(`${API_BASE}/watchbox-detections`, async () => {
                requestCount++;
                if (requestCount === 1) {
                    // The tab's own initial fetch.
                    await initialRequestHeld;
                    return HttpResponse.json(emptyPage());
                }
                if (requestCount === 2) {
                    // The restart det-a's reconcile triggers — simulates having
                    // started before det-b committed, so it comes back without it.
                    await restartRequestHeld;
                    return HttpResponse.json({
                        ...emptyPage(),
                        content: [makeDetection({ id: "det-a" })],
                        totalElements: 1,
                    });
                }
                // The trailing catch-up reconcile, fired once the throttle
                // window elapses — by then the server has both rows.
                return HttpResponse.json({
                    ...emptyPage(),
                    content: [makeDetection({ id: "det-b" }), makeDetection({ id: "det-a" })],
                    totalElements: 2,
                });
            }),
        );

        renderHook(() => useQuery(watchboxQueries.detections("wb-1")), { queryClient });
        await waitFor(() => expect(requestCount).toBe(1));

        const opts = { reconcileThrottleMs: 500 };

        // det-a: written directly, and since the initial fetch is still in
        // flight, triggers the leading reconcile — restarting the fetch
        // (request 2, held).
        recordDetection(queryClient, makeAlert({ watchboxId: "wb-1", detectionId: "det-a" }), opts);
        await waitFor(() => expect(requestCount).toBe(2));
        // Superseded by the restart; let it drain in the background.
        releaseInitialRequest();

        // det-b: arrives while that restart is still in flight, inside the
        // throttle window. Written directly too, so the cache briefly holds
        // both rows.
        recordDetection(queryClient, makeAlert({ watchboxId: "wb-1", detectionId: "det-b" }), opts);
        expect(
            queryClient.getQueryData<Page<WatchboxDetection>>(queryKey)?.content.map((d) => d.id),
        ).toEqual(["det-b", "det-a"]);

        // The restart settles with only det-a (it started before det-b
        // committed), overwriting the optimistic write and losing det-b.
        releaseRestartRequest();
        await waitFor(() =>
            expect(
                queryClient
                    .getQueryData<Page<WatchboxDetection>>(queryKey)
                    ?.content.map((d) => d.id),
            ).toEqual(["det-a"]),
        );

        // The trailing reconcile det-b's call scheduled fires on its own once
        // the window elapses, catching det-b back up without the tab needing
        // to be reopened or refreshed.
        await waitFor(
            () =>
                expect(
                    queryClient
                        .getQueryData<Page<WatchboxDetection>>(queryKey)
                        ?.content.map((d) => d.id),
                ).toEqual(["det-b", "det-a"]),
            { timeout: 1000 },
        );
        expect(requestCount).toBe(3);
    });
});
