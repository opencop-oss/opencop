// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * DEV-only instrumentation for {@link HistoricalSession}. Lifts chunk-,
 * decode-, and audit-related logging out of the source so production hot
 * paths skip every diagnostic call via `?.`. Manager-side playhead
 * diagnostics live in {@link HistoricalDataManagerDiagnostics} alongside
 * the bucket projection state they observe.
 *
 * Owns three side-tables that production never allocates:
 *   - `devChunks` — per-chunk objectId set + stale-window counters.
 *   - `rawIds` — raw proto id bytes per chunk for the cross-chunk audit.
 *   - `protoStale` — WeakMap from element to its proto-stale-ms (needed
 *     by manager-side dumps that reason about staleness).
 */
import type { Element } from "@opencop/elements";
import type { ElementMessage } from "@opencop/proto";
import Logger from "js-logger";

import type { HistoricalChunkEntry, HistoricalSession } from "./HistoricalSession";

const logger = Logger.get("historical");

/**
 * Per-chunk diagnostic accumulators. Lifted off the production
 * `HistoricalChunkEntry` to keep its struct minimal.
 */
interface DevChunkInfo {
    objectIds: Set<string>;
    minStaleMs: number | undefined;
    maxStaleMs: number | undefined;
    staleInWindow: number;
    staleAfterWindow: number;
    staleBeforeWindow: number;
}

export interface HistoricalChunkStatusEvent {
    chunkIndex: number;
    status: string;
    partCount?: number;
    cached?: boolean;
    chunkKey: string;
}

/** Hooks the source calls in DEV. Production keeps `null` for this. */
export interface HistoricalSessionDiagnostics {
    onChunkEnqueued(
        sourceId: string,
        chunkKey: string,
        chunkIndex: number,
        frameElementCount: number,
        partIndex: number,
        partCount: number,
        finalForChunk: boolean,
    ): void;
    onElementMessageRead(
        sourceId: string,
        chunkIndex: number,
        elementMessage: ElementMessage,
    ): void;
    onElementDecoded(
        sourceId: string,
        chunkIndex: number,
        element: Element,
        elementMessage: ElementMessage,
        chunkEntry: HistoricalChunkEntry,
    ): void;
    onElementDecodeFailed(elementMessage: ElementMessage, totalFailedSoFar: number): void;
    onChunkDrainPass(
        sourceId: string,
        chunkKey: string,
        chunkIndex: number,
        frameElements: number,
    ): void;
    onDrainComplete(chunks: number, seen: number, decoded: number, failed: number): void;
    onChunkStatus(event: HistoricalChunkStatusEvent): void;
    /**
     * A binary chunk frame was rejected because its `history-job-id` header did not
     * match this source's jobs — cross-tab leakage or superseded-rezoom pollution on
     * the shared queue. The observable signal that the job-id gate is working.
     */
    onChunkDiscarded(
        frameJobId: string | undefined,
        expectedJobIds: readonly string[],
        chunkKey: string | undefined,
    ): void;
    /** Manager-side diagnostics call this to reason about element staleness. */
    getProtoStaleMs(element: Element): number | null;
    dispose(): void;
}

interface HistoricalDebugBridge {
    source: HistoricalSession;
    dumpChunks: () => unknown;
    dumpJobStatus: () => unknown;
    auditRawIds: () => unknown;
    /** Augmented by manager diagnostics when it installs. */
    dumpObjects?: () => unknown;
    dumpPlayhead?: () => unknown;
    /**
     * Augmented by manager diagnostics. Counts store elements that can never
     * render because their `filterValue` falls outside the plan's reachable
     * playhead range (the "arrived but not displayed" failure mode).
     */
    dumpUnrendered?: () => unknown;
}

declare global {
    // `var` is required for globalThis-visible declarations under `declare global`
    var __historicalSession: HistoricalSession | null;
    var __historicalDebug: HistoricalDebugBridge | null;
}

if (import.meta.env.DEV) {
    globalThis.__historicalSession = null;
    globalThis.__historicalDebug = null;
}

