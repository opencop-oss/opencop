// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * Historical-mode data plane: the playhead → `dataFilterRange` projection for
 * one historical session. Delivery is delegated to {@link HistoricalSession}
 * (REST, WS, decode, tag, audit, heartbeat); the store and render pipeline are
 * the shared, app-wide ones. This class routes session output into that store,
 * derives the GPU sliding-window range from the current playhead, and asks the
 * pipeline to rebuild when the plan or the playhead changes.
 *
 * Lifetime: one manager per session. The provider re-creates the manager
 * (and the session it wraps) when params change, so this class has no
 * `clear()`/`load()` of its own — only `dispose`.
 *
 * The {@link PlayheadSource} dependency is constructor-injected so the
 * manager has no global imports, leaving the door open for multi-layer
 * configurations (per-layer playhead) without refactoring this class again.
 */
import { ElementStore } from "@opencop/ingest";
import { DateTime } from "luxon";

import { computeActiveBucketIndex } from "./HistoricalChunk";
import type { HistoricalSession } from "./HistoricalSession";
import {
    installHistoricalDataManagerDiagnostics,
    type HistoricalDataManagerDiagnostics,
} from "./HistoricalDataManagerDiagnostics";

/**
 * Subscribable playhead position abstraction. The default implementation
 * wraps the global historical Zustand store; tests and future per-layer
 * configurations can supply alternatives.
 */
export interface PlayheadSource {
    subscribe(listener: () => void): () => void;
    getCurrent(): string | null;
}

export interface HistoricalDataManagerDeps {
    session: HistoricalSession;
    playhead: PlayheadSource;
    /**
     * The shared, app-wide {@link ElementStore}. Historical elements live in the
     * same store as live ones — they are distinguished by `elementClass`, which
     * keys them per version and exempts them from stale eviction — so picking,
     * selection, and the detail panel all resolve against a single store.
     */
    store: ElementStore;
}

export class HistoricalDataManager {
    private readonly _session: HistoricalSession;
    private readonly _playhead: PlayheadSource;
    private readonly _store: ElementStore;

    /** Cached active bucket index derived from the current playhead. */
    private _activeBucketIndex = 0;

    /**
     * Sliding-window range fed into deck.gl's DataFilterExtension.
     * `(playheadRel, playheadRel + bucketWidthMs]` selects the version whose
     * effective-stale offset (encoded into each element's `filterValue`) falls
     * within one bucket-width ahead of the playhead. Because lifetimes are
     * chunk-bounded and non-overlapping per object_id, this picks exactly one
     * version per object at any playhead.
     */
    private _dataFilterRange: [number, number] = [0, 0];

    private _planSeen = false;
    private _unsubscribePlayhead: (() => void) | null = null;
    private _unsubscribeSession: (() => void) | null = null;
    private _diagnostics: HistoricalDataManagerDiagnostics | null = null;
    private readonly _listeners = new Set<() => void>();

    constructor(deps: HistoricalDataManagerDeps) {
        this._session = deps.session;
        this._playhead = deps.playhead;
        this._store = deps.store;

        // Fallback delivery path. In production each subject is registered as a
        // `HistoricalSource`, and the session routes elements to those
        // sinks instead. This binding covers the sessions that have no subject
        // sources — the DEV perf bridge's synthetic streams — so they still land
        // in the store.
        this._session.onElements((elements) => {
            this._store.add(elements);
        });

        // Re-seed bucket / filter range when the plan first arrives.
        this._unsubscribeSession = this._session.subscribe(() => {
            const planNowSeen = this._session.getPlan() !== null;
            if (planNowSeen && !this._planSeen) {
                this._planSeen = true;
                this.handlePlayheadChange(this._playhead.getCurrent());
            }
        });

        // Recompute filter range on playhead moves; pipeline rebuild is
        // triggered via `requestRebuild()` so the diffable layers pick up the
        // new `filterRange` prop on the next render.
        this._unsubscribePlayhead = this._playhead.subscribe(() => {
            this.handlePlayheadChange(this._playhead.getCurrent());
        });

        if (import.meta.env.DEV) {
            this._diagnostics = installHistoricalDataManagerDiagnostics(this);
        }
    }

    private handlePlayheadChange(playheadIso: string | null): void {
        const plan = this._session.getPlan();
        if (plan === null) return;

        const playheadMs = playheadIso === null ? null : DateTime.fromISO(playheadIso).toMillis();
        const nextIndex = computeActiveBucketIndex(
            playheadMs,
            plan.fromMs,
            plan.bucketWidthMs,
            plan.totalChunkCount,
        );

        const nextRange = this.computeDataFilterRange(playheadMs, nextIndex);
        const indexChanged = nextIndex !== this._activeBucketIndex;
        const rangeChanged =
            nextRange[0] !== this._dataFilterRange[0] || nextRange[1] !== this._dataFilterRange[1];
        if (!indexChanged && !rangeChanged) return;

        if (indexChanged) {
            this._diagnostics?.onPlayheadBucketChange(
                this._activeBucketIndex,
                nextIndex,
                playheadMs,
            );
        }

        this._activeBucketIndex = nextIndex;
        this._dataFilterRange = nextRange;
        if (rangeChanged) for (const listener of this._listeners) listener();
    }

