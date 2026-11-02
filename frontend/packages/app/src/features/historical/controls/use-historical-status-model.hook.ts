// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * Derives the status-bar view model from the union of source state, chunk
 * audit, histogram query, and zoom state. Returns a single discriminated
 * `tier` plus the actions wired to it. Lives separate from the panel so the
 * cascade is unit-testable without rendering.
 */
import { DateTime } from "luxon";
import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import { assertNever } from "assert-never";
import type { TFunction } from "i18next";

import {
    useChunkRanges,
    useLoadError,
    useLoadingProgress,
    type ChunkRange,
    type LoadingProgress,
    type SourceError,
} from "../data-plane/HistoricalDataContext";
import { useMultiSourceHistograms } from "../histogram/use-multi-source-histograms.hook";
import {
    computeBars,
    isRangeOverLimit,
    MAX_HISTORICAL_RANGE_DAYS,
    MIN_ZOOM_RANGE_MS,
    useDismissedSignature,
    useHistoricalActions,
    useHistoricalState,
    useIsActivated,
    usePlayheadPosition,
    useZoomLimitHit,
    useZoomStack,
} from "../data-plane/historical.store";

/**
 * Tagged tier the status bar should display. Higher-priority tiers (actionable,
 * loading) pre-empt lower-priority hints in the order defined in
 * `useHistoricalStatusModel`.
 */
export type StatusTier =
    | { kind: "empty" }
    | {
          kind: "actionable";
          signature: string;
          /**
           * Short, glanceable label. Always rendered inline in the bar; will
           * truncate with ellipsis if the viewport is narrow.
           */
          headline: string;
          /**
           * Longer explanation. Surfaced behind a `More` link with a tooltip
           * so the bar stays one line. Null when the headline is self-
           * explanatory (e.g. "N data parts missing").
           */
          detail: string | null;
          canRetry: boolean;
          /**
           * When false, the Dismiss button is hidden. Used for client-side
           * validation states (e.g. range exceeds 30 days) where dismissing
           * would just hide useful info without fixing the underlying problem.
           */
          dismissible: boolean;
      }
    | {
          /** Streamed event chunks are still arriving. */
          kind: "loading";
          loaded: number;
          total: number;
          /** Subjects the query fanned out to; 1 means a chunk is a time slice. */
          subjects: number;
      }
    | {
          /**
           * The activity chart's own REST query is still in flight. Deliberately
           * distinct from `loading`, which tracks the streamed event chunks: the
           * two are independent requests and the chart routinely outlives the
           * stream, which is exactly the case this tier exists to narrate.
           */
          kind: "preparing";
          /** 1 on the first request; above 1 while a retry is running. */
          attempt: number;
          totalAttempts: number;
      }
    | { kind: "hint"; message: string };

export interface HistoricalStatusModel {
    tier: StatusTier;
    zoomDepth: number;
    playheadSet: boolean;
    activated: boolean;
    onRetry: () => void;
    onDismiss: () => void;
    onPopZoom: () => void;
    onResetZoom: () => void;
}

