// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * Shared value tokens for the histogram chart contract.
 *
 * `@opencop/ui` is a leaf package and can't import the data-plane (`@opencop/api`)
 * or app-side definitions, so the chart keeps its own copies of the wire tokens
 * here. They are defined as `as const` objects (not bare unions) so neither the
 * chart nor its callers hand-retype the literals — `"incomplete-parts"` in
 * particular is easy to mistype. The derived union types are the prop-facing
 * contract. Keep these in sync with their canonical homes:
 *   - {@link HISTOGRAM_METRIC} mirrors `HISTOGRAM_METRIC` in `@opencop/api`.
 *   - {@link CHUNK_STATUS} mirrors `CHUNK_STATUS` in
 *     `packages/app/src/features/historical/data-plane/HistoricalSession.ts`.
 */

/**
 * Delivery status of a single overlay chunk. Omitted/default = {@link CHUNK_STATUS.Ok};
 * the gap statuses render as full-height muted-red bars.
 */
export const CHUNK_STATUS = {
    Ok: "ok",
    Missing: "missing",
    IncompleteParts: "incomplete-parts",
} as const;
export type HistogramChunkStatus = (typeof CHUNK_STATUS)[keyof typeof CHUNK_STATUS];

/**
 * Which metric a bucket value carries (see `@opencop/api`'s `HISTOGRAM_METRIC`):
 * - `ACTIVE`  — entities alive in the bucket (density bars)
 * - `UPDATED` — entities that emitted a sample (sparkline / update lines)
 */
export const HISTOGRAM_METRIC = {
    Active: "ACTIVE",
    Updated: "UPDATED",
} as const;
export type HistogramMetric = (typeof HISTOGRAM_METRIC)[keyof typeof HISTOGRAM_METRIC];

/** Fallback bar color for a source with no assigned/overridden color. */
export const DEFAULT_SOURCE_COLOR = "var(--chakra-colors-brand-solid)";

/** Fill for missing / incomplete chunk bars (CVD-friendly muted red). */
export const GAP_COLOR = "var(--chakra-colors-red-muted)";

/**
 * Categorical palette for the colored active-event bars / update lines / legend
 * (dark-bg tuned, max-separation). Lives here because it is the raw palette the
 * chart draws with; the app-side selection-order assignment + per-source
 * overrides build on it (see `historical-palette.ts`, which re-exports this).
 * There is no source cap, so consumers cycle it once exhausted.
 */
export const HISTOGRAM_SOURCE_COLORS = [
    "#4FD1C5", // teal
    "#F6AD55", // orange
    "#63B3ED", // blue
    "#F687B3", // pink
    "#68D391", // green
    "#B794F4", // purple
    "#FC8181", // red
    "#FBD38D", // yellow
] as const;
