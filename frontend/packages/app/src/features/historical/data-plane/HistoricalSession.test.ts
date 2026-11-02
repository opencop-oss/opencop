// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it, vi, beforeEach, afterEach } from "vitest";
import { ElementMessage, HistoricalChunkPayload } from "@opencop/proto";
import { getProcessor } from "@opencop/ingest";
import type { OCMPClient } from "@opencop/websocket";
import type { Element } from "@opencop/elements";

import type { HistoricalTracksAck, HistoricalTracksRequest } from "@opencop/api";
import { server, http, HttpResponse, API_BASE } from "~/test";

import { HistoricalSession } from "./HistoricalSession";
import type {
    HistoryJobCompletedEvent,
    HistoryJobFailedEvent,
    HistoryJobPlanEvent,
} from "./history-events";

// ---------------------------------------------------------------------------
// API boundary — submitHistoricalTracks is exercised through real MSW handlers
// (POST http://localhost/api/v1/history/tracks). Each test that triggers a
// submit registers a response via the helpers below; the shared server's
// afterEach(resetHandlers) drops the per-test handler, so `installSubmitHandler`
// is re-registered in the top `beforeEach`.
// ---------------------------------------------------------------------------

/** Bodies POSTed to /history/tracks, in call order (the observable submit args). */
let submittedBodies: HistoricalTracksRequest[] = [];
/**
 * Responders consumed per successive POST. While more than one remains the
 * handler shifts them in order; the last is reused for any further calls (so a
 * single queued ack behaves like `mockResolvedValue`).
 */
let responders: Array<() => Response | Promise<Response>> = [];
/** When set, every submit fails with this ApiError-shaped body (status + code/message). */
let failure: { status: number; body: { code?: string; message?: string } } | null = null;

/** Queue one JSON ack per successive submit; a lone ack is reused for later calls. */
function respondWith(...acks: HistoricalTracksAck[]): void {
    responders = acks.map((ack) => () => HttpResponse.json(ack));
}

/**
 * Queue a deferred ack: the submit stays pending until `resolve()` is called,
 * so a test can deliver WS frames while the request's ack is still in flight.
 */
function deferAck(ack: HistoricalTracksAck): { resolve: () => void } {
    let release!: () => void;
    const gate = new Promise<void>((res) => {
        release = res;
    });
    responders.push(async () => {
        await gate;
        return HttpResponse.json(ack);
    });
    return { resolve: release };
}

