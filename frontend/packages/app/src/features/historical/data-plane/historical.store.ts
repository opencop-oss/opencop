// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { DateTime } from "luxon";
import { create } from "zustand";
import { persist } from "zustand/middleware";
import { useShallow } from "zustand/react/shallow";

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

export type IntervalMode = "auto" | "1m" | "5m" | "15m" | "30m" | "1h" | "6h" | "1d" | "1w";

/**
 * Which kind of subject the historical query targets. Mirrors
 * `HistoricalSubjectRequest["subjectType"]` on the backend contract. Both kinds
 * share a single polymorphic `sourceId` (a data-source serverId or a layerId).
 */
export type HistoricalSubjectType = "SOURCE" | "LAYER";

export type TimePreset = "1h" | "6h" | "24h" | "7d" | "30d";

const INTERVAL_SECONDS: Record<Exclude<IntervalMode, "auto">, number> = {
    "1m": 60,
    "5m": 300,
    "15m": 900,
    "30m": 1800,
    "1h": 3600,
    "6h": 21600,
    "1d": 86400,
    "1w": 604800,
};

/**
 * Backend chunks are at least one minute wide, so zooming below ~15 minutes
 * produces a window that's too narrow to meaningfully visualize. `pushZoom`
 * refuses brushes narrower than this.
 */
export const MIN_ZOOM_RANGE_MS = 15 * 60 * 1000;

/**
 * Mirrors `SubmitHistoricalTracksUsecase.MAX_RANGE_DAYS` on the backend.
 * The custom date pickers stay unconstrained — users can pick any pair of
 * dates — but the Activate button is gated on this check and the status bar
 * surfaces a validation warning when the span exceeds the limit. Backend
 * still enforces it (`HISTORICAL_RANGE_TOO_LARGE`) as defense in depth.
 */
export const MAX_HISTORICAL_RANGE_DAYS = 30;

/**
 * Pure predicate. `true` when `(from, to)` covers more than
 * `MAX_HISTORICAL_RANGE_DAYS`. Returns `false` for null/empty/invalid inputs
 * so a partial UI state doesn't trip the warning prematurely.
 */
export function isRangeOverLimit(from: string | null, to: string | null): boolean {
    if (!from || !to) return false;
    const fromMs = DateTime.fromISO(from).toMillis();
    const toMs = DateTime.fromISO(to).toMillis();
    if (!Number.isFinite(fromMs) || !Number.isFinite(toMs)) return false;
    return toMs - fromMs > MAX_HISTORICAL_RANGE_DAYS * 24 * 60 * 60 * 1000;
}

const PRESET_HOURS: Record<TimePreset, number> = {
    "1h": 1,
    "6h": 6,
    "24h": 24,
    "7d": 168,
    "30d": 720,
};

const ORDERED_INTERVALS: Exclude<IntervalMode, "auto">[] = [
    "1m",
    "5m",
    "15m",
    "30m",
    "1h",
    "6h",
    "1d",
    "1w",
];

const MIN_BAR_WIDTH_PX = 4;
const DEFAULT_PIXEL_WIDTH = 800;
const MAX_BARS = 50; // server cap — will be raised later
const DEFAULT_PANEL_HEIGHT = 100;
export const MIN_PANEL_HEIGHT = 60;
export const MAX_PANEL_HEIGHT = 300;

// ---------------------------------------------------------------------------
// Auto-interval calculator
// ---------------------------------------------------------------------------

/**
 * Pick the smallest predefined interval that keeps bar width >= MIN_BAR_WIDTH_PX.
 * Returns the interval key and computed bar count.
 */
export function computeAutoInterval(
    from: string,
    to: string,
    pixelWidth = DEFAULT_PIXEL_WIDTH,
): { interval: Exclude<IntervalMode, "auto">; bars: number } {
    const durationSeconds = DateTime.fromISO(to).diff(DateTime.fromISO(from), "seconds").seconds;
    if (durationSeconds <= 0) return { interval: "1m", bars: 1 };

    const maxBars = Math.floor(pixelWidth / MIN_BAR_WIDTH_PX);
    const minIntervalSeconds = durationSeconds / maxBars;

    for (const key of ORDERED_INTERVALS) {
        if (INTERVAL_SECONDS[key] >= minIntervalSeconds) {
            const bars = Math.min(MAX_BARS, Math.ceil(durationSeconds / INTERVAL_SECONDS[key]));
            return { interval: key, bars: Math.max(1, bars) };
        }
    }

    // Fallback: widest interval
    const bars = Math.min(MAX_BARS, Math.ceil(durationSeconds / INTERVAL_SECONDS["1w"]));
    return { interval: "1w", bars: Math.max(1, bars) };
}

