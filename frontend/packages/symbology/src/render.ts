// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { chooseRenderStrategy } from "./strategy";
import * as milstd from "./mil-sym-ts-wrapper";
import * as milsymbol from "./milsymbol-wrapper";
import { LRU } from "./lru";
import { assertNever } from "assert-never";

export type RenderResult = { kind: "icon"; source: IconSource };

/**
 * Renderer return shape. Intentionally not re-exported from the package
 * barrel: `@opencop/elements` exports its own wider `IconSource` (a union
 * that includes bare URL strings and renderer-specific atlas hints), and
 * exposing this narrower variant under the same name in a sibling package
 * would create a silent type-collision risk if either side later evolves.
 * Consumers read fields from the `source` arm of `Renderer.render(...)`.
 */
export interface IconSource {
    /** SVG or raster data: URI suitable for any `<img src>` or icon-atlas consumer. */
    url: string;
    /** Intrinsic width in pixels. */
    width: number;
    /** Intrinsic height in pixels. */
    height: number;
    /** Optional anchor point in source pixels for precise placement. */
    anchorX?: number;
    /** Optional anchor point in source pixels for precise placement. */
    anchorY?: number;
    /**
     * Stable, opaque identifier for atlas-dedupe (or any per-icon caching
     * key on the consumer side). Consumers must treat this as an opaque
     * string — do **not** parse it as a SIDC even when today's value
     * happens to equal one. The encoding may grow modifier-hash suffixes
     * once Phase 2D wires modifiers through the icon path; the field's
     * contract is "two icons with the same id render to the same pixels"
     * and nothing more.
     */
    id?: string;
}

/**
 * 1×1 transparent GIF, safe to use as an `IconSource`-shaped fallback
 * anywhere an `IconSource` is expected. `<img src>` consumers see
 * nothing; icon-atlas consumers render a harmless empty icon. Never
 * returns `""` (which would trigger a request to the page URL and
 * surface as a confusing network error in the dev console).
 *
 * Used in two distinct cases — neither is "neither library can render
 * the SIDC" (that case routes through milsymbol's permissive `"?"`
 * placeholder instead, owned by the milsymbol wrapper):
 *
 *   1. External fallback when stored `IconSource` data is malformed
 *      (persisted `IconSource` shapes revalidated on load).
 *   2. Historical absolute-last-resort if milsymbol's permissive
 *      placeholder ever fails to produce *anything*. The milsymbol
 *      wrapper now catches its own throw surface and falls through to a
 *      pre-built placeholder, so the path from inside `render.ts` that
 *      used to land here is unreachable in real usage. Kept as a
 *      defensive sentinel and for the external use case above.
 */
export const MISSING_ICON_FALLBACK: IconSource = {
    url: "data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///ywAAAAAAQABAAACAUwAOw==",
    width: 1,
    height: 1,
    id: "__missing_symbol_icon__",
};

// ─── Caches ──────────────────────────────────────────────────────────────
//
// Each public entry point owns a cache keyed for its audience. The wrappers
// (milsymbol, milstd) cache the underlying library renders separately; these
// caches stabilize the *public* `IconSource` reference so consumers
// (icon-atlas slots, identity-based memoization) can rely on `===` identity
// per SIDC. The `id` field is set to the SIDC so any consumer-side atlas
// dedupe key matches the cache key.
//
// Two caches, not one, because the two entry points are semantically
// independent — they could legitimately diverge later (different modifier
// handling, different fallback policy) without cross-contaminating
// references. Today the underlying dispatch is shared, so the *values* may
// be identical objects; the cache split protects the boundary.

const PREVIEW_CACHE_SIZE = 2_000;
const MAP_ICON_CACHE_SIZE = 2_000;

/**
 * Octagon width (px) for icons packed into the on-map GPU atlas.
 *
 * milsymbol renders at a 100px octagon by default (~108px SVG), but the map
 * *displays* symbols at ~24–40px, so packing 100px slots wastes atlas space.
 * Rendering map icons at 64px shrinks each slot ~0.4× in area while keeping
 * the texture crisp at the sizes icons actually reach on-map — including the
 * ≥50px they hit when the map is pitched (perspective upscaling), and matching
 * the 64px the text atlas uses. Previews keep the default 100px for UI crispness.
 *
 * The atlas is kept bounded by `RecyclingIconLayer` (which recycles the
 * never-evicting atlas in place), not by this size — so this is chosen for
 * visual fidelity, not headroom.
 *
 * This applies to the milsymbol path only. The milstd path (operational
 * graphics) already renders at ~50px natively, so it is left at its native size
 * rather than threading a near-no-op `PixelSize` attribute through `RenderSVG`.
 */
