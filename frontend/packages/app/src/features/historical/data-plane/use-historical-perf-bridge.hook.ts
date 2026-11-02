// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * DEV-only seam that registers the active {@link HistoricalDataManager} with the
 * perf bridge (`window.__opencop_perf.historical`) so historical rendering can be
 * stress-tested without the backend — synthetic session → inject → scrub, or a
 * timed synthetic stream — and so e2e tests can drive selection and screen
 * projection for historical elements (which deck.gl picking can't resolve).
 * Lives apart from {@link HistoricalDataContext} so the production provider stays
 * free of perf plumbing (and of the `features/historical → features/map` imports
 * this introduces: perf-bridge registration, plus direct `map.store` /
 * `MapManagerContext` access used to drive selection and world→screen projection).
 *
 * The whole hook compiles to a no-op effect outside DEV.
 */
import { useEffect } from "react";
import { DateTime } from "luxon";
import { useQueryClient } from "@tanstack/react-query";
import { HISTOGRAM_METRIC, type HistogramBucket } from "@opencop/api";

import { setHistoricalPerfManager } from "~/features/map/perf-bridge";
import { useMapStore } from "~/features/map/core/map.store";
import { useMapManager } from "~/features/map/MapManagerContext";

import type { HistoricalDataManager } from "./HistoricalDataManager";
import { historicalQueries } from "./historical.queries";
import { computeBars, useHistoricalStore } from "./historical.store";

/**
 * Build a flat synthetic density histogram (`bars` ACTIVE buckets across the
 * range) to seed the histogram query cache for a synthetic session, which never
 * hits the REST aggregate the real density bars come from.
 */
function buildSyntheticHistogramBuckets(
    from: string,
    to: string,
    bars: number,
    totalActive: number,
): HistogramBucket[] {
    const fromMs = DateTime.fromISO(from, { zone: "utc" }).toMillis();
    const toMs = DateTime.fromISO(to, { zone: "utc" }).toMillis();
    const width = (toMs - fromMs) / bars;
    const perBar = Math.round(totalActive / bars);
    const buckets: HistogramBucket[] = [];
    for (let i = 0; i < bars; i++) {
        const start = fromMs + i * width;
        buckets.push({
            start: DateTime.fromMillis(start, { zone: "utc" }).toISO() ?? "",
            end: DateTime.fromMillis(start + width, { zone: "utc" }).toISO() ?? "",
            values: [{ key: "perf", metric: HISTOGRAM_METRIC.Active, count: perBar }],
        });
    }
    return buckets;
}

/**
 * Register `manager` with the perf bridge (and clear it on teardown) in DEV.
 * No-op in production builds. Pass the active manager (or `null` when none).
 */
export function useHistoricalPerfBridge(manager: HistoricalDataManager | null): void {
    const queryClient = useQueryClient();
    const mapManager = useMapManager();

    useEffect(() => {
        if (!import.meta.env.DEV) return;
        setHistoricalPerfManager(
            manager
                ? {
                      manager,
                      setPlayhead: (iso) =>
                          useHistoricalStore.getState().actions.setPlayheadPosition(iso),
                      selectByKey: (key) => {
                          const s = useMapStore.getState();
                          if (key) s.select(key);
                          else s.resetSelection();
                      },
                      projectToScreen: (lngLat) => mapManager.projectToScreen(lngLat),
                      seedHistogram: ({ totalActive }) => {
                          const s = useHistoricalStore.getState();
                          const sourceId = s.sourceIds[0];
                          if (!sourceId || !s.from || !s.to) return;
                          const bars = computeBars(s.from, s.to, s.intervalMode);
                          queryClient.setQueryData(
                              historicalQueries.histogram(
                                  sourceId,
                                  { from: s.from, to: s.to, bars },
                                  true,
                              ).queryKey,
                              {
                                  buckets: buildSyntheticHistogramBuckets(
                                      s.from,
                                      s.to,
                                      bars,
                                      totalActive,
                                  ),
                              },
                          );
                      },
                  }
                : null,
        );
        return () => setHistoricalPerfManager(null);
    }, [manager, queryClient, mapManager]);
}