/**
 * Compute the bar count for a manual interval selection.
 */
export function computeBars(from: string, to: string, intervalMode: IntervalMode): number {
    if (intervalMode === "auto") return computeAutoInterval(from, to).bars;
    const durationSeconds = DateTime.fromISO(to).diff(DateTime.fromISO(from), "seconds").seconds;
    if (durationSeconds <= 0) return 1;
    return Math.min(
        MAX_BARS,
        Math.max(1, Math.ceil(durationSeconds / INTERVAL_SECONDS[intervalMode])),
    );
}

// ---------------------------------------------------------------------------
// Store
// ---------------------------------------------------------------------------

export type RangeMode = TimePreset | "custom";

interface ZoomEntry {
    from: string;
    to: string;
    rangeMode: RangeMode;
}

interface HistoricalState {
    mode: "live" | "historical";
    /**
     * Selected subjects for the historical query, in selection order (which also
     * drives stack position + palette assignment). Each id is a data-source
     * serverId or a layerId — see {@link subjectTypes}. The backend fans out one
     * job per subject and the chunks are routed back per `history-job-id`.
     */
    sourceIds: string[];
    /**
     * Subject kind per selected id (data source vs layer). Layer and data-source
     * ids occupy distinct UUID spaces, so this is a flat id→type map covering the
     * current `sourceIds`; it drives the `subjectType` sent per subject at submit.
     * Ids absent from the map default to `"SOURCE"`.
     */
    subjectTypes: Record<string, HistoricalSubjectType>;
    from: string | null;
    to: string | null;
    rangeMode: RangeMode;
    intervalMode: IntervalMode;
    activated: boolean;
    zoomStack: ZoomEntry[];
    playheadPosition: string | null;
    panelHeight: number;
    /** Bumped to force the data provider to re-create its source (retry). */
    retryNonce: number;
    /**
     * Signature of the actionable status the user most recently dismissed.
     * Cleared on retry so a recurring error reappears with feedback. Ephemeral
     * — not persisted.
     */
    dismissedSignature: string | null;
    /** Show the brand-color per-chunk overlay bars in the histogram. */
    showActiveEvents: boolean;
    /** Show the cyan event-activity sparkline (UPDATED metric). */
    showActiveEventUpdates: boolean;
    /**
     * Source ids the user has hidden from the histogram via the legend. The
     * data stays loaded (the source remains in `sourceIds`); only its series
     * (chunk bars, density skeleton, sparkline, tooltip row) are suppressed.
     * Ephemeral — a hidden series reappears on reload.
     */
    hiddenSourceIds: string[];
    /**
     * Per-source color overrides (hex), keyed by source id, set from the legend
     * color picker. Overrides the palette color for that source everywhere it's
     * drawn. Persisted so a chosen color survives reloads.
     */
    sourceColorOverrides: Record<string, string>;
    /**
     * When true, the histogram panel renders the settings UI in place of the
     * histogram chart. Ephemeral — not persisted across sessions.
     */
    settingsOpen: boolean;
    /**
     * Set when the user tried to brush a zoom narrower than the minimum width
     * ({@link MIN_ZOOM_RANGE_MS}). Surfaces a "minimum zoom reached" hint in the
     * status bar until the next successful range change clears it. Ephemeral.
     */
    zoomLimitHit: boolean;
}

/**
 * The subset of {@link HistoricalState} that survives reloads. Declaring it as a
 * named type and annotating the persist `partialize` return with it makes the
 * persisted shape a compile-checked contract: adding a field here without adding
 * it to `partialize` (or removing one from state) is a type error, so the two
 * can't silently drift (which is how the stale `showDensity` slot lingered).
 *
 * Everything not listed is intentionally ephemeral: session/lifecycle state
 * (`mode`, `activated`, `retryNonce`, `zoomStack`, `playheadPosition`,
 * `dismissedSignature`, `settingsOpen`) and the legend's hidden set
 * (`hiddenSourceIds`), which resets each session by design.
 */
type PersistedHistoricalState = Pick<
    HistoricalState,
    | "sourceIds"
    | "subjectTypes"
    | "from"
    | "to"
    | "rangeMode"
    | "intervalMode"
    | "panelHeight"
    | "showActiveEvents"
    | "showActiveEventUpdates"
    | "sourceColorOverrides"
>;

