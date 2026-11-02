// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/** Point-symbol icon support and classification of excluded tactical graphics. */
import {
    MilStdIconRenderer,
    MSLookup,
    SymbolID,
    SymbolUtilities,
} from "@armyc2.c5isr.renderer/mil-sym-ts-web";
import { LRU } from "../lru";
import type { WrapperIconResult } from "../wrapper-icon-result";
const SYMBOL_LOOKUP_VERSION = SymbolID.Version_2525Dch1;
const emptyModifiers = (): Map<string, string> => new Map();
const iconCache = new LRU<string, WrapperIconResult | null>(1_000);

export function isMultiPoint(sidc: string): boolean {
    try {
        return SymbolUtilities.isMultiPoint(sidc);
    } catch {
        return false;
    }
}

export function isTacticalGraphic(sidc: string): boolean {
    try {
        return SymbolUtilities.isTacticalGraphic(sidc);
    } catch {
        return false;
    }
}

export function canRenderAsIcon(sidc: string): boolean {
    try {
        return MilStdIconRenderer.getInstance().CanRender(sidc, emptyModifiers());
    } catch {
        return false;
    }
}

export function listBasicIds(): readonly string[] {
    try {
        return MSLookup.getInstance().getIDList(SYMBOL_LOOKUP_VERSION);
    } catch {
        return [];
    }
}

export function renderIcon(sidc: string): WrapperIconResult | null {
    // `null` and a populated result are both valid cached values; `undefined`
    // means not-cached (LRU.get returns undefined for missing keys, never for
    // explicit null entries).
    const cached = iconCache.get(sidc);
    if (cached !== undefined) return cached;

    let result: WrapperIconResult | null;
    try {
        const renderer = MilStdIconRenderer.getInstance();
        if (!renderer.CanRender(sidc, emptyModifiers())) {
            result = null;
        } else {
            const info = renderer.RenderSVG(sidc, emptyModifiers(), emptyModifiers());
            if (!info) {
                result = null;
            } else {
                const bounds = info.getImageBounds();
                const rawAnchorX = info.getSymbolCenterX();
                const rawAnchorY = info.getSymbolCenterY();
                const anchorX = Number.isFinite(rawAnchorX) ? rawAnchorX : bounds.getWidth() / 2;
                const anchorY = Number.isFinite(rawAnchorY) ? rawAnchorY : bounds.getHeight() / 2;
                result = {
                    url: info.getSVGDataURI(),
                    width: bounds.getWidth(),
                    height: bounds.getHeight(),
                    anchorX,
                    anchorY,
                };
            }
        }
    } catch {
        result = null;
    }
    iconCache.set(sidc, result);
    return result;
}

export function _resetIconCacheForTests(): void {
    iconCache.clear();
}