const MAP_ICON_ATLAS_SIZE = 64;

const previewCache = new LRU<string, IconSource | null>(PREVIEW_CACHE_SIZE);
const mapIconCache = new LRU<string, IconSource>(MAP_ICON_CACHE_SIZE);

// ─── Public: renderPreview ──────────────────────────────────────────────

/**
 * Get a thumbnail icon URL for a SIDC — the UI-thumbnail entry point.
 * Dispatcher-driven: prefers milsymbol (visual continuity with today's
 * OpenCOP rendering); falls through to milstd's icon renderer for SIDCs
 * milsymbol can't render.
 *
 * Returns just the URL string because every UI consumer (SIDCImage,
 * SymbolHistoryItem, EventsHeader, ElementPreviewCanvas) wants `<img src>`
 * only — dimensions, anchor, atlas id are noise here. Reach for `render`
 * if you need the full IconSource.
 *
 * **Strict by default; placeholder is opt-in.** Returns `null` for the
 * strategy-`"none"` case so callers can branch to a text label, dash, or
 * alternate UI. Every current caller already wants that — the permissive
 * "?" placeholder is opt-in via `{ fallback: true }`.
 *
 * Pass `{ fallback: true }` to route the strategy-`"none"` outcome to
 * milsymbol's permissive renderer, which always produces the MIL-STD-2525
 * "unknown" placeholder (a "?" glyph, framed by the affiliation rectangle
 * if the identity bits parsed). The absolute-last-resort
 * `MISSING_ICON_FALLBACK` only fires if milsymbol itself throws (rare;
 * the wrapper guards against it).
 *
 * Validation is unaffected: `chooseRenderStrategy` still returns `"none"`
 * for these SIDCs, so `validateSidc` flags them as `invalid` at input
 * time. The permissive render is *only* the safety net for legacy/
 * imported SIDCs already in stored data.
 */
export function renderPreview(sidc: string, opts: { fallback: true }): string;
export function renderPreview(sidc: string, opts?: { fallback?: false }): string | null;
export function renderPreview(sidc: string, opts: { fallback?: boolean } = {}): string | null {
    return resolvePreviewIcon(sidc, opts.fallback ?? false)?.url ?? null;
}

// ─── Public: render ─────────────────────────────────────────────────────

/** Map icon request. Multipoint geometry is not supported. */
export interface IconLookupOptions {
    sidc: string;
    /** Reject callers that still provide the removed geometry request shape. */
    controls?: never;
}

/** Render a point symbol for the map's icon atlas. */
export function render(opts: IconLookupOptions): RenderResult {
    if (milstd.isMultiPoint(opts.sidc)) {
        throw new Error("Renderer.render requires a single-point SIDC: " + opts.sidc);
    }
    return { kind: "icon", source: resolveMapIcon(opts.sidc) };
}

// ─── Internal: per-audience icon resolvers ──────────────────────────────

/**
 * Preview-audience icon resolver: permissive by default, owned by the
 * preview cache. Sole call site from `renderPreview`.
 */
function resolvePreviewIcon(sidc: string, useFallback: boolean): IconSource | null {
    const key = `${useFallback}:${sidc}`;
    const cached = previewCache.get(key);
    if (cached !== undefined) return cached;

    // Branch on the boolean so the overload's literal-typed signature
    // picks the right return narrowing per path.
    const result: IconSource | null = useFallback
        ? dispatchIcon(sidc, { allowPlaceholder: true })
        : dispatchIcon(sidc, { allowPlaceholder: false });
    previewCache.set(key, result);
    return result;
}