export function useHistoricalStatusModel(): HistoricalStatusModel {
    const { t } = useTranslation();
    const { sourceIds, from, to, intervalMode } = useHistoricalState();
    const activated = useIsActivated();
    const playheadPosition = usePlayheadPosition();
    const loadError = useLoadError();
    const loadingProgress = useLoadingProgress();
    const chunks = useChunkRanges();
    const dismissedSignature = useDismissedSignature();
    const { depth: zoomDepth } = useZoomStack();
    const zoomLimitHit = useZoomLimitHit();
    const { popZoom, resetZoom, retry, dismissSignature } = useHistoricalActions();

    const bars = from && to ? computeBars(from, to, intervalMode) : 0;
    // One histogram per selected source — the "no events" hint must reflect the
    // union of every source, not just the first (a multi-source view shows
    // events whenever *any* source has activity in the window).
    // `allResolved` gates the idle interaction hints and the "no events" verdict
    // rather than "any source resolved": with a mix of fast and slow sources, one
    // quick response was enough to make the bar invite the operator to brush and
    // scrub while the spinner was still running on the others.
    const {
        responses,
        isLoading: histogramLoading,
        allResolved: histogramAllResolved,
        errorCount: histogramFailedCount,
        total: histogramTotal,
        attempt: histogramAttempt,
        totalAttempts: histogramTotalAttempts,
    } = useMultiSourceHistograms(sourceIds, { from: from ?? "", to: to ?? "", bars }, activated);

    const missingPartsCount = useMemo(
        () =>
            chunks.reduce(
                (n, c: ChunkRange) =>
                    c.status === "missing" || c.status === "incomplete-parts" ? n + 1 : n,
                0,
            ),
        [chunks],
    );

    const chunkEventCount = useMemo(
        () => chunks.reduce((n, c: ChunkRange) => n + c.count, 0),
        [chunks],
    );

    // The current zoom is at the floor when the range duration is within one
    // minute of the minimum — chunks are 1-minute resolution, so even a 16-min
    // range is effectively at maximum useful zoom.
    const atMaxZoom = useMemo(() => {
        if (!from || !to) return false;
        const ms = DateTime.fromISO(to).toMillis() - DateTime.fromISO(from).toMillis();
        return ms <= MIN_ZOOM_RANGE_MS + 60_000;
    }, [from, to]);

    // "No events in this range" reads from the histogram (the visible source of
    // truth) rather than chunk element counts. The two can disagree — chunks
    // may stream empty while the histogram endpoint still reports activity —
    // and we want the hint to match what the user actually sees rendered.
    const histogramHasEvents = useMemo(
        () =>
            [...responses.values()].some((response) =>
                (response?.buckets ?? []).some((bucket) => bucket.values.some((v) => v.count > 0)),
            ),
        [responses],
    );
    const showNoEventsHint = activated && histogramAllResolved && !histogramHasEvents && !loadError;

    // Diagnostic: histogram endpoint reports events in this range but the WS
    // chunk stream delivered all-empty chunks. Backend / chunk-pipeline
    // inconsistency — surfaced as an error so it's noticed during in-flight
    // backend work.
    const chunkPipelineEmpty =
        activated &&
        !loadingProgress &&
        chunks.length > 0 &&
        missingPartsCount === 0 &&
        chunkEventCount === 0 &&
        histogramHasEvents &&
        !loadError;

    // Client-side validation: the current (from, to) span exceeds the
    // backend's 30-day cap. Surfaced as an actionable warning so the user
    // sees *why* Activate is disabled; pickers stay unconstrained.
    const rangeExceedsLimit = isRangeOverLimit(from, to);

    const tier = useMemo<StatusTier>(
        () =>
            computeStatusTier({
                loadError,
                chunkPipelineEmpty,
                missingPartsCount,
                rangeExceedsLimit,
                dismissedSignature,
                canRetry: sourceIds.length > 0 && !!from && !!to,
                loadingProgress,
                activated,
                showNoEventsHint,
                atMaxZoom,
                zoomDepth,
                playheadSet: !!playheadPosition,
                zoomLimitHit,
                histogram: {
                    pending: histogramLoading,
                    allResolved: histogramAllResolved,
                    failedCount: histogramFailedCount,
                    attempt: histogramAttempt,
                    totalAttempts: histogramTotalAttempts,
                    total: histogramTotal,
                },
                t,
            }),
        [
            loadError,
            chunkPipelineEmpty,
            missingPartsCount,
            rangeExceedsLimit,
            t,
            dismissedSignature,
            sourceIds,
            from,
            to,
            loadingProgress,
            activated,
            showNoEventsHint,
            atMaxZoom,
            zoomDepth,
            playheadPosition,
            zoomLimitHit,
            histogramLoading,
            histogramAllResolved,
            histogramFailedCount,
            histogramTotal,
            histogramAttempt,
            histogramTotalAttempts,
        ],
    );

    const onDismiss = useMemo(
        () => () => {
            if (tier.kind === "actionable") dismissSignature(tier.signature);
        },
        [tier, dismissSignature],
    );

    return {
        tier,
        zoomDepth,
        playheadSet: !!playheadPosition,
        activated,
        onRetry: retry,
        onDismiss,
        onPopZoom: popZoom,
        onResetZoom: resetZoom,
    };
}

/**
 * Pure cascade: pick the StatusTier from the union of all status inputs.
 * Exported for unit testing — the panel uses this through the hook above.
 *
 * Priority (highest first):
 *   1. actionable (transport error / chunkPipelineEmpty / missingParts) —
 *      suppressed if the user has dismissed this exact signature
 *   2. loading (a plan exists and chunks are streaming)
 *   3. preparing (the activity chart's REST query is still in flight)
 *   4. empty (pre-activation — histogram placeholder owns the message)
 *   5. hint: min-zoom / partial-chart-failure / no-events / max-zoom /
 *      brush+playhead / playhead
 *
 * `preparing` sits above every hint on purpose. The chart's query and the chunk
 * stream are independent, and the chart routinely finishes minutes after the
 * stream does; without this tier the cascade fell through to the idle
 * "click and drag to zoom" hint while the chart's spinner was still running.
 */
