// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * TypeScript types for JSON lifecycle events arriving on `/user/queue/history`.
 *
 * These mirror the Java `HistoryEvent` sealed hierarchy in the websocket
 * service. Binary chunk data frames are NOT represented here — they arrive
 * as protobuf `HistoricalChunkPayload` and are decoded separately.
 */

/** Discriminator values for the `type` field on JSON history events. */
export type HistoryEventType =
    | "history.job.plan"
    | "history.chunk.status"
    | "history.job.progress"
    | "history.job.completed"
    | "history.job.failed";

/** Sent once after the backend plans all chunks for a job. */
export interface HistoryJobPlanEvent {
    type: "history.job.plan";
    requestId: string;
    jobId: string;
    subjectType: string;
    subjectId: string;
    resolution: string;
    samplingVersion: string;
    chunks: number;
    cachedChunks: number;
    chunksToCompute: number;
    effectiveFrom: string;
    effectiveTo: string;
}

/** Per-chunk lifecycle status. Sequence: AVAILABLE_FOR_DELIVERY → SENDING → COMPLETED. */
export interface HistoryChunkStatusEvent {
    type: "history.chunk.status";
    requestId: string;
    jobId: string;
    subjectType: string;
    subjectId: string;
    chunkKey: string;
    chunkIndex: number;
    chunkFrom: string;
    chunkTo: string;
    resolution: string;
    samplingVersion: string;
    cached: boolean;
    partCount: number;
    status: "AVAILABLE_FOR_DELIVERY" | "SENDING" | "COMPLETED";
}

/** Periodic progress update during chunk delivery. */
export interface HistoryJobProgressEvent {
    type: "history.job.progress";
    requestId: string;
    jobId: string;
    totalChunks: number;
    computedChunks: number;
    deliveredChunks: number;
    failedChunks: number;
    remainingChunks: number;
    percentComplete: number;
}

/** Terminal success — all chunks have been sent by the websocket service. */
export interface HistoryJobCompletedEvent {
    type: "history.job.completed";
    requestId: string;
    jobId: string;
    totalChunks: number;
    deliveredChunks: number;
    failedChunks: number;
}

/** Terminal failure — the job could not complete. */
export interface HistoryJobFailedEvent {
    type: "history.job.failed";
    requestId: string;
    jobId: string;
    stage: string;
    reason: string;
}

/** Discriminated union of all JSON history events. */
export type HistoryEvent =
    | HistoryJobPlanEvent
    | HistoryChunkStatusEvent
    | HistoryJobProgressEvent
    | HistoryJobCompletedEvent
    | HistoryJobFailedEvent;

const KNOWN_EVENT_TYPES = new Set<string>([
    "history.job.plan",
    "history.chunk.status",
    "history.job.progress",
    "history.job.completed",
    "history.job.failed",
]);

/**
 * Parse a raw JSON string into a typed HistoryEvent.
 * Throws if the JSON is unparseable or has an unrecognized `type` field.
 */
export function parseHistoryEvent(json: string): HistoryEvent {
    const parsed: unknown = JSON.parse(json);
    if (
        typeof parsed !== "object" ||
        parsed === null ||
        !("type" in parsed) ||
        typeof (parsed as { type: unknown }).type !== "string"
    ) {
        throw new Error("Invalid history event: missing or non-string 'type' field");
    }
    const type = (parsed as { type: string }).type;
    if (!KNOWN_EVENT_TYPES.has(type)) {
        throw new Error(`Unknown history event type: ${type}`);
    }
    return parsed as HistoryEvent;
}
