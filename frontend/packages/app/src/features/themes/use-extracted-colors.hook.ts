// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useEffect, useState, type RefObject } from "react";
import { rgbToHex, useThemeTokenMap } from "./use-theme-token-map.hook";

export interface ExtractedColor {
    computed: string; // The raw computed CSS color (hex)
    token: string | null; // Matched theme token name, if found
    property: string; // Which CSS property produced this (for dedup context)
}

const IGNORED_COLORS = new Set([
    "#000000",
    "#ffffff",
    "#00000000",
    "transparent",
    "rgba(0, 0, 0, 0)",
]);

const PROPERTIES = ["color", "backgroundColor", "borderColor"] as const;

/**
 * After mount, extracts computed colors from all descendant elements of the
 * ref target, deduplicates, and reverse-maps to theme token names.
 */
export function useExtractedColors(ref: RefObject<HTMLDivElement | null>): ExtractedColor[] {
    const tokenMap = useThemeTokenMap();
    const [colors, setColors] = useState<ExtractedColor[]>([]);

    useEffect(() => {
        const el = ref.current;
        if (!el) return;

        // Wait a tick for styles to settle
        const timer = setTimeout(() => {
            const seen = new Set<string>();
            const result: ExtractedColor[] = [];

            const elements = [el, ...Array.from(el.querySelectorAll("*"))];

            for (const child of elements) {
                const style = getComputedStyle(child);
                for (const prop of PROPERTIES) {
                    const raw = style[prop];
                    if (!raw) continue;

                    const hex = rgbToHex(raw);
                    if (!hex || IGNORED_COLORS.has(hex) || IGNORED_COLORS.has(raw)) continue;
                    if (seen.has(hex)) continue;
                    seen.add(hex);

                    const token = tokenMap.get(hex) ?? null;
                    result.push({ computed: hex, token, property: prop });
                }
            }

            // Sort: tokens first, then by hex
            result.sort((a, b) => {
                if (a.token && !b.token) return -1;
                if (!a.token && b.token) return 1;
                return a.computed.localeCompare(b.computed);
            });

            setColors(result);
        }, 100);

        return () => clearTimeout(timer);
    }, [ref, tokenMap]);

    return colors;
}
