// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * Historical-mode transport session. Owns the entire delivery pipeline for one
 * `(sourceIds, from, to)` query: REST submission, WebSocket subscription,
 * multi-part chunk assembly, mapper-decode + per-element `filterValue`
 * tagging, completion audit, and heartbeat watchdog.
 *
 * **It is the session, not the registration unit.** One query fans out to N
 * per-subject {@link Source}s ({@link HistoricalSource}) — those are what
 * the `SourceController` registers/enables/disables. They drive this shared
 * session via {@link retain}/{@link release} (refcount: first subject starts the
 * transport, last one stops it), and it routes each subject only its own
 * elements via {@link attachSubject}. See {@link HistoricalSource} for
 * why the split exists.
 *
 * **Multi-source.** One request fans out to one backend job per selected source
 * (`POST /v1/history/tracks` with several subjects); each job streams its own
 * timeline of time-chunks, and the binary frames are stamped only with a
 * `history-job-id` header (the payload carries no source id). So this source
 * builds a `jobId → sourceId` map from the ack and keys its chunk ledger by
 * `(sourceId, chunkKey)` — two sources legitimately emit the same `chunkKey`
 * for the same UTC slot, so a `chunkKey`-only ledger would collide. The job
 * completes (state → `enabled`) only once every job has reported completion.
 * Decoded elements all flow into one shared store keyed by `source + id`, so
 * the map renders every source together.
 *
 * Runs a small `state` machine (reusing the ingest `SourceState` enum), driven
 * by `start`/`stop` off the subjects' refcount — NOT by the controller. It is
 * deliberately not a `Source`; the subjects are.
 *
 *   - `disabled` → idle (pre-construct or post-dispose).
 *   - `syncing` → REST submitted; chunks streaming; awaiting all `history.job.completed`.
 *   - `enabled` → every job complete (regardless of audit completeness).
 *   - `error`   → REST failure, heartbeat timeout, or `history.job.failed`.
 *
 * **Errors are independent of state.** `getError()` is populated only by
 * transport failures (REST submit failure, heartbeat timeout, or
 * `history.job.failed`) — those also move `state` to `"error"`. Audit gaps
 * (chunks announced by the plan but not delivered by `history.job.completed`)
 * leave `state === "enabled"` and `getError() === null`; they're surfaced
 * through `ChunkRange.status` on the per-chunk overlay so the histogram
 * itself communicates the gap. The data already on the map remains
 * scrubbable.
 *
 * Reactive surface: `subscribe(listener)` + `getVersion()` follow the
 * `useSyncExternalStore`-friendly contract.
 * Consumers call `getPlan()` / `getChunkRanges()` / `getError()` for the
 * snapshot; element output flows to subjects via {@link attachSubject}, with a
 * no-subject fallback sink ({@link onElements}) for the perf bridge.
 */
import type { Element } from "@opencop/elements";
import { ElementMessage, HistoricalChunkPayload } from "@opencop/proto";
import { submitHistoricalTracks, ApiError } from "@opencop/api";
import { decodeHistoricalElement, type MessageProcessor, type SourceState } from "@opencop/ingest";
import type { OCMPClient } from "@opencop/websocket";
import { DateTime } from "luxon";
import Logger from "js-logger";

import type { HistoryEvent, HistoryJobPlanEvent } from "./history-events";
import { parseHistoryEvent } from "./history-events";
import { RESOLUTION_WIDTH_MS, type HistoricalChunkResolution } from "./HistoricalChunk";
import {
    installHistoricalSessionDiagnostics,
    type HistoricalSessionDiagnostics,
} from "./HistoricalSessionDiagnostics";

const logger = Logger.get("historical");

const HISTORY_DESTINATION = "/user/queue/history" as const;

// OCMP frame-header names/values for the historical queue. Kept as constants so a
// rename can't silently diverge between the router and the binary gate. (Tests pin
// the literal wire strings on purpose, so they keep catching an accidental change.)
const HISTORY_TYPE_HEADER = "history-type" as const;
const HISTORY_CHUNK_DATA_TYPE = "history.chunk.data" as const;
const HISTORY_JOB_ID_HEADER = "history-job-id" as const;

/** UTC ISO string from epoch ms — house-style alternative to `new Date(ms).toISOString()`. */
function msToIso(ms: number): string {
    return DateTime.fromMillis(ms, { zone: "utc" }).toISO() ?? new Date(ms).toISOString();
}

/** Backend-derived ingest-window metadata, populated from `history.job.plan`. */
export interface HistoricalPlan {
    fromMs: number;
    bucketWidthMs: number;
    totalChunkCount: number;
    resolution: HistoricalChunkResolution;
}

/** Streamed-chunk progress reported by {@link HistoricalSession.getLoadingProgress}. */
export interface LoadingProgress {
    /** Chunks delivered so far, across every subject. */
    readonly loaded: number;
    /** Chunks the plan expects in total, across every subject. */
    readonly total: number;
    /** Subjects (jobs) the query fanned out to — 1 makes a chunk a time slice. */
    readonly subjects: number;
}

/**
 * Per-chunk classification surfaced to the histogram overlay. Only ever
 * assigned at job completion — during streaming every received chunk is
 * `Ok` and missing chunks have no entry, so loading never paints red. See
 * `auditJobCompletion` for the classification rules.
 *
 * Named members so the construction sites never hand-type the tokens. The
 * chart (`@opencop/ui`) is a leaf package and can't import this; it keeps its
 * own mirror (`CHUNK_STATUS` in `Histogram`), so keep the two in sync.
 */
export const CHUNK_STATUS = {
    Ok: "ok",
    Missing: "missing",
    IncompleteParts: "incomplete-parts",
} as const;
export type ChunkStatus = (typeof CHUNK_STATUS)[keyof typeof CHUNK_STATUS];

/** Public per-chunk view consumed by the histogram overlay. */
export interface ChunkRange {
    /** Which selected source this chunk belongs to (colored chunk bars stack per source). */
    readonly sourceId: string;
    readonly chunkIndex: number;
    readonly start: string;
    readonly end: string;
    /** Element count for the chunk. Zero for `"missing"`. */
    readonly count: number;
    readonly status: ChunkStatus;
    /** Populated only when `status === "incomplete-parts"`. */
    readonly parts?: { readonly seen: number; readonly expected: number };
}

/**
 * Lightweight record of an ingested chunk, keyed in the ledger by
 * `(sourceId, chunkKey)`. Chunks can be delivered in multiple parts; this
 * entry accumulates across parts so `elementCount` reflects the full chunk
 * once all parts have arrived.
 *
 * Keyed by `chunkKey` (stable per wall-clock time slot) rather than
 * `chunkIndex` because the backend can legitimately reuse `chunkIndex`
 * across distinct time slots within one job. And scoped by `sourceId`
 * because separate sources (jobs) emit the same `chunkKey` for the same UTC
 * slot — see the regression tests "distinct chunkKeys with colliding
 * chunkIndex" and "two sources sharing a chunkKey".
 */
export interface HistoricalChunkEntry {
    sourceId: string;
    chunkKey: string;
    chunkIndex: number;
    fromMs: number;
    toMs: number;
    elementCount: number;
    frameCount: number;
    seenParts: Set<number>;
    maxPartCount: number;
    finalForChunkSeen: boolean;
}

export interface HistoricalSessionParams {
    /** One or more selected subjects; the backend fans out one job per subject. */
    sourceIds: string[];
    /**
     * Subject kind per id (data source vs layer), sent as each subject's
     * `subjectType` at submit. Ids absent from the map default to `"SOURCE"`.
     */
    subjectTypes: Record<string, "SOURCE" | "LAYER">;
    from: string;
    to: string;
}