export interface ComputeStatusTierInput {
    loadError: SourceError | null;
    chunkPipelineEmpty: boolean;
    missingPartsCount: number;
    rangeExceedsLimit: boolean;
    dismissedSignature: string | null;
    canRetry: boolean;
    loadingProgress: LoadingProgress | null;
    activated: boolean;
    showNoEventsHint: boolean;
    atMaxZoom: boolean;
    zoomDepth: number;
    playheadSet: boolean;
    /** The user just tried to brush a zoom narrower than the minimum width. */
    zoomLimitHit: boolean;
    /** State of the per-source activity-chart requests, which load separately
     * from the chunk stream and routinely outlive it. */
    histogram: {
        /** At least one source's request is still in flight. */
        readonly pending: boolean;
        /** Every selected source's chart has resolved with data. */
        readonly allResolved: boolean;
        /** Sources whose chart gave up after its last attempt. */
        readonly failedCount: number;
        /** Attempt number of the most-retried in-flight request; 1 is the first try. */
        readonly attempt: number;
        /** Attempts a request makes before it is declared failed. */
        readonly totalAttempts: number;
        /** Selected sources — the denominator for a partial-failure report. */
        readonly total: number;
    };
    t: TFunction;
}

export function computeStatusTier(input: ComputeStatusTierInput): StatusTier {
    const {
        loadError,
        chunkPipelineEmpty,
        missingPartsCount,
        rangeExceedsLimit,
        dismissedSignature,
        canRetry,
        loadingProgress,
        activated,
        showNoEventsHint,
        atMaxZoom,
        zoomDepth,
        playheadSet,
        zoomLimitHit,
        histogram,
        t,
    } = input;

    const actionable = computeActionableTier({
        loadError,
        chunkPipelineEmpty,
        missingPartsCount,
        rangeExceedsLimit,
        t,
    });
    if (actionable && dismissedSignature !== actionable.signature) {
        return {
            kind: "actionable",
            signature: actionable.signature,
            headline: actionable.headline,
            detail: actionable.detail,
            canRetry: canRetry && actionable.retryable,
            dismissible: actionable.dismissible,
        };
    }

    if (loadingProgress) {
        return {
            kind: "loading",
            loaded: loadingProgress.loaded,
            total: loadingProgress.total,
            subjects: loadingProgress.subjects,
        };
    }

    // Before activation the histogram placeholder explains what to do, so
    // the status bar is empty (height stays reserved by the caller).
    if (!activated) return { kind: "empty" };

    // The chart's own request is still running. Ranks above every hint because
    // the chart draws a spinner the whole time, and a spinner next to text
    // inviting the operator to interact reads as a hung view.
    if (histogram.pending) {
        return {
            kind: "preparing",
            attempt: Math.max(1, histogram.attempt),
            totalAttempts: histogram.totalAttempts,
        };
    }

    // Direct feedback on the gesture that just failed: the user brushed a zoom
    // narrower than the minimum width. Takes precedence over the ambient hints
    // (it reflects an action they just took) but not over real load state.
    if (zoomLimitHit) return { kind: "hint", message: t("historical.statusHintMinZoom") };

    // Some charts resolved and some gave up. The panel still draws the healthy
    // sources, so the bar is where the operator learns the picture is partial.
    // Deliberately a hint, not an actionable: `actionable` offers Retry, which
    // calls the store's `retry` → bumps `retryNonce`. That nonce is only in the
    // chunk stream's key (`HistoricalDataContext`), never the histogram query
    // key — so a Retry button here would look live and refetch nothing.
    if (histogram.failedCount > 0 && histogram.failedCount < histogram.total) {
        return {
            kind: "hint",
            message: t("historical.statusHintChartPartial", {
                count: histogram.failedCount,
                total: histogram.total,
            }),
        };
    }

    if (showNoEventsHint) return { kind: "hint", message: t("historical.statusHintNoEvents") };
    if (atMaxZoom) return { kind: "hint", message: t("historical.statusHintMaxZoom") };

    // Idle interaction hints only once every selected source's chart is on
    // screen — inviting the operator to zoom or scrub while a chart is still
    // missing (or has failed outright) reads as broken.
    if (!histogram.allResolved) return { kind: "empty" };

    // Default idle state — until the user has zoomed (zoom stack empty),
    // surface both interactions at once via a single translatable key so
    // translators can rephrase / reorder freely. Keyed off zoom depth, not
    // range mode: activating with a hand-picked custom range is still
    // zoom-depth 0 and should advertise click-and-drag-to-zoom.
    if (zoomDepth === 0) {
        return { kind: "hint", message: t("historical.statusHintBrushAndPlayhead") };
    }
    if (!playheadSet) {
        return { kind: "hint", message: t("historical.statusHintPlayhead") };
    }
    return { kind: "empty" };
}