/**
 * Map-audience icon resolver: produces an IconSource for the
 * single-point branch of `render`. Owned by the map-icon cache.
 *
 * Returns `IconSource` (non-null) — `dispatchIcon` with
 * `allowPlaceholder: true` always produces something visible (milsymbol's
 * "?" placeholder for unrenderable SIDCs). The overload on `dispatchIcon`
 * encodes that invariant in the type system.
 *
 * Today `allowPlaceholder: true` preserves legacy behavior where a
 * malformed or unknown SIDC renders as the milsymbol "?" glyph. A
 * future change may flip this to `false` so the caller can substitute
 * its own placeholder or skip — that decision is separable from this
 * seam and belongs in its own PR.
 */
function resolveMapIcon(sidc: string): IconSource {
    const cached = mapIconCache.get(sidc);
    if (cached !== undefined) return cached;

    // Map icons render at the smaller atlas size; previews stay at 100px.
    const result = dispatchIcon(sidc, { allowPlaceholder: true }, MAP_ICON_ATLAS_SIZE);
    mapIconCache.set(sidc, result);
    return result;
}

// ─── Internal: strategy dispatch ────────────────────────────────────────

/**
 * Pure strategy switch over milsymbol / milstd. No caching — the resolvers
 * above own that.
 *
 * Overloaded on the `allowPlaceholder` literal so the caller's invariant
 * is encoded in the type system:
 *   - `true` → always returns an `IconSource` (milsymbol's "?"
 *     placeholder is the fall-through for unrenderable SIDCs, and the
 *     milsymbol wrapper guarantees its `renderIcon` never throws).
 *   - `false` → may return `null` when both libraries decline and the
 *     placeholder is opted out; caller branches on null.
 *
 * `size` is the milsymbol octagon width; the map path passes
 * {@link MAP_ICON_ATLAS_SIZE} and the preview path omits it (milsymbol
 * defaults to 100). It is intentionally not forwarded to milstd — see
 * {@link MAP_ICON_ATLAS_SIZE}.
 */
function dispatchIcon(sidc: string, opts: { allowPlaceholder: true }, size?: number): IconSource;
function dispatchIcon(
    sidc: string,
    opts: { allowPlaceholder: false },
    size?: number,
): IconSource | null;
function dispatchIcon(
    sidc: string,
    opts: { allowPlaceholder: boolean },
    size?: number,
): IconSource | null {
    const strategy = chooseRenderStrategy(sidc, "icon");
    switch (strategy.library) {
        case "milsymbol": {
            const r = milsymbol.renderIcon(sidc, { size });
            return iconSourceWithId(sidc, r);
        }
        case "milstd": {
            const r = milstd.renderIcon(sidc);
            if (r) return iconSourceWithId(sidc, r);
            // milstd claimed the SIDC but failed to produce SVG; fall
            // through to the permissive milsymbol placeholder when allowed.
            return opts.allowPlaceholder ? milsymbolPlaceholder(sidc, size) : null;
        }
        case "none":
            return opts.allowPlaceholder ? milsymbolPlaceholder(sidc, size) : null;
        default:
            // Adding a new RenderStrategy variant must add a case above —
            // assertNever fails to type-check otherwise, and throws at
            // runtime so a missed variant doesn't silently return undefined.
            assertNever(strategy);
    }
}

/**
 * Permissive last-resort: ask milsymbol to render the SIDC, even though
 * `canRender` said no. milsymbol's wrapper now handles its own throw
 * surface internally — pathological input falls through to milsymbol's
 * pre-built "?" placeholder rather than propagating an exception — so
 * this call always returns a populated result. No try/catch needed.
 */
function milsymbolPlaceholder(sidc: string, size?: number): IconSource {
    return iconSourceWithId(sidc, milsymbol.renderIcon(sidc, { size }));
}

function iconSourceWithId(
    sidc: string,
    source: {
        url: string;
        width: number;
        height: number;
        anchorX?: number;
        anchorY?: number;
    },
): IconSource {
    return {
        url: source.url,
        width: source.width,
        height: source.height,
        ...(source.anchorX !== undefined ? { anchorX: source.anchorX } : {}),
        ...(source.anchorY !== undefined ? { anchorY: source.anchorY } : {}),
        id: sidc,
    };
}

// ─── Test hooks ─────────────────────────────────────────────────────────

/** Test-only: clear both icon caches so reference-identity tests start fresh. */
export function _resetRenderCachesForTests(): void {
    previewCache.clear();
    mapIconCache.clear();
}
