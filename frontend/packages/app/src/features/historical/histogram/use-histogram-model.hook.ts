// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * Derives the multi-source histogram render model from the union of selected
 * subjects, per-source color/visibility state, the histogram REST responses,
 * and the streamed chunk ranges. Lives apart from `HistoricalPanel` so the
 * panel stays declarative composition and this data-massaging is reusable /
 * independently reasoned about.
 */
import { HISTOGRAM_METRIC, type HistogramBucket, type HistogramSourceSeries } from "@opencop/ui";
import { useMemo } from "react";

import { useChunkRanges, type ChunkRange } from "../data-plane/HistoricalDataContext";
import {
    computeBars,
    useHiddenSourceIds,
    useSourceColorOverrides,
    type IntervalMode,
} from "../data-plane/historical.store";
import { assembleMultiSourceBuckets } from "./historical-histogram.assembly";
import {
    assignSourceColors,
    assignSourceShades,
    DEFAULT_SKELETON_SHADE,
    DEFAULT_SOURCE_COLOR,
} from "./historical-palette";
import { useMultiSourceHistograms } from "./use-multi-source-histograms.hook";
import { useSubjectNames } from "./use-subject-names.hook";

export interface HistogramModelInput {
    sourceIds: string[];
    from: string | null;
    to: string | null;
    intervalMode: IntervalMode;
    activated: boolean;
}

export interface HistogramModel {
    /** Per-source color map (selection order, override-aware) — for the legend. */
    sourceColors: ReadonlyMap<string, string>;
    /** Density/activity buckets summed across the *visible* sources. */
    histogramBuckets: HistogramBucket[];
    /** Per-source series for the visible sources (stack order preserved). */
    sourceSeries: HistogramSourceSeries[];
    /** Color map restricted to visible sources — the stacked chunk layer. */
    visibleSourceColors: ReadonlyMap<string, string>;
    /** Chunk ranges restricted to visible sources. */
    visibleChunks: readonly ChunkRange[];
    /** Planner-resolved effective range that aligns density + chunk bars. */
    effectiveRange: { start: string; end: string } | null;
    isLoading: boolean;
    /**
     * Every selected source's histogram failed, so there is nothing to draw.
     * A *partial* failure is deliberately not reported here: the sources that
     * did resolve still render, and the status bar names the ones that didn't.
     */
    allFailed: boolean;
}