/**
 * Pick the highest-priority actionable condition, if any. Pure helper —
 * exported for unit testing the cascade.
 *
 * Each actionable condition produces a `headline` (short, glanceable, always
 * rendered) and an optional `detail` (longer explanation, surfaced behind a
 * "More" affordance). Conditions whose full description fits a glance return
 * `detail: null` so the bar omits the affordance.
 *
 * `retryable` controls whether the Retry button is shown. `dismissible`
 * controls Dismiss. Transport / audit failures default to both true; client-
 * side validation states (e.g. `rangeExceedsLimit`) set both false because
 * Retry doesn't help and Dismiss would just hide useful info while the
 * Activate button stays disabled.
 */
export interface ActionableTier {
    signature: string;
    headline: string;
    detail: string | null;
    retryable: boolean;
    dismissible: boolean;
}

export function computeActionableTier(input: {
    loadError: SourceError | null;
    chunkPipelineEmpty: boolean;
    missingPartsCount: number;
    rangeExceedsLimit: boolean;
    t: TFunction;
}): ActionableTier | null {
    const { loadError, chunkPipelineEmpty, missingPartsCount, rangeExceedsLimit, t } = input;
    if (loadError) {
        const { headline, detail } = formatErrorParts(loadError, t);
        return {
            signature: signatureFor(loadError),
            headline,
            detail,
            retryable: true,
            dismissible: true,
        };
    }
    if (rangeExceedsLimit) {
        return {
            signature: "rangeExceedsLimit",
            headline: t("historical.errorRangeTooLargeHeadline", {
                days: MAX_HISTORICAL_RANGE_DAYS,
            }),
            detail: t("historical.errorRangeTooLargeDetail", {
                days: MAX_HISTORICAL_RANGE_DAYS,
            }),
            retryable: false,
            dismissible: false,
        };
    }
    if (chunkPipelineEmpty) {
        return {
            signature: "chunkPipelineEmpty",
            headline: t("historical.statusErrorEmptyChunksHeadline"),
            detail: t("historical.statusErrorEmptyChunksDetail"),
            retryable: true,
            dismissible: true,
        };
    }
    if (missingPartsCount > 0) {
        return {
            signature: `missingParts:${missingPartsCount}`,
            headline: t("historical.statusMissingParts", { count: missingPartsCount }),
            detail: null,
            retryable: true,
            dismissible: true,
        };
    }
    return null;
}

export function signatureFor(err: SourceError): string {
    switch (err.kind) {
        case "concurrentLimit":
        case "rangeInvalid":
        case "rangeTooLarge":
        case "tooManyChunks":
        case "sourcesRequired":
        case "filtersNotSupported":
        case "layerNotSupported":
        case "subjectForbidden":
        case "heartbeatTimeout":
        case "jobSuperseded":
            return err.kind;
        case "jobFailed":
            // Stage AND reason both participate so a different exception
            // message in the same category re-shows after dismissal.
            return `${err.kind}:${err.stage}:${err.reason}`;
        case "badRequest":
            return `${err.kind}:${err.message}`;
        case "generic":
            return `${err.kind}:${err.status}:${err.message}`;
        default:
            return assertNever(err);
    }
}

/**
 * Map a transport-level `SourceError` to a user-facing headline + detail pair.
 * Generic / jobFailed pull the detail from the server-supplied message verbatim
 * so we don't paper over backend specifics; the headline stays canned so the
 * bar is glanceable regardless.
 */
