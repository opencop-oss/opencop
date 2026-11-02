// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useEffect, useRef } from "react";
import {
    useHistoricalActions,
    useHistoricalState,
    useHistoricalStore,
    usePlayheadPosition,
    type HistoricalSubjectType,
    type IntervalMode,
    type TimePreset,
} from "../data-plane/historical.store";

/** Read historical params from the URL and apply them to the store. */
function restoreHistoricalFromUrl(actions: ReturnType<typeof useHistoricalActions>) {
    const params = new URLSearchParams(window.location.search);

    if (params.get("hmode") !== "1") {
        // URL has no historical params — exit historical if currently active.
        if (useHistoricalStore.getState().mode === "historical") {
            actions.exitHistorical();
        }
        return;
    }

    const sourceIds =
        params
            .get("hsrc")
            ?.split(",")
            .map((s) => s.trim())
            .filter(Boolean) ?? [];
    // `hstype` is a parallel comma-separated list of subject kinds, one per
    // `hsrc` id. Missing/short entries default to "SOURCE".
    const subjectTypeList = params.get("hstype")?.split(",") ?? [];
    const subjectTypes: Record<string, HistoricalSubjectType> = Object.fromEntries(
        sourceIds.map((id, i) => [id, subjectTypeList[i] === "LAYER" ? "LAYER" : "SOURCE"]),
    );
    const from = params.get("hfrom");
    const to = params.get("hto");
    const interval = params.get("hint") as IntervalMode | null;
    const playhead = params.get("hplay");
    const rangeMode = params.get("hrangemode");

    // Enter historical first so preset/range actions don't fight with
    // enterHistorical's own preset application.
    if (useHistoricalStore.getState().mode !== "historical") {
        actions.enterHistorical();
    }

    if (sourceIds.length) actions.setSourceIds(sourceIds, subjectTypes);
    if (rangeMode && rangeMode !== "custom") {
        actions.applyPreset(rangeMode as TimePreset);
    } else if (from && to) {
        actions.setTimeRange(from, to);
    }
    if (interval) actions.setIntervalMode(interval);
    if (playhead) actions.setPlayheadPosition(playhead);
}

export function useHistoricalUrlSync() {
    const state = useHistoricalState();
    const actions = useHistoricalActions();
    const playheadPosition = usePlayheadPosition();
    const initialisedRef = useRef(false);
    const debounceTimerRef = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);

    // On mount: restore from URL
    useEffect(() => {
        if (initialisedRef.current) return;
        initialisedRef.current = true;
        restoreHistoricalFromUrl(actions);
    }, [actions]);

    // On popstate (browser back/forward): restore historical state from URL
    useEffect(() => {
        const handler = () => restoreHistoricalFromUrl(actions);
        window.addEventListener("popstate", handler);
        return () => window.removeEventListener("popstate", handler);
    }, [actions]);

    // On state change: serialize to URL
    useEffect(() => {
        if (!initialisedRef.current) return;

        clearTimeout(debounceTimerRef.current);
        debounceTimerRef.current = setTimeout(() => {
            const url = new URL(window.location.href);

            if (state.mode === "historical") {
                url.searchParams.set("hmode", "1");
                if (state.sourceIds.length) {
                    url.searchParams.set("hsrc", state.sourceIds.join(","));
                    // Parallel per-id subject kinds (see the restore path).
                    url.searchParams.set(
                        "hstype",
                        state.sourceIds.map((id) => state.subjectTypes[id] ?? "SOURCE").join(","),
                    );
                } else {
                    url.searchParams.delete("hsrc");
                    url.searchParams.delete("hstype");
                }
                url.searchParams.set("hrangemode", state.rangeMode);
                if (state.rangeMode === "custom") {
                    if (state.from) url.searchParams.set("hfrom", state.from);
                    if (state.to) url.searchParams.set("hto", state.to);
                } else {
                    url.searchParams.delete("hfrom");
                    url.searchParams.delete("hto");
                }
                url.searchParams.set("hint", state.intervalMode);
                if (playheadPosition) url.searchParams.set("hplay", playheadPosition);
                else url.searchParams.delete("hplay");
            } else {
                url.searchParams.delete("hmode");
                url.searchParams.delete("hsrc");
                url.searchParams.delete("hstype");
                url.searchParams.delete("hrangemode");
                url.searchParams.delete("hfrom");
                url.searchParams.delete("hto");
                url.searchParams.delete("hint");
                url.searchParams.delete("hplay");
            }

            history.replaceState(null, "", url.toString());
        }, 200);

        return () => clearTimeout(debounceTimerRef.current);
    }, [
        state.mode,
        state.sourceIds,
        state.subjectTypes,
        state.from,
        state.to,
        state.rangeMode,
        state.intervalMode,
        playheadPosition,
    ]);
}