/**
 * Classified transport-failure error surfaced via `getError()`. The display
 * layer maps `kind` to a localized message; the source itself stays free of
 * i18n.
 *
 * The REST kinds (everything except `heartbeatTimeout` and `jobFailed`) are
 * driven by the stable `code` field on the backend's `GlobalErrorResponse`.
 * `badRequest` and `generic` are fallbacks that carry the verbatim server
 * message in case a code we don't recognize ships, or the failure isn't a
 * `GlobalOpenCopException` at all (proxy errors, network failures, etc.).
 */
export type SourceError =
    // REST submit — classified off backend `body.code`
    | { kind: "concurrentLimit" } // HISTORICAL_CONCURRENT_LIMIT (422)
    | { kind: "rangeInvalid" } // HISTORICAL_RANGE_INVALID (400)
    | { kind: "rangeTooLarge" } // HISTORICAL_RANGE_TOO_LARGE (422)
    | { kind: "tooManyChunks" } // HISTORICAL_TOO_MANY_CHUNKS (422)
    | { kind: "sourcesRequired" } // HISTORICAL_SOURCES_REQUIRED (400)
    | { kind: "filtersNotSupported" } // HISTORICAL_FILTERS_NOT_SUPPORTED (422)
    | { kind: "layerNotSupported" } // HISTORICAL_LAYER_NOT_SUPPORTED (422)
    | { kind: "subjectForbidden" } // HISTORICAL_SUBJECT_FORBIDDEN (403)
    | { kind: "badRequest"; message: string } // Other 400 (unrecognized code or no code)
    | { kind: "generic"; status: number; message: string } // Any other failure
    // WS / source-level
    | { kind: "heartbeatTimeout" }
    // The backend cancelled this job because the same user issued a newer
    // historical request (one-job-at-a-time-per-user). Carried on the wire as a
    // `history.job.failed` with stage `CANCELLED`; surfaced as its own retryable
    // kind so the message reads "stopped by a newer request" rather than "failed".
    | { kind: "jobSuperseded" }
    | { kind: "jobFailed"; stage: string; reason: string };

/**
 * Sink for one subject's share of a session's output. Implemented by
 * {@link HistoricalSource}, which is what the SourceController actually
 * registers.
 */
export interface SubjectSink {
    deliver(elements: Element[]): void;
    setState(state: SourceState): void;
}

export interface HistoricalSessionDeps {
    client: OCMPClient;
    processor: MessageProcessor;
    params: HistoricalSessionParams;
    /**
     * Discriminates sessions rebuilt for the *same* query (same sources + time
     * window) so their ids never collide. A retry (or any provider rebuild that
     * keeps the selection) bumps this; without it the rebuilt session's id would
     * equal the stale one's, and `SourceController.reconcile` — which keeps the
     * existing instance on an id match — would silently keep the dead session
     * and drop the fresh one, so the retry would never fire. See the id
     * derivation in the constructor.
     */
    nonce?: number;
}

const LOAD_HEARTBEAT_TIMEOUT_MS = 90_000;

// Rate-limit for the production discard warning so a superseded job's foreign-frame
// tail can't flood the console. The first pre-ack discard always logs (it's the
// high-value signal); subsequent discards are throttled to one line per interval.
const DISCARD_LOG_THROTTLE_MS = 1_000;

// Safety cap on the pre-ack frame buffer (see `bufferPreAckFrame`). It spans a
// single submit's REST round-trip — normally a few hundred frames at most — so the
// cap is a backstop against a pathologically slow ack, not a normal-path limit.
// Buffered payloads are raw (compressed) WS frames bounded by the backend's ~1 MiB
// per-part cap, so the worst case before the cap engages is ~1024 × 1 MiB ≈ 1 GiB of
// transient allocation, freed the moment the ack resolves. To lower that ceiling,
// switch the cap from frame-count to bytes.
const MAX_PREACK_BUFFER = 1_024;

/** A raw WebSocket frame held in the pre-ack buffer, replayed verbatim once the ack lands. */
type PreAckFrame = { payload: Uint8Array; headers?: Record<string, string> };

export class HistoricalSession {
    /** Session-unique id (see the constructor); NOT a store source id. */
    readonly id: string;
    /**
     * Transport lifecycle state, reusing the ingest `SourceState` enum (a good
     * fit: `syncing`/`enabled`/`error`). This is a session, not a `Source` — it
     * is never registered with the controller; its subjects are (see class doc).
     */
    private _state: SourceState = "disabled";
    /** No-subject delivery sink (perf bridge); see {@link onElements}. */
    private _fallback?: (elements: Element[]) => void;

    private readonly client: OCMPClient;
    private readonly processor: MessageProcessor;
    private readonly params: HistoricalSessionParams;

    private _abortController: AbortController | null = null;
    private _requestId: string | null = null;
    // The set of job ids this request spawned — one per accepted subject. A binary
    // chunk frame is accepted only when its `history-job-id` header is in this set.
    private readonly _jobIds = new Set<string>();
    // Maps each job id back to the source it queries, so a frame's `history-job-id`
    // header (the only source attribution on the wire) resolves to a source id.
    private readonly _jobToSource = new Map<string, string>();
    // Jobs that have reported `history.job.completed`. The query finalizes only
    // once this covers every job id.
    private readonly _completedJobs = new Set<string>();
    private _plan: HistoricalPlan | null = null;
    // Ledger keyed by `ledgerKey(sourceId, chunkKey)`.
    private _chunks = new Map<string, HistoricalChunkEntry>();
    private _chunkRanges: ChunkRange[] = [];
    private _error: SourceError | null = null;
    private _disposed = false;
    /**
     * Bumped on every `onStart`. A `submit()` captures the current value and
     * bails if it changed by the time its async ack resolves — so a submit from
     * a previous start cycle can't apply its (stale, possibly superseded) jobs
     * over a newer one. `_disposed` alone is insufficient: a stop→re-start
     * resets it to `false` before the stale continuation runs.
     */
    private _generation = 0;
    private _loadingProgress: LoadingProgress | null = null;

    private _loadHeartbeatTimer: ReturnType<typeof setTimeout> | null = null;

    private _pendingDecodedPayloads: { payload: HistoricalChunkPayload; sourceId: string }[] = [];
    private _drainScheduled = false;
    // Set while `drainPreAckBuffer` replays buffered frames in a synchronous loop.
    // Suppresses the per-frame chunk-range recompute + bump in `handleBinaryChunk`
    // (each an O(n) sort over a growing ledger → O(n²) across the batch); the replay
    // does one recompute + one bump at the end instead. The live-streaming path, which
    // handles one frame per tick, is unaffected.
    private _replayingPreAck = false;

    private _version = 0;
    private readonly _listeners = new Set<() => void>();

    private _diagnostics: HistoricalSessionDiagnostics | null = null;

    private _t0Ms = 0;
    private _planAtMs = 0;
    private _chunksArrived = 0;

    // Production-visible counter for frames discarded because their job id belongs to
    // another tab or a superseded request (see `logForeignDiscard`). Frames arriving
    // during our own submit's ack window are buffered + replayed (not discarded), so
    // the health signal for that race is `preAckReplayed` on the job.completed line —
    // a discard here is genuinely foreign, expected cross-tab noise on the shared queue.
    private _discardedForeignCount = 0;
    private _lastDiscardLogMs = 0;

    // Pre-ack frame buffer. Frames arriving while a submit is in flight (`_ackPending`)
    // are held here and replayed once its jobs are known — see `wsHandler` /
    // `drainPreAckBuffer` for the why. `_ackPending` covers every submit (initial load
    // and in-place `addSources`) through the one path.
    private _ackPending = false;
    private _preAckBuffer: PreAckFrame[] = [];
    private _preAckBufferedTotal = 0;

