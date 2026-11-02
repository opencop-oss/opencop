// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * Historical chunk helpers and constants.
 *
 * The on-wire proto types are generated in `@opencop/proto` (HistoricalChunkPayload).
 * This file provides frontend-only helpers and the resolution width constants.
 */

/** Fixed bucket resolutions supported by Phase 1. */
export type HistoricalChunkResolution = "MINUTE_1" | "HOUR_1" | "DAY_1";

/** Bucket widths in milliseconds, keyed by resolution. */
export const RESOLUTION_WIDTH_MS: Record<HistoricalChunkResolution, number> = {
    MINUTE_1: 60_000,
    HOUR_1: 3_600_000,
    DAY_1: 86_400_000,
};

/**
 * Compute the active chunk index for a playhead position.
 *
 * Returns `totalChunks - 1` when `playheadMs` is null (playhead at right edge),
 * clamped to `[0, totalChunks - 1]`. Integer-truncates toward the bucket that
 * contains `playheadMs` under inclusive-start / exclusive-end semantics.
 */
export function computeActiveBucketIndex(
    playheadMs: number | null,
    fromMs: number,
    bucketWidthMs: number,
    totalChunks: number,
): number {
    if (totalChunks <= 0) return 0;
    if (playheadMs === null) return totalChunks - 1;
    const idx = Math.floor((playheadMs - fromMs) / bucketWidthMs);
    if (idx < 0) return 0;
    if (idx >= totalChunks) return totalChunks - 1;
    return idx;
}
