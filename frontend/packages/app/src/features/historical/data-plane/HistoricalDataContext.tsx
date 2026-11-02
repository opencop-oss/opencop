// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * Owns the historical-mode data plane lifecycle. Constructs one
 * `(HistoricalSession, HistoricalDataManager)` pair per historical *session* —
 * keyed on the time window + retry nonce, deliberately NOT the selected source
 * set, so changing the selection reconciles in place rather than rebuilding the
 * session (see the memo below). When the key changes a new session + manager
 * are built and the old ones disposed. The session's lifecycle (`start`/`stop`,
 * driven by its subjects' refcount) handles WS subscription, REST submission,
 * and heartbeat internally.
 *
 * Reactive observability for the panel flows through session-scoped hooks
 * (`useChunkRanges`, `useLoadError`, `useHistoricalPlan`) that subscribe via
 * `useSyncExternalStore` over the session's `subscribe`/`getVersion`. When
 * no session is active, the hooks return stable empty defaults.
 */
import {
    createContext,
    use,
    useEffect,
    useMemo,
    useSyncExternalStore,
    type ReactNode,
} from "react";
import Logger from "js-logger";
import {
    getProcessor,
    useContributeSources,
    useElementActions,
    _useIngestContext,
} from "@opencop/ingest";
import type { Source } from "@opencop/ingest";

import { useWebSocket } from "~/features/websocket/WebSocketContext";

import { HistoricalDataManager, type PlayheadSource } from "./HistoricalDataManager";
import { HistoricalSource } from "./HistoricalSource";
import {
    HistoricalSession,
    type ChunkRange,
    type ChunkStatus,
    type HistoricalPlan,
    type LoadingProgress,
    type SourceError,
} from "./HistoricalSession";
import {
    useHistoricalMode,
    useHistoricalStore,
    useIsActivated,
    useRetryNonce,
} from "./historical.store";
import { useHistoricalPerfBridge } from "./use-historical-perf-bridge.hook";

const logger = Logger.get("historical");

export type { ChunkRange, ChunkStatus, HistoricalPlan, LoadingProgress, SourceError };

// ---------------------------------------------------------------------------
// Context
// ---------------------------------------------------------------------------

interface HistoricalDataContextValue {
    manager: HistoricalDataManager | null;
    session: HistoricalSession | null;
}

const HistoricalDataContext = createContext<HistoricalDataContextValue>({
    manager: null,
    session: null,
});

export function useHistoricalDataManager(): HistoricalDataManager | null {
    return use(HistoricalDataContext).manager;
}

export function useHistoricalSession(): HistoricalSession | null {
    return use(HistoricalDataContext).session;
}

// ---------------------------------------------------------------------------
// Session-scoped reactive hooks (option C: data observed via the session's
// subscribe/getVersion contract; no store slices for chunks/plan/loadError).
// ---------------------------------------------------------------------------

const NOOP_SUBSCRIBE = () => () => {};
const EMPTY_SOURCES: Source[] = [];
const EMPTY_RANGES: readonly ChunkRange[] = [];
const NOOP_RANGES = (): readonly ChunkRange[] => EMPTY_RANGES;
const NOOP_PLAN = (): HistoricalPlan | null => null;
const NOOP_ERROR = (): SourceError | null => null;

export function useChunkRanges(): readonly ChunkRange[] {
    const session = useHistoricalSession();
    return useSyncExternalStore(
        session?.subscribe ?? NOOP_SUBSCRIBE,
        session?.getChunkRanges ?? NOOP_RANGES,
    );
}

export function useHistoricalPlan(): HistoricalPlan | null {
    const session = useHistoricalSession();
    return useSyncExternalStore(
        session?.subscribe ?? NOOP_SUBSCRIBE,
        session?.getPlan ?? NOOP_PLAN,
    );
}

export function useLoadError(): SourceError | null {
    const session = useHistoricalSession();
    return useSyncExternalStore(
        session?.subscribe ?? NOOP_SUBSCRIBE,
        session?.getError ?? NOOP_ERROR,
    );
}

const NOOP_PROGRESS = (): LoadingProgress | null => null;

export function useLoadingProgress(): LoadingProgress | null {
    const session = useHistoricalSession();
    return useSyncExternalStore(
        session?.subscribe ?? NOOP_SUBSCRIBE,
        session?.getLoadingProgress ?? NOOP_PROGRESS,
    );
}

// ---------------------------------------------------------------------------
// Provider
// ---------------------------------------------------------------------------

/**
 * Default `PlayheadSource` adapter — reads `playheadPosition` from the
 * global historical Zustand store. Module-level singleton because the store
 * itself is one-per-app today; multi-layer configurations would supply a
 * per-layer adapter instead.
 */
const STORE_PLAYHEAD: PlayheadSource = {
    subscribe(listener) {
        let prev = useHistoricalStore.getState().playheadPosition;
        return useHistoricalStore.subscribe((state) => {
            if (state.playheadPosition !== prev) {
                prev = state.playheadPosition;
                listener();
            }
        });
    },
    getCurrent() {
        return useHistoricalStore.getState().playheadPosition;
    },
};