    constructor(deps: HistoricalSessionDeps) {
        // Session-unique id — survives same-source re-loads with different
        // time ranges and gives diagnostics a label that uniquely identifies
        // this load. Sorted so the same source set yields the same id
        // regardless of selection order. The trailing `nonce` disambiguates
        // rebuilds of the same query (e.g. retry) so a stale session and its
        // replacement can never share an id in the controller (see `nonce` on
        // {@link HistoricalSessionDeps}).
        this.id = `${[...deps.params.sourceIds].sort().join("+")}|${deps.params.from}|${deps.params.to}|${deps.nonce ?? 0}`;
        this.client = deps.client;
        this.processor = deps.processor;
        this.params = deps.params;
        if (import.meta.env.DEV) {
            this._diagnostics = installHistoricalSessionDiagnostics(this);
        }
    }

    // -----------------------------------------------------------------------
    // Reactive observability surface (useSyncExternalStore contract)
    // -----------------------------------------------------------------------

    readonly subscribe = (listener: () => void): (() => void) => {
        this._listeners.add(listener);
        return () => {
            this._listeners.delete(listener);
        };
    };

    readonly getVersion = (): number => this._version;

    /** Current transport state. Subjects mirror it via {@link notifySubjects}. */
    get state(): SourceState {
        return this._state;
    }

    private bump(): void {
        this._version++;
        for (const listener of this._listeners) listener();
    }

    /** Set state, fan it out to subjects, and bump listeners. */
    private setState(state: SourceState): void {
        this._state = state;
        // Subjects share the session's fate: the transport is one submit, one
        // queue, one heartbeat, so a failure or completion applies to all of
        // them. (Per-job granularity is available via `_completedJobs` if a
        // future UI wants to surface partial completion.)
        this.notifySubjects(state);
        this.updateLoadingProgress();
        this.bump();
    }

    // -----------------------------------------------------------------------
    // Public state getters
    // -----------------------------------------------------------------------

    readonly getPlan = (): HistoricalPlan | null => this._plan;
    readonly getChunkRanges = (): readonly ChunkRange[] => this._chunkRanges;
    readonly getError = (): SourceError | null => this._error;

    /** Ledger key combining the source and the per-slot chunk key. */
    private ledgerKey(sourceId: string, chunkKey: string): string {
        return `${sourceId}\u0000${chunkKey}`;
    }

    /** Look up a chunk ledger entry by its `(sourceId, chunkKey)`. */
    chunkEntry(sourceId: string, chunkKey: string): HistoricalChunkEntry | undefined {
        return this._chunks.get(this.ledgerKey(sourceId, chunkKey));
    }

    /**
     * Drop one or more sources from this active query *in place* — no
     * re-submit. Removes their chunk ledger entries, job tracking, and queued
     * payloads, and shrinks the active source set. The owning manager deletes
     * their elements from the store. Used when the operator deselects a source
     * while the view is active: the time window and the remaining sources are
     * unaffected, so re-streaming everything would be wasteful.
     */
    removeSources(ids: string[]): void {
        const remove = new Set(ids);
        if (remove.size === 0) return;
        this.params.sourceIds = this.params.sourceIds.filter((id) => !remove.has(id));
        for (const [key, entry] of this._chunks) {
            if (remove.has(entry.sourceId)) this._chunks.delete(key);
        }
        for (const [jobId, sourceId] of this._jobToSource) {
            if (remove.has(sourceId)) {
                this._jobToSource.delete(jobId);
                this._jobIds.delete(jobId);
                this._completedJobs.delete(jobId);
            }
        }
        this._pendingDecodedPayloads = this._pendingDecodedPayloads.filter(
            (p) => !remove.has(p.sourceId),
        );
        this.recomputeChunkRanges();

        // Removing a still-loading source can leave every remaining job already
        // complete — finalize so the view leaves the syncing state.
        if (
            this.state === "syncing" &&
            this._plan &&
            this._jobIds.size > 0 &&
            this._completedJobs.size >= this._jobIds.size
        ) {
            this.auditJobCompletion(this._plan.totalChunkCount);
            this.clearLoadHeartbeat();
            this.setState("enabled");
            return;
        }

        this.updateLoadingProgress();
        this.bump();
    }

    /**
     * Whether a source can be streamed into this query *in place* (via
     * {@link addSources}) rather than forcing a full re-submit — the single
     * authority on the backend admission rule both the source and the manager
     * depend on.
     *
     * The backend admits one in-flight historical request per user: a new
     * submission supersedes/cancels any prior one, and an explicit over-limit
     * submit returns 422 (`concurrentLimit`). So a second, additive sub-request
     * is only safe once the current load has finished and freed that slot
     * (`state === "enabled"`). Mid-load the slot is occupied, so the caller
     * ({@link HistoricalDataManager.reconcile}) must re-submit the full set
     * instead. If the backend ever admits concurrent requests, this predicate is
     * the one place to relax.
     */
    canAcceptInPlaceAdd(): boolean {
        return this.state === "enabled";
    }

    /**
     * Add one or more sources to this active query *in place* — no re-activate.
     * Only valid when {@link canAcceptInPlaceAdd} holds; the new jobs stream
     * their timelines into the existing ledger and shared store alongside the
     * sources already loaded, and the query re-enters `syncing` until every job
     * (old + new) has completed. The guard makes the admission contract explicit
     * and is a no-op if the caller violates it. `subjectTypes` carries the kind
     * of each new id so the sub-request submits the right `subjectType`.
     */
    addSources(ids: string[], subjectTypes: Record<string, "SOURCE" | "LAYER">): void {
        const add = ids.filter((id) => !this.params.sourceIds.includes(id));
        if (add.length === 0) return;
        if (!this.canAcceptInPlaceAdd()) return;
        this.params.sourceIds = [...this.params.sourceIds, ...add];
        this.params.subjectTypes = { ...this.params.subjectTypes, ...subjectTypes };
        // Back to syncing while the new jobs stream; `manager.loaded` stays true
        // across the transition so the already-loaded data keeps rendering.
        this.setState("syncing");
        void this.submit(add);
    }

    /**
     * DEV/perf only: stand up a synthetic plan + `enabled` state so the perf
     * bridge can drive historical rendering without the backend (no REST/WS/
     * Kafka). Setting `_plan` unblocks the manager's playhead → `dataFilterRange`
     * math (which early-returns while the plan is null), and `setState("enabled")`
     * makes `manager.loaded` true and `bump()`s the manager's subscriber so the
     * plan-seen path fires. No-op outside DEV. See {@link perf-bridge.ts}.
     */
    __beginPerfSession(plan: HistoricalPlan): void {
        if (!import.meta.env.DEV) return;
        this.resetForPerf();
        this._plan = plan;
        this.setState("enabled");
    }

    /**
     * DEV/perf only: begin a synthetic *streaming* session. Seeds the job-scoped
     * routing state the REST ack normally sets (`_requestId`, `_jobIds`,
     * `_jobToSource`) plus the plan, and enters `syncing`. Chunks then arrive
     * over time via {@link __perfDeliverChunk}, finished by
     * {@link __perfCompleteStream}. No-op outside DEV. See {@link perf-bridge.ts}.
     */
    __beginPerfStream(jobId: string, plan: HistoricalPlan): void {
        if (!import.meta.env.DEV) return;
        this.resetForPerf();
        this._requestId = "perf-request";
        this._jobIds.clear();
        this._jobIds.add(jobId);
        this._jobToSource.set(jobId, this.perfSourceId());
        this._plan = plan;
        const now = performance.now();
        this._t0Ms = now;
        this._planAtMs = now;
        this._chunksArrived = 0;
        this.setState("syncing");
    }

    private perfSourceId(): string {
        return this.params.sourceIds[0] ?? "perf";
    }