/** Registers the default /history/tracks handler for the current test. */
function installSubmitHandler(): void {
    server.use(
        http.post(`${API_BASE}/history/tracks`, async ({ request }) => {
            submittedBodies.push((await request.json()) as HistoricalTracksRequest);
            if (failure) {
                return HttpResponse.json(failure.body, { status: failure.status });
            }
            const responder = responders.length > 1 ? responders.shift()! : responders[0];
            return responder();
        }),
    );
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

const SOURCE_ID = "00000000-0000-0000-0000-000000000000";
const JOB_ID = "job-1";

type WSHandler = (payload: Uint8Array, headers?: Record<string, string>) => void;

interface FakeClient extends OCMPClient {
    deliver(payload: Uint8Array, headers?: Record<string, string>): void;
}

function fakeClient(): FakeClient {
    const handlers = new Map<string, Set<WSHandler>>();
    return {
        subscribe: vi.fn((dest: string, handler: WSHandler) => {
            const set = handlers.get(dest) ?? new Set<WSHandler>();
            set.add(handler);
            handlers.set(dest, set);
        }),
        unsubscribe: vi.fn((dest: string, handler?: WSHandler) => {
            const set = handlers.get(dest);
            if (!set) return;
            if (handler) set.delete(handler);
            else set.clear();
        }),
        deliver(payload: Uint8Array, headers?: Record<string, string>) {
            const set = handlers.get("/user/queue/history");
            if (!set) return;
            for (const h of set) h(payload, headers);
        },
    } as unknown as FakeClient;
}

function makeSource(
    client: OCMPClient,
    subjectType: "SOURCE" | "LAYER" = "SOURCE",
): HistoricalSession {
    return new HistoricalSession({
        client,
        processor: getProcessor(),
        params: {
            sourceIds: ["00000000-0000-0000-0000-000000000000"],
            subjectTypes: { "00000000-0000-0000-0000-000000000000": subjectType },
            from: "2026-01-01T00:00:00.000Z",
            to: "2026-01-01T01:00:00.000Z",
        },
    });
}

function encodeChunk(payload: {
    chunkIndex: number;
    chunkKey?: string;
    fromEpochMillis?: number;
    toEpochMillis?: number;
    elements?: ElementMessage[];
    partIndex?: number;
    partCount?: number;
    finalForChunk?: boolean;
}): Uint8Array {
    return HistoricalChunkPayload.encode(
        HistoricalChunkPayload.create({
            chunkKey: payload.chunkKey ?? `chunk-${payload.chunkIndex}`,
            chunkIndex: payload.chunkIndex,
            fromEpochMillis: payload.fromEpochMillis ?? 0,
            toEpochMillis: payload.toEpochMillis ?? 60_000,
            elements: payload.elements ?? [],
            partIndex: payload.partIndex ?? 0,
            partCount: payload.partCount ?? 1,
            finalForChunk: payload.finalForChunk ?? true,
        }),
    ).finish();
}

function encodeJsonEvent(event: object): Uint8Array {
    return new TextEncoder().encode(JSON.stringify(event));
}

function buildSymbolMessage(stale: number = 0): ElementMessage {
    return ElementMessage.create({
        type: "symbol",
        id: new Uint8Array(16),
        objectId: "obj-1",
        source: new Uint8Array(16),
        displayName: "Sym",
        positions: [38.0, -77.0],
        stale,
        start: 0,
        time: 0,
        values: {
            symbol: { string: "SFGPUCI----D***" },
            size: { double: 20 },
        },
    });
}

const PLAN_EVENT: HistoryJobPlanEvent = {
    type: "history.job.plan",
    requestId: "req-1",
    jobId: "job-1",
    subjectType: "SOURCE",
    subjectId: "00000000-0000-0000-0000-000000000000",
    resolution: "MINUTE_1",
    samplingVersion: "v1",
    chunks: 2,
    cachedChunks: 0,
    chunksToCompute: 2,
    effectiveFrom: "2026-01-01T00:00:00.000Z",
    effectiveTo: "2026-01-01T00:02:00.000Z",
};

const COMPLETED_EVENT: HistoryJobCompletedEvent = {
    type: "history.job.completed",
    requestId: "req-1",
    jobId: "job-1",
    totalChunks: 2,
    deliveredChunks: 2,
    failedChunks: 0,
};

const FAILED_EVENT: HistoryJobFailedEvent = {
    type: "history.job.failed",
    requestId: "req-1",
    jobId: "job-1",
    stage: "compute",
    reason: "boom",
};

// The real MSW fetch pipeline (interception → handler → res.text() → JSON.parse)
// resolves across real event-loop turns, not just microtasks, so `flush()` yields
// real macrotasks to let the submit's ack land and the source drain its chunk
// buffer. The count is the deepest observed await-chain (fetch → interceptor →
// handler-json → apiFetch's res.text() → the source's chunk-drain) plus generous
// margin — over-flushing costs only a few empty ticks, while under-flushing would
// intermittently fail under CI load, so we err high. This file uses no fake
// timers, so setTimeout is safe.
const FLUSH_MACROTASKS = 8;

async function flush(): Promise<void> {
    for (let i = 0; i < FLUSH_MACROTASKS; i++) {
        await new Promise((resolve) => setTimeout(resolve, 0));
    }
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe("HistoricalSession", () => {
    beforeEach(() => {
        submittedBodies = [];
        responders = [];
        failure = null;
        installSubmitHandler();
    });
    afterEach(() => {
        vi.useRealTimers();
    });

    describe("lifecycle", () => {
        it("enables to syncing and subscribes to the WS destination", async () => {
            respondWith({
                requestId: "req-1",
                status: "ACCEPTED",
                subjects: [{ subjectType: "SOURCE", subjectId: SOURCE_ID, jobId: JOB_ID }],
            });
            const client = fakeClient();
            const source = makeSource(client);

            source.retain();
            expect(source.state).toBe("syncing");
            expect(client.subscribe).toHaveBeenCalledWith(
                "/user/queue/history",
                expect.any(Function),
            );

            await flush();
            expect(source.requestId).toBe("req-1");
        });

        it("submits the subject with its configured subjectType (SOURCE)", async () => {
            respondWith({
                requestId: "req-1",
                status: "ACCEPTED",
                subjects: [{ subjectType: "SOURCE", subjectId: SOURCE_ID, jobId: JOB_ID }],
            });
            makeSource(fakeClient(), "SOURCE").retain();
            await flush();

            expect(submittedBodies[0]).toEqual(
                expect.objectContaining({
                    sources: [{ subjectType: "SOURCE", subjectId: SOURCE_ID }],
                }),
            );
        });

        it("submits the subject with its configured subjectType (LAYER)", async () => {
            respondWith({
                requestId: "req-1",
                status: "ACCEPTED",
                subjects: [{ subjectType: "LAYER", subjectId: SOURCE_ID, jobId: JOB_ID }],
            });
            makeSource(fakeClient(), "LAYER").retain();
            await flush();

            expect(submittedBodies[0]).toEqual(
                expect.objectContaining({
                    sources: [{ subjectType: "LAYER", subjectId: SOURCE_ID }],
                }),
            );
        });

        it("disable unsubscribes and aborts the in-flight request", () => {
            respondWith({
                requestId: "req-1",
                status: "ACCEPTED",
                subjects: [{ subjectType: "SOURCE", subjectId: SOURCE_ID, jobId: JOB_ID }],
            });
            const client = fakeClient();
            const source = makeSource(client);

            source.retain();
            source.release();

            expect(source.state).toBe("disabled");
            expect(client.unsubscribe).toHaveBeenCalledWith(
                "/user/queue/history",
                expect.any(Function),
            );
        });

        it("a stale submit from a previous enable cycle does not corrupt the current session", async () => {
            // submit-A's ack stays pending across the disable→re-enable; submit-B
            // (the current session) resolves normally. When submit-A finally
            // resolves it belongs to a superseded generation and must not merge
            // its job/request into the live session.
            const gateA = deferAck({
                requestId: "req-A",
                status: "ACCEPTED",
                subjects: [{ subjectType: "SOURCE", subjectId: SOURCE_ID, jobId: "job-A" }],
            });
            responders.push(() =>
                HttpResponse.json({
                    requestId: "req-B",
                    status: "ACCEPTED",
                    subjects: [{ subjectType: "SOURCE", subjectId: SOURCE_ID, jobId: "job-B" }],
                }),
            );
            const source = makeSource(fakeClient());

            source.retain(); // submit-A (generation 1), ack deferred on gateA
            await flush();
            source.release(); // aborts submit-A's request, _disposed = true
            source.retain(); // generation 2, submit-B fires and resolves
            await flush();

            gateA.resolve(); // submit-A's stale ack resolves last — must be ignored
            await flush();

            expect(source.requestId).toBe("req-B");
            expect([...source.jobIds]).toEqual(["job-B"]);
            expect([...source.jobIds]).not.toContain("job-A");
        });

        it("uses a session-unique id derived from sourceId|from|to|nonce", () => {
            const source = new HistoricalSession({
                client: fakeClient(),
                processor: getProcessor(),
                params: {
                    sourceIds: ["src-A"],
                    subjectTypes: { "src-A": "SOURCE" },
                    from: "2026-01-01",
                    to: "2026-01-02",
                },
            });
            expect(source.id).toBe("src-A|2026-01-01|2026-01-02|0");
        });

        it("gives a distinct id to a rebuild of the same query with a bumped nonce", () => {
            // The retry path rebuilds a session for the same sources + window; the
            // nonce is what keeps the fresh session's id from colliding with the
            // stale one still parked in the SourceController (which would keep the
            // dead instance and silently drop the retry).
            const params = {
                sourceIds: ["src-A"],
                subjectTypes: { "src-A": "SOURCE" as const },
                from: "2026-01-01",
                to: "2026-01-02",
            };
            const first = new HistoricalSession({
                client: fakeClient(),
                processor: getProcessor(),
                params,
                nonce: 0,
            });
            const retried = new HistoricalSession({
                client: fakeClient(),
                processor: getProcessor(),
                params,
                nonce: 1,
            });
            expect(retried.id).not.toBe(first.id);
        });
    });

    describe("REST failure paths — classified off body.code", () => {
        async function rejectWith(code: string | undefined, status: number, message = "boom") {
            failure = { status, body: { code, message } };
            const client = fakeClient();
            const source = makeSource(client);
            source.retain();
            await flush();
            return source;
        }

        it("HISTORICAL_CONCURRENT_LIMIT → concurrentLimit", async () => {
            const source = await rejectWith("HISTORICAL_CONCURRENT_LIMIT", 422);
            expect(source.getError()).toEqual({ kind: "concurrentLimit" });
        });

        it("HISTORICAL_RANGE_INVALID → rangeInvalid", async () => {
            const source = await rejectWith("HISTORICAL_RANGE_INVALID", 400);
            expect(source.getError()).toEqual({ kind: "rangeInvalid" });
        });

        it("HISTORICAL_RANGE_TOO_LARGE → rangeTooLarge", async () => {
            const source = await rejectWith("HISTORICAL_RANGE_TOO_LARGE", 422);
            expect(source.getError()).toEqual({ kind: "rangeTooLarge" });
        });

        it("HISTORICAL_TOO_MANY_CHUNKS → tooManyChunks", async () => {
            const source = await rejectWith("HISTORICAL_TOO_MANY_CHUNKS", 422);
            expect(source.getError()).toEqual({ kind: "tooManyChunks" });
        });

        it("HISTORICAL_SOURCES_REQUIRED → sourcesRequired", async () => {
            const source = await rejectWith("HISTORICAL_SOURCES_REQUIRED", 400);
            expect(source.getError()).toEqual({ kind: "sourcesRequired" });
        });

        it("HISTORICAL_FILTERS_NOT_SUPPORTED → filtersNotSupported", async () => {
            const source = await rejectWith("HISTORICAL_FILTERS_NOT_SUPPORTED", 422);
            expect(source.getError()).toEqual({ kind: "filtersNotSupported" });
        });

        it("HISTORICAL_LAYER_NOT_SUPPORTED → layerNotSupported", async () => {
            const source = await rejectWith("HISTORICAL_LAYER_NOT_SUPPORTED", 422);
            expect(source.getError()).toEqual({ kind: "layerNotSupported" });
        });

        it("HISTORICAL_SUBJECT_FORBIDDEN → subjectForbidden", async () => {
            const source = await rejectWith("HISTORICAL_SUBJECT_FORBIDDEN", 403);
            expect(source.getError()).toEqual({ kind: "subjectForbidden" });
        });

        it("unknown 4xx code (HISTORICAL_SOURCES_DUPLICATE) → badRequest with verbatim message", async () => {
            // HISTORICAL_SOURCES_DUPLICATE is a real backend code but not user-
            // triggerable from the UI today, so it intentionally falls through
            // to the generic badRequest tier.
            const source = await rejectWith(
                "HISTORICAL_SOURCES_DUPLICATE",
                400,
                "sources must not contain duplicate subjects",
            );
            expect(source.getError()).toEqual({
                kind: "badRequest",
                message: "sources must not contain duplicate subjects",
            });
        });

        it("4xx with no code at all → badRequest fallback", async () => {
            const source = await rejectWith(undefined, 400, "Invalid request body");
            expect(source.getError()).toEqual({
                kind: "badRequest",
                message: "Invalid request body",
            });
        });

        it("5xx (any code) → generic with status + verbatim message", async () => {
            const source = await rejectWith(undefined, 503, "service unavailable");
            expect(source.getError()).toEqual({
                kind: "generic",
                status: 503,
                message: "service unavailable",
            });
        });

        it("network failure (non-ApiError throwable) → generic with status=0", async () => {
            // A real transport error (not an HTTP response) — apiFetch's fetch()
            // rejects with a TypeError, which the classifier maps to status=0.
            server.use(http.post(`${API_BASE}/history/tracks`, () => HttpResponse.error()));
            const client = fakeClient();
            const source = makeSource(client);
            source.retain();
            await flush();

            expect(source.state).toBe("error");
            const error = source.getError();
            // The verbatim message is whatever the fetch rejection carries; the
            // classification (generic + status 0) is the behavior under test.
            expect(error).toMatchObject({ kind: "generic", status: 0 });
            expect(typeof (error as { message: string }).message).toBe("string");
        });

        it("AbortError (from disable during in-flight submit) does not set error state", async () => {
            // Hang the submit so it is genuinely in flight when disable() aborts
            // its AbortSignal — apiFetch's fetch() then rejects with a DOMException
            // AbortError, which submit() swallows rather than surfacing as an error.
            server.use(
                http.post(`${API_BASE}/history/tracks`, () => new Promise<Response>(() => {})),
            );
            const client = fakeClient();
            const source = makeSource(client);
            source.retain();
            source.release();
            await flush();

            expect(source.state).toBe("disabled");
            expect(source.getError()).toBeNull();
        });
    });

    describe("plan event", () => {
        it("populates getPlan() with bucket-aligned fromMs and bucketWidthMs", async () => {
            respondWith({
                requestId: "req-1",
                status: "ACCEPTED",
                subjects: [{ subjectType: "SOURCE", subjectId: SOURCE_ID, jobId: JOB_ID }],
            });
            const client = fakeClient();
            const source = makeSource(client);
            source.retain();
            await flush();

            client.deliver(encodeJsonEvent(PLAN_EVENT));

            const plan = source.getPlan();
            expect(plan).not.toBeNull();
            expect(plan?.bucketWidthMs).toBe(60_000);
            expect(plan?.totalChunkCount).toBe(2);
            expect(plan?.resolution).toBe("MINUTE_1");
            // 2026-01-01T00:00 floored to MINUTE_1 = same instant
            expect(plan?.fromMs).toBe(new Date("2026-01-01T00:00:00.000Z").getTime());
        });

        it("ignores events whose requestId does not match the current request", async () => {
            respondWith({
                requestId: "req-1",
                status: "ACCEPTED",
                subjects: [{ subjectType: "SOURCE", subjectId: SOURCE_ID, jobId: JOB_ID }],
            });
            const client = fakeClient();
            const source = makeSource(client);
            source.retain();
            await flush();

            client.deliver(encodeJsonEvent({ ...PLAN_EVENT, requestId: "OTHER" }));
            expect(source.getPlan()).toBeNull();
        });
    });

    describe("chunk ingestion", () => {
        it("decodes and tags elements with filterValue, emits via the bound handler", async () => {
            respondWith({
                requestId: "req-1",
                status: "ACCEPTED",
                subjects: [{ subjectType: "SOURCE", subjectId: SOURCE_ID, jobId: JOB_ID }],
            });
            const client = fakeClient();
            const source = makeSource(client);
            const emitted: Element[][] = [];
            source.onElements((els) => emitted.push(els));
            source.retain();
            await flush();

            client.deliver(encodeJsonEvent(PLAN_EVENT));
            const planFromMs = source.getPlan()!.fromMs;
            const chunkToMs = planFromMs + 30_000;
            const protoStaleMs = planFromMs + 50_000;
            client.deliver(
                encodeChunk({
                    chunkIndex: 0,
                    fromEpochMillis: planFromMs,
                    toEpochMillis: chunkToMs,
                    elements: [buildSymbolMessage(protoStaleMs)],
                }),
                { "history-type": "history.chunk.data", "history-job-id": JOB_ID },
            );

            // Chunk-range publish is synchronous; element drain is microtask.
            expect(source.getChunkRanges()).toHaveLength(1);
            await flush();

            expect(emitted).toHaveLength(1);
            expect(emitted[0]).toHaveLength(1);
            // protoStale > chunkToMs → effective = chunkToMs.
            // filterValue = chunkToMs - planFromMs = 30_000.
            expect(emitted[0][0].filterValue).toBe(30_000);
        });

        it("accumulates multi-part chunks under the same chunkIndex", async () => {
            respondWith({
                requestId: "req-1",
                status: "ACCEPTED",
                subjects: [{ subjectType: "SOURCE", subjectId: SOURCE_ID, jobId: JOB_ID }],
            });
            const client = fakeClient();
            const source = makeSource(client);
            source.retain();
            await flush();

            client.deliver(
                encodeChunk({
                    chunkIndex: 0,
                    elements: [buildSymbolMessage()],
                    partIndex: 0,
                    partCount: 2,
                    finalForChunk: false,
                }),
                { "history-type": "history.chunk.data", "history-job-id": JOB_ID },
            );
            client.deliver(
                encodeChunk({
                    chunkIndex: 0,
                    elements: [buildSymbolMessage(), buildSymbolMessage()],
                    partIndex: 1,
                    partCount: 2,
                    finalForChunk: true,
                }),
                { "history-type": "history.chunk.data", "history-job-id": JOB_ID },
            );

            const entry = source.chunkEntry(SOURCE_ID, "chunk-0");
            expect(entry).toBeDefined();
            expect(entry?.elementCount).toBe(3);
            expect(entry?.frameCount).toBe(2);
            expect(entry?.seenParts.size).toBe(2);
            expect(entry?.finalForChunkSeen).toBe(true);
        });

        it("duplicate part deliveries do not double-count elements", async () => {
            // Regression: when the backend re-delivers the same (chunkIndex,
            // partIndex) — replay across runs, retry, or dedup miss — the
            // element store overwrites by id so the data stays correct, but
            // the running elementCount was being added twice. Now we gate the
            // accumulation on seenParts.has(partIndex).
            respondWith({
                requestId: "req-1",
                status: "ACCEPTED",
                subjects: [{ subjectType: "SOURCE", subjectId: SOURCE_ID, jobId: JOB_ID }],
            });
            const client = fakeClient();
            const source = makeSource(client);
            source.retain();
            await flush();

            client.deliver(
                encodeChunk({
                    chunkIndex: 0,
                    elements: [buildSymbolMessage(), buildSymbolMessage()],
                    partIndex: 0,
                    partCount: 1,
                    finalForChunk: true,
                }),
                { "history-type": "history.chunk.data", "history-job-id": JOB_ID },
            );
            // Same partIndex delivered again — must NOT double the count.
            client.deliver(
                encodeChunk({
                    chunkIndex: 0,
                    elements: [buildSymbolMessage(), buildSymbolMessage()],
                    partIndex: 0,
                    partCount: 1,
                    finalForChunk: true,
                }),
                { "history-type": "history.chunk.data", "history-job-id": JOB_ID },
            );

            const entry = source.chunkEntry(SOURCE_ID, "chunk-0");
            expect(entry?.elementCount).toBe(2);
            expect(entry?.seenParts.size).toBe(1);
            // frameCount still tracks frames received (useful diagnostic).
            expect(entry?.frameCount).toBe(2);
        });

        it("distinct chunkKeys with colliding chunkIndex register as separate slots", async () => {
            // Regression: the backend can reuse the same `chunkIndex` value
            // across distinct wall-clock time slots within one job (observed
            // when cached and live producer streams interleave and each starts
            // its index at 0). Previously the client ledger was keyed by
            // `chunkIndex`, so the second slot's frames were classified as
            // duplicates of the first, dropped silently, and the audit then
            // marked the second slot "missing" because no entry existed at
            // its `fromMs`.
            respondWith({
                requestId: "req-1",
                status: "ACCEPTED",
                subjects: [{ subjectType: "SOURCE", subjectId: SOURCE_ID, jobId: JOB_ID }],
            });
            const client = fakeClient();
            const source = makeSource(client);
            source.retain();
            await flush();
            client.deliver(encodeJsonEvent(PLAN_EVENT));

            const planFromMs = source.getPlan()!.fromMs;
            const bucketWidthMs = source.getPlan()!.bucketWidthMs;

            // Slot 0: chunkIndex=0, key "slot-A".
            client.deliver(
                encodeChunk({
                    chunkIndex: 0,
                    chunkKey: "slot-A",
                    fromEpochMillis: planFromMs,
                    toEpochMillis: planFromMs + bucketWidthMs,
                    elements: [buildSymbolMessage()],
                }),
                { "history-type": "history.chunk.data", "history-job-id": JOB_ID },
            );
            // Slot 1: chunkIndex=0 (collision!), key "slot-B" — must NOT be
            // deduped against slot A.
            client.deliver(
                encodeChunk({
                    chunkIndex: 0,
                    chunkKey: "slot-B",
                    fromEpochMillis: planFromMs + bucketWidthMs,
                    toEpochMillis: planFromMs + 2 * bucketWidthMs,
                    elements: [buildSymbolMessage(), buildSymbolMessage()],
                }),
                { "history-type": "history.chunk.data", "history-job-id": JOB_ID },
            );
            client.deliver(encodeJsonEvent(COMPLETED_EVENT));

            // Both slots got a distinct ledger entry.
            const slotA = source.chunkEntry(SOURCE_ID, "slot-A");
            const slotB = source.chunkEntry(SOURCE_ID, "slot-B");
            expect(slotA?.elementCount).toBe(1);
            expect(slotA?.fromMs).toBe(planFromMs);
            expect(slotB?.elementCount).toBe(2);
            expect(slotB?.fromMs).toBe(planFromMs + bucketWidthMs);

            // Audit sees both slots covered — no "missing" classification.
            const ranges = source.getChunkRanges();
            expect(ranges).toHaveLength(2);
            expect(ranges.every((r) => r.status === "ok")).toBe(true);
        });
    });

    describe("job-id chunk filtering", () => {
        it("ingests a chunk whose history-job-id matches the ack's jobId", async () => {
            respondWith({
                requestId: "req-1",
                status: "ACCEPTED",
                subjects: [{ subjectType: "SOURCE", subjectId: SOURCE_ID, jobId: JOB_ID }],
            });
            const client = fakeClient();
            const source = makeSource(client);
            source.retain();
            await flush();
            client.deliver(encodeJsonEvent(PLAN_EVENT));

            client.deliver(encodeChunk({ chunkIndex: 0, elements: [buildSymbolMessage()] }), {
                "history-type": "history.chunk.data",
                "history-job-id": JOB_ID,
            });

            expect(source.chunks.size).toBe(1);
            expect(source.getChunkRanges()).toHaveLength(1);
        });

        it("discards a chunk whose history-job-id does not match the ack's jobId", async () => {
            respondWith({
                requestId: "req-1",
                status: "ACCEPTED",
                subjects: [{ subjectType: "SOURCE", subjectId: SOURCE_ID, jobId: JOB_ID }],
            });
            const client = fakeClient();
            const source = makeSource(client);
            const emitted: Element[][] = [];
            source.onElements((els) => emitted.push(els));
            source.retain();
            await flush();
            client.deliver(encodeJsonEvent(PLAN_EVENT));

            // A frame from another tab's job arriving on the shared per-user queue.
            client.deliver(encodeChunk({ chunkIndex: 0, elements: [buildSymbolMessage()] }), {
                "history-type": "history.chunk.data",
                "history-job-id": "job-FOREIGN",
            });

            await flush();
            expect(source.chunks.size).toBe(0);
            expect(source.getChunkRanges()).toHaveLength(0);
            expect(emitted).toHaveLength(0);
        });

        it("discards a chunk frame with no history-job-id header (hard-require)", async () => {
            respondWith({
                requestId: "req-1",
                status: "ACCEPTED",
                subjects: [{ subjectType: "SOURCE", subjectId: SOURCE_ID, jobId: JOB_ID }],
            });
            const client = fakeClient();
            const source = makeSource(client);
            source.retain();
            await flush();
            client.deliver(encodeJsonEvent(PLAN_EVENT));

            client.deliver(encodeChunk({ chunkIndex: 0, elements: [buildSymbolMessage()] }), {
                "history-type": "history.chunk.data",
            });

            expect(source.chunks.size).toBe(0);
        });

        it("discards every chunk when the ack carries no subjects (no job ids)", async () => {
            // Degenerate ack: `_jobIds` stays empty, so no frame can ever match. This is
            // the silent-failure path the source guards with an error log + heartbeat.
            respondWith({ requestId: "req-1", status: "ACCEPTED", subjects: [] });
            const client = fakeClient();
            const source = makeSource(client);
            source.retain();
            await flush();

            client.deliver(encodeChunk({ chunkIndex: 0, elements: [buildSymbolMessage()] }), {
                "history-type": "history.chunk.data",
                "history-job-id": JOB_ID,
            });
            expect(source.chunks.size).toBe(0);
        });
    });

    describe("terminal events", () => {
        it("history.job.completed transitions to enabled", async () => {
            respondWith({
                requestId: "req-1",
                status: "ACCEPTED",
                subjects: [{ subjectType: "SOURCE", subjectId: SOURCE_ID, jobId: JOB_ID }],
            });
            const client = fakeClient();
            const source = makeSource(client);
            source.retain();
            await flush();
            client.deliver(encodeJsonEvent(PLAN_EVENT));

            // Deliver both chunks the plan promises so the audit passes clean.
            for (let i = 0; i < 2; i++) {
                client.deliver(encodeChunk({ chunkIndex: i, elements: [buildSymbolMessage()] }), {
                    "history-type": "history.chunk.data",
                    "history-job-id": JOB_ID,
                });
            }
            client.deliver(encodeJsonEvent(COMPLETED_EVENT));

            expect(source.state).toBe("enabled");
            expect(source.getError()).toBeNull();
        });

        it("audit gap leaves state=enabled, no error, and surfaces missing chunk with synthesized boundaries", async () => {
            respondWith({
                requestId: "req-1",
                status: "ACCEPTED",
                subjects: [{ subjectType: "SOURCE", subjectId: SOURCE_ID, jobId: JOB_ID }],
            });
            const client = fakeClient();
            const source = makeSource(client);
            source.retain();
            await flush();
            client.deliver(encodeJsonEvent(PLAN_EVENT));

            const planFromMs = source.getPlan()!.fromMs;
            const bucketWidthMs = source.getPlan()!.bucketWidthMs;

            // Deliver only chunk 0 of 2 — chunk 1 is missing.
            client.deliver(
                encodeChunk({
                    chunkIndex: 0,
                    fromEpochMillis: planFromMs,
                    toEpochMillis: planFromMs + bucketWidthMs,
                    elements: [buildSymbolMessage()],
                }),
                { "history-type": "history.chunk.data", "history-job-id": JOB_ID },
            );
            client.deliver(encodeJsonEvent(COMPLETED_EVENT));

            expect(source.state).toBe("enabled");
            // No curtain-triggering error — the histogram communicates the gap.
            expect(source.getError()).toBeNull();

            const ranges = source.getChunkRanges();
            expect(ranges).toHaveLength(2);

            expect(ranges[0]).toMatchObject({
                chunkIndex: 0,
                status: "ok",
                count: 1,
            });

            // Missing chunk synthesizes uniform boundaries from the plan.
            const expectedMissingFromMs = planFromMs + 1 * bucketWidthMs;
            const expectedMissingToMs = expectedMissingFromMs + bucketWidthMs;
            expect(ranges[1]).toMatchObject({
                chunkIndex: 1,
                status: "missing",
                count: 0,
                start: new Date(expectedMissingFromMs).toISOString(),
                end: new Date(expectedMissingToMs).toISOString(),
            });
        });

        it("audit aligns chunks by fromMs when backend uses non-contiguous chunkIndex (no duplicate start times)", async () => {
            // Regression: the backend can leave gaps in the chunkIndex
            // sequence (e.g., idx 0 covers slot 0, idx 2 covers slot 4) that
            // don't correspond to missing time slots. Index-based iteration
            // previously emitted two ChunkRange entries for the same wall-
            // clock time — one "ok" (from the received chunk) and one
            // "missing" (synthesized at the same slot) — which collided on
            // React keys and stacked invisible bars under red bars.
            respondWith({
                requestId: "req-1",
                status: "ACCEPTED",
                subjects: [{ subjectType: "SOURCE", subjectId: SOURCE_ID, jobId: JOB_ID }],
            });
            const client = fakeClient();
            const source = makeSource(client);
            source.retain();
            await flush();

            const planEvent: HistoryJobPlanEvent = {
                ...PLAN_EVENT,
                chunks: 6,
                chunksToCompute: 6,
                effectiveTo: "2026-01-01T00:06:00.000Z",
            };
            client.deliver(encodeJsonEvent(planEvent));

            const planFromMs = source.getPlan()!.fromMs;
            const bucketWidthMs = source.getPlan()!.bucketWidthMs;

            // chunkIndex 0 → slot 0 (fromMs aligned)
            client.deliver(
                encodeChunk({
                    chunkIndex: 0,
                    fromEpochMillis: planFromMs,
                    toEpochMillis: planFromMs + bucketWidthMs,
                    elements: [buildSymbolMessage()],
                }),
                { "history-type": "history.chunk.data", "history-job-id": JOB_ID },
            );
            // chunkIndex 2 → slot 4 (fromMs offset by 4 buckets, not 2).
            // Slots 1, 2, 3, 5 receive no chunks.
            client.deliver(
                encodeChunk({
                    chunkIndex: 2,
                    fromEpochMillis: planFromMs + 4 * bucketWidthMs,
                    toEpochMillis: planFromMs + 5 * bucketWidthMs,
                    elements: [buildSymbolMessage()],
                }),
                { "history-type": "history.chunk.data", "history-job-id": JOB_ID },
            );
            client.deliver(
                encodeJsonEvent({ ...COMPLETED_EVENT, totalChunks: 6, deliveredChunks: 2 }),
            );

            const ranges = source.getChunkRanges();
            // Exactly one range per slot — no duplicates.
            expect(ranges).toHaveLength(6);
            const starts = ranges.map((r) => r.start);
            expect(new Set(starts).size).toBe(6);

            // Slot 0 and slot 4 are "ok" (the received chunks); slots 1, 2, 3,
            // 5 are "missing".
            const slotFor = (slotIdx: number) =>
                new Date(planFromMs + slotIdx * bucketWidthMs).toISOString();
            expect(ranges.find((r) => r.start === slotFor(0))?.status).toBe("ok");
            expect(ranges.find((r) => r.start === slotFor(1))?.status).toBe("missing");
            expect(ranges.find((r) => r.start === slotFor(2))?.status).toBe("missing");
            expect(ranges.find((r) => r.start === slotFor(3))?.status).toBe("missing");
            expect(ranges.find((r) => r.start === slotFor(4))?.status).toBe("ok");
            expect(ranges.find((r) => r.start === slotFor(5))?.status).toBe("missing");
        });

        it("audit surfaces incomplete-parts on a chunk that never received its final part", async () => {
            respondWith({
                requestId: "req-1",
                status: "ACCEPTED",
                subjects: [{ subjectType: "SOURCE", subjectId: SOURCE_ID, jobId: JOB_ID }],
            });
            const client = fakeClient();
            const source = makeSource(client);
            source.retain();
            await flush();
            client.deliver(encodeJsonEvent(PLAN_EVENT));

            const planFromMs = source.getPlan()!.fromMs;
            const bucketWidthMs = source.getPlan()!.bucketWidthMs;

            // Chunk 0: 2 of 3 parts, no final marker on either — incomplete.
            client.deliver(
                encodeChunk({
                    chunkIndex: 0,
                    fromEpochMillis: planFromMs,
                    toEpochMillis: planFromMs + bucketWidthMs,
                    elements: [buildSymbolMessage()],
                    partIndex: 0,
                    partCount: 3,
                    finalForChunk: false,
                }),
                { "history-type": "history.chunk.data", "history-job-id": JOB_ID },
            );
            client.deliver(
                encodeChunk({
                    chunkIndex: 0,
                    fromEpochMillis: planFromMs,
                    toEpochMillis: planFromMs + bucketWidthMs,
                    elements: [buildSymbolMessage()],
                    partIndex: 1,
                    partCount: 3,
                    finalForChunk: false,
                }),
                { "history-type": "history.chunk.data", "history-job-id": JOB_ID },
            );
            // Chunk 1: clean.
            client.deliver(
                encodeChunk({
                    chunkIndex: 1,
                    fromEpochMillis: planFromMs + bucketWidthMs,
                    toEpochMillis: planFromMs + 2 * bucketWidthMs,
                    elements: [buildSymbolMessage()],
                }),
                { "history-type": "history.chunk.data", "history-job-id": JOB_ID },
            );
            client.deliver(encodeJsonEvent(COMPLETED_EVENT));

            expect(source.state).toBe("enabled");
            expect(source.getError()).toBeNull();

            const ranges = source.getChunkRanges();
            expect(ranges[0]).toMatchObject({
                chunkIndex: 0,
                status: "incomplete-parts",
                parts: { seen: 2, expected: 3 },
            });
            expect(ranges[1].status).toBe("ok");
        });

        it("does not classify incomplete-parts during streaming (no red flash mid-load)", async () => {
            respondWith({
                requestId: "req-1",
                status: "ACCEPTED",
                subjects: [{ subjectType: "SOURCE", subjectId: SOURCE_ID, jobId: JOB_ID }],
            });
            const client = fakeClient();
            const source = makeSource(client);
            source.retain();
            await flush();
            client.deliver(encodeJsonEvent(PLAN_EVENT));

            // Deliver only 1 of 3 expected parts for chunk 0 — without a
            // final marker. Later parts could still arrive.
            client.deliver(
                encodeChunk({
                    chunkIndex: 0,
                    elements: [buildSymbolMessage()],
                    partIndex: 0,
                    partCount: 3,
                    finalForChunk: false,
                }),
                { "history-type": "history.chunk.data", "history-job-id": JOB_ID },
            );

            // history.job.completed has NOT been delivered yet.
            const ranges = source.getChunkRanges();
            expect(ranges).toHaveLength(1);
            expect(ranges[0].status).toBe("ok");
        });

        it("history.job.failed transitions to error and carries stage + reason", async () => {
            respondWith({
                requestId: "req-1",
                status: "ACCEPTED",
                subjects: [{ subjectType: "SOURCE", subjectId: SOURCE_ID, jobId: JOB_ID }],
            });
            const client = fakeClient();
            const source = makeSource(client);
            source.retain();
            await flush();

            client.deliver(encodeJsonEvent(FAILED_EVENT));
            expect(source.state).toBe("error");
            expect(source.getError()).toEqual({
                kind: "jobFailed",
                stage: "compute",
                reason: "boom",
            });
        });

        it("history.job.failed carries the EXECUTION_FAILED_STORAGE stage verbatim", async () => {
            respondWith({
                requestId: "req-1",
                status: "ACCEPTED",
                subjects: [{ subjectType: "SOURCE", subjectId: SOURCE_ID, jobId: JOB_ID }],
            });
            const client = fakeClient();
            const source = makeSource(client);
            source.retain();
            await flush();

            client.deliver(
                encodeJsonEvent({
                    ...FAILED_EVENT,
                    stage: "EXECUTION_FAILED_STORAGE",
                    reason: "redis timed out",
                }),
            );
            expect(source.getError()).toEqual({
                kind: "jobFailed",
                stage: "EXECUTION_FAILED_STORAGE",
                reason: "redis timed out",
            });
        });

        it("history.job.failed with stage CANCELLED maps to jobSuperseded, not jobFailed", async () => {
            // The backend cancels a user's running job when that user issues a newer
            // request (one-job-per-user). It arrives as history.job.failed/CANCELLED;
            // the source surfaces the retryable jobSuperseded kind instead of a failure.
            respondWith({
                requestId: "req-1",
                status: "ACCEPTED",
                subjects: [{ subjectType: "SOURCE", subjectId: SOURCE_ID, jobId: JOB_ID }],
            });
            const client = fakeClient();
            const source = makeSource(client);
            source.retain();
            await flush();

            client.deliver(
                encodeJsonEvent({
                    ...FAILED_EVENT,
                    stage: "CANCELLED",
                    reason: "Superseded by request 11111111-1111-1111-1111-111111111111",
                }),
            );
            expect(source.state).toBe("error");
            expect(source.getError()).toEqual({ kind: "jobSuperseded" });
        });
    });

    describe("observability", () => {
        it("subscribe fires for plan, chunk, and state transitions", async () => {
            respondWith({
                requestId: "req-1",
                status: "ACCEPTED",
                subjects: [{ subjectType: "SOURCE", subjectId: SOURCE_ID, jobId: JOB_ID }],
            });
            const client = fakeClient();
            const source = makeSource(client);
            const versions: number[] = [];
            source.subscribe(() => versions.push(source.getVersion()));
            source.retain();
            await flush();

            const before = versions.length;
            client.deliver(encodeJsonEvent(PLAN_EVENT));
            client.deliver(encodeChunk({ chunkIndex: 0, elements: [buildSymbolMessage()] }), {
                "history-type": "history.chunk.data",
                "history-job-id": JOB_ID,
            });
            client.deliver(encodeJsonEvent(COMPLETED_EVENT));

            expect(versions.length).toBeGreaterThan(before);
            // Versions are monotonically increasing.
            for (let i = 1; i < versions.length; i++) {
                expect(versions[i]).toBeGreaterThan(versions[i - 1]);
            }
        });
    });

    describe("getLoadingProgress lifecycle", () => {
        it("returns null before submit completes (no plan yet)", async () => {
            respondWith({
                requestId: "req-1",
                status: "ACCEPTED",
                subjects: [{ subjectType: "SOURCE", subjectId: SOURCE_ID, jobId: JOB_ID }],
            });
            const client = fakeClient();
            const source = makeSource(client);
            source.retain();
            await flush();

            // Submit returned, state is syncing, but no plan event has arrived.
            expect(source.state).toBe("syncing");
            expect(source.getLoadingProgress()).toBeNull();
        });

        it("populates {loaded:0,total:N} on plan and increments per chunk", async () => {
            respondWith({
                requestId: "req-1",
                status: "ACCEPTED",
                subjects: [{ subjectType: "SOURCE", subjectId: SOURCE_ID, jobId: JOB_ID }],
            });
            const client = fakeClient();
            const source = makeSource(client);
            source.retain();
            await flush();

            client.deliver(encodeJsonEvent(PLAN_EVENT));
            expect(source.getLoadingProgress()).toEqual({ loaded: 0, total: 2, subjects: 1 });

            client.deliver(encodeChunk({ chunkIndex: 0, elements: [buildSymbolMessage()] }), {
                "history-type": "history.chunk.data",
                "history-job-id": JOB_ID,
            });
            expect(source.getLoadingProgress()).toEqual({ loaded: 1, total: 2, subjects: 1 });

            client.deliver(encodeChunk({ chunkIndex: 1, elements: [buildSymbolMessage()] }), {
                "history-type": "history.chunk.data",
                "history-job-id": JOB_ID,
            });
            expect(source.getLoadingProgress()).toEqual({ loaded: 2, total: 2, subjects: 1 });
        });

        it("clears to null on history.job.completed (state leaves syncing)", async () => {
            respondWith({
                requestId: "req-1",
                status: "ACCEPTED",
                subjects: [{ subjectType: "SOURCE", subjectId: SOURCE_ID, jobId: JOB_ID }],
            });
            const client = fakeClient();
            const source = makeSource(client);
            source.retain();
            await flush();

            client.deliver(encodeJsonEvent(PLAN_EVENT));
            for (let i = 0; i < 2; i++) {
                client.deliver(encodeChunk({ chunkIndex: i, elements: [buildSymbolMessage()] }), {
                    "history-type": "history.chunk.data",
                    "history-job-id": JOB_ID,
                });
            }
            client.deliver(encodeJsonEvent(COMPLETED_EVENT));

            expect(source.state).toBe("enabled");
            expect(source.getLoadingProgress()).toBeNull();
        });

        it("clears to null when transport error transitions state out of syncing", async () => {
            respondWith({
                requestId: "req-1",
                status: "ACCEPTED",
                subjects: [{ subjectType: "SOURCE", subjectId: SOURCE_ID, jobId: JOB_ID }],
            });
            const client = fakeClient();
            const source = makeSource(client);
            source.retain();
            await flush();

            client.deliver(encodeJsonEvent(PLAN_EVENT));
            expect(source.getLoadingProgress()).not.toBeNull();

            client.deliver(encodeJsonEvent(FAILED_EVENT));
            expect(source.state).toBe("error");
            expect(source.getLoadingProgress()).toBeNull();
        });

        it("returns a stable reference across repeated calls when underlying state is unchanged", async () => {
            respondWith({
                requestId: "req-1",
                status: "ACCEPTED",
                subjects: [{ subjectType: "SOURCE", subjectId: SOURCE_ID, jobId: JOB_ID }],
            });
            const client = fakeClient();
            const source = makeSource(client);
            source.retain();
            await flush();

            client.deliver(encodeJsonEvent(PLAN_EVENT));
            client.deliver(encodeChunk({ chunkIndex: 0, elements: [buildSymbolMessage()] }), {
                "history-type": "history.chunk.data",
                "history-job-id": JOB_ID,
            });

            // useSyncExternalStore calls the snapshot multiple times per render
            // and expects identity equality when underlying state is unchanged.
            const first = source.getLoadingProgress();
            const second = source.getLoadingProgress();
            const third = source.getLoadingProgress();
            expect(first).toBe(second);
            expect(second).toBe(third);
        });

        it("returns a new reference when loaded changes; same reference until the next change", async () => {
            respondWith({
                requestId: "req-1",
                status: "ACCEPTED",
                subjects: [{ subjectType: "SOURCE", subjectId: SOURCE_ID, jobId: JOB_ID }],
            });
            const client = fakeClient();
            const source = makeSource(client);
            source.retain();
            await flush();

            client.deliver(encodeJsonEvent(PLAN_EVENT));
            const afterPlan = source.getLoadingProgress();

            client.deliver(encodeChunk({ chunkIndex: 0, elements: [buildSymbolMessage()] }), {
                "history-type": "history.chunk.data",
                "history-job-id": JOB_ID,
            });
            const afterChunk0 = source.getLoadingProgress();

            expect(afterPlan).not.toBe(afterChunk0);
            // Same reference on repeated reads with no new chunk.
            expect(source.getLoadingProgress()).toBe(afterChunk0);
        });
    });

    describe("multi-chunk same-proto-id ingestion", () => {
        it("produces distinct elements when the same proto id appears in two different chunks", async () => {
            respondWith({
                requestId: "req-1",
                status: "ACCEPTED",
                subjects: [{ subjectType: "SOURCE", subjectId: SOURCE_ID, jobId: JOB_ID }],
            });
            const client = fakeClient();
            const source = makeSource(client);
            const emitted: Element[] = [];
            source.onElements((els) => emitted.push(...els));
            source.retain();
            await flush();

            client.deliver(encodeJsonEvent(PLAN_EVENT));

            // Two ElementMessages share the same proto id bytes and objectId
            // but arrive in different chunks (chunkIndex 19 and 20).
            const sharedId = new Uint8Array(16);
            const sharedObjectId = "carry-forward-entity";

            const elementMsg19 = ElementMessage.create({
                type: "symbol",
                id: sharedId,
                objectId: sharedObjectId,
                source: new Uint8Array(16),
                displayName: "Entity",
                positions: [38, -77],
                stale: 0,
                start: 0,
                time: 0,
                values: {
                    symbol: { string: "SFGPUCI----D***" },
                    size: { double: 20 },
                },
            });

            const elementMsg20 = ElementMessage.create({
                type: "symbol",
                id: sharedId,
                objectId: sharedObjectId,
                source: new Uint8Array(16),
                displayName: "Entity",
                positions: [38, -77],
                stale: 0,
                start: 0,
                time: 0,
                values: {
                    symbol: { string: "SFGPUCI----D***" },
                    size: { double: 20 },
                },
            });

            client.deliver(
                encodeChunk({
                    chunkIndex: 19,
                    elements: [elementMsg19],
                    finalForChunk: true,
                }),
                { "history-type": "history.chunk.data", "history-job-id": JOB_ID },
            );

            client.deliver(
                encodeChunk({
                    chunkIndex: 20,
                    elements: [elementMsg20],
                    finalForChunk: true,
                }),
                { "history-type": "history.chunk.data", "history-job-id": JOB_ID },
            );

            await flush();

            // Handler received two elements
            expect(emitted).toHaveLength(2);

            // Their id values differ — each carries the chunk index suffix
            expect(emitted[0].id).not.toBe(emitted[1].id);
            expect(emitted[0].id).toContain(":19");
            expect(emitted[1].id).toContain(":20");

            // Their key values differ
            expect(emitted[0].key).not.toBe(emitted[1].key);

            // Their objectId values are equal
            expect(emitted[0].objectId).toBe(sharedObjectId);
            expect(emitted[1].objectId).toBe(sharedObjectId);
        });
    });

    describe("multi-source (multi-subject)", () => {
        const SRC_A = "00000000-0000-0000-0000-00000000000a";
        const SRC_B = "00000000-0000-0000-0000-00000000000b";
        const JOB_A = "job-A";
        const JOB_B = "job-B";

        function makeMultiSource(client: OCMPClient): HistoricalSession {
            return new HistoricalSession({
                client,
                processor: getProcessor(),
                params: {
                    sourceIds: [SRC_A, SRC_B],
                    subjectTypes: { [SRC_A]: "SOURCE", [SRC_B]: "SOURCE" },
                    from: "2026-01-01T00:00:00.000Z",
                    to: "2026-01-01T01:00:00.000Z",
                },
            });
        }

        it("submits every selected source as a subject", async () => {
            respondWith({
                requestId: "req-1",
                status: "ACCEPTED",
                subjects: [
                    { subjectType: "SOURCE", subjectId: SRC_A, jobId: JOB_A },
                    { subjectType: "SOURCE", subjectId: SRC_B, jobId: JOB_B },
                ],
            });
            const source = makeMultiSource(fakeClient());
            source.retain();
            await flush();

            expect(submittedBodies).toHaveLength(1);
            expect(submittedBodies[0].sources).toEqual([
                { subjectType: "SOURCE", subjectId: SRC_A },
                { subjectType: "SOURCE", subjectId: SRC_B },
            ]);
            expect([...source.jobIds].sort()).toEqual([JOB_A, JOB_B]);
        });

        it("routes a chunkKey shared by two sources to separate ledger entries and completes only after all jobs", async () => {
            respondWith({
                requestId: "req-1",
                status: "ACCEPTED",
                subjects: [
                    { subjectType: "SOURCE", subjectId: SRC_A, jobId: JOB_A },
                    { subjectType: "SOURCE", subjectId: SRC_B, jobId: JOB_B },
                ],
            });
            const client = fakeClient();
            const source = makeMultiSource(client);
            source.retain();
            await flush();

            // One identical plan (both jobs share the window); one chunk per source.
            client.deliver(
                encodeJsonEvent({
                    ...PLAN_EVENT,
                    chunks: 1,
                    chunksToCompute: 1,
                    effectiveTo: "2026-01-01T00:01:00.000Z",
                }),
            );
            const planFromMs = source.getPlan()!.fromMs;
            const bucketWidthMs = source.getPlan()!.bucketWidthMs;

            // The SAME chunkKey "slot-0" arrives from BOTH jobs (sources legitimately
            // collide on chunkKey for the same UTC slot). Must NOT dedup across sources.
            client.deliver(
                encodeChunk({
                    chunkIndex: 0,
                    chunkKey: "slot-0",
                    fromEpochMillis: planFromMs,
                    toEpochMillis: planFromMs + bucketWidthMs,
                    elements: [buildSymbolMessage()],
                }),
                { "history-type": "history.chunk.data", "history-job-id": JOB_A },
            );
            client.deliver(
                encodeChunk({
                    chunkIndex: 0,
                    chunkKey: "slot-0",
                    fromEpochMillis: planFromMs,
                    toEpochMillis: planFromMs + bucketWidthMs,
                    elements: [buildSymbolMessage(), buildSymbolMessage()],
                }),
                { "history-type": "history.chunk.data", "history-job-id": JOB_B },
            );

            // Two distinct ledger entries despite the shared chunkKey.
            expect(source.chunkEntry(SRC_A, "slot-0")?.elementCount).toBe(1);
            expect(source.chunkEntry(SRC_B, "slot-0")?.elementCount).toBe(2);
            expect(source.chunks.size).toBe(2);
            expect(
                source
                    .getChunkRanges()
                    .map((r) => r.sourceId)
                    .sort(),
            ).toEqual([SRC_A, SRC_B]);

            // Job A completes — the query stays syncing while B is outstanding.
            client.deliver(
                encodeJsonEvent({
                    ...COMPLETED_EVENT,
                    jobId: JOB_A,
                    totalChunks: 1,
                    deliveredChunks: 1,
                }),
            );
            expect(source.state).toBe("syncing");

            // Job B completes — now enabled; the audit classifies both sources' slots.
            client.deliver(
                encodeJsonEvent({
                    ...COMPLETED_EVENT,
                    jobId: JOB_B,
                    totalChunks: 1,
                    deliveredChunks: 1,
                }),
            );
            expect(source.state).toBe("enabled");
            expect(source.getError()).toBeNull();

            const ranges = source.getChunkRanges();
            expect(ranges).toHaveLength(2);
            expect(ranges.every((r) => r.status === "ok")).toBe(true);
            expect(ranges.map((r) => r.sourceId).sort()).toEqual([SRC_A, SRC_B]);
        });

        it("counts delivered chunks against planned chunks across every subject", async () => {
            respondWith({
                requestId: "req-1",
                status: "ACCEPTED",
                subjects: [
                    { subjectType: "SOURCE", subjectId: SRC_A, jobId: JOB_A },
                    { subjectType: "SOURCE", subjectId: SRC_B, jobId: JOB_B },
                ],
            });
            const client = fakeClient();
            const source = makeMultiSource(client);
            source.retain();
            await flush();

            client.deliver(
                encodeJsonEvent({
                    ...PLAN_EVENT,
                    chunks: 1,
                    chunksToCompute: 1,
                    effectiveTo: "2026-01-01T00:01:00.000Z",
                }),
            );
            const planFromMs = source.getPlan()!.fromMs;
            const bucketWidthMs = source.getPlan()!.bucketWidthMs;

            // `total` is the per-subject plan count (1) × 2 subjects. The earlier
            // rule reported the slice count (1) and only advanced once *every*
            // subject had delivered a slot, so a query with one slow source read
            // as zero while the fast ones had already rendered.
            expect(source.getLoadingProgress()).toEqual({ loaded: 0, total: 2, subjects: 2 });

            // SRC_A's chunk has arrived and rendered, so progress moves now —
            // it no longer waits on SRC_B to deliver the same slot.
            client.deliver(
                encodeChunk({
                    chunkIndex: 0,
                    chunkKey: "slot-0",
                    fromEpochMillis: planFromMs,
                    toEpochMillis: planFromMs + bucketWidthMs,
                    elements: [buildSymbolMessage()],
                }),
                { "history-type": "history.chunk.data", "history-job-id": JOB_A },
            );
            expect(source.getLoadingProgress()).toEqual({ loaded: 1, total: 2, subjects: 2 });

            // Both subjects have delivered their chunk for the slot.
            client.deliver(
                encodeChunk({
                    chunkIndex: 0,
                    chunkKey: "slot-0",
                    fromEpochMillis: planFromMs,
                    toEpochMillis: planFromMs + bucketWidthMs,
                    elements: [buildSymbolMessage()],
                }),
                { "history-type": "history.chunk.data", "history-job-id": JOB_B },
            );
            expect(source.getLoadingProgress()).toEqual({ loaded: 2, total: 2, subjects: 2 });
        });

        it("removeSources drops a source's chunks/jobs in place without re-submitting", async () => {
            respondWith({
                requestId: "req-1",
                status: "ACCEPTED",
                subjects: [
                    { subjectType: "SOURCE", subjectId: SRC_A, jobId: JOB_A },
                    { subjectType: "SOURCE", subjectId: SRC_B, jobId: JOB_B },
                ],
            });
            const client = fakeClient();
            const source = makeMultiSource(client);
            source.retain();
            await flush();
            client.deliver(encodeJsonEvent(PLAN_EVENT));
            const planFromMs = source.getPlan()!.fromMs;
            const bucketWidthMs = source.getPlan()!.bucketWidthMs;
            for (const job of [JOB_A, JOB_B]) {
                client.deliver(
                    encodeChunk({
                        chunkIndex: 0,
                        chunkKey: "slot-0",
                        fromEpochMillis: planFromMs,
                        toEpochMillis: planFromMs + bucketWidthMs,
                        elements: [buildSymbolMessage()],
                    }),
                    { "history-type": "history.chunk.data", "history-job-id": job },
                );
            }
            expect(source.chunks.size).toBe(2);

            source.removeSources([SRC_A]);

            expect(source.chunkEntry(SRC_A, "slot-0")).toBeUndefined();
            expect(source.chunkEntry(SRC_B, "slot-0")).toBeDefined();
            expect(source.chunks.size).toBe(1);
            expect([...source.jobIds]).toEqual([JOB_B]);
            expect(source.sourceParams.sourceIds).toEqual([SRC_B]);
            expect(source.getChunkRanges().map((r) => r.sourceId)).toEqual([SRC_B]);
            // No re-submit — removal is purely local.
            expect(submittedBodies).toHaveLength(1);
        });

        it("addSources streams a new source in via a sub-request once the initial load is complete", async () => {
            respondWith(
                {
                    requestId: "req-1",
                    status: "ACCEPTED",
                    subjects: [{ subjectType: "SOURCE", subjectId: SRC_A, jobId: JOB_A }],
                },
                {
                    requestId: "req-2",
                    status: "ACCEPTED",
                    subjects: [{ subjectType: "SOURCE", subjectId: SRC_B, jobId: JOB_B }],
                },
            );
            const client = fakeClient();
            const source = new HistoricalSession({
                client,
                processor: getProcessor(),
                params: {
                    sourceIds: [SRC_A],
                    subjectTypes: { [SRC_A]: "SOURCE" },
                    from: "2026-01-01T00:00:00.000Z",
                    to: "2026-01-01T01:00:00.000Z",
                },
            });
            source.retain();
            await flush();

            // Drive the initial source to completion (enabled).
            client.deliver(
                encodeJsonEvent({
                    ...PLAN_EVENT,
                    chunks: 1,
                    chunksToCompute: 1,
                    effectiveTo: "2026-01-01T00:01:00.000Z",
                }),
            );
            const planFromMs = source.getPlan()!.fromMs;
            const bucketWidthMs = source.getPlan()!.bucketWidthMs;
            client.deliver(
                encodeChunk({
                    chunkIndex: 0,
                    chunkKey: "slot-0",
                    fromEpochMillis: planFromMs,
                    toEpochMillis: planFromMs + bucketWidthMs,
                    elements: [buildSymbolMessage()],
                }),
                { "history-type": "history.chunk.data", "history-job-id": JOB_A },
            );
            client.deliver(
                encodeJsonEvent({
                    ...COMPLETED_EVENT,
                    jobId: JOB_A,
                    totalChunks: 1,
                    deliveredChunks: 1,
                }),
            );
            expect(source.state).toBe("enabled");

            // Add a second source post-load: a sub-request goes out for JUST the
            // new source, the query re-enters syncing, and both jobs are tracked.
            source.addSources([SRC_B], { [SRC_B]: "SOURCE" });
            await flush();

            expect(submittedBodies).toHaveLength(2);
            expect(submittedBodies[1].sources).toEqual([
                { subjectType: "SOURCE", subjectId: SRC_B },
            ]);
            expect(source.state).toBe("syncing");
            expect([...source.jobIds].sort()).toEqual([JOB_A, JOB_B]);
            expect(source.sourceParams.sourceIds).toEqual([SRC_A, SRC_B]);

            // The new source streams its chunk + completion (the plan from the
            // first load still applies — same window). Back to enabled, both
            // sources' chunks present.
            client.deliver(
                encodeChunk({
                    chunkIndex: 0,
                    chunkKey: "slot-0",
                    fromEpochMillis: planFromMs,
                    toEpochMillis: planFromMs + bucketWidthMs,
                    elements: [buildSymbolMessage(), buildSymbolMessage()],
                }),
                { "history-type": "history.chunk.data", "history-job-id": JOB_B },
            );
            client.deliver(
                encodeJsonEvent({
                    ...COMPLETED_EVENT,
                    requestId: "req-2",
                    jobId: JOB_B,
                    totalChunks: 1,
                    deliveredChunks: 1,
                }),
            );
            expect(source.state).toBe("enabled");
            expect(source.getError()).toBeNull();
            expect(source.chunkEntry(SRC_A, "slot-0")?.elementCount).toBe(1);
            expect(source.chunkEntry(SRC_B, "slot-0")?.elementCount).toBe(2);
            expect(
                source
                    .getChunkRanges()
                    .map((r) => r.sourceId)
                    .sort(),
            ).toEqual([SRC_A, SRC_B]);
        });

        it("addSources is a no-op while a load is still in flight (mid-load add is a full re-submit at the provider)", async () => {
            respondWith({
                requestId: "req-1",
                status: "ACCEPTED",
                subjects: [{ subjectType: "SOURCE", subjectId: SRC_A, jobId: JOB_A }],
            });
            const client = fakeClient();
            const source = new HistoricalSession({
                client,
                processor: getProcessor(),
                params: {
                    sourceIds: [SRC_A],
                    subjectTypes: { [SRC_A]: "SOURCE" },
                    from: "2026-01-01T00:00:00.000Z",
                    to: "2026-01-01T01:00:00.000Z",
                },
            });
            source.retain();
            await flush();
            expect(source.state).toBe("syncing");

            // The admission slot is occupied mid-load, so an in-place add isn't
            // admissible — the predicate the manager reconcile decision keys on.
            expect(source.canAcceptInPlaceAdd()).toBe(false);

            source.addSources([SRC_B], { [SRC_B]: "SOURCE" });
            await flush();

            // No sub-request while syncing; the selection is left untouched (the
            // provider re-submits the full set instead).
            expect(submittedBodies).toHaveLength(1);
            expect(source.sourceParams.sourceIds).toEqual([SRC_A]);
        });

        it("canAcceptInPlaceAdd tracks the single admission slot across the load lifecycle", async () => {
            respondWith({
                requestId: "req-1",
                status: "ACCEPTED",
                subjects: [{ subjectType: "SOURCE", subjectId: SRC_A, jobId: JOB_A }],
            });
            const client = fakeClient();
            const source = new HistoricalSession({
                client,
                processor: getProcessor(),
                params: {
                    sourceIds: [SRC_A],
                    subjectTypes: { [SRC_A]: "SOURCE" },
                    from: "2026-01-01T00:00:00.000Z",
                    to: "2026-01-01T01:00:00.000Z",
                },
            });
            // Disabled (pre-enable): nothing to extend in place yet.
            expect(source.canAcceptInPlaceAdd()).toBe(false);

            source.retain();
            await flush();
            // Syncing: the slot is occupied → reconcile must route adds to a rebuild.
            expect(source.state).toBe("syncing");
            expect(source.canAcceptInPlaceAdd()).toBe(false);

            client.deliver(
                encodeJsonEvent({
                    ...PLAN_EVENT,
                    chunks: 1,
                    chunksToCompute: 1,
                    effectiveTo: "2026-01-01T00:01:00.000Z",
                }),
            );
            const planFromMs = source.getPlan()!.fromMs;
            const bucketWidthMs = source.getPlan()!.bucketWidthMs;
            client.deliver(
                encodeChunk({
                    chunkIndex: 0,
                    chunkKey: "slot-0",
                    fromEpochMillis: planFromMs,
                    toEpochMillis: planFromMs + bucketWidthMs,
                    elements: [buildSymbolMessage()],
                }),
                { "history-type": "history.chunk.data", "history-job-id": JOB_A },
            );
            client.deliver(
                encodeJsonEvent({
                    ...COMPLETED_EVENT,
                    jobId: JOB_A,
                    totalChunks: 1,
                    deliveredChunks: 1,
                }),
            );
            // Enabled (load complete): the slot is free → an in-place add is admissible.
            expect(source.state).toBe("enabled");
            expect(source.canAcceptInPlaceAdd()).toBe(true);
        });
    });

    describe("per-subject delivery", () => {
        // A session fans out to one Source per subject. Each must receive only
        // its own elements, so the controller can enable, disable, and purge
        // them independently.
        it("routes each subject's elements to its own sink", async () => {
            const SOURCE_B = "11111111-1111-1111-1111-111111111111";
            respondWith({
                requestId: "req-1",
                status: "ACCEPTED",
                subjects: [
                    { subjectType: "SOURCE", subjectId: SOURCE_ID, jobId: "job-a" },
                    { subjectType: "SOURCE", subjectId: SOURCE_B, jobId: "job-b" },
                ],
            });
            const client = fakeClient();
            const source = new HistoricalSession({
                client,
                processor: getProcessor(),
                params: {
                    sourceIds: [SOURCE_ID, SOURCE_B],
                    subjectTypes: { [SOURCE_ID]: "SOURCE", [SOURCE_B]: "SOURCE" },
                    from: "2026-01-01T00:00:00.000Z",
                    to: "2026-01-01T01:00:00.000Z",
                },
            });

            const toA: Element[] = [];
            const toB: Element[] = [];
            source.attachSubject(SOURCE_ID, { deliver: (e) => toA.push(...e), setState: () => {} });
            source.attachSubject(SOURCE_B, { deliver: (e) => toB.push(...e), setState: () => {} });

            source.retain();
            await flush();
            client.deliver(encodeJsonEvent(PLAN_EVENT));
            const planFromMs = source.getPlan()!.fromMs;

            // One chunk per job; each carries an element for its own subject.
            client.deliver(
                encodeChunk({
                    chunkIndex: 0,
                    fromEpochMillis: planFromMs,
                    toEpochMillis: planFromMs + 30_000,
                    elements: [buildSymbolMessage(planFromMs + 50_000)],
                }),
                { "history-type": "history.chunk.data", "history-job-id": "job-a" },
            );
            await flush();

            expect(toA).toHaveLength(1);
            expect(toB).toHaveLength(0);
            expect(toA[0].source).toBe(SOURCE_ID);
        });
    });

    describe("re-enable after disable", () => {
        // `_disposed` gates both frame dispatch and the decode drain, and
        // `onDisable` sets it. If it is never re-armed, a disable -> enable cycle
        // on the same instance leaves the source subscribed and assembling chunks
        // while every decoded element is dropped before it reaches the store —
        // progress advances but nothing renders. The controller owns this
        // lifecycle now, so the cycle is reachable.
        it("delivers elements again after being disabled and re-enabled", async () => {
            respondWith({
                requestId: "req-1",
                status: "ACCEPTED",
                subjects: [{ subjectType: "SOURCE", subjectId: SOURCE_ID, jobId: JOB_ID }],
            });
            const client = fakeClient();
            const source = makeSource(client);
            const emitted: Element[] = [];
            source.onElements((els) => emitted.push(...els));

            source.retain();
            await flush();
            source.release();

            // Second lifecycle on the same instance.
            source.retain();
            await flush();

            client.deliver(encodeJsonEvent(PLAN_EVENT));
            const planFromMs = source.getPlan()!.fromMs;
            client.deliver(
                encodeChunk({
                    chunkIndex: 0,
                    fromEpochMillis: planFromMs,
                    toEpochMillis: planFromMs + 30_000,
                    elements: [buildSymbolMessage(planFromMs + 50_000)],
                }),
                { "history-type": "history.chunk.data", "history-job-id": JOB_ID },
            );
            await flush();

            expect(emitted).toHaveLength(1);
        });
    });

    describe("pre-ack buffering (initial ack window)", () => {
        // A deferred REST ack: the submit stays pending until we resolve it, so we
        // can deliver WS frames while `_jobIds`/`_requestId` are still empty — the
        // window a warm socket / cache hit can push the plan + leading chunks into,
        // faster than the ack round-trip, on real-latency deployments.
        function deferredAck(): { resolve: () => void } {
            return deferAck({
                requestId: "req-1",
                status: "ACCEPTED",
                subjects: [{ subjectType: "SOURCE", subjectId: SOURCE_ID, jobId: JOB_ID }],
            });
        }

        it("holds a plan + chunk delivered before the ack, then replays them (no loss)", async () => {
            const ack = deferredAck();
            const client = fakeClient();
            const source = makeSource(client);
            const emitted: Element[][] = [];
            source.onElements((els) => emitted.push(els));

            source.retain();

            // Backend pushes the chunk (out of order, before the plan) and the plan
            // while the ack is still in flight. Pre-fix both would be discarded and
            // never re-sent — losing the plan and the leading chunk.
            client.deliver(encodeChunk({ chunkIndex: 0, elements: [buildSymbolMessage()] }), {
                "history-type": "history.chunk.data",
                "history-job-id": JOB_ID,
            });
            client.deliver(encodeJsonEvent(PLAN_EVENT));

            // Held, not applied, while the ack is pending.
            expect(source.getPlan()).toBeNull();
            expect(source.chunks.size).toBe(0);

            ack.resolve();
            await flush();

            // Replayed after the ack: the plan is applied and the chunk is ingested.
            expect(source.getPlan()).not.toBeNull();
            expect(source.chunks.size).toBe(1);
            expect(source.getChunkRanges()).toHaveLength(1);
            expect(emitted.flat()).toHaveLength(1);
        });

        it("replays buffered frames but still discards genuinely foreign ones", async () => {
            const ack = deferredAck();
            const client = fakeClient();
            const source = makeSource(client);
            source.retain();

            // Our chunk and another tab's chunk both land in the pre-ack window.
            client.deliver(
                encodeChunk({ chunkIndex: 0, chunkKey: "ours", elements: [buildSymbolMessage()] }),
                { "history-type": "history.chunk.data", "history-job-id": JOB_ID },
            );
            client.deliver(
                encodeChunk({
                    chunkIndex: 0,
                    chunkKey: "theirs",
                    elements: [buildSymbolMessage()],
                }),
                { "history-type": "history.chunk.data", "history-job-id": "job-FOREIGN" },
            );

            ack.resolve();
            await flush();

            // Replay re-runs both through the gate: ours is kept, the foreign one is
            // discarded — buffering never admits another job's data.
            expect(source.chunks.size).toBe(1);
            expect(source.chunkEntry(SOURCE_ID, "ours")).toBeDefined();
            expect(source.chunkEntry(SOURCE_ID, "theirs")).toBeUndefined();
        });

        it("drops the buffer without applying it when the submit fails", async () => {
            failure = {
                status: 422,
                body: { code: "HISTORICAL_CONCURRENT_LIMIT", message: "boom" },
            };
            const client = fakeClient();
            const source = makeSource(client);
            source.retain();

            client.deliver(encodeChunk({ chunkIndex: 0, elements: [buildSymbolMessage()] }), {
                "history-type": "history.chunk.data",
                "history-job-id": JOB_ID,
            });
            client.deliver(encodeJsonEvent(PLAN_EVENT));

            await flush();

            // The request errored, so buffered frames are discarded, not applied.
            expect(source.getError()).toEqual({ kind: "concurrentLimit" });
            expect(source.getPlan()).toBeNull();
            expect(source.chunks.size).toBe(0);
        });

        it("caps the buffer at MAX_PREACK_BUFFER, dropping the newest frames beyond it", async () => {
            const ack = deferredAck();
            const client = fakeClient();
            const source = makeSource(client);
            source.retain();

            // Deliver more distinct pre-ack frames than the cap. Empty-element chunks
            // still create one ledger entry each on replay, so the ledger size after
            // the ack reveals how many survived the cap.
            const CAP = 1024; // mirrors MAX_PREACK_BUFFER in HistoricalSession.ts
            for (let i = 0; i < CAP + 5; i++) {
                client.deliver(encodeChunk({ chunkIndex: i, chunkKey: `slot-${i}` }), {
                    "history-type": "history.chunk.data",
                    "history-job-id": JOB_ID,
                });
            }

            ack.resolve();
            await flush();

            // The first CAP frames replayed; the newest 5 were dropped at the cap.
            expect(source.chunks.size).toBe(CAP);
        }, 20_000); // Heaviest test in the file: 1024 synchronous frame replays, each
        // triggering an O(n) sort in recomputeChunkRanges (O(n²) overall). ~0.3s in
        // isolation, but tips past the 5s default under saturated-CI contention.

        it("coalesces the replay into a single bump, not one per frame", async () => {
            // The replay of N buffered chunk frames is a synchronous loop. It must
            // notify subscribers once for the whole batch — not once per frame — so a
            // large pre-ack buffer can't fan out into N re-renders (and N O(n) sorts).
            const ack = deferredAck();
            const client = fakeClient();
            const source = makeSource(client);
            source.retain();

            const FRAMES = 6;
            for (let i = 0; i < FRAMES; i++) {
                client.deliver(encodeChunk({ chunkIndex: i, chunkKey: `slot-${i}` }), {
                    "history-type": "history.chunk.data",
                    "history-job-id": JOB_ID,
                });
            }

            // Subscribe just before the ack so we capture only the replay's bumps.
            let bumps = 0;
            source.subscribe(() => bumps++);

            ack.resolve();
            await flush();

            expect(source.chunks.size).toBe(FRAMES);
            expect(bumps).toBe(1);
        });

        it("also buffers a new source's frames during an addSources sub-request's ack window", async () => {
            // Regression guard for the generalized gate: buffering must cover ANY
            // submit in flight, not just the initial load. A frame for a source added
            // in place, arriving before that sub-request's ack lands, must be held and
            // replayed — not discarded as "foreign" and lost like the initial-load bug.
            const SRC_A = "00000000-0000-0000-0000-0000000000aa";
            const SRC_B = "00000000-0000-0000-0000-0000000000bb";
            const JOB_A = "job-A";
            const JOB_B = "job-B";
            // First submit (initial load) acks immediately; the addSources
            // sub-request's ack is deferred so a JOB_B frame can arrive pre-ack.
            respondWith({
                requestId: "req-1",
                status: "ACCEPTED",
                subjects: [{ subjectType: "SOURCE", subjectId: SRC_A, jobId: JOB_A }],
            });
            const { resolve: resolveAdd } = deferAck({
                requestId: "req-2",
                status: "ACCEPTED",
                subjects: [{ subjectType: "SOURCE", subjectId: SRC_B, jobId: JOB_B }],
            });
            const client = fakeClient();
            const source = new HistoricalSession({
                client,
                processor: getProcessor(),
                params: {
                    sourceIds: [SRC_A],
                    subjectTypes: { [SRC_A]: "SOURCE" },
                    from: "2026-01-01T00:00:00.000Z",
                    to: "2026-01-01T01:00:00.000Z",
                },
            });
            source.retain();
            await flush();

            // Drive the initial source to completion so an in-place add is admissible.
            client.deliver(
                encodeJsonEvent({
                    ...PLAN_EVENT,
                    chunks: 1,
                    chunksToCompute: 1,
                    effectiveTo: "2026-01-01T00:01:00.000Z",
                }),
            );
            const planFromMs = source.getPlan()!.fromMs;
            const bucketWidthMs = source.getPlan()!.bucketWidthMs;
            client.deliver(
                encodeChunk({
                    chunkIndex: 0,
                    chunkKey: "slot-0",
                    fromEpochMillis: planFromMs,
                    toEpochMillis: planFromMs + bucketWidthMs,
                    elements: [buildSymbolMessage()],
                }),
                { "history-type": "history.chunk.data", "history-job-id": JOB_A },
            );
            client.deliver(
                encodeJsonEvent({
                    ...COMPLETED_EVENT,
                    jobId: JOB_A,
                    totalChunks: 1,
                    deliveredChunks: 1,
                }),
            );
            expect(source.state).toBe("enabled");

            // Add SRC_B; its sub-request ack is deferred, so JOB_B's chunk arrives pre-ack.
            source.addSources([SRC_B], { [SRC_B]: "SOURCE" });
            client.deliver(
                encodeChunk({
                    chunkIndex: 0,
                    chunkKey: "slot-0",
                    fromEpochMillis: planFromMs,
                    toEpochMillis: planFromMs + bucketWidthMs,
                    elements: [buildSymbolMessage(), buildSymbolMessage()],
                }),
                { "history-type": "history.chunk.data", "history-job-id": JOB_B },
            );
            // Held during the add's ack window — not yet in the ledger (pre-fix: lost).
            expect(source.chunkEntry(SRC_B, "slot-0")).toBeUndefined();

            resolveAdd();
            await flush();

            // Replayed after the add's ack: SRC_B's chunk is applied, SRC_A's untouched.
            expect(source.chunkEntry(SRC_B, "slot-0")?.elementCount).toBe(2);
            expect(source.chunkEntry(SRC_A, "slot-0")?.elementCount).toBe(1);
        });
    });
});