interface HistoricalActions {
    enterHistorical: () => void;
    exitHistorical: () => void;
    /** Set the full subject selection plus the id→kind map for those ids. */
    setSourceIds: (ids: string[], subjectTypes: Record<string, HistoricalSubjectType>) => void;
    setTimeRange: (from: string, to: string) => void;
    setRangeMode: (mode: RangeMode) => void;
    setIntervalMode: (mode: IntervalMode) => void;
    applyPreset: (preset: TimePreset) => void;
    activate: () => void;
    pushZoom: (from: string, to: string) => void;
    popZoom: () => void;
    resetZoom: () => void;
    setPlayheadPosition: (iso: string | null) => void;
    setPanelHeight: (height: number) => void;
    retry: () => void;
    dismissSignature: (signature: string) => void;
    setShowActiveEvents: (show: boolean) => void;
    setShowActiveEventUpdates: (show: boolean) => void;
    /** Hide/show one source's histogram series (data stays loaded). */
    toggleSeriesHidden: (id: string) => void;
    /** Override one source's color (hex). */
    setSourceColor: (id: string, color: string) => void;
    /** Clear a source's color override, reverting to its palette color. */
    resetSourceColor: (id: string) => void;
    toggleSettings: () => void;
    closeSettings: () => void;
}

type HistoricalStore = HistoricalState & { actions: HistoricalActions };