export function useHistogramModel({
    sourceIds,
    from,
    to,
    intervalMode,
    activated,
}: HistogramModelInput): HistogramModel {
    // Stable per-source color map (selection order) — shared by the stacked
    // chunk bars and the legend. Keyed off the joined ids so it only recomputes
    // when the set changes, not on every render.
    const sourcesKey = sourceIds.join(",");
    const colorOverrides = useSourceColorOverrides();
    // Palette colors with the user's per-source overrides layered on top. The
    // override map is keyed by source id, so a chosen color follows the source
    // regardless of selection order.
    const sourceColors = useMemo(() => {
        const map = assignSourceColors(sourcesKey ? sourcesKey.split(",") : []);
        for (const id of map.keys()) {
            const override = colorOverrides[id];
            if (override) map.set(id, override);
        }
        return map;
    }, [sourcesKey, colorOverrides]);
    // Grayscale shades for the per-source density skeleton (same selection-order
    // keying as the colors, so a source keeps a stable stack position).
    const sourceShades = useMemo(
        () => assignSourceShades(sourcesKey ? sourcesKey.split(",") : []),
        [sourcesKey],
    );
    // Sources the user hid from the histogram via the legend — their series are
    // filtered out of every chart layer below, but the data stays loaded.
    const hiddenSourceIds = useHiddenSourceIds();
    const hidden = useMemo(() => new Set(hiddenSourceIds), [hiddenSourceIds]);
    // Sources visible in the chart (selection order minus the ones hidden via the
    // legend) — the single source of truth for every per-source layer below.
    const visibleIds = useMemo(
        () => sourceIds.filter((id) => !hidden.has(id)),
        [sourceIds, hidden],
    );
    const chunks = useChunkRanges();

    const bars = from && to ? computeBars(from, to, intervalMode) : 0;
    // One histogram fetch per selected source (identical from/to/bars → buckets
    // align by index), assembled into a per-source/per-bucket model.
    const { responses, isLoading, allFailed } = useMultiSourceHistograms(
        sourceIds,
        { from: from ?? "", to: to ?? "", bars },
        activated,
    );
    const multiBuckets = useMemo(
        () => assembleMultiSourceBuckets(sourceIds, responses),
        [sourceIds, responses],
    );

    // The planner snaps the requested [from, to] to resolution boundaries; every
    // source fetched with the same window shares that effective grid, and the
    // historical chunk slots use the same grid. So the effective range — from any
    // resolved response — is the time domain that lines the histogram bars up with
    // the chunk-overlay bars. Falls back to the requested range while loading or
    // when the backend/perf-bridge doesn't report it. (Plain expression rather
    // than `useMemo`: a manual memo over the responses Map iteration can't be
    // preserved — the `react-hooks/preserve-manual-memoization` lint rule rejects
    // it. Cheap enough to redo per render: it stops at the first resolved
    // response and its result is consumed as two primitive strings.)
    let effectiveRange: { start: string; end: string } | null = null;
    for (const r of responses.values()) {
        if (r?.effectiveFrom && r?.effectiveTo) {
            effectiveRange = { start: r.effectiveFrom, end: r.effectiveTo };
            break;
        }
    }

    // Subject names for the per-source tooltip rows + legend — both data sources
    // and layers (a selected subject can be either).
    const { nameOf } = useSubjectNames();

    // Per-source series for the visible sources (stack order preserved). Gathers
    // each source's per-bucket ACTIVE (skeleton height) and UPDATED (activity
    // line); `b.sources` is in selection order so a source keeps a stable slot.
    const sourceSeries = useMemo<HistogramSourceSeries[]>(
        () =>
            visibleIds.map((id) => ({
                sourceId: id,
                name: nameOf(id),
                color: sourceColors.get(id) ?? DEFAULT_SOURCE_COLOR,
                shade: sourceShades.get(id) ?? DEFAULT_SKELETON_SHADE,
                active: multiBuckets.map(
                    (b) => b.sources.find((s) => s.sourceId === id)?.active ?? 0,
                ),
                activity: multiBuckets.map(
                    (b) => b.sources.find((s) => s.sourceId === id)?.updated ?? 0,
                ),
            })),
        [visibleIds, nameOf, sourceColors, sourceShades, multiBuckets],
    );
    // The density/activity axis sums the visible series per bucket, so hiding a
    // source rescales the chart. Derived from `sourceSeries` (already visible-only
    // and index-aligned) rather than re-filtering `multiBuckets`.
    const histogramBuckets = useMemo<HistogramBucket[]>(
        () =>
            multiBuckets.map((b, i) => ({
                start: b.start,
                end: b.end,
                values: [
                    {
                        key: "active",
                        metric: HISTOGRAM_METRIC.Active,
                        count: sourceSeries.reduce((sum, s) => sum + (s.active[i] ?? 0), 0),
                    },
                    {
                        key: "updated",
                        metric: HISTOGRAM_METRIC.Updated,
                        count: sourceSeries.reduce((sum, s) => sum + (s.activity[i] ?? 0), 0),
                    },
                ],
            })),
        [multiBuckets, sourceSeries],
    );
    // Color map restricted to visible sources — the stacked chunk layer renders
    // exactly these (hidden sources contribute nothing).
    const visibleSourceColors = useMemo<ReadonlyMap<string, string>>(
        () => new Map(visibleIds.map((id) => [id, sourceColors.get(id) ?? DEFAULT_SOURCE_COLOR])),
        [visibleIds, sourceColors],
    );
    const visibleChunks = useMemo(
        () => chunks.filter((c) => !hidden.has(c.sourceId)),
        [chunks, hidden],
    );

    return {
        sourceColors,
        histogramBuckets,
        sourceSeries,
        visibleSourceColors,
        visibleChunks,
        effectiveRange,
        isLoading,
        allFailed,
    };
}
