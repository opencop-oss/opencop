// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box } from "@chakra-ui/react";
import { type ReactElement } from "react";
import { assertNever } from "assert-never";
import { DEFAULT_SOURCE_COLOR, GAP_COLOR, HISTOGRAM_SOURCE_COLORS } from "./histogram.constants.ts";

// Illustrative source colors for the multi-source glyphs (stacked active-event
// bars, activity lines) — the first two entries of the categorical palette, so
// the glyph reads as "colored by source" and tracks the real chart colors.
const [GLYPH_TEAL, GLYPH_ORANGE] = HISTOGRAM_SOURCE_COLORS;

/** Which on-chart histogram series a swatch previews. */
export type SeriesSwatchVariant =
    | "expected-active-events"
    | "active-events"
    | "active-events-gap"
    | "active-events-stacked"
    | "active-event-updates";

/**
 * The inner glyph for a swatch variant. Split out so the `switch` carries an
 * exhaustiveness check — adding a variant without a glyph is a compile error.
 * Colors are kept in lockstep with the chart encoding (see `Histogram.tsx`):
 *   - `expected-active-events` — `gray-600` mini-bars (the expected-activity skeleton)
 *   - `active-events`          — translucent `brand-solid` block (loaded active events)
 *   - `active-events-gap`      — translucent `red-muted` block (missing / incomplete delivery)
 *   - `active-events-stacked`  — stacked source-colored bars (the multi-source active-event layer)
 *   - `active-event-updates`   — source-colored update lines
 */
function swatchGlyph(variant: SeriesSwatchVariant): ReactElement {
    switch (variant) {
        case "expected-active-events":
            return (
                <g fill="var(--chakra-colors-gray-600)">
                    <rect x="1" y="6" width="4" height="6" />
                    <rect x="7" y="1" width="4" height="11" />
                    <rect x="13" y="4" width="4" height="8" />
                </g>
            );
        case "active-events-stacked":
            // Stacked, source-colored bars — mirrors the colored active-event layer.
            return (
                <g fillOpacity={0.85}>
                    <rect x="1" y="6" width="4" height="6" fill={GLYPH_TEAL} />
                    <rect x="1" y="3" width="4" height="3" fill={GLYPH_ORANGE} />
                    <rect x="7" y="4" width="4" height="8" fill={GLYPH_TEAL} />
                    <rect x="7" y="1" width="4" height="3" fill={GLYPH_ORANGE} />
                    <rect x="13" y="7" width="4" height="5" fill={GLYPH_TEAL} />
                    <rect x="13" y="4" width="4" height="3" fill={GLYPH_ORANGE} />
                </g>
            );
        case "active-events":
        case "active-events-gap": {
            const color = variant === "active-events-gap" ? GAP_COLOR : DEFAULT_SOURCE_COLOR;
            return (
                <rect
                    x="1"
                    y="2"
                    width="16"
                    height="9"
                    fill={color}
                    fillOpacity={0.3}
                    stroke={color}
                    strokeOpacity={0.7}
                />
            );
        }
        case "active-event-updates":
            // Two source-colored lines — mirrors the per-source update overlay.
            return (
                <g fill="none" strokeWidth={1.5} strokeLinecap="round" strokeLinejoin="round">
                    <polyline points="1,8 5,4 9,6 13,3 17,6" stroke={GLYPH_TEAL} />
                    <polyline points="1,6 5,7 9,3 13,5 17,2" stroke={GLYPH_ORANGE} />
                </g>
            );
        default:
            // Exhaustiveness: a new SeriesSwatchVariant must add a glyph above.
            return assertNever(variant);
    }
}

/**
 * Tiny preview glyph that mirrors how a histogram series is drawn on the
 * chart, so legend-style affordances (the settings toggles and the hover
 * tooltip) read as the thing they describe.
 */
export function SeriesSwatch({ variant }: { variant: SeriesSwatchVariant }): ReactElement {
    return (
        <Box as="span" display="inline-flex" alignItems="center" flexShrink={0}>
            <svg width="18" height="12" viewBox="0 0 18 12" aria-hidden="true">
                {swatchGlyph(variant)}
            </svg>
        </Box>
    );
}
