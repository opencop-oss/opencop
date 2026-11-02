// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * Atlas height (px) at which {@link RecyclingTextLayer} recycles its
 * never-evicting SDF font atlas.
 *
 * deck.gl's font atlas is a module-level LRU cache keyed by font config only
 * (`fontFamily fontWeight fontSize buffer radius cutoff` — never the character
 * set). With `characterSet: "auto"`, every distinct glyph ever seen for a font
 * config is appended to a 1024-wide canvas whose height grows monotonically and
 * never shrinks (`getNewChars` only ever appends). Latin saturates a few hundred
 * glyphs into a ~256px atlas; CJK has thousands of distinct glyphs, so over a
 * long session the atlas climbs past the GPU `MAX_TEXTURE_SIZE` (Firefox 8192,
 * Chrome 16384). Once it overflows, the texture allocation fails and the SDF
 * shader reads the incomplete texture's alpha as `1.0` everywhere → every label
 * quad fills solid with the text colour (white on this map).
 *
 * Like the icon atlas, the packed canvas height is always a power of two
 * (1024 → 2048 → 4096 → 8192 — measured). We recycle at 4096, one level below
 * Firefox's 8192 ceiling, leaving a full power-of-two of headroom for the
 * one-render detection lag.
 */
export const FONT_RECYCLE_AT_PX = 4096;

/**
 * Ratio of packed glyphs to currently-live glyphs above which the accumulated
 * staleness is worth reclaiming.
 */
const BLOAT_RATIO = 1.5;

/**
 * Absolute floor on `packedGlyphs - liveDistinct`. Below this the two counts are
 * close enough that recycling would churn without meaningfully shrinking the
 * atlas, so we hold off.
 */
const BLOAT_FLOOR = 32;

/** The signals that drive the recycle decision, read off the live font atlas. */
export interface FontAtlasPressure {
    /** Packed-atlas canvas height in px (grows by glyph rows); 0 before the first build. */
    atlasHeight: number;
    /** Distinct glyphs ever packed into the atlas (it never evicts). */
    packedGlyphs: number;
    /** Distinct glyphs across the current/live labels — the footprint a fresh atlas would need. */
    liveDistinct: number;
}

/**
 * Decide whether the never-evicting font atlas should be recycled.
 *
 * Two gates, both of which must hold:
 *
 *  - **Pressure** — the real atlas height has reached {@link FONT_RECYCLE_AT_PX},
 *    so a small atlas is never recycled needlessly.
 *  - **Bloat** — packed glyphs (distinct glyphs ever seen) far exceed
 *    *currently-live* distinct glyphs, i.e. there are stale glyphs to reclaim.
 *    This is the anti-thrash guard *and* size-agnostic: if the live set itself is
 *    what fills the atlas, packed ≈ live, so we don't recycle — recycling
 *    couldn't help. After a recycle the fresh atlas packs only live glyphs, so
 *    packed ≈ live again and we don't re-recycle.
 *
 * Mirrors `recycling-icon-layer`'s `shouldRecycleAtlas`; the decision is
 * atlas-agnostic (pressure + bloat), only the threshold and the reset mechanism
 * differ between the icon and font atlases.
 */
export function shouldRecycleAtlas({
    atlasHeight,
    packedGlyphs,
    liveDistinct,
}: FontAtlasPressure): boolean {
    if (atlasHeight < FONT_RECYCLE_AT_PX) return false; // not under pressure
    return packedGlyphs > liveDistinct * BLOAT_RATIO && packedGlyphs - liveDistinct >= BLOAT_FLOOR;
}