    /**
     * Drop any state the real session left behind. Activating historical mode
     * enables a real source (REST submit + WS subscribe), so by the time the
     * perf bridge starts a synthetic session the backend may already have
     * delivered chunks. Without this reset, synthetic chunks coexist with real
     * ones at the same wall-clock `fromMs` (different `chunkKey`), and
     * `recomputeChunkRanges` then emits two chunk ranges with the same `start`
     * — duplicate React keys in the histogram overlay. Overwriting `_jobIds`
     * (in the callers) makes the binary gate reject any further real frames.
     */
    private resetForPerf(): void {
        this._chunks.clear();
        this._chunkRanges = [];
        this._completedJobs.clear();
        this._jobToSource.clear();
        this._pendingDecodedPayloads = [];
        this._drainScheduled = false;
    }

    /**
     * DEV/perf only: deliver one chunk's worth of already-built, filter-tagged
     * elements. Mirrors the observable side of `handleBinaryChunk` + the drain:
     * records the chunk-range ledger entry (so the histogram chunk overlay
     * paints), routes the elements through the same {@link deliver} path the
     * real drain uses (one `store.add` → one pipeline rebuild + GPU upload), and
     * bumps subscribers. Skips the protobuf decode + mapper step by design
     * (observable-streaming fidelity).
     */
    __perfDeliverChunk(
        chunkIndex: number,
        fromMs: number,
        toMs: number,
        elements: Element[],
    ): void {
        if (!import.meta.env.DEV) return;
        const sourceId = this.perfSourceId();
        const chunkKey = `perf:${fromMs}`;
        this._chunks.set(this.ledgerKey(sourceId, chunkKey), {
            sourceId,
            chunkKey,
            chunkIndex,
            fromMs,
            toMs,
            elementCount: elements.length,
            frameCount: 1,
            seenParts: new Set<number>([0]),
            maxPartCount: 1,
            finalForChunkSeen: true,
        });
        this.recomputeChunkRanges();
        this._chunksArrived++;
        this.deliver(elements);
        this.updateLoadingProgress();
        this.bump();
    }

    /**
     * DEV/perf only: finish a synthetic stream — run the completion audit (final
     * chunk-range classification) and enter `enabled`, mirroring the
     * `history.job.completed` path. No-op outside DEV.
     */
    __perfCompleteStream(totalChunks: number): void {
        if (!import.meta.env.DEV) return;
        this.auditJobCompletion(totalChunks);
        this.clearLoadHeartbeat();
        this.setState("enabled");
    }

    /**
     * Streaming progress while state is `syncing` and a plan has been received;
     * otherwise null. Reference-stable while the values are unchanged so
     * `useSyncExternalStore` won't re-render.
     *
     * Counted in *delivered chunks against planned chunks*: `total` is the
     * per-subject plan chunk count multiplied by the number of jobs, and every
     * chunk that arrives advances `loaded` immediately. `subjects` tells the
     * caller which unit that is, so the status bar can say "time slices" for a
     * single subject and "data parts" once the two diverge.
     *
     * This replaces an earlier rule that counted fully-covered wall-clock slices
     * — a slot only counted once *every* subject had delivered it. That avoided
     * multiplying the denominator by the subject count, but it made the slowest
     * subject pace the whole readout: a multi-source query has been observed to
     * sit at a near-zero count for many seconds after most of its chunks had
     * already arrived and rendered. Reading near-zero while most of the data is
     * on screen is a worse error than a denominator that counts per-subject
     * work, so the readout now tracks what has actually arrived.
     *
     * Pure read: the cached `_loadingProgress` is recomputed by mutation sites
     * (`handleBinaryChunk`, `handlePlan`, `setState`) before they call `bump()`.
     */
    readonly getLoadingProgress = (): LoadingProgress | null => this._loadingProgress;

    /** Recompute `_loadingProgress` and call from any mutation that affects it. */
    private updateLoadingProgress(): void {
        if (this.state !== "syncing" || !this._plan) {
            this._loadingProgress = null;
            return;
        }
        // Every job sends `totalChunkCount` chunks, and the ledger holds one entry
        // per (subject, slot) — so the ledger size is the delivered count against
        // that product. A single subject reduces to `chunks.size` of
        // `totalChunkCount`, unchanged from the slice-based rule.
        const subjects = Math.max(1, this._jobIds.size);
        const total = this._plan.totalChunkCount * subjects;
        const loaded = this._chunks.size;
        if (
            this._loadingProgress?.loaded === loaded &&
            this._loadingProgress.total === total &&
            this._loadingProgress.subjects === subjects
        ) {
            return;
        }
        this._loadingProgress = { loaded, total, subjects };
    }

    // -----------------------------------------------------------------------
    // Per-subject delivery
    // -----------------------------------------------------------------------

    /**
     * Per-subject sinks, keyed by subject id. A historical query is one transport
     * session (one REST submit, one WS queue, one heartbeat, one admission slot)
     * fanned out to N subjects, so the session owns delivery and each subject
     * gets only its own elements — letting each be an ordinary `Source` in the
     * controller instead of the session pretending to be one.
     */
    private readonly _subjectSinks = new Map<string, SubjectSink>();

    /** Number of subject sources currently holding this session open. */
    private _retainCount = 0;

    /**
     * Start the transport on the first subject, and count the rest.
     *
     * The session is shared by every subject in the query, so it must outlive
     * any individual one: one submit, one WS subscription, one heartbeat, one
     * admission slot. Reference counting lets each subject be enabled and
     * disabled independently by the controller while the transport starts and
     * stops exactly once.
     */
    retain(): void {
        this._retainCount++;
        if (this._retainCount === 1) this.start();
    }

    /** Release a subject's hold; the last one out stops the transport. */
    release(): void {
        if (this._retainCount === 0) return;
        this._retainCount--;
        if (this._retainCount === 0) this.stop();
    }

    /**
     * Start the transport (was the inherited `Source.enable` → `onEnable`).
     * `start`/`stop` are the session's only lifecycle entry points, driven
     * exclusively by the subjects' refcount above.
     */
    private start(): void {
        this.setState("syncing");
        this.onStart();
    }

    /** Stop the transport (was `Source.disable` → `onDisable`). Idempotent. */
    private stop(): void {
        if (this._state === "disabled") return;
        this.onStop();
        this.setState("disabled");
    }

    /**
     * Attach a subject sink. Returns a detach function.
     *
     * Registering a sink does not start the transport — the owning
     * {@link HistoricalSource} drives that via {@link retain}, so the
     * controller's enable/disable remains the single lifecycle authority.
     */
    attachSubject(subjectId: string, sink: SubjectSink): () => void {
        this._subjectSinks.set(subjectId, sink);
        return () => {
            if (this._subjectSinks.get(subjectId) === sink) {
                this._subjectSinks.delete(subjectId);
            }
        };
    }

    /**
     * Register a no-subject delivery sink. Replaces the old inherited
     * `bind(handler)`: a session with no subject sinks (the DEV perf bridge, and
     * the manager's fallback binding) delivers whole batches here.
     */
    onElements(handler: (elements: Element[]) => void): void {
        this._fallback = handler;
    }

    /**
     * Route decoded elements to their subject's sink.
     *
     * Falls back to {@link onElements} when no sinks are registered, so the
     * session can still be driven directly (the perf bridge and the existing
     * tests do this).
     */
    private deliver(elements: Element[]): void {
        // Single delivery method for both the real drain and the DEV perf stream.
        // Fallback: when no subject sinks are attached, hand the whole batch to
        // the `onElements` sink. In production the subject sources are always
        // enabled before chunks arrive (a subject source's `onEnable` attaches
        // its sink and only then starts the session), so this branch is only the
        // perf bridge's synthetic streams, which drive a session with no subject
        // sources and rely on the manager's fallback delivery sink.
        if (this._subjectSinks.size === 0) {
            this._fallback?.(elements);
            return;
        }

        const bySubject = new Map<string, Element[]>();
        for (const element of elements) {
            const bucket = bySubject.get(element.source);
            if (bucket) bucket.push(element);
            else bySubject.set(element.source, [element]);
        }

        for (const [subjectId, batch] of bySubject) {
            const sink = this._subjectSinks.get(subjectId);
            // A subject with no sink is one the controller has already evicted;
            // its elements were purged, so dropping them here is correct.
            if (sink) sink.deliver(batch);
        }
    }