function bytesToHex(bytes: Uint8Array): string {
    let out = "";
    for (let i = 0; i < bytes.length; i++) {
        out += bytes[i].toString(16).padStart(2, "0");
    }
    return out;
}

/** Return the value at `key`, inserting `make()` first if absent. */
function getOrCreate<K, V>(map: Map<K, V>, key: K, make: () => V): V {
    let value = map.get(key);
    if (value === undefined) {
        value = make();
        map.set(key, value);
    }
    return value;
}

class HistoricalSessionDiagnosticsImpl implements HistoricalSessionDiagnostics {
    private readonly source: HistoricalSession;
    // Dev side-tables keyed by `devKey(sourceId, chunkIndex)` so per-chunk stats
    // stay separated across sources (each source is its own job with its own
    // chunkIndex sequence — keying by index alone would merge them).
    private readonly devChunks: Map<string, DevChunkInfo> = new Map();
    private readonly rawIds: Map<string, string[]> = new Map();
    private readonly protoStale: WeakMap<Element, number> = new WeakMap();
    private discardedFrames = 0;

    private devKey(sourceId: string, chunkIndex: number): string {
        return `${sourceId}\u0000${chunkIndex}`;
    }

    constructor(source: HistoricalSession) {
        this.source = source;
        globalThis.__historicalSession = source;
        globalThis.__historicalDebug = {
            source,
            dumpChunks: () => this.dumpChunkMembership(),
            dumpJobStatus: () => this.dumpJobStatus(),
            auditRawIds: () => this.auditRawProtoIds(),
        };
    }

    onChunkEnqueued(
        sourceId: string,
        chunkKey: string,
        chunkIndex: number,
        frameElementCount: number,
        partIndex: number,
        partCount: number,
        finalForChunk: boolean,
    ): void {
        const entry = this.source.chunkEntry(sourceId, chunkKey);
        if (!entry) return;
        const planSeen = this.source.getPlan() !== null;
        logger.info(
            `chunk ${chunkIndex} enqueued: ` +
                `frame=${frameElementCount}, part=${partIndex}/${partCount}, ` +
                `finalForChunk=${finalForChunk}, ` +
                `cumulative=${entry.elementCount} (frames=${entry.frameCount}), ` +
                `planSeen=${planSeen}`,
        );
        if (!planSeen) {
            logger.warn(
                `chunk ${chunkIndex} arrived before history.job.plan — activeBucket tracking disabled until plan event arrives`,
            );
        }
    }

    onElementMessageRead(
        sourceId: string,
        chunkIndex: number,
        elementMessage: ElementMessage,
    ): void {
        if (!elementMessage.id || elementMessage.id.byteLength === 0) return;
        const bucket = getOrCreate(this.rawIds, this.devKey(sourceId, chunkIndex), () => []);
        bucket.push(bytesToHex(elementMessage.id));
    }

    onElementDecoded(
        sourceId: string,
        chunkIndex: number,
        element: Element,
        elementMessage: ElementMessage,
        chunkEntry: HistoricalChunkEntry,
    ): void {
        const dev = this.ensureDevChunk(this.devKey(sourceId, chunkIndex));
        dev.objectIds.add(element.objectId);

        // Everything below tracks proto-stale ms; skip elements without one.
        const protoStale = Number(elementMessage.stale);
        if (!Number.isFinite(protoStale) || protoStale <= 0) return;

        this.protoStale.set(element, protoStale);
        dev.minStaleMs =
            dev.minStaleMs === undefined ? protoStale : Math.min(dev.minStaleMs, protoStale);
        dev.maxStaleMs =
            dev.maxStaleMs === undefined ? protoStale : Math.max(dev.maxStaleMs, protoStale);
        if (protoStale >= chunkEntry.fromMs && protoStale < chunkEntry.toMs) {
            dev.staleInWindow += 1;
        } else if (protoStale >= chunkEntry.toMs) {
            dev.staleAfterWindow += 1;
        } else {
            dev.staleBeforeWindow += 1;
        }
    }