export function HistoricalDataProvider({ children }: { children: ReactNode }) {
    const mode = useHistoricalMode();
    const sourceIds = useHistoricalStore((s) => s.sourceIds);
    const subjectTypes = useHistoricalStore((s) => s.subjectTypes);
    const from = useHistoricalStore((s) => s.from);
    const to = useHistoricalStore((s) => s.to);
    const activated = useIsActivated();
    const retryNonce = useRetryNonce();
    const { client } = useWebSocket();
    const { store } = _useIngestContext();
    const { add } = useElementActions();

    // Construct the session + manager once the user activates. Keyed on the time
    // window + retry nonce — deliberately NOT the source set — so changing the
    // selection reconciles in place (see the effect below) instead of tearing
    // the whole session down. `sourceIds` is therefore read non-reactively via
    // `getState()` at construction: DO NOT add it to the dependency array or
    // read it reactively here, or every source toggle will rebuild the session
    // and re-fetch everything. The retry nonce is the one lever that forces a
    // rebuild with the latest selection (used by the mid-load add path).
    const value = useMemo<HistoricalDataContextValue>(() => {
        // Read the selection (ids + their kinds) non-reactively — see the dep note above.
        const selected = useHistoricalStore.getState().sourceIds;
        const selectedTypes = useHistoricalStore.getState().subjectTypes;
        if (mode !== "historical" || !activated || selected.length === 0 || !from || !to) {
            return { manager: null, session: null };
        }
        const session = new HistoricalSession({
            client,
            processor: getProcessor(),
            params: { sourceIds: [...selected], subjectTypes: { ...selectedTypes }, from, to },
            // Retry (and any rebuild that keeps the same selection) bumps the
            // nonce so the fresh session's id can't collide with the stale one
            // still parked in the controller — otherwise reconcile keeps the
            // dead session and the retry silently no-ops.
            nonce: retryNonce,
        });
        const manager = new HistoricalDataManager({
            session,
            playhead: STORE_PLAYHEAD,
            store,
        });
        logger.info(
            `[timing] HistoricalDataProvider: constructed session for ` +
                `sourceIds=[${selected.join(",")}] from=${from} to=${to} retryNonce=${retryNonce}`,
        );
        return { manager, session };
        // `store` is the app-wide singleton from IngestProvider — stable for the
        // lifetime of the app, so it never actually retriggers this memo.
    }, [mode, activated, from, to, retryNonce, client, store]);

    // Contribute one ordinary Source per selected subject. The session behind
    // them is shared (one submit, one WS queue, one heartbeat); the controller
    // drives it by reference count, starting it when the first subject is
    // admitted and stopping it when the last is evicted. Selecting or
    // deselecting a subject is therefore just a reconcile — admitting a new
    // source or evicting one and purging its elements via `ownedSourceIds`.
    const historicalSources = useMemo(() => {
        const session = value.session;
        if (!session) return EMPTY_SOURCES;
        // Built from the *current* selection, so selecting or deselecting a
        // subject re-contributes the slice and the controller admits or evicts
        // that subject's source. The session's own job bookkeeping (submitting a
        // sub-request for a newly selected subject, dropping a removed one) is
        // handled by the reconcile effect below.
        return sourceIds.map((subjectId) => new HistoricalSource(session, subjectId).bind(add));
    }, [value.session, sourceIds, add]);
    useContributeSources("historical", historicalSources);

    // The manager owns no store, so its teardown is just its own subscriptions
    // and pipeline; the source's elements are purged by the controller above.
    useEffect(() => {
        if (!value.manager) return;
        return () => {
            logger.info(
                `[timing] HistoricalDataProvider: disposing manager ` +
                    `(state=${value.session?.state ?? "?"})`,
            );
            value.manager?.dispose();
        };
    }, [value]);

    // Reconcile a selection change against the active view in place.
    //
    // This is the *session's* half of a selection change; the contributed source
    // slice above is the *controller's* half, and they are complementary:
    //   - the slice decides which subjects are registered, so the controller
    //     admits or evicts them and purges an evicted subject's elements;
    //   - this effect decides what the transport does about it — submitting a
    //     sub-request for a newly selected subject, or dropping a removed
    //     subject's chunks and jobs.
    // The element deletion happens on both paths and is idempotent.
    //
    // The manager owns the add/remove/resubmit decision (see `reconcile`); the
    // only React-side concern left here is the rebuild lever — bumping the retry
    // nonce when a mid-load add needs the full set re-submitted.
    useEffect(() => {
        if (!value.manager) return;
        if (value.manager.reconcile(sourceIds, subjectTypes) === "rebuild") {
            useHistoricalStore.getState().actions.retry();
        }
    }, [value, sourceIds, subjectTypes]);

    // DEV-only: expose the active manager to the perf bridge so historical
    // rendering can be stress-tested without the backend. No-op in production.
    useHistoricalPerfBridge(value.manager);

    return <HistoricalDataContext value={value}>{children}</HistoricalDataContext>;
}
