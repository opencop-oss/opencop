// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

const HEX = /^(?:[0-9a-f]{3,4}|[0-9a-f]{6}|[0-9a-f]{8})$/i;

/**
 * Parse a hex colour into `[r, g, b, a]`, each 0–255: `#RRGGBB`, `#RRGGBBAA`, or
 * the shorthands `#RGB` and `#RGBA` (the `#` optional). Alpha is the hex alpha
 * when present, else opaque, then scaled by `opacity` (0–1). Anything else reads
 * as transparent, so a bad colour draws nothing rather than `NaN` channels.
 */
export function hexToRgba(hex: string, opacity = 1): [number, number, number, number] {
    const raw = hex.startsWith("#") ? hex.slice(1) : hex;
    if (!HEX.test(raw)) return [0, 0, 0, 0];
    const code = raw.length <= 4 ? [...raw].map((digit) => digit + digit).join("") : raw;
    const alpha = code.length === 8 ? parseInt(code.substring(6, 8), 16) : 255;
    return [
        parseInt(code.substring(0, 2), 16),
        parseInt(code.substring(2, 4), 16),
        parseInt(code.substring(4, 6), 16),
        alpha * opacity,
    ];
}
