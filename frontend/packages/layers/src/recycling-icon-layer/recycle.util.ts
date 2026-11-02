// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * Atlas height (px) at which {@link RecyclingIconLayer} recycles its auto-packed
 * icon atlas.
 *
 * deck.gl's icon atlas never evicts — every distinct icon ever seen accumulates
 * into a 1024-wide texture whose height is always a power of two
 * (1024→2048→4096→8192→16384). Firefox caps `MAX_TEXTURE_SIZE` at 8192, so once
 * the atlas would jump to 16384 the allocation fails and every icon samples
 * opaque black (Chrome's ceiling is 16384, so it shows the same failure with ~2×
 * the distinct icons). Recycling at 4096 — one power-of-two below Firefox's
 * ceiling — leaves a full level of headroom for the one-render detection lag.
 */
export const RECYCLE_AT_PX = 4096;

/**
 * Ratio of packed slots to live-distinct icons above which the accumulated
 * staleness is worth reclaiming.
 */
const BLOAT_RATIO = 1.5;

/**
 * Absolute floor on `packedSlots - liveDistinct`. Below this the two counts are
 * close enough that recycling would churn without meaningfully shrinking the
 * atlas, so we hold off.
 */
const BLOAT_FLOOR = 32;

/** The two signals that drive the recycle decision, read off the live atlas. */
export interface AtlasPressure {
    /** Packed-atlas texture height in px (always a power of two); 0 before the first render. */
    atlasHeight: number;
    /** Distinct icons ever packed into the atlas (it never evicts). */
    packedSlots: number;
    /** Distinct icons across the current/live data — the footprint a fresh atlas would need. */
    liveDistinct: number;
}

/**
 * Decide whether the never-evicting icon atlas should be recycled.
 *
 * Two gates, both of which must hold:
 *
 *  - **Pressure** — the real packed-texture height has reached {@link RECYCLE_AT_PX},
 *    so a small atlas is never recycled needlessly.
 *  - **Bloat** — packed slots (distinct icons ever seen) far exceed *currently-live*
 *    distinct icons, i.e. there are stale icons to reclaim. This is the anti-thrash
 *    guard *and* size-agnostic: if the live set itself is what fills the atlas,
 *    packed ≈ live, so we don't recycle — recycling couldn't help. After a recycle
 *    the new atlas packs only live icons, so packed ≈ live again and we don't re-recycle.
 */
export function shouldRecycleAtlas({
    atlasHeight,
    packedSlots,
    liveDistinct,
}: AtlasPressure): boolean {
    if (atlasHeight < RECYCLE_AT_PX) return false; // not under pressure
    return packedSlots > liveDistinct * BLOAT_RATIO && packedSlots - liveDistinct >= BLOAT_FLOOR;
}
