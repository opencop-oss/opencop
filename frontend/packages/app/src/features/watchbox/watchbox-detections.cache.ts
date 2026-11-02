// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { Page } from "@opencop/api";
import type { QueryClient } from "@tanstack/react-query";
import { Throttler } from "@tanstack/react-pacer/throttler";
import { uniqBy } from "lodash";
import {
    DETECTIONS_PAGE_SIZE,
    type WatchboxAlertEvent,
    type WatchboxDetection,
} from "./watchbox.api";
import { watchboxQueries } from "./watchbox.queries";

/**
 * Minimum time between reconciliation requests for one watchbox. Bounds not
 * just a simultaneous burst (the leading edge alone collapses that to one
 * request) but a *sustained*, spread-out run of alerts that each individually
 * need to fall back — observed in practice to reach 13 requests in ~25s
 * without this, once a backend stopped sending `detectionId` on alerts.
 */
const RECONCILE_THROTTLE_MS = 2_000;

/**
 * Per-QueryClient (not module-global, so tests using their own client can't
 * leak into one another), per-watchbox reconciliation throttler.
 *
 * `trailing: true`: a call dropped inside the window still needs a catch-up.
 * The outbox-ordering guarantee only covers a reconciliation that starts
 * *after* the alert's row committed — a call that lands while an earlier
 * restart is still in flight (started before this alert's row committed)
 * isn't covered by it, and this alert has no other path back to reconciling:
 * its own direct write already landed, so it won't see "no fetch in flight"
 * and try again, and a later alert takes the direct-write path too, not a
 * reconcile. Without a trailing call, a row lost when that in-flight restart
 * settles (overwriting the optimistic write with a response that predates
 * it) stays lost until the tab is reopened or refreshed. `trailing: true`
 * schedules exactly one catch-up reconciliation at window end for whatever
 * was dropped, and it's cheap even with no tab open: `invalidateQueries`
 * defaults to `refetchType: "active"`, so with no active observer it only
 * marks the query stale rather than sending a request.
 */
const reconcileThrottlers = new WeakMap<QueryClient, Map<string, Throttler<() => void>>>();

function getReconcileThrottler(
    queryClient: QueryClient,
    watchboxId: string,
    wait: number,
): Throttler<() => void> {
    let byWatchbox = reconcileThrottlers.get(queryClient);
    if (!byWatchbox) {
        byWatchbox = new Map();
        reconcileThrottlers.set(queryClient, byWatchbox);
    }

    let throttler = byWatchbox.get(watchboxId);
    if (!throttler) {
        throttler = new Throttler(
            () => {
                reconcileNow(queryClient, watchboxId);
            },
            { wait, leading: true, trailing: true },
        );
        byWatchbox.set(watchboxId, throttler);
    }
    return throttler;
}

/**
 * Forces the detections query back in sync with the server — the fallback
 * path `recordDetection` takes when it can't trust a direct cache write.
 *
 * TanStack's `invalidateQueries` only cancels and restarts a query that is
 * *currently* fetching if that query already has data (`query.fetch`'s own
 * `state.data !== undefined && cancelRefetch` gate); a query's very first
 * fetch has no data yet, so invalidating while it's still in flight is a
 * silent no-op against it — the initial request runs to completion exactly
 * as if nothing had happened, possibly missing the row that prompted this
 * call, with nothing left to correct it afterward. So when there's no data
 * yet *and* a fetch is already running (the tab was just opened and its
 * first request hasn't landed), this waits for that request instead of
 * invalidating it, then invalidates once data exists — which *does* trigger
 * TanStack's normal cancel-and-restart, and per the outbox-ordering
 * guarantee, that restart begins after this alert's row is committed.
 */
function reconcileNow(queryClient: QueryClient, watchboxId: string): Promise<void> {
    const { queryKey } = watchboxQueries.detections(watchboxId);
    const hasData = queryClient.getQueryData(queryKey) !== undefined;
    const isFetching = queryClient.isFetching({ queryKey }) > 0;

    return !hasData && isFetching
        ? queryClient
              .fetchQuery(watchboxQueries.detections(watchboxId))
              .catch(() => {})
              .then(() => queryClient.invalidateQueries({ queryKey }))
        : queryClient.invalidateQueries({ queryKey });
}

/**
 * Adds a live alert to its watchbox's cached detections page without a request —
 * the alert already carries every field a row renders, and the processor's
 * outbox commits the detection row before publishing the alert for it, so the
 * row is safe to trust. Falls back to a throttled `reconcileNow` (a request,
 * only if the Notifications tab is open to observe it) when the write can't
 * be trusted: no stable id to reconcile against, nothing cached to add to, or
 * a fetch already in flight that could overwrite this write once it lands.
 *
 * `reconcileThrottleMs` overrides {@link RECONCILE_THROTTLE_MS}; test-only —
 * production callers should not pass it.
 */
export function recordDetection(
    queryClient: QueryClient,
    alert: WatchboxAlertEvent,
    { reconcileThrottleMs = RECONCILE_THROTTLE_MS }: { reconcileThrottleMs?: number } = {},
): void {
    const { queryKey } = watchboxQueries.detections(alert.watchboxId);
    const cachedPage = queryClient.getQueryData<Page<WatchboxDetection>>(queryKey);

    if (!alert.detectionId || !cachedPage) {
        getReconcileThrottler(queryClient, alert.watchboxId, reconcileThrottleMs).maybeExecute();
        return;
    }

    const row: WatchboxDetection = {
        id: alert.detectionId,
        watchboxId: alert.watchboxId,
        configEpoch: alert.configEpoch,
        sourceId: alert.sourceId,
        objectId: alert.objectId,
        eventId: alert.eventId,
        effectiveEventTime: alert.effectiveEventTime,
        kind: alert.kind,
        objectDisplayName: alert.objectDisplayName,
        // Not rendered; replaced by the server's receipt time on the next fetch.
        detectedAt: alert.effectiveEventTime,
    };

    queryClient.setQueryData(queryKey, (page: Page<WatchboxDetection> | undefined) => {
        if (!page) return page;
        const isNew = !page.content.some((existing) => existing.id === row.id);
        return {
            ...page,
            content: uniqBy([row, ...page.content], "id").slice(0, DETECTIONS_PAGE_SIZE),
            totalElements: page.totalElements + (isNew ? 1 : 0),
        };
    });

    // A fetch that began before this row was committed would overwrite the
    // write above once it settles — restart it so it lands after the commit.
    if (queryClient.isFetching({ queryKey }) > 0) {
        getReconcileThrottler(queryClient, alert.watchboxId, reconcileThrottleMs).maybeExecute();
    }
}