export const useHistoricalStore = create<HistoricalStore>()(
    persist(
        (set, get) => ({
            mode: "live" as const,
            sourceIds: [] as string[],
            subjectTypes: {} as Record<string, HistoricalSubjectType>,
            from: null,
            to: null,
            rangeMode: "24h" as RangeMode,
            intervalMode: "auto" as IntervalMode,
            activated: false,
            zoomStack: [] as ZoomEntry[],
            playheadPosition: null as string | null,
            panelHeight: DEFAULT_PANEL_HEIGHT,
            retryNonce: 0,
            dismissedSignature: null as string | null,
            showActiveEvents: true,
            showActiveEventUpdates: false,
            hiddenSourceIds: [] as string[],
            sourceColorOverrides: {} as Record<string, string>,
            settingsOpen: false,
            zoomLimitHit: false,

            actions: {
                enterHistorical: () => {
                    const now = DateTime.utc();
                    // Reset rangeMode/intervalMode to known defaults so the
                    // preset label always agrees with the from/to timestamps.
                    // Persisted values from a prior session would otherwise
                    // produce a UI/state mismatch (e.g. preset "1h" with a 24h
                    // time range).
                    set({
                        mode: "historical" as const,
                        from: now.minus({ hours: 24 }).toISO(),
                        to: now.toISO(),
                        rangeMode: "24h" as RangeMode,
                        intervalMode: "auto" as IntervalMode,
                        activated: false,
                        zoomLimitHit: false,
                    });
                },

                exitHistorical: () =>
                    set({
                        mode: "live" as const,
                        activated: false,
                        zoomStack: [],
                        playheadPosition: null,
                        zoomLimitHit: false,
                    }),

                setSourceIds: (sourceIds, subjectTypes) =>
                    set((s) => {
                        // Changing the selection of an active view never changes the
                        // time window, so keep the activation (and zoom/playhead) and
                        // let the data plane reconcile in place: a removal drops just
                        // that source's data, an add streams the new source in (or
                        // re-submits the full set if a load is still in flight). The
                        // one exception is clearing the last source — with nothing
                        // selected there's nothing to show, so deactivate. Selecting
                        // while inactive discards the plan as before.
                        if (s.activated && sourceIds.length > 0) return { sourceIds, subjectTypes };
                        return {
                            sourceIds,
                            subjectTypes,
                            activated: false,
                            zoomStack: [],
                            playheadPosition: null,
                            zoomLimitHit: false,
                        };
                    }),

                setTimeRange: (from, to) =>
                    set({
                        from,
                        to,
                        rangeMode: "custom" as RangeMode,
                        activated: false,
                        zoomStack: [],
                        playheadPosition: null,
                        zoomLimitHit: false,
                    }),

                setRangeMode: (rangeMode) => set({ rangeMode }),

                setIntervalMode: (intervalMode) => set({ intervalMode }),

                applyPreset: (preset) => {
                    const now = DateTime.utc();
                    set({
                        from: now.minus({ hours: PRESET_HOURS[preset] }).toISO(),
                        to: now.toISO(),
                        rangeMode: preset as RangeMode,
                        activated: false,
                        zoomStack: [],
                        playheadPosition: null,
                        zoomLimitHit: false,
                    });
                },

                activate: () => {
                    // Preset rangeModes represent an intent like "last N
                    // hours relative to now". The concrete from/to timestamps
                    // stored in state are computed when the preset is applied,
                    // but can go stale if the user waits before activating.
                    // Refresh them at the moment of activation so the request
                    // reflects the user's current intent.
                    const { rangeMode } = get();
                    if (rangeMode !== "custom") {
                        const now = DateTime.utc();
                        set({
                            from: now.minus({ hours: PRESET_HOURS[rangeMode] }).toISO(),
                            to: now.toISO(),
                            activated: true,
                            zoomLimitHit: false,
                        });
                    } else {
                        set({ activated: true, zoomLimitHit: false });
                    }
                },

                pushZoom: (from, to) => {
                    const { from: currentFrom, to: currentTo, rangeMode, zoomStack } = get();
                    if (!currentFrom || !currentTo) return;
                    // Refuse zooms narrower than the minimum width — chunks
                    // are 1-minute resolution and anything finer than ~15
                    // minutes is unhelpful (and triggers repeated empty
                    // submits as the user drills in).
                    const rangeMs =
                        DateTime.fromISO(to).toMillis() - DateTime.fromISO(from).toMillis();
                    // Too-narrow brush: don't apply it, but surface a "minimum
                    // zoom reached" hint instead of silently doing nothing.
                    if (rangeMs < MIN_ZOOM_RANGE_MS) {
                        set({ zoomLimitHit: true });
                        return;
                    }
                    set({
                        zoomStack: [...zoomStack, { from: currentFrom, to: currentTo, rangeMode }],
                        from,
                        to,
                        rangeMode: "custom" as RangeMode,
                        playheadPosition: null,
                        zoomLimitHit: false,
                    });
                },

                popZoom: () => {
                    const { zoomStack } = get();
                    if (zoomStack.length === 0) return;
                    const previous = zoomStack[zoomStack.length - 1];
                    set({
                        zoomStack: zoomStack.slice(0, -1),
                        from: previous.from,
                        to: previous.to,
                        rangeMode: previous.rangeMode,
                        playheadPosition: null,
                        zoomLimitHit: false,
                    });
                },

                resetZoom: () => {
                    const { zoomStack } = get();
                    if (zoomStack.length === 0) return;
                    const original = zoomStack[0];
                    set({
                        zoomStack: [],
                        from: original.from,
                        to: original.to,
                        rangeMode: original.rangeMode,
                        playheadPosition: null,
                        zoomLimitHit: false,
                    });
                },

                setPlayheadPosition: (playheadPosition) => set({ playheadPosition }),

                setPanelHeight: (height) =>
                    set({
                        panelHeight: Math.max(MIN_PANEL_HEIGHT, Math.min(MAX_PANEL_HEIGHT, height)),
                    }),

                retry: () =>
                    // Retry is an explicit re-engagement: clear any prior
                    // dismissal so a recurring error reappears with feedback.
                    set((s) => ({ retryNonce: s.retryNonce + 1, dismissedSignature: null })),

                dismissSignature: (signature) => set({ dismissedSignature: signature }),

                setShowActiveEvents: (showActiveEvents) => set({ showActiveEvents }),

                setShowActiveEventUpdates: (showActiveEventUpdates) =>
                    set({ showActiveEventUpdates }),

                toggleSeriesHidden: (id) =>
                    set((s) => ({
                        hiddenSourceIds: s.hiddenSourceIds.includes(id)
                            ? s.hiddenSourceIds.filter((x) => x !== id)
                            : [...s.hiddenSourceIds, id],
                    })),

                setSourceColor: (id, color) =>
                    set((s) => ({
                        sourceColorOverrides: { ...s.sourceColorOverrides, [id]: color },
                    })),

                resetSourceColor: (id) =>
                    set((s) => {
                        if (!(id in s.sourceColorOverrides)) return {};
                        const next = { ...s.sourceColorOverrides };
                        delete next[id];
                        return { sourceColorOverrides: next };
                    }),

                toggleSettings: () => set((s) => ({ settingsOpen: !s.settingsOpen })),

                closeSettings: () => set({ settingsOpen: false }),
            },
        }),
        {
            name: "opencop:historical",
            version: 2,
            // Cumulative migration:
            //   v0 → v1: a single `sourceId: string | null` became
            //     `sourceIds: string[]`; coerce the old value to a one-element
            //     (or empty) array so existing users don't hydrate a broken shape.
            //   v1 → v2: the `showDensity` display toggle was removed (density is
            //     now always shown); drop the stale persisted field.
            // (`subjectTypes` needs no migration step — a persisted state without
            // it hydrates `{}` from the initial state via persist's shallow merge.
            // The histogram series toggles were renamed `showChunks`/`showSparkline`
            // → `showActiveEvents`/`showActiveEventUpdates` without a migration: any
            // stale old keys are simply ignored and the new toggles hydrate from
            // their defaults, which match the prior defaults.)
            migrate: (persisted, version) => {
                if (!persisted || typeof persisted !== "object") {
                    return persisted as HistoricalStore;
                }
                const state = persisted as Record<string, unknown>;
                if (version < 1) {
                    const sourceId = state.sourceId;
                    delete state.sourceId;
                    state.sourceIds = typeof sourceId === "string" && sourceId ? [sourceId] : [];
                }
                if (version < 2) {
                    delete state.showDensity;
                }
                return state as unknown as HistoricalStore;
            },
            // Return type annotated so the persisted shape stays a compile-checked
            // contract — see {@link PersistedHistoricalState}.
            partialize: (state): PersistedHistoricalState => ({
                sourceIds: state.sourceIds,
                subjectTypes: state.subjectTypes,
                from: state.from,
                to: state.to,
                rangeMode: state.rangeMode,
                intervalMode: state.intervalMode,
                panelHeight: state.panelHeight,
                showActiveEvents: state.showActiveEvents,
                showActiveEventUpdates: state.showActiveEventUpdates,
                sourceColorOverrides: state.sourceColorOverrides,
            }),
        },
    ),
);