    onElementDecodeFailed(elementMessage: ElementMessage, totalFailedSoFar: number): void {
        if (totalFailedSoFar > 3) return;
        logger.warn(
            `Failed to decode element type="${elementMessage.type}" id="${elementMessage.id}"`,
            elementMessage,
        );
    }

    onChunkDrainPass(
        sourceId: string,
        chunkKey: string,
        chunkIndex: number,
        frameElements: number,
    ): void {
        const entry = this.source.chunkEntry(sourceId, chunkKey);
        if (!entry) return;
        const dev = this.devChunks.get(this.devKey(sourceId, chunkIndex));
        logger.info(
            `chunk ${chunkIndex} drain: frameElements=${frameElements}, ` +
                `cumulativeElements=${entry.elementCount} (frames=${entry.frameCount}), ` +
                `parts=${entry.seenParts.size}/${entry.maxPartCount}, ` +
                `finalForChunk=${entry.finalForChunkSeen}, ` +
                `cumulativeUniqObjects=${dev?.objectIds.size ?? 0}, ` +
                `staleIn=${dev?.staleInWindow ?? 0}, staleAfter=${dev?.staleAfterWindow ?? 0}, ` +
                `staleBefore=${dev?.staleBeforeWindow ?? 0}`,
        );
    }

    onDrainComplete(chunks: number, seen: number, decoded: number, failed: number): void {
        logger.info(`drain: chunks=${chunks}, seen=${seen}, decoded=${decoded}, failed=${failed}`);
    }

    onChunkStatus(event: HistoricalChunkStatusEvent): void {
        logger.info(
            `chunk.status: idx=${event.chunkIndex} status=${event.status} ` +
                `parts=${event.partCount} cached=${event.cached} key=${event.chunkKey}`,
        );
    }

    onChunkDiscarded(
        frameJobId: string | undefined,
        expectedJobIds: readonly string[],
        chunkKey: string | undefined,
    ): void {
        this.discardedFrames += 1;
        logger.warn(
            `discarded foreign history chunk: frameJobId=${frameJobId ?? "<none>"} ` +
                `expectedJobIds=[${expectedJobIds.join(",")}] chunkKey=${chunkKey ?? "<unknown>"} ` +
                `(totalDiscarded=${this.discardedFrames})`,
        );
    }

    getProtoStaleMs(element: Element): number | null {
        return this.protoStale.get(element) ?? null;
    }

    dispose(): void {
        if (globalThis.__historicalSession === this.source) {
            globalThis.__historicalSession = null;
        }
        if (globalThis.__historicalDebug?.source === this.source) {
            globalThis.__historicalDebug = null;
        }
        this.devChunks.clear();
        this.rawIds.clear();
    }

    // -----------------------------------------------------------------------
    // Dump methods (callable via `globalThis.__historicalDebug` in DEV)
    // -----------------------------------------------------------------------

    private dumpChunkMembership(): void {
        const rows = [...this.source.chunks.values()]
            .sort((a, b) => a.sourceId.localeCompare(b.sourceId) || a.chunkIndex - b.chunkIndex)
            .map((c) => {
                const dev = this.devChunks.get(this.devKey(c.sourceId, c.chunkIndex));
                return {
                    source: c.sourceId,
                    chunk: c.chunkIndex,
                    from: new Date(c.fromMs).toISOString(),
                    to: new Date(c.toMs).toISOString(),
                    elements: c.elementCount,
                    frames: c.frameCount,
                    partsSeen: c.seenParts.size,
                    maxPartCount: c.maxPartCount,
                    finalSeen: c.finalForChunkSeen,
                    uniqObjects: dev?.objectIds.size ?? 0,
                    staleIn: dev?.staleInWindow ?? 0,
                    staleAfter: dev?.staleAfterWindow ?? 0,
                    staleBefore: dev?.staleBeforeWindow ?? 0,
                    minStale: dev?.minStaleMs ? new Date(dev.minStaleMs).toISOString() : "",
                    maxStale: dev?.maxStaleMs ? new Date(dev.maxStaleMs).toISOString() : "",
                };
            });
        // eslint-disable-next-line no-console -- diagnostic bridge: console.table has no js-logger equivalent
        console.table(rows);
    }