    /** Notify every attached subject sink of a session-level state change. */
    private notifySubjects(state: SourceState): void {
        for (const sink of this._subjectSinks.values()) sink.setState(state);
    }

    /** Read-only view of chunk metadata for diagnostics. Keyed by `ledgerKey`. */
    get chunks(): ReadonlyMap<string, HistoricalChunkEntry> {
        return this._chunks;
    }

    get requestId(): string | null {
        return this._requestId;
    }

    get jobIds(): ReadonlySet<string> {
        return this._jobIds;
    }

    get sourceParams(): HistoricalSessionParams {
        return this.params;
    }

    // Note: this session is never registered with the `SourceController` — only
    // the per-subject `HistoricalSource` wrappers are (see
    // `HistoricalDataContext`). Purge ownership therefore lives on those
    // wrappers' `ownedSourceIds`, not here, so this class deliberately does not
    // override it.

    // -----------------------------------------------------------------------
    // Transport lifecycle (wrapped by start/stop)
    // -----------------------------------------------------------------------

    private onStart(): void {
        // Re-arm the teardown latch. `_disposed` gates frame dispatch and the
        // decode drain, and `onStop` sets it permanently — so without this a
        // start→stop→start cycle on the same instance would leave the session
        // subscribed and assembling chunks (its ledger and progress would advance)
        // while every decoded element was silently dropped before reaching the
        // store. The subjects' refcount can re-start a retained session, so this
        // cycle is reachable in a way it was not when the provider constructed a
        // fresh session per query.
        this._disposed = false;
        this._generation++;
        this.client.subscribe(HISTORY_DESTINATION, this.wsHandler);
        void this.submit();
    }

    private onStop(): void {
        if (this._disposed) return;
        this._disposed = true;
        this.client.unsubscribe(HISTORY_DESTINATION, this.wsHandler);
        this._abortController?.abort();
        this._abortController = null;
        this._ackPending = false;
        this._preAckBuffer = [];
        this.clearLoadHeartbeat();
        this._diagnostics?.dispose();
        this._diagnostics = null;
        // Clear the chunk/job ledger so a re-enable of this same instance (the
        // controller reuses instances across evict/re-admit) starts clean rather
        // than carrying forward a prior session's chunks and job accounting.
        this._chunks.clear();
        this._chunkRanges = [];
        this._completedJobs.clear();
        this._jobToSource.clear();
        this._jobIds.clear();
        this._pendingDecodedPayloads = [];
        this._drainScheduled = false;
        this._plan = null;
        this._requestId = null;
        this._loadingProgress = null;
    }

    // -----------------------------------------------------------------------
    // REST submit
    // -----------------------------------------------------------------------

    /**
     * Submit a historical request for `subjectIds` (defaults to the full
     * selection). Used both for the initial load and for a post-load
     * incremental {@link addSources} sub-request, where `subjectIds` is just
     * the newly-added sources. The ack's jobs are *merged* into `_jobIds` /
     * `_jobToSource` (never replaced), so a sub-request's jobs stream alongside
     * the ones already loaded; `_requestId` advances to the latest ack so the
     * JSON gate matches the new jobs' plan/completion events.
     */
    private async submit(subjectIds: string[] = this.params.sourceIds): Promise<void> {
        // Capture the enable generation this submit belongs to. If the source is
        // disabled and re-enabled before the ack resolves, the generation moves
        // on and this submit's continuation must not touch `_jobIds`/`_requestId`.
        const generation = this._generation;
        this._error = null;
        this._abortController = new AbortController();
        this._t0Ms = performance.now();
        this._chunksArrived = 0;
        // Open the pre-ack window synchronously, before the first await, so a WS frame
        // arriving while this submit's ack is in flight is buffered rather than dropped
        // (see `wsHandler`).
        this._ackPending = true;
        logger.info(
            `[timing] submit start ids=[${subjectIds.join(",")}] range=[${this.params.from},${this.params.to})`,
        );

        try {
            const ack = await submitHistoricalTracks(
                {
                    start: this.params.from,
                    end: this.params.to,
                    filters: [],
                    sources: subjectIds.map((subjectId) => ({
                        subjectType: this.params.subjectTypes[subjectId] ?? "SOURCE",
                        subjectId,
                    })),
                },
                { signal: this._abortController.signal },
            );
            if (this._disposed || generation !== this._generation) return;
            // Capture every job this request spawned (one per accepted subject)
            // BEFORE exposing `_requestId`, so the binary gate (`handleBinaryChunk`,
            // keyed on job id) is already active before the JSON gate
            // (`handleJsonEvent`, keyed on request id) starts matching. Each job's
            // `subjectId` is recorded so binary frames (stamped only with the job
            // id) can be attributed back to a source.
            for (const subject of ack.subjects) {
                this._jobIds.add(subject.jobId);
                this._jobToSource.set(subject.jobId, subject.subjectId);
            }
            this._requestId = ack.requestId;
            logger.info(
                `Historical tracks request accepted: requestId=${ack.requestId} jobIds=[${[...this._jobIds].join(",")}]`,
            );
            if (this._jobIds.size === 0) {
                // Unrecoverable: `_jobIds` is populated once and never again, so with
                // no ids every binary chunk frame is discarded and the source will
                // heartbeat-timeout. Surface it as an error, not a benign warning.
                logger.error(
                    `Historical ack carried no job ids; every binary chunk frame will be discarded and the source will heartbeat-timeout (subjects=${ack.subjects.length})`,
                );
            }
            // Ack applied: close the pre-ack window and replay anything the backend
            // pushed before `_jobIds`/`_requestId` were set (plan + leading chunks).
            this._ackPending = false;
            this.drainPreAckBuffer();
            this.resetLoadHeartbeat();
        } catch (err) {
            if (this._disposed || generation !== this._generation) return;
            if (err instanceof DOMException && err.name === "AbortError") return;

            // Submit failed: the buffered frames belong to a request that will never
            // complete, so drop them and close the window.
            this._ackPending = false;
            this._preAckBuffer = [];
            this._error = classifySubmitError(err);
            logger.error("Failed to submit historical tracks request", err);
            this.setState("error");
        }
    }

    // -----------------------------------------------------------------------
    // Heartbeat — rejects only when backend is silent for the full interval.
    // -----------------------------------------------------------------------

    private resetLoadHeartbeat(): void {
        if (this.state !== "syncing") return;
        this.clearLoadHeartbeat();
        this._loadHeartbeatTimer = setTimeout(() => {
            this._loadHeartbeatTimer = null;
            this._error = { kind: "heartbeatTimeout" };
            this.setState("error");
        }, LOAD_HEARTBEAT_TIMEOUT_MS);
    }

    private clearLoadHeartbeat(): void {
        if (this._loadHeartbeatTimer !== null) {
            clearTimeout(this._loadHeartbeatTimer);
            this._loadHeartbeatTimer = null;
        }
    }

    // -----------------------------------------------------------------------
    // WebSocket frame router
    // -----------------------------------------------------------------------

    private readonly wsHandler = (payload: Uint8Array, headers?: Record<string, string>): void => {
        if (this._disposed) return;
        // Pre-ack window: while a submit is in flight its jobs aren't in `_jobIds` yet,
        // so the gates below would discard every frame for them — including the plan +
        // earliest chunks a warm socket or cache hit pushes before the ack lands. Buffer
        // for replay instead of losing them; frames for already-known jobs fall through
        // to `dispatchFrame` and pass the gate unchanged.
        if (this._ackPending) {
            this.bufferPreAckFrame(payload, headers);
            return;
        }
        this.dispatchFrame(payload, headers);
    };