export function formatErrorParts(
    err: SourceError,
    t: TFunction,
): { headline: string; detail: string | null } {
    switch (err.kind) {
        // -- REST (classified off backend `body.code`) ----------------------
        case "concurrentLimit":
            return {
                headline: t("historical.errorConcurrentJobLimitHeadline"),
                detail: t("historical.errorConcurrentJobLimitDetail"),
            };
        case "rangeInvalid":
            return {
                headline: t("historical.errorRangeInvalidHeadline"),
                detail: t("historical.errorRangeInvalidDetail"),
            };
        case "rangeTooLarge":
            return {
                headline: t("historical.errorRangeTooLargeHeadline", {
                    days: MAX_HISTORICAL_RANGE_DAYS,
                }),
                detail: t("historical.errorRangeTooLargeDetail", {
                    days: MAX_HISTORICAL_RANGE_DAYS,
                }),
            };
        case "tooManyChunks":
            return {
                headline: t("historical.errorTooManyChunksHeadline"),
                detail: t("historical.errorTooManyChunksDetail"),
            };
        case "sourcesRequired":
            return {
                headline: t("historical.errorSourcesRequiredHeadline"),
                detail: t("historical.errorSourcesRequiredDetail"),
            };
        case "filtersNotSupported":
            return {
                headline: t("historical.errorFiltersNotSupportedHeadline"),
                detail: t("historical.errorFiltersNotSupportedDetail"),
            };
        case "layerNotSupported":
            return {
                headline: t("historical.errorLayerNotSupportedHeadline"),
                detail: t("historical.errorLayerNotSupportedDetail"),
            };
        case "subjectForbidden":
            return {
                headline: t("historical.errorSubjectForbiddenHeadline"),
                detail: t("historical.errorSubjectForbiddenDetail"),
            };
        case "badRequest":
            // 4xx without a code we recognize — show the server message verbatim
            // under a generic-but-honest headline.
            return {
                headline: t("historical.errorBadRequestHeadline"),
                detail: err.message,
            };
        case "generic":
            return {
                headline: t("historical.errorGenericHeadline"),
                detail: t("historical.errorGenericDetail", { message: err.message }),
            };
        // -- WS / source-level ---------------------------------------------
        case "heartbeatTimeout":
            return {
                headline: t("historical.errorHeartbeatTimeoutHeadline"),
                detail: t("historical.errorHeartbeatTimeoutDetail"),
            };
        case "jobSuperseded":
            return {
                headline: t("historical.errorJobSupersededHeadline"),
                detail: t("historical.errorJobSupersededDetail"),
            };
        case "jobFailed":
            return formatJobFailedParts(err.stage, err.reason, t);
        default:
            return assertNever(err);
    }
}

/**
 * Map a `history.job.failed` `stage` value to user-facing copy. Specific
 * `EXECUTION_FAILED_*` stages get category-specific headlines; transient
 * stages (`STORAGE`, `TIMEOUT`) get canned reassurance text and hide the raw
 * backend reason. Truly-unknown failures pass the verbatim backend reason as
 * the detail so the user has *something* to act on.
 *
 * `EXECUTION_FAILED_UNKNOWN` is the backend's permanent catch-all for failure
 * shapes it doesn't recognize (and the converted form of pre-subdivision
 * `"EXECUTION_FAILED"` snapshots in Redis). The legacy literal `EXECUTION_FAILED`
 * is still listed as a case label so the deploy window — where in-flight events
 * from pre-MR Java replicas may still carry the old wire value — renders the
 * same generic copy.
 */
function formatJobFailedParts(
    stage: string,
    reason: string,
    t: TFunction,
): { headline: string; detail: string | null } {
    switch (stage) {
        case "INGRESS_REJECTED":
            return {
                headline: t("historical.errorJobIngressRejectedHeadline"),
                detail: t("historical.errorJobIngressRejectedDetail"),
            };
        case "TIMEOUT":
            return {
                headline: t("historical.errorJobTimeoutHeadline"),
                detail: t("historical.errorJobTimeoutDetail"),
            };
        case "EXECUTION_FAILED_STORAGE":
            return {
                headline: t("historical.errorJobStorageHeadline"),
                detail: t("historical.errorJobStorageDetail"),
            };
        case "EXECUTION_FAILED_TIMEOUT":
            return {
                headline: t("historical.errorJobSubTimeoutHeadline"),
                detail: t("historical.errorJobSubTimeoutDetail"),
            };
        case "EXECUTION_FAILED_INTERRUPTED":
            return {
                headline: t("historical.errorJobInterruptedHeadline"),
                detail: t("historical.errorJobInterruptedDetail"),
            };
        case "DELIVERY_FAILED":
            return {
                headline: t("historical.errorJobDeliveryFailedHeadline"),
                detail: reason,
            };
        case "EXECUTION_FAILED_UNEXPECTED":
        case "EXECUTION_FAILED_UNKNOWN": // permanent catch-all for unrecognized stage names
        case "EXECUTION_FAILED": // legacy wire value from pre-MR Java replicas during deploy
        default:
            return {
                headline: t("historical.errorJobFailedHeadline"),
                detail: reason,
            };
    }
}