    private dumpJobStatus(): void {
        const plan = this.source.getPlan();
        const planSeen = plan !== null;
        const totalChunkCount = plan?.totalChunkCount ?? 0;
        const chunks = [...this.source.chunks.values()];
        const receivedIndices = chunks.map((c) => c.chunkIndex).sort((a, b) => a - b);
        const incomplete = chunks
            .filter(
                (c) =>
                    !c.finalForChunkSeen ||
                    (c.maxPartCount > 0 && c.seenParts.size < c.maxPartCount),
            )
            .map((c) => ({
                chunk: c.chunkIndex,
                partsSeen: c.seenParts.size,
                maxPartCount: c.maxPartCount,
                finalSeen: c.finalForChunkSeen,
                cumulativeElements: c.elementCount,
            }));
        const missingIndices: number[] = [];
        if (planSeen) {
            const receivedSet = new Set(receivedIndices);
            for (let i = 0; i < totalChunkCount; i++) {
                if (!receivedSet.has(i)) missingIndices.push(i);
            }
        }
        logger.info("[historical-debug] job status", {
            requestId: this.source.requestId,
            jobIds: [...this.source.jobIds],
            planSeen,
            totalChunksExpected: totalChunkCount,
            chunksReceived: receivedIndices.length,
            receivedIndices,
            missingIndices,
            incompleteChunks: incomplete,
            discardedForeignFrames: this.discardedFrames,
        });
    }

    private auditRawProtoIds(): {
        perChunk: { chunk: string; count: number; uniqueInChunk: number }[];
        globalUnique: number;
        totalRawIds: number;
        crossChunkCollisions: number;
        sampleCollisions: { id: string; chunks: string[] }[];
    } {
        // Keys are `devKey(sourceId, chunkIndex)` — the cross-chunk collision
        // audit now spans sources too (an id appearing under two distinct
        // source/chunk keys is a collision).
        const seenAt = new Map<string, Set<string>>();
        const perChunk: { chunk: string; count: number; uniqueInChunk: number }[] = [];
        let totalRawIds = 0;
        for (const [chunk, ids] of [...this.rawIds.entries()].sort((a, b) =>
            a[0].localeCompare(b[0]),
        )) {
            const inChunk = new Set<string>();
            for (const id of ids) {
                inChunk.add(id);
                getOrCreate(seenAt, id, () => new Set<string>()).add(chunk);
            }
            totalRawIds += ids.length;
            perChunk.push({ chunk, count: ids.length, uniqueInChunk: inChunk.size });
        }
        const collisions: { id: string; chunks: string[] }[] = [];
        let collisionCount = 0;
        for (const [id, chunks] of seenAt) {
            if (chunks.size > 1) {
                collisionCount++;
                if (collisions.length < 5) {
                    collisions.push({ id, chunks: [...chunks].sort() });
                }
            }
        }
        const result = {
            perChunk,
            globalUnique: seenAt.size,
            totalRawIds,
            crossChunkCollisions: collisionCount,
            sampleCollisions: collisions,
        };
        logger.info("[historical-debug] raw-id audit", result);
        return result;
    }

    private ensureDevChunk(key: string): DevChunkInfo {
        return getOrCreate(this.devChunks, key, () => ({
            objectIds: new Set<string>(),
            minStaleMs: undefined,
            maxStaleMs: undefined,
            staleInWindow: 0,
            staleAfterWindow: 0,
            staleBeforeWindow: 0,
        }));
    }
}

export function installHistoricalSessionDiagnostics(
    source: HistoricalSession,
): HistoricalSessionDiagnostics {
    return new HistoricalSessionDiagnosticsImpl(source);
}