    /** Route a frame to the binary-chunk or JSON-event handler. */
    private dispatchFrame(payload: Uint8Array, headers?: Record<string, string>): void {
        if (headers?.[HISTORY_TYPE_HEADER] === HISTORY_CHUNK_DATA_TYPE) {
            try {
                this.handleBinaryChunk(payload, headers);
            } catch (err) {
                logger.warn("Failed to decode history binary chunk", err);
            }
        } else {
            try {
                const text = new TextDecoder().decode(payload);
                const event = parseHistoryEvent(text);
                this.handleJsonEvent(event);
            } catch (err) {
                logger.warn("Failed to parse history JSON event", err);
            }
        }
    }

    /**
     * Hold a frame that arrived before the initial ack. Bounded by
     * {@link MAX_PREACK_BUFFER}: on overflow the *newest* frame is dropped so the
     * earliest — crucially the plan — survives, and a warning fires (an ack that
     * slow is pathological and worth investigating). Frames are raw WS payloads,
     * replayed verbatim by {@link drainPreAckBuffer}.
     */
    private bufferPreAckFrame(payload: Uint8Array, headers?: Record<string, string>): void {
        if (this._preAckBuffer.length >= MAX_PREACK_BUFFER) {
            logger.warn(
                `Pre-ack buffer at cap (${MAX_PREACK_BUFFER}); dropping newest frame. ` +
                    `The REST ack is abnormally slow relative to WS delivery.`,
            );
            return;
        }
        this._preAckBuffer.push({ payload, headers });
        this._preAckBufferedTotal += 1;
    }

    /**
     * Replay every frame held during the submit round-trip, in arrival order, now
     * that `_jobIds`/`_requestId` are populated: the plan and this request's chunks
     * pass the gates; genuinely foreign frames (other tabs) are discarded by the
     * same gates. Ordering is safe even if a chunk was buffered before the plan —
     * chunk decode is deferred to a microtask, so `_plan` is set (from the plan
     * event dispatched here) before the drain runs. Called once, right after the ack.
     */
    private drainPreAckBuffer(): void {
        if (this._preAckBuffer.length === 0) return;
        const buffered = this._preAckBuffer;
        this._preAckBuffer = [];
        logger.info(
            `[timing] replaying ${buffered.length} pre-ack frame(s) held during the ` +
                `submit round-trip (requestId=${this._requestId})`,
        );
        // Replay is a synchronous loop over up to MAX_PREACK_BUFFER frames. Suppress the
        // per-frame chunk-range recompute + bump (each an O(n) sort → O(n²) across the
        // batch) and do a single recompute + bump after the loop instead.
        this._replayingPreAck = true;
        try {
            for (const { payload, headers } of buffered) {
                if (this._disposed) return;
                this.dispatchFrame(payload, headers);
            }
        } finally {
            this._replayingPreAck = false;
        }
        this.recomputeChunkRanges();
        this.updateLoadingProgress();
        this.bump();
    }

    /**
     * Production-visible warning when a binary frame is discarded because its job id
     * isn't part of this request — a foreign frame (another tab's job or a superseded
     * one) on the shared per-user queue. Rate-limited so a superseded job's tail can't
     * flood the console; the first always logs. Survives the production build (WARN ≥
     * the prod INFO level), so pre-prod keeps visibility into unexpected cross-job traffic.
     */
    private logForeignDiscard(frameJobId: string | undefined): void {
        this._discardedForeignCount += 1;
        const now = performance.now();
        if (
            this._discardedForeignCount > 1 &&
            now - this._lastDiscardLogMs < DISCARD_LOG_THROTTLE_MS
        ) {
            return;
        }
        this._lastDiscardLogMs = now;
        logger.warn(
            `Discarded foreign historical chunk frame (job not part of this request): ` +
                `frameJobId=${frameJobId ?? "<none>"} totalForeignDiscards=${this._discardedForeignCount}`,
        );
    }

    /**
     * Cheap-path on the WS read loop: envelope-decode the chunk payload,
     * accumulate the multi-part ledger entry (scoped to the frame's source),
     * publish chunk ranges. Heavy mapper + tagging work runs in a microtask
     * drain.
     */
    private handleBinaryChunk(payload: Uint8Array, headers?: Record<string, string>): void {
        // Job-scoped routing. Chunks arrive on the shared per-user queue
        // `/user/queue/history` (across tabs and this source's prior, now-disposed
        // queries). Accept a frame only when its `history-job-id` header is one of this
        // request's jobs. The header is always present on chunk frames, so a missing one
        // is itself a mismatch.
        const frameJobId = headers?.[HISTORY_JOB_ID_HEADER];
        if (frameJobId === undefined || !this._jobIds.has(frameJobId)) {
            // No submit is in flight (ack-window frames are buffered upstream in
            // `wsHandler`), so an unknown job id is genuinely foreign — another tab's job
            // or a superseded one on the shared queue. Surface it (production-visible)
            // and feed the DEV per-frame bridge.
            this.logForeignDiscard(frameJobId);
            if (this._diagnostics) {
                // Decode only to surface the chunkKey in the DEV discard log.
                let chunkKey: string | undefined;
                try {
                    chunkKey = HistoricalChunkPayload.decode(payload).chunkKey;
                } catch {
                    chunkKey = undefined;
                }
                this._diagnostics.onChunkDiscarded(frameJobId, [...this._jobIds], chunkKey);
            }
            return;
        }
        // The job id resolves to its source (the only source attribution available;
        // the payload carries none). Guaranteed present because `frameJobId` is in
        // `_jobIds`, which is populated in lockstep with `_jobToSource`.
        const sourceId = this._jobToSource.get(frameJobId);
        if (sourceId === undefined) return;

        this.resetLoadHeartbeat();

        const chunkPayload = HistoricalChunkPayload.decode(payload);
        const chunkKey = chunkPayload.chunkKey;
        const chunkIndex = chunkPayload.chunkIndex;
        const partIndex = chunkPayload.partIndex ?? 0;
        const partCount = chunkPayload.partCount ?? 1;
        const finalForChunk = Boolean(chunkPayload.finalForChunk);
        const frameElementCount = chunkPayload.elements.length;

        // Key the ledger by `(sourceId, chunkKey)`. `chunkKey` is stable per
        // wall-clock time slot (not `chunkIndex`, which the backend can reuse
        // across slots within a job); `sourceId` separates the parallel
        // timelines of different sources, which legitimately share a `chunkKey`
        // for the same UTC slot.
        const key = this.ledgerKey(sourceId, chunkKey);
        const existing = this._chunks.get(key);
        if (existing) {
            existing.frameCount += 1;
            // Only accumulate the element count when this part is new. The
            // backend can re-deliver the same `partIndex` (replay across runs,
            // retry, dedup miss) — the downstream element store overwrites by
            // id so the data is fine, but the counter would double.
            if (existing.seenParts.has(partIndex)) {
                logger.warn(
                    `Duplicate chunk part received — sourceId=${sourceId} chunkKey=${chunkKey} ` +
                        `chunkIndex=${chunkIndex} partIndex=${partIndex}; ` +
                        `skipping count accumulation`,
                );
            } else {
                existing.elementCount += frameElementCount;
                existing.seenParts.add(partIndex);
            }
            if (partCount > existing.maxPartCount) existing.maxPartCount = partCount;
            if (finalForChunk) existing.finalForChunkSeen = true;
        } else {
            this._chunks.set(key, {
                sourceId,
                chunkKey,
                chunkIndex,
                fromMs: Number(chunkPayload.fromEpochMillis),
                toMs: Number(chunkPayload.toEpochMillis),
                elementCount: frameElementCount,
                frameCount: 1,
                seenParts: new Set<number>([partIndex]),
                maxPartCount: partCount,
                finalForChunkSeen: finalForChunk,
            });
        }

        if (!this._replayingPreAck) this.recomputeChunkRanges();

        this._chunksArrived++;
        const elapsed = (performance.now() - this._t0Ms).toFixed(0);
        const expected = this._plan
            ? this._plan.totalChunkCount * Math.max(1, this._jobIds.size)
            : "?";
        logger.info(
            `[timing] chunk ${chunkIndex} arrived (${this._chunksArrived}/${expected}) ` +
                `src=${sourceId} part=${partIndex}/${partCount} final=${finalForChunk} elapsed=${elapsed}ms`,
        );

        this._pendingDecodedPayloads.push({ payload: chunkPayload, sourceId });
        if (!this._drainScheduled) {
            this._drainScheduled = true;
            queueMicrotask(() => this.drainPendingPayloads());
        }

        this._diagnostics?.onChunkEnqueued(
            sourceId,
            chunkKey,
            chunkIndex,
            frameElementCount,
            partIndex,
            partCount,
            finalForChunk,
        );

        this.updateLoadingProgress();
        if (!this._replayingPreAck) this.bump();
    }

