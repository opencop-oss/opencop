// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import ms, { type SymbolOptions } from "milsymbol";
import { LRU } from "../lru";
import type { WrapperIconResult } from "../wrapper-icon-result";

/**
 * Subset of milsymbol's SymbolOptions that OpenCOP surfaces. Today only `size`
 * flows through; the remaining fields are wired up by Phase 2D's modifier
 * conduit so SYMBOL elements can show direction, leader lines, altitude
 * labels, designations, etc. Add fields here as consumers need them.
 */
export interface MilsymbolModifiers {
    direction?: number;
    speedLeader?: number;
    altitudeDepth?: string;
    dtg?: string;
    uniqueDesignation?: string;
}

export interface RenderIconOptions {
    /**
     * Width of the icon octagon in pixels (milsymbol's "L value"). NOT
     * the dimensions of the rendered SVG — the SVG can be wider or
     * taller because of frames, modifiers, leader lines, etc. Use
     * `WrapperIconResult.{width,height}` (read from `sym.getSize()`
     * after construction) for actual SVG dimensions. Defaults to 100.
     */
    size?: number;
    modifiers?: MilsymbolModifiers;
}

/**
 * Default `size` arg to pass to `new ms.Symbol(...)`. This is the width
 * of the icon octagon (milsymbol's "L value"), NOT the rendered SVG
 * dimensions. Passed explicitly so the cache key stays stable even if
 * milsymbol's own internal default ever changes.
 */
const DEFAULT_OCTAGON_WIDTH = 100;
const ICON_CACHE_SIZE = 5_000;

const iconCache = new LRU<string, WrapperIconResult>(ICON_CACHE_SIZE);

/**
 * Absolute-last-resort 1×1 transparent GIF, used only if the module-load
 * placeholder construction below itself throws (broken milsymbol install,
 * missing DOM canvas, etc.). Pre-rendered so the wrapper has a safe value
 * even when milsymbol cannot run at all.
 */
const HARDCODED_GIF_FALLBACK: WrapperIconResult = {
    url: "data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///ywAAAAAAQABAAACAUwAOw==",
    width: 1,
    height: 1,
};

/**
 * milsymbol-rendered "?" placeholder built once at module load using a
 * universally-renderable SIDC. `renderIcon` returns this when `new ms.Symbol`
 * throws on pathological input — preserves the legacy "?" UX rather than
 * surfacing an exception, and lets `renderIcon` honor its "always returns"
 * contract.
 *
 * The IIFE runs once at import; if even this construction fails, falls back
 * to {@link HARDCODED_GIF_FALLBACK} so the wrapper still has a valid value.
 */
const PERMISSIVE_PLACEHOLDER: WrapperIconResult = (() => {
    try {
        const sym = new ms.Symbol("SUUP-----------", { size: DEFAULT_OCTAGON_WIDTH });
        const dim = sym.getSize();
        return { url: sym.toDataURL(), width: dim.width, height: dim.height };
    } catch {
        return HARDCODED_GIF_FALLBACK;
    }
})();

function modifiersKey(mods?: MilsymbolModifiers): string {
    if (!mods) return "";
    // JSON.stringify with a stable key order avoids the delimiter-collision
    // class of bugs (`{uniqueDesignation: "A;direction=5"}` vs
    // `{uniqueDesignation: "A", direction: 5}` produced the same key under
    // a `${k}=${v};` join). `uniqueDesignation` and `dtg` carry free-text
    // user input — when Phase 2D wires modifiers through here, key
    // ambiguity becomes a wrong-icon cache hit.
    const keys = Object.keys(mods).sort() as (keyof MilsymbolModifiers)[];
    const filtered = keys.filter((k) => mods[k] !== undefined);
    if (filtered.length === 0) return "";
    return JSON.stringify(filtered.map((k) => [k, mods[k]]));
}

function buildSymbolOptions(opts: RenderIconOptions): SymbolOptions {
    return { size: opts.size ?? DEFAULT_OCTAGON_WIDTH, ...opts.modifiers };
}

/**
 * Does milsymbol recognize this SIDC?
 *
 * Wraps `ms.Symbol(sidc).isValid()` — milsymbol's own gate. In default mode
 * the library returns `true | false`; in extended mode it returns `true` or
 * an error object. We treat anything other than literal `true` as not
 * renderable (the safer answer), and catch construction errors so the gate
 * never throws.
 */
export function canRender(sidc: string, extended?: boolean): boolean {
    try {
        return new ms.Symbol(sidc).isValid(extended) === true;
    } catch {
        return false;
    }
}

/**
 * Render a SIDC to an icon (data URI + intrinsic size).
 *
 * Always returns a populated `WrapperIconResult`. milsymbol renders the "?"
 * placeholder for unknown or malformed SIDCs rather than throwing, and the
 * construction step is wrapped in try/catch so genuinely pathological input
 * (null, malformed shapes) falls through to {@link PERMISSIVE_PLACEHOLDER}
 * — consumers can rely on the "always returns" contract.
 *
 * Consumers that need to distinguish "real symbol" from "placeholder" must
 * call `canRender` first. Cached LRU by `${size}:${modifiersKey}:${sidc}`.
 */
export function renderIcon(sidc: string, opts: RenderIconOptions = {}): WrapperIconResult {
    const size = opts.size ?? DEFAULT_OCTAGON_WIDTH;
    const key = `${size}:${modifiersKey(opts.modifiers)}:${sidc}`;
    const cached = iconCache.get(key);
    if (cached) return cached;

    let result: WrapperIconResult;
    try {
        const sym = new ms.Symbol(sidc, buildSymbolOptions(opts));
        const dim = sym.getSize();
        result = {
            url: sym.toDataURL(),
            width: dim.width,
            height: dim.height,
        };
    } catch {
        result = PERMISSIVE_PLACEHOLDER;
    }
    iconCache.set(key, result);
    return result;
}

/** Test-only: clear the icon cache. */
export function _resetIconCacheForTests(): void {
    iconCache.clear();
}
