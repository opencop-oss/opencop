// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useMemo } from "react";

/**
 * Flattens a nested color config object into a map of hex → token path.
 * Handles both `{ value: "#hex" }` leaf nodes and nested objects.
 */
function flattenColors(obj: Record<string, unknown>, prefix: string, map: Map<string, string>) {
    for (const [key, val] of Object.entries(obj)) {
        const path = prefix ? `${prefix}.${key}` : key;

        if (val && typeof val === "object") {
            const record = val as Record<string, unknown>;
            if (typeof record.value === "string") {
                const hex = record.value.toLowerCase();
                // Only map concrete hex values, skip token references like "{colors.xxx}"
                if (hex.startsWith("#")) {
                    map.set(normalizeHex(hex), path);
                }
            } else {
                flattenColors(record, path, map);
            }
        }
    }
}

/** Normalize hex to 6-digit lowercase without alpha for matching */
function normalizeHex(hex: string): string {
    let h = hex.toLowerCase().replace(/ff$/, "");
    // Expand 3-digit shorthand
    if (h.length === 4) {
        h = `#${h[1]}${h[1]}${h[2]}${h[2]}${h[3]}${h[3]}`;
    }
    return h;
}

/** Convert rgb(r, g, b) to #rrggbb for lookup */
export function rgbToHex(rgb: string): string | null {
    const match = rgb.match(/rgba?\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)(?:\s*,\s*[\d.]+)?\s*\)/);
    if (!match) return null;
    const [, r, g, b] = match;
    return `#${[r, g, b].map((c) => Number(c).toString(16).padStart(2, "0")).join("")}`;
}

/**
 * Build a reverse lookup: normalized hex → theme token path.
 * Import the raw theme config colors and call this once.
 */
export function buildTokenMap(
    tokenColors: Record<string, unknown>,
    semanticColors?: Record<string, unknown>,
): Map<string, string> {
    const map = new Map<string, string>();
    flattenColors(tokenColors, "", map);
    if (semanticColors) {
        flattenColors(semanticColors, "", map);
    }
    return map;
}

// Import the theme config statically so we can extract token colors
import { ocSystem } from "@opencop/ui";

/**
 * Hook that returns a stable Map<hex, tokenName> for the current theme.
 */
export function useThemeTokenMap(): Map<string, string> {
    return useMemo(() => {
        const map = new Map<string, string>();

        // Extract token colors from the system
        // The system exposes _config which has our defineConfig output
        const cfg = ocSystem as unknown as {
            _config?: {
                theme?: {
                    tokens?: { colors?: Record<string, unknown> };
                    semanticTokens?: { colors?: Record<string, unknown> };
                };
            };
        };

        const tokenColors = cfg._config?.theme?.tokens?.colors;
        const semanticColors = cfg._config?.theme?.semanticTokens?.colors;

        if (tokenColors) {
            flattenColors(tokenColors, "", map);
        }
        if (semanticColors) {
            flattenColors(semanticColors, "", map);
        }

        return map;
    }, []);
}