    /**
     * Decode all queued chunk payloads through the mapper, tag each element
     * with its effective-stale offset (the value `DataFilterExtension`
     * compares against `filterRange`), and emit them via the bound handler
     * as a single batch. One `bind` call → one `store.add` → one
     * pipeline `bump`.
     */
    private drainPendingPayloads(): void {
        this._drainScheduled = false;
        if (this._disposed) return;

        const payloads = this._pendingDecodedPayloads;
        if (payloads.length === 0) return;
        this._pendingDecodedPayloads = [];

        const allDecoded: Element[] = [];
        let totalSeen = 0;
        let totalFailed = 0;
        // If a chunk arrives before the plan event (diagnostics warns when this
        // happens), `windowStartMs` falls back to 0 and the resulting filterValue
        // is the proto stale time itself.
        const windowStartMs = this._plan?.fromMs ?? 0;

        for (const { payload: chunkPayload, sourceId } of payloads) {
            const chunkIndex = chunkPayload.chunkIndex;
            const chunkEntry = this.chunkEntry(sourceId, chunkPayload.chunkKey);
            for (const raw of chunkPayload.elements) {
                totalSeen++;
                const elementMessage = ElementMessage.create(raw);
                this._diagnostics?.onElementMessageRead(sourceId, chunkIndex, elementMessage);
                // This loop decodes the whole batch in one microtask; an escaped
                // throw would drop every remaining element in the batch (and
                // surface uncaught). A failed decode is just a skipped element —
                // route any throw through the same path as a null result.
                let element: Element | undefined;
                try {
                    element = decodeHistoricalElement(elementMessage, this.processor, chunkIndex);
                } catch (err) {
                    if (import.meta.env.DEV) {
                        logger.warn("Failed to decode historical element", err);
                    }
                    element = undefined;
                }
                if (!element) {
                    totalFailed++;
                    this._diagnostics?.onElementDecodeFailed(elementMessage, totalFailed);
                    continue;
                }
                // Encode each element's effective-stale offset (relative to
                // windowStart) into `filterValue`. Combined with the
                // dataFilterRange the manager derives from the playhead, this
                // lets the GPU select exactly the version alive at the
                // playhead — entities going stale mid-chunk drop out at their
                // actual stale time, not at the chunk boundary.
                const protoStale = Number(elementMessage.stale);
                const protoStaleMs =
                    Number.isFinite(protoStale) && protoStale > 0
                        ? protoStale
                        : Number.POSITIVE_INFINITY;
                const chunkToMs = chunkEntry?.toMs ?? Number.POSITIVE_INFINITY;
                const effectiveStaleMs = Math.min(chunkToMs, protoStaleMs);
                element.setFilterValue(effectiveStaleMs - windowStartMs);
                if (chunkEntry) {
                    this._diagnostics?.onElementDecoded(
                        sourceId,
                        chunkIndex,
                        element,
                        elementMessage,
                        chunkEntry,
                    );
                }
                allDecoded.push(element);
            }
            this._diagnostics?.onChunkDrainPass(
                sourceId,
                chunkPayload.chunkKey,
                chunkIndex,
                chunkPayload.elements.length,
            );
        }

        if (allDecoded.length > 0) {
            this.deliver(allDecoded);
        }

        this._diagnostics?.onDrainComplete(
            payloads.length,
            totalSeen,
            allDecoded.length,
            totalFailed,
        );
    }

    private handleJsonEvent(event: HistoryEvent): void {
        if (event.requestId !== this._requestId) return;
        this.resetLoadHeartbeat();

        switch (event.type) {
            case "history.job.plan":
                this.handlePlan(event);
                break;

            case "history.job.completed": {
                this._completedJobs.add(event.jobId);
                const totalElapsed = (performance.now() - this._t0Ms).toFixed(0);
                const planToCompleted = this._planAtMs
                    ? (performance.now() - this._planAtMs).toFixed(0)
                    : "?";
                logger.info(
                    `[timing] job.completed (${this._completedJobs.size}/${this._jobIds.size} jobs): ` +
                        `${event.deliveredChunks}/${event.totalChunks} chunks, ` +
                        `received=${this._chunks.size}, preAckReplayed=${this._preAckBufferedTotal}, ` +
                        `total=${totalElapsed}ms, plan→completed=${planToCompleted}ms`,
                );
                // Flush any queued chunks synchronously so partial data is visible.
                if (this._pendingDecodedPayloads.length > 0) {
                    this.drainPendingPayloads();
                }
                // Multi-source: stay `syncing` until every job has completed.
                if (this._jobIds.size > 0 && this._completedJobs.size < this._jobIds.size) {
                    this.updateLoadingProgress();
                    this.bump();
                    break;
                }
                this.auditJobCompletion(event.totalChunks);
                this.clearLoadHeartbeat();
                this.setState("enabled");
                break;
            }

            case "history.job.failed":
                // A `CANCELLED` stage is a supersede, not a failure: the user issued
                // a newer historical request and the backend cancelled this one. Map
                // it to the dedicated `jobSuperseded` kind so the status bar offers a
                // clear retry instead of generic failure copy. A failure on any one
                // job fails the whole query (the picture would be incomplete).
                if (event.stage === "CANCELLED") {
                    logger.info(
                        `Historical job superseded by a newer request (reason: ${event.reason})`,
                    );
                    this._error = { kind: "jobSuperseded" };
                } else {
                    logger.error(`Historical job failed: ${event.reason} (stage: ${event.stage})`);
                    this._error = { kind: "jobFailed", stage: event.stage, reason: event.reason };
                }
                this.clearLoadHeartbeat();
                this.setState("error");
                break;

            case "history.job.progress":
                logger.info(
                    `Historical progress: ${event.deliveredChunks}/${event.totalChunks} ` +
                        `(${event.percentComplete}%, computed=${event.computedChunks}, ` +
                        `failed=${event.failedChunks})`,
                );
                break;

            case "history.chunk.status":
                this._diagnostics?.onChunkStatus({
                    chunkIndex: event.chunkIndex,
                    status: event.status,
                    partCount: event.partCount,
                    cached: event.cached,
                    chunkKey: event.chunkKey,
                });
                break;
        }
    }

    private handlePlan(event: HistoryJobPlanEvent): void {
        const resolution = mapBackendResolution(event.resolution);
        const bucketWidthMs = RESOLUTION_WIDTH_MS[resolution];
        const effectiveFromMs = DateTime.fromISO(event.effectiveFrom).toMillis();
        const firstBucketStart = Math.floor(effectiveFromMs / bucketWidthMs) * bucketWidthMs;

        // Every job shares the same range and resolution, so their plans are
        // identical; the last one to arrive simply re-confirms the window. The
        // playhead filter math only needs one.
        this._plan = {
            fromMs: firstBucketStart,
            bucketWidthMs,
            totalChunkCount: event.chunks,
            resolution,
        };
        this._planAtMs = performance.now();
        const elapsed = (this._planAtMs - this._t0Ms).toFixed(0);
        logger.info(
            `[timing] plan received: ${event.chunks} chunks at ${resolution}, ` +
                `effective range [${event.effectiveFrom}, ${event.effectiveTo}), elapsed=${elapsed}ms`,
        );
        this.updateLoadingProgress();
        this.bump();
    }

