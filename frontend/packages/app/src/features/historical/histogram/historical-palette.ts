// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * Per-source color/shade assignment for the multi-source historical histogram.
 *
 * Two scales, both keyed by **selection order** so a source occupies the same
 * stack position (and the same color) in every layer:
 *
 * - {@link HISTOGRAM_SOURCE_COLORS} — a qualitative categorical palette used for
 *   the colored active-event bars, the per-source active-event-updates lines, and
 *   the legend. This is the layer that carries source identity. There is **no
 *   source cap**, so the palette cycles once exhausted; color is a grouping aid,
 *   not the sole identifier (identity is reinforced by stable stack position +
 *   interaction + tooltip — see the histogram UI).
 * - {@link HISTOGRAM_SKELETON_SHADES} — muted grayscale shades for the
 *   informational expected-active-events skeleton. The skeleton only needs to
 *   convey the expected shape of activity, so grayscale suffices and avoids
 *   competing with the colored active-event layer.
 *
 * The project's Chakra theme has no qualitative chart palette (only semantic
 * tokens), so these are chart-specific raw values tuned for the dark map panel.
 * The categorical {@link HISTOGRAM_SOURCE_COLORS} is the raw palette the chart
 * draws with, so it is owned by `@opencop/ui` and re-exported here; this module
 * adds the selection-order assignment + per-source override semantics on top.
 *
 * Order-keyed default colors mean removing an earlier source shifts the default
 * color of every source after it — this is intentional, not a bug: color and
 * stack position both follow selection order, so they always agree. Per-source
 * overrides (see `sourceColorOverrides` in the store) are keyed by source id
 * instead, so a deliberately chosen color stays pinned to its source regardless
 * of order. Hashing ids to palette slots was rejected: it would desync color
 * from stack position and let two sources collide on the same color.
 */
import { DEFAULT_SOURCE_COLOR, HISTOGRAM_SOURCE_COLORS } from "@opencop/ui";

/** Categorical palette / its fallback, re-exported from the chart package. */
export { DEFAULT_SOURCE_COLOR, HISTOGRAM_SOURCE_COLORS };

/** Fallback shade for a source with no assigned skeleton shade. */
export const DEFAULT_SKELETON_SHADE = "var(--chakra-colors-gray-600)";

/** Muted grayscale shades for the informational expected-active-events skeleton segments. */
export const HISTOGRAM_SKELETON_SHADES = [
    "#3a4150",
    "#4a5568",
    "#5a6678",
    "#6c7888",
    "#7d8a9c",
] as const;

/** Cyclic index into a palette; safe for any (including negative) index. */
function cyclic<T>(palette: readonly T[], index: number): T {
    const n = palette.length;
    return palette[((index % n) + n) % n];
}

/** Categorical active-event/update-line/legend color for the source at selection index `i`. */
export function sourceColor(index: number): string {
    return cyclic(HISTOGRAM_SOURCE_COLORS, index);
}

/** Grayscale skeleton shade for the source at selection index `i`. */
export function sourceShade(index: number): string {
    return cyclic(HISTOGRAM_SKELETON_SHADES, index);
}

/**
 * Map each source id to its categorical color by selection order. Cycles past
 * the palette length. The input order is the source of truth for stack position.
 */
export function assignSourceColors(sourceIds: readonly string[]): Map<string, string> {
    return new Map(sourceIds.map((id, i) => [id, sourceColor(i)]));
}

/** Map each source id to its grayscale skeleton shade by selection order. */
export function assignSourceShades(sourceIds: readonly string[]): Map<string, string> {
    return new Map(sourceIds.map((id, i) => [id, sourceShade(i)]));
}
