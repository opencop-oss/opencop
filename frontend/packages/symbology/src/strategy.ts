// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * Cross-library render strategy. Composes the per-library gates to answer
 * "which renderer should handle this SIDC, given what we want to do with it?"
 *
 * The only file allowed to import both `mil-sym-ts-wrapper` and
 * `milsymbol-wrapper`. All other consumers go through this module instead
 * of routing by hand.
 */
import * as milstd from "./mil-sym-ts-wrapper";
import * as milsymbol from "./milsymbol-wrapper";
import { LRU } from "./lru";

export type RenderIntent = "icon";

export type RenderStrategy =
    /** mil-sym-ts MilStdIconRenderer static SVG. */
    | { library: "milstd"; mode: "icon" }
    /** milsymbol static icon. */
    | { library: "milsymbol"; mode: "icon" }
    /** No library can render this SIDC for the requested intent. */
    | { library: "none" };

/**
 * Which editor "owns" a given SIDC. Library-authoritative — derived from
 * `SymbolUtilities.isTacticalGraphic`, which classifies SS25 (Control
 * Measures) and weather symbols as graphics independent of point count.
 */
export type EditorContext = "symbol-editor" | "graphic-editor";

// Both caches are keyed by SIDC alone (or `intent:SIDC` for the strategy
// cache); not by modifiers or per-element fields. The realistic upper
// bound is the size of the catalog (~hundreds of distinct SIDCs in use),
// well below 5,000. If milsymbol icon caching ever extends to per-element
// label modifiers, that combinatorial growth lands in
// `milsymbol-wrapper/wrapper.ts`'s iconCache — not here. Don't bump this cap to
// "fix" a hit-rate problem that's actually downstream.
const CACHE_SIZE = 5_000;
const cache = new LRU<string, RenderStrategy>(CACHE_SIZE);
const editorCache = new LRU<string, EditorContext>(CACHE_SIZE);

/** Choose an icon renderer, retaining the existing point-symbol appearance. */
export function chooseRenderStrategy(sidc: string, intent: RenderIntent): RenderStrategy {
    const key = `${intent}:${sidc}`;
    const cached = cache.get(key);
    if (cached !== undefined) return cached;

    const strategy = compute(sidc);
    cache.set(key, strategy);
    return strategy;
}

/**
 * Library-authoritative classification for symbol validation. SS25 control measures
 * and weather symbols are tactical graphics regardless of point count. The
 * "graphic-editor" classification is rejected by this build's Symbol editor;
 * it does not imply that a Graphic Editor is registered.
 */
export function chooseEditor(sidc: string): EditorContext {
    const cached = editorCache.get(sidc);
    if (cached !== undefined) return cached;

    const editor: EditorContext = milstd.isTacticalGraphic(sidc)
        ? "graphic-editor"
        : "symbol-editor";
    editorCache.set(sidc, editor);
    return editor;
}

function compute(sidc: string): RenderStrategy {
    // intent === "icon". For tactical graphics (SS25 + weather, including
    // single-point ones like Action Point), prefer milstd's icon renderer
    // for their static previews. Unit symbols and equipment go
    // milsymbol-first for visual continuity with today's rendering.
    if (milstd.isTacticalGraphic(sidc)) {
        if (milstd.canRenderAsIcon(sidc)) return { library: "milstd", mode: "icon" };
        if (milsymbol.canRender(sidc)) return { library: "milsymbol", mode: "icon" };
        return { library: "none" };
    }
    if (milsymbol.canRender(sidc)) return { library: "milsymbol", mode: "icon" };
    if (milstd.canRenderAsIcon(sidc)) return { library: "milstd", mode: "icon" };
    return { library: "none" };
}

/**
 * Symbol-set codes (2-char) excluded from the ordinary point-symbol picker.
 * "Multipoint" here is a name-of-convenience kept for backwards compatibility
 * with the catalog partition in `sidc.ts` — the real predicate is
 * "tactical graphic" (`SymbolUtilities.isTacticalGraphic`), which classifies
 * SS25 (Control Measures) and weather symbols regardless of point count.
 * A single-point Action Point (SS25) is still excluded.
 *
 * Computed once at first call by walking every basic ID the library knows
 * about and synthesizing a Standard-Identity-3 SIDC for the
 * `isTacticalGraphic` probe. ~2k basic IDs; one-shot, no I/O. Memoized at
 * module scope — the library is static per version.
 */
let multipointSymbolSets: ReadonlySet<string> | null = null;

export function getMultipointSymbolSets(): ReadonlySet<string> {
    if (multipointSymbolSets !== null) return multipointSymbolSets;

    const result = new Set<string>();
    for (const basicId of milstd.listBasicIds()) {
        const symbolSet = basicId.slice(0, 2);
        if (result.has(symbolSet)) continue;
        // Synthesize a SIDC for the probe. Format:
        // version(11) + context(0) + identity(3=friend) + symbolSet + status(0)
        //   + hqtf(0) + amplifier(00) + entityCode + modifiers(0000)
        const entityCode = basicId.slice(2, 8);
        const sidc = `110${"3"}${symbolSet}0000${entityCode}0000`;
        if (milstd.isTacticalGraphic(sidc)) {
            result.add(symbolSet);
        }
    }
    multipointSymbolSets = result;
    return result;
}

/** Test-only: clear both caches. */
export function _resetRenderStrategyCacheForTests(): void {
    cache.clear();
    editorCache.clear();
    multipointSymbolSets = null;
}