    /**
     * Reconcile what arrived against what `history.job.completed` claims was
     * sent, then rebuild `_chunkRanges` to cover every `(source, index)` with a
     * classified status. Audit gaps no longer set `_error` — the histogram
     * communicates the gap via the chunk overlay's status. Transport failures
     * still populate `_error` elsewhere.
     *
     * Runs **per source**: each source is its own job with its own chunk
     * timeline, so coverage is reconciled separately and the resulting
     * `ChunkRange`s carry their `sourceId`.
     */
    private auditJobCompletion(totalChunks: number): void {
        const plan = this._plan;
        if (!plan) {
            // Backend contract: `history.job.completed` always follows
            // `history.job.plan`. If this invariant ever breaks we'd silently
            // under-report gaps; fail loud instead.
            logger.warn("auditJobCompletion ran with no plan; skipping classification");
            return;
        }

        // The backend planner can include leading/trailing slots that lie
        // entirely outside the requested range — e.g., a trailing slot that
        // starts at exactly `requestedTo`, or a leading slot that ends at or
        // before `requestedFrom` when the planner snaps to bucket boundaries.
        // The backend correctly doesn't deliver data for such slots; without
        // this guard we'd mark them missing. Range semantics are half-open
        // `[from, to)` — slots overlapping either boundary still audit
        // normally, slots fully outside are skipped.
        const requestedFromMs = DateTime.fromISO(this.params.from).toMillis();
        const requestedToMs = DateTime.fromISO(this.params.to).toMillis();

        const missingSlots: string[] = [];
        const incompleteSlots: string[] = [];
        const ranges: ChunkRange[] = [];

        for (const sourceId of this.params.sourceIds) {
            // Index this source's received chunks by their wall-clock `fromMs`
            // rather than by `chunkIndex` — the backend can leave gaps in the
            // chunkIndex sequence that don't correspond to missing time slots.
            const chunksByFromMs = new Map<number, HistoricalChunkEntry>();
            for (const entry of this._chunks.values()) {
                if (entry.sourceId === sourceId) chunksByFromMs.set(entry.fromMs, entry);
            }

            for (let i = 0; i < totalChunks; i++) {
                const slotFromMs = plan.fromMs + i * plan.bucketWidthMs;
                const slotToMs = slotFromMs + plan.bucketWidthMs;
                // Slot entirely outside `[requestedFrom, requestedTo)`.
                if (slotFromMs >= requestedToMs) continue;
                if (slotToMs <= requestedFromMs) continue;
                const entry = chunksByFromMs.get(slotFromMs);
                if (!entry) {
                    missingSlots.push(`${sourceId}:${i}`);
                    ranges.push({
                        sourceId,
                        chunkIndex: i,
                        start: msToIso(slotFromMs),
                        end: msToIso(slotToMs),
                        count: 0,
                        status: CHUNK_STATUS.Missing,
                    });
                    continue;
                }
                const incomplete =
                    !entry.finalForChunkSeen ||
                    (entry.maxPartCount > 0 && entry.seenParts.size < entry.maxPartCount);
                if (incomplete) {
                    incompleteSlots.push(`${sourceId}:${i}`);
                    ranges.push({
                        sourceId,
                        chunkIndex: entry.chunkIndex,
                        start: msToIso(entry.fromMs),
                        end: msToIso(entry.toMs),
                        count: entry.elementCount,
                        status: CHUNK_STATUS.IncompleteParts,
                        parts: { seen: entry.seenParts.size, expected: entry.maxPartCount },
                    });
                } else {
                    ranges.push({
                        sourceId,
                        chunkIndex: entry.chunkIndex,
                        start: msToIso(entry.fromMs),
                        end: msToIso(entry.toMs),
                        count: entry.elementCount,
                        status: CHUNK_STATUS.Ok,
                    });
                }
            }
        }

        ranges.sort(
            (a, b) =>
                Date.parse(a.start) - Date.parse(b.start) || a.sourceId.localeCompare(b.sourceId),
        );
        this._chunkRanges = ranges;

        if (missingSlots.length === 0 && incompleteSlots.length === 0) return;

        logger.warn(
            `Historical job reported complete but client is missing data: ` +
                `missingSlots=[${missingSlots.join(",")}] incompleteSlots=[${incompleteSlots.join(",")}]`,
            { missingSlots, incompleteSlots, totalChunks, received: this._chunks.size },
        );
    }

    /**
     * Streaming-time view: only received chunks become entries, all tagged
     * `"ok"`. Part-incompleteness is not classified here — later parts may
     * still arrive, and flashing red mid-stream would be wrong. The
     * job-completion audit is the only place that distinguishes
     * `"incomplete-parts"` from `"ok"`. Entries from every source are emitted,
     * each carrying its `sourceId` (the colored chunk bars stack per source).
     */
    private recomputeChunkRanges(): void {
        this._chunkRanges = [...this._chunks.values()]
            .sort((a, b) => a.fromMs - b.fromMs || a.sourceId.localeCompare(b.sourceId))
            .map((c) => ({
                sourceId: c.sourceId,
                chunkIndex: c.chunkIndex,
                start: msToIso(c.fromMs),
                end: msToIso(c.toMs),
                count: c.elementCount,
                status: CHUNK_STATUS.Ok,
            }));
    }
}

/**
 * Map a REST submit failure into a `SourceError`. Branches on the stable
 * `body.code` from `GlobalOpenCopException` so each backend condition gets its
 * own typed kind. Unrecognized codes fall back to `badRequest` (4xx) or
 * `generic` (5xx / network failure / abort) so unknown future codes still
 * surface with the verbatim server message.
 */
function classifySubmitError(err: unknown): SourceError {
    if (!(err instanceof ApiError)) {
        return {
            kind: "generic",
            status: 0,
            message: err instanceof Error ? err.message : "An unexpected error occurred.",
        };
    }
    switch (err.body.code) {
        case "HISTORICAL_CONCURRENT_LIMIT":
            return { kind: "concurrentLimit" };
        case "HISTORICAL_RANGE_INVALID":
            return { kind: "rangeInvalid" };
        case "HISTORICAL_RANGE_TOO_LARGE":
            return { kind: "rangeTooLarge" };
        case "HISTORICAL_TOO_MANY_CHUNKS":
            return { kind: "tooManyChunks" };
        case "HISTORICAL_SOURCES_REQUIRED":
            return { kind: "sourcesRequired" };
        case "HISTORICAL_FILTERS_NOT_SUPPORTED":
            return { kind: "filtersNotSupported" };
        case "HISTORICAL_LAYER_NOT_SUPPORTED":
            return { kind: "layerNotSupported" };
        case "HISTORICAL_SUBJECT_FORBIDDEN":
            return { kind: "subjectForbidden" };
        default:
            // HISTORICAL_SOURCES_CONTAINS_NULL / HISTORICAL_SOURCES_DUPLICATE /
            // HISTORICAL_FILTERS_CONTAINS_NULL — the user can't trigger these from the
            // UI today (the picker forbids the shapes), so they get the generic
            // 400 fallback instead of dedicated copy. Anything else with a 4xx
            // also lands here.
            if (err.status >= 400 && err.status < 500) {
                return { kind: "badRequest", message: err.message };
            }
            return { kind: "generic", status: err.status, message: err.message };
    }
}

function mapBackendResolution(backendRes: string): HistoricalChunkResolution {
    switch (backendRes) {
        case "MINUTE_1":
            return "MINUTE_1";
        case "HOUR_1":
            return "HOUR_1";
        case "DAY_1":
            return "DAY_1";
        default:
            return "HOUR_1";
    }
}