    /**
     * Compute the sliding-window range `(playheadRel, playheadRel + bucketWidthMs]`,
     * normalized so deck.gl's inclusive-on-both-ends range check produces the
     * correct half-open semantics. When the playhead is null we fall back to
     * the active bucket's right edge so the right-most chunk renders by default.
     */
    private computeDataFilterRange(
        playheadMs: number | null,
        bucketIndex: number,
    ): [number, number] {
        const plan = this._session.getPlan();
        if (plan === null) return [0, 0];
        const effectivePlayheadMs =
            playheadMs ?? plan.fromMs + (bucketIndex + 1) * plan.bucketWidthMs - 1;
        const playheadRel = effectivePlayheadMs - plan.fromMs;
        return [playheadRel + 1, playheadRel + plan.bucketWidthMs];
    }

    // -----------------------------------------------------------------------
    // Accessors used by the render sink and diagnostics
    // -----------------------------------------------------------------------

    /**
     * True once data is being delivered (`syncing`) or delivery has finished
     * (`enabled`).
     */
    get loaded(): boolean {
        return this._session.state === "syncing" || this._session.state === "enabled";
    }

    get session(): HistoricalSession {
        return this._session;
    }

    get store(): ElementStore {
        return this._store;
    }

    get activeBucketIndex(): number {
        return this._activeBucketIndex;
    }

    get dataFilterRange(): [number, number] {
        return this._dataFilterRange;
    }

    /** Listens for changes to {@link dataFilterRange}; returns the unsubscribe. */
    subscribe(listener: () => void): () => void {
        this._listeners.add(listener);
        return () => {
            this._listeners.delete(listener);
        };
    }

    /**
     * Drop the given sources from the active view in place — the session forgets
     * their chunks/jobs and the store deletes their elements (the pipeline
     * rebuilds from the remove events). No re-submit; the remaining sources and
     * the time window are untouched.
     */
    removeSources(ids: string[]): void {
        if (ids.length === 0) return;
        this._session.removeSources(ids);
        for (const id of ids) this._store.deleteBySource(id);
    }

    /**
     * Add the given sources to the active view in place — the session submits a
     * sub-request for just the new sources and streams them into the shared
     * store (the session routes their elements to the subject sinks). No store
     * pre-touch is needed; new elements simply arrive. Valid only post-load —
     * see {@link HistoricalSession.addSources}; the provider routes a mid-load
     * add to a full re-submit instead. `subjectTypes` carries the kind of each
     * new id (data source vs layer) so the sub-request submits the right
     * `subjectType` per subject.
     */
    addSources(ids: string[], subjectTypes: Record<string, "SOURCE" | "LAYER">): void {
        if (ids.length === 0) return;
        this._session.addSources(ids, subjectTypes);
    }

    /**
     * Reconcile the live view against a new source selection *in place*, owning
     * the add/remove/resubmit decision so callers never have to inspect the
     * session's state machine:
     *   - Sources no longer selected are dropped in place (chunks + elements).
     *   - Newly selected sources stream in via a sub-request when the source can
     *     accept an in-place add (see {@link HistoricalSession.canAcceptInPlaceAdd}).
     *   - Newly selected sources that can't join in place (mid-load, when the
     *     backend admission slot is occupied) make this return `"rebuild"`,
     *     asking the caller to re-submit the full set (the view stays activated;
     *     the caller bumps the retry nonce). Otherwise it returns `"in-place"`.
     *
     * Returning a verdict (rather than rebuilding here) keeps the React-owned
     * concerns — the memo that constructs the session, the retry nonce — out of
     * the data plane, while the admission decision stays where the session it
     * inspects actually lives.
     */
    reconcile(
        nextSourceIds: readonly string[],
        nextSubjectTypes: Record<string, "SOURCE" | "LAYER">,
    ): "in-place" | "rebuild" {
        const current = this._session.sourceParams.sourceIds;
        const removed = current.filter((id) => !nextSourceIds.includes(id));
        const added = nextSourceIds.filter((id) => !current.includes(id));
        if (removed.length > 0) this.removeSources(removed);
        if (added.length === 0) return "in-place";
        if (this._session.canAcceptInPlaceAdd()) {
            this.addSources(added, nextSubjectTypes);
            return "in-place";
        }
        return "rebuild";
    }

    dispose(): void {
        this._unsubscribePlayhead?.();
        this._unsubscribePlayhead = null;
        this._unsubscribeSession?.();
        this._unsubscribeSession = null;
        this._diagnostics?.dispose();
        this._diagnostics = null;
        this._listeners.clear();
        // The store and pipeline are shared and outlive this manager — never
        // dispose them here.
        // The historical elements it holds are purged by the controller when the
        // per-subject sources are evicted (see `HistoricalSource.ownedSourceIds`).
    }
}