// ---------------------------------------------------------------------------
// Selector hooks
// ---------------------------------------------------------------------------

export function useHistoricalMode(): "live" | "historical" {
    return useHistoricalStore((s) => s.mode);
}

/**
 * Bulk selector for the persisted + transient query-shaping state. Omits
 * `settingsOpen` (UI mode), `dismissedSignature` (status-bar UX), and
 * `zoomLimitHit` (transient zoom feedback) so this hook doesn't re-render when
 * the settings panel toggles, the user dismisses a status message, or a too-small
 * brush is rejected — consumers that need those values use the dedicated
 * `useSettingsOpen()` / `useDismissedSignature()` / `useZoomLimitHit()` selectors.
 */
export function useHistoricalState(): Omit<
    HistoricalState,
    | "settingsOpen"
    | "dismissedSignature"
    | "hiddenSourceIds"
    | "sourceColorOverrides"
    | "zoomLimitHit"
> {
    return useHistoricalStore(
        useShallow((s) => ({
            mode: s.mode,
            sourceIds: s.sourceIds,
            subjectTypes: s.subjectTypes,
            from: s.from,
            to: s.to,
            rangeMode: s.rangeMode,
            intervalMode: s.intervalMode,
            activated: s.activated,
            zoomStack: s.zoomStack,
            playheadPosition: s.playheadPosition,
            panelHeight: s.panelHeight,
            retryNonce: s.retryNonce,
            showActiveEvents: s.showActiveEvents,
            showActiveEventUpdates: s.showActiveEventUpdates,
        })),
    );
}

export function useHistoricalActions(): HistoricalActions {
    return useHistoricalStore((s) => s.actions);
}

export function useIsActivated(): boolean {
    return useHistoricalStore((s) => s.activated);
}

export function useZoomStack(): { depth: number; canPop: boolean } {
    return useHistoricalStore(
        useShallow((s) => ({
            depth: s.zoomStack.length,
            canPop: s.zoomStack.length > 0,
        })),
    );
}

export function usePlayheadPosition(): string | null {
    return useHistoricalStore((s) => s.playheadPosition);
}

export function usePanelHeight(): number {
    return useHistoricalStore((s) => s.panelHeight);
}

export function useRetryNonce(): number {
    return useHistoricalStore((s) => s.retryNonce);
}

export function useDismissedSignature(): string | null {
    return useHistoricalStore((s) => s.dismissedSignature);
}

export function useShowActiveEvents(): boolean {
    return useHistoricalStore((s) => s.showActiveEvents);
}

export function useShowActiveEventUpdates(): boolean {
    return useHistoricalStore((s) => s.showActiveEventUpdates);
}

export function useHiddenSourceIds(): string[] {
    return useHistoricalStore((s) => s.hiddenSourceIds);
}

export function useSourceColorOverrides(): Record<string, string> {
    return useHistoricalStore((s) => s.sourceColorOverrides);
}

export function useSettingsOpen(): boolean {
    return useHistoricalStore((s) => s.settingsOpen);
}

export function useZoomLimitHit(): boolean {
    return useHistoricalStore((s) => s.zoomLimitHit);
}
